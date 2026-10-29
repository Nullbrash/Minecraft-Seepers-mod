package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.MemoryCardItem;
import io.github.nullbrash.seepers.network.payload.CardActivePayload;
import io.github.nullbrash.seepers.network.payload.CardDropPayload;
import io.github.nullbrash.seepers.network.payload.CardSectionPayload;
import io.github.nullbrash.seepers.network.payload.CardUploadPayload;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the memory cards (CLAUDE.md 5.3): which card each player's lidar holds (the one in hand; with no
 * lidar in hand, the last one stays), its sections streamed to the player nearest first within a byte budget, the
 * points the player scans taken in (only into the card in their hand, not locked, within an upload budget), and the
 * card's point count kept on the item for its tooltip.
 */
public final class CardService {
    private static final int COUNT_TICKS = 40;
    private static final int SAVE_TICKS = 1200;

    private static final class Session {
        @Nullable UUID card;
        int capacity;
        boolean writable;
        int epoch;
        /** Sections still to send, nearest first, and the one being sent. */
        final LongArrayList queue = new LongArrayList();
        int next;
        byte @Nullable [] sending;
        long sendingKey;
        int sendingOffset;
        /** Upload bytes the player may still send: refills at the limit's rate, holds up to two seconds of it. */
        double allowance;
        int allowanceTick;
        /** The card before the last change, still taking the points the client had not sent yet, until the tick. */
        @Nullable UUID previous;
        int previousCapacity;
        int previousUntil;
        /** A rewrite coming in parts: its card and section, and the records so far; null when none (or one broke). */
        @Nullable UUID rewriteCard;
        long rewriteKey;
        java.io.@Nullable ByteArrayOutputStream rewrite;
    }

    /** The client sends what the old card had not yet received within its budget, for up to 120 s. */
    private static final int PREVIOUS_CARD_TICKS = 2600;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int epochs;

