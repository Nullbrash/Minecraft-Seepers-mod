package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.lidar.CardRecords;
import io.github.nullbrash.seepers.network.payload.CardActivePayload;
import io.github.nullbrash.seepers.network.payload.CardDropPayload;
import io.github.nullbrash.seepers.network.payload.CardSectionPayload;
import io.github.nullbrash.seepers.network.payload.CardUploadPayload;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of the memory cards (CLAUDE.md 5.3): the active card's points arrive from the server section by
 * section and are replayed into the cloud as they were scanned; the points the player scans into the card go back in
 * small packets. A section whose file has grown far past its live points (pixels replaced many times, data refreshed)
 * is rewritten whole. Without a writable card the lidar's points are unsaved: they fade in seconds.
 */
public final class CardSync {
    private static final int FLUSH_TICKS = 5;

    private static @Nullable UUID card;
    private static boolean writable;
    private static int epoch = -1;
    /** Records waiting to go to the server, by section, oldest first. */
    private static final Long2ObjectLinkedOpenHashMap<ByteArrayList> PENDING = new Long2ObjectLinkedOpenHashMap<>();
    /** Sections to rewrite whole, and records received per section of the current card. */
    private static final LongLinkedOpenHashSet REWRITE = new LongLinkedOpenHashSet();
    private static final Long2IntOpenHashMap RECEIVED = new Long2IntOpenHashMap();
    private static int ticks;

    static {
        PointCloud.setRecorder(new PointCloud.Recorder() {
            @Override
            public void record(long section, short x, short y, short z, int face, int material, int rgb) {
                if (card == null || !writable) return;
                ByteArrayList bytes = PENDING.computeIfAbsent(section, key -> new ByteArrayList());
                ByteBuffer record = ByteBuffer.allocate(CardRecords.BYTES);
                CardRecords.put(record, x, y, z, face, material, rgb);
                bytes.addElements(bytes.size(), record.array());
            }

            @Override
            public void rewrite(long section) {
                CardSync.rewrite(section);
            }
        });
    }

    /** Where the lidar's points go now: onto the card, or the unsaved share that fades. */
    public static PointSource target() {
        return card != null && writable ? PointSource.LIDAR : PointSource.LIDAR_UNSAVED;
    }

    public static boolean hasCard() {
        return card != null;
    }

    public static void active(CardActivePayload payload) {
        UUID next = payload.card().orElse(null);
        PointCloud cloud = PointCloud.get();
        if (!Objects.equals(next, card) || payload.epoch() != epoch) {
            // What the old card has not yet received goes now (the server takes it a few seconds more), not lost.
            if (card != null && writable && Objects.equals(next, card) == false) {
                while (!PENDING.isEmpty()) {
                    long key = PENDING.firstLongKey();
                    ByteArrayList bytes = PENDING.removeFirst();
                    send(key, bytes.toByteArray(), cloud == null ? 0 : cloud.liveIn(key, PointSource.LIDAR), false);
                }
            }
            if (cloud != null) cloud.clearSource(PointSource.LIDAR);
            PENDING.clear();
            REWRITE.clear();
            RECEIVED.clear();
        }
        card = next;
        epoch = payload.epoch();
        writable = payload.writable();
        PointSource.setLidarLimit(payload.capacity());
    }

    public static void section(CardSectionPayload payload) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || !payload.card().equals(card) || payload.epoch() != epoch) return;
        long key = payload.section();
        CardRecords.read(payload.records(), (x, y, z, face, material, rgb) -> cloud.addRecord(PointSource.LIDAR, key, x, y, z, face, material, rgb));
        RECEIVED.addTo(key, payload.records().length / CardRecords.BYTES);
        if (payload.last() && writable) {
            int live = cloud.liveIn(key, PointSource.LIDAR);
            if (RECEIVED.get(key) > live * 2 + 512) REWRITE.add(key);
        }
    }

    public static void drop(CardDropPayload payload) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || !payload.card().equals(card)) return;
        for (long key : payload.sections()) {
            cloud.dropSection(key, PointSource.LIDAR);
            PENDING.remove(key);
        }
    }

    /** The data refresh dropped wrong points from a section: the card gets the section anew. */
    public static void rewrite(long section) {
        if (card != null && writable) REWRITE.add(section);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % FLUSH_TICKS != 0 || card == null || !writable) return;
        PointCloud cloud = PointCloud.current();
        if (cloud == null) return;
        int budget = ServerConfig.get(ServerConfig.LIDAR_CARD_UPLOAD_KB) * 1024 * FLUSH_TICKS / 20 * 9 / 10;
        while (budget > 0 && !REWRITE.isEmpty()) {
            long key = REWRITE.removeFirstLong();
            PENDING.remove(key);
            byte[] all = cloud.exportRecords(key, PointSource.LIDAR);
            budget -= send(key, all, cloud.liveIn(key, PointSource.LIDAR), true);
        }
        while (budget > 0 && !PENDING.isEmpty()) {
            long key = PENDING.firstLongKey();
            ByteArrayList bytes = PENDING.removeFirst();
            budget -= send(key, bytes.toByteArray(), cloud.liveIn(key, PointSource.LIDAR), false);
        }
    }

    /** Sends a section's records in packets the server takes; the first one replaces when rewriting. Returns bytes sent. */
    private static int send(long section, byte[] records, int live, boolean replace) {
        int step = CardUploadPayload.MAX_BYTES - CardUploadPayload.MAX_BYTES % CardRecords.BYTES;
        int offset = 0;
        do {
            int length = Math.min(step, records.length - offset);
            PacketDistributor.sendToServer(new CardUploadPayload(card, section, live, replace && offset == 0,
                    java.util.Arrays.copyOfRange(records, offset, offset + length)));
            offset += length;
        } while (offset < records.length);
        return Math.max(records.length, 64);
    }

    public static void onLoggingOut() {
        card = null;
        writable = false;
        epoch = -1;
        PENDING.clear();
        REWRITE.clear();
        RECEIVED.clear();
        PointSource.setLidarLimit(0);
    }

    private CardSync() {}
}