    /** The lidar in the player's hand, main first; null if none. */
    private static @Nullable InteractionHand lidarHand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof LidarItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof LidarItem) return InteractionHand.OFF_HAND;
        return null;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        InteractionHand hand = lidarHand(player);
        if (hand != null) {
            ItemStack lidar = player.getItemInHand(hand);
            ItemStack card = LidarModules.card(lidar);
            UUID id = card.isEmpty() ? null : ensureId(player, lidar, card);
            CardData data = card.isEmpty() ? CardData.BLANK : MemoryCardItem.data(LidarModules.card(lidar));
            boolean writable = id != null && !data.noWrite();
            int capacity = card.isEmpty() ? 0 : MemoryCardItem.capacity(((MemoryCardItem) card.getItem()).level());
            if (!java.util.Objects.equals(id, session.card)) {
                activate(player, session, id, capacity, writable);
            } else if (writable != session.writable || capacity != session.capacity) {
                session.writable = writable;
                session.capacity = capacity;
                send(player, session);
            }
            if (id != null && player.tickCount % COUNT_TICKS == 0) {
                int points = (int) Math.min(Integer.MAX_VALUE, CardStore.get(player.server).live(id));
                if (points != data.points()) LidarModules.setCard(lidar, withData(card, data.withPoints(points)));
            }
        }
        stream(player, session);
        if (player.server.getTickCount() % SAVE_TICKS == 0) CardStore.get(player.server).saveAll();
    }

    /** A card going into a lidar gets its id here; a crafted copy is filled from its source then. */
    private static UUID ensureId(ServerPlayer player, ItemStack lidar, ItemStack card) {
        CardData data = MemoryCardItem.data(card);
        if (data.id().isPresent()) return data.id().get();
        UUID id = UUID.randomUUID();
        CardStore store = CardStore.get(player.server);
        data.copyOf().ifPresent(source -> store.copy(source, id));
        data = data.withId(id).withPoints((int) Math.min(Integer.MAX_VALUE, store.live(id)));
        LidarModules.setCard(lidar, withData(card, data));
        return id;
    }

    private static ItemStack withData(ItemStack card, CardData data) {
        ItemStack copy = card.copy();
        copy.set(SeepersComponents.CARD.get(), data);
        return copy;
    }

    /** Another card (or none): the player's cloud starts over, the new card's sections follow nearest first. */
    private static void activate(ServerPlayer player, Session session, @Nullable UUID card, int capacity, boolean writable) {
        if (session.card != null && session.writable && !session.card.equals(card)) {
            session.previous = session.card;
            session.previousCapacity = session.capacity;
            session.previousUntil = player.server.getTickCount() + PREVIOUS_CARD_TICKS;
        }
        session.card = card;
        session.capacity = capacity;
        session.writable = writable;
        session.epoch = ++epochs;
        session.queue.clear();
        session.next = 0;
        session.sending = null;
        if (card != null) {
            var sections = CardStore.get(player.server).sections(card);
            session.queue.addAll(sections.keySet());
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            long[] keys = session.queue.toLongArray();
            Long[] boxed = java.util.Arrays.stream(keys).boxed().toArray(Long[]::new);
            java.util.Arrays.sort(boxed, java.util.Comparator.comparingDouble(key -> distance(key, px, py, pz)));
            session.queue.clear();
            for (Long key : boxed) session.queue.add(key.longValue());
            io.github.nullbrash.seepers.Seepers.LOGGER.debug("Card {} active for {}: {} sections, {} live", card, player.getGameProfile().getName(),
                    sections.size(), CardStore.get(player.server).live(card));
        }
        send(player, session);
    }

    private static double distance(long key, double x, double y, double z) {
        double dx = SectionPos.sectionToBlockCoord(SectionPos.x(key)) + 8 - x;
        double dy = SectionPos.sectionToBlockCoord(SectionPos.y(key)) + 8 - y;
        double dz = SectionPos.sectionToBlockCoord(SectionPos.z(key)) + 8 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void send(ServerPlayer player, Session session) {
        PacketDistributor.sendToPlayer(player, new CardActivePayload(Optional.ofNullable(session.card), session.capacity, session.writable, session.epoch));
    }

    /** This tick's share of the download budget: section files in pieces. */
    private static void stream(ServerPlayer player, Session session) {
        if (session.card == null) return;
        int budget = ServerConfig.get(ServerConfig.LIDAR_CARD_DOWNLOAD_KB) * 1024 / 20;
        CardStore store = CardStore.get(player.server);
        while (budget > 0) {
            if (session.sending == null) {
                if (session.next >= session.queue.size()) return;
                session.sendingKey = session.queue.getLong(session.next++);
                session.sending = store.read(session.card, session.sendingKey);
                session.sendingOffset = 0;
            }
            byte[] all = session.sending;
            int length = Math.min(Math.min(CardSectionPayload.MAX_BYTES, all.length - session.sendingOffset), Math.max(budget, CardRecords.BYTES));
            length -= length % CardRecords.BYTES;
            byte[] piece = java.util.Arrays.copyOfRange(all, session.sendingOffset, session.sendingOffset + length);
            session.sendingOffset += length;
            boolean last = session.sendingOffset >= all.length;
            PacketDistributor.sendToPlayer(player, new CardSectionPayload(session.card, session.epoch, session.sendingKey, piece, last));
            budget -= Math.max(length, 64);
            if (last) session.sending = null;
        }
    }

    public static void handleUpload(CardUploadPayload upload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        boolean current = session.card != null && session.card.equals(upload.card()) && session.writable;
        boolean previous = upload.card().equals(session.previous) && player.server.getTickCount() < session.previousUntil;
        if (!current && !previous) {
            dropped(player, "not the card in hand", upload.records().length);
            return;
        }
        double rate = ServerConfig.get(ServerConfig.LIDAR_CARD_UPLOAD_KB) * 1024.0;
        int now = player.server.getTickCount();
        session.allowance = Math.min(session.allowance + rate * (now - session.allowanceTick) / 20, rate * 2);
        session.allowanceTick = now;
        boolean affordable = session.allowance >= upload.records().length;
        if (affordable) session.allowance -= upload.records().length;
        boolean valid = affordable && upload.records().length % CardRecords.BYTES == 0 && upload.live() >= 0 && upload.live() <= 16 * 16 * 16 * 1024;
        byte[] records = upload.records();
        if (upload.rewrite()) {
            // A rewrite replaces the file only whole: a part missing (over budget, out of order) and it is dropped.
            if ((upload.mode() & CardUploadPayload.FIRST) != 0) {
                session.rewriteCard = upload.card();
                session.rewriteKey = upload.section();
                session.rewrite = new java.io.ByteArrayOutputStream();
            } else if (!upload.card().equals(session.rewriteCard) || upload.section() != session.rewriteKey) {
                session.rewrite = null;
            }
            if (!valid || session.rewrite == null || session.rewrite.size() + records.length > CardStore.MAX_SECTION_BYTES) {
                if (session.rewrite != null || valid) dropped(player, "rewrite broken (" + (valid ? "order/size" : "over budget") + ")", records.length);
                session.rewrite = null;
                return;
            }
            session.rewrite.write(records, 0, records.length);
            if ((upload.mode() & CardUploadPayload.LAST) == 0) return;
            records = session.rewrite.toByteArray();
            session.rewrite = null;
        } else if (!valid) {
            dropped(player, "over upload budget or invalid", records.length);
            return;
        } else if (records.length == 0) {
            // Only the section's count (it changed on the player's side without new points).
            CardStore.get(player.server).setLive(upload.card(), upload.section(), upload.live());
            return;
        }
        LongArrayList dropped = CardStore.get(player.server).write(upload.card(), upload.section(), records, upload.live(),
                upload.rewrite(), player.level().getGameTime(), current ? session.capacity : session.previousCapacity);
        if (current && !dropped.isEmpty()) PacketDistributor.sendToPlayer(player, new CardDropPayload(session.card, dropped.toLongArray()));
    }

    /** Card points the server could not keep: worth a line in the log (their loss shows only on the next replay). */
    private static void dropped(ServerPlayer player, String why, int bytes) {
        io.github.nullbrash.seepers.Seepers.LOGGER.warn("Card upload from {} dropped: {}, {} records", player.getGameProfile().getName(), why,
                bytes / CardRecords.BYTES);
    }

    /** The erase button: the card in the open lidar empties (unless its author locked it). */
    public static void erase(ServerPlayer player, ItemStack lidar) {
        ItemStack card = LidarModules.card(lidar);
        if (card.isEmpty()) return;
        CardData data = MemoryCardItem.data(card);
        if (data.noWrite() || data.id().isEmpty()) return;
        CardStore.get(player.server).erase(data.id().get());
        LidarModules.setCard(lidar, withData(card, data.withPoints(0)));
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && data.id().get().equals(session.card)) activate(player, session, session.card, session.capacity, session.writable);
    }

    /** The author's lock switches on the open lidar's card. */
    public static void lock(ServerPlayer player, ItemStack lidar, boolean write) {
        ItemStack card = LidarModules.card(lidar);
        if (card.isEmpty()) return;
        CardData data = MemoryCardItem.data(card);
        if (data.author().isEmpty() || !data.author().get().equals(player.getUUID())) return;
        LidarModules.setCard(lidar, withData(card, write ? data.withLocks(!data.noWrite(), data.noCopy()) : data.withLocks(data.noWrite(), !data.noCopy())));
    }

    /** Only the author renames a signed card: a name on it is the author's signature. */
    public static void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack card = event.getLeft();
        if (!(card.getItem() instanceof MemoryCardItem)) return;
        CardData data = MemoryCardItem.data(card);
        if (data.author().isPresent() && !data.author().get().equals(event.getPlayer().getUUID())) event.setCanceled(true);
    }

    /** Renaming a card on an anvil signs it: the author alone sets its locks from then on. */
    public static void onAnvilRepair(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent event) {
        ItemStack output = event.getOutput();
        if (!(output.getItem() instanceof MemoryCardItem) || !output.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) return;
        CardData data = MemoryCardItem.data(output);
        if (data.author().isEmpty()) {
            output.set(SeepersComponents.CARD.get(), data.withAuthor(event.getEntity().getUUID(), event.getEntity().getGameProfile().getName()));
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
        CardStore.close();
    }

    private CardService() {}
}
