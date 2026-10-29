package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.lidar.CardRecords;
import io.github.nullbrash.seepers.network.payload.CardActivePayload;
import io.github.nullbrash.seepers.network.payload.CardDropPayload;
import io.github.nullbrash.seepers.network.payload.CardSectionPayload;
import io.github.nullbrash.seepers.network.payload.CardUploadPayload;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.UUID;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of the memory cards (CLAUDE.md 5.3): the active card's points arrive from the server section by
 * section and are replayed into the cloud as they were scanned; the points the player scans into the card go back in
 * small packets, within the server's upload budget (what goes past it is dropped there). Waiting records keep one per
 * cell (a cell hit again replaces its unsent record), so the queue never outgrows the points. A section whose file has
 * grown far past its live points (pixels replaced many times, data refreshed) is rewritten whole, in parts. After a
 * card change what the old card had not received — records and rewrites — still goes to it. Without a writable card
 * the lidar's points are unsaved: they fade in seconds.
 */
public final class CardSync {
    private static final int FLUSH_TICKS = 5;
    /** A section file past this many records is rewritten even if most are live: the server refuses files over 8 MB. */
    private static final int HELD_LIMIT = 500_000;
    /** How long the old card keeps getting what it had not received after a card change (the server waits 130 s). */
    private static final int OLD_CARD_TICKS = 2400;
    /** Share of the server's upload limit used. */
    private static final double UPLOAD_SHARE = 0.7;
    private static final int STREAM_QUIET_TICKS = 40;
    private static final int STEP = CardUploadPayload.MAX_BYTES - CardUploadPayload.MAX_BYTES % CardRecords.BYTES;

    /** A section's records waiting to go: one per cell while unsent (index: cell -> offset), sent from the front. */
    private static final class Waiting {
        final ByteArrayList bytes = new ByteArrayList();
        @Nullable Int2IntOpenHashMap index = new Int2IntOpenHashMap();
        int sent;

        void add(int cell, byte[] record) {
            if (index != null) {
                int at = index.getOrDefault(cell, -1);
                if (at >= sent) {
                    bytes.setElements(at, record);
                    return;
                }
                index.put(cell, bytes.size());
            }
            bytes.addElements(bytes.size(), record);
        }

        boolean done() {
            return sent >= bytes.size();
        }

        int left() {
            return bytes.size() - sent;
        }
    }

    /** A whole section going to a card in parts; the last one makes the server replace its file. */
    private static final class Rewrite {
        final UUID card;
        final long key;
        final byte[] data;
        final int live;
        int offset;

        Rewrite(UUID card, long key, byte[] data, int live) {
            this.card = card;
            this.key = key;
            this.data = data;
            this.live = live;
        }
    }

    private static @Nullable UUID card;
    private static boolean writable;
    private static int epoch = -1;
    /** Records waiting to go to the active card, by section, oldest first. */
    private static final Long2ObjectLinkedOpenHashMap<Waiting> PENDING = new Long2ObjectLinkedOpenHashMap<>();
    /** Sections to rewrite whole, and records the server holds per section of the active card (as far as known). */
    private static final LongLinkedOpenHashSet REWRITE = new LongLinkedOpenHashSet();
    private static final Long2IntOpenHashMap HELD = new Long2IntOpenHashMap();
    /** The rewrite under way; new points of its section wait for it. */
    private static @Nullable Rewrite rewrite;
    /** The card before the last change and what it had not yet received: rewrites first, then records. */
    private static @Nullable UUID oldCard;
    private static final ArrayDeque<Rewrite> OLD_REWRITES = new ArrayDeque<>();
    private static final Long2ObjectLinkedOpenHashMap<Waiting> OLD_PENDING = new Long2ObjectLinkedOpenHashMap<>();
    private static final Long2IntOpenHashMap OLD_LIVE = new Long2IntOpenHashMap();
    private static int oldUntil;
    /**
     * The same card back before it got all of that: those points are not in what the server streams now, so they go
     * over each section once it has arrived (and over the rest once the stream goes quiet).
     */
    private static final Long2ObjectLinkedOpenHashMap<byte[]> OVERLAY = new Long2ObjectLinkedOpenHashMap<>();
    private static int lastSectionTick;
    /** Sections whose card count may differ from the server's (just replayed): their count goes to it. */
    private static final LongLinkedOpenHashSet LIVE_CHANGED = new LongLinkedOpenHashSet();
    /** Bytes that may go out now: builds up with time, up to a second's worth. */
    private static double allowance;
    private static int ticks;
    /** Dev runs only: the card's points when it came out, compared with what its replay brings back. */
    private static it.unimi.dsi.fastutil.longs.@Nullable Long2ObjectOpenHashMap<Int2IntOpenHashMap> checkBefore;
    private static @Nullable UUID checkCard;
    private static boolean checkPending;

    static {
        PointCloud.setRecorder(new PointCloud.Recorder() {
            @Override
            public void record(long section, short x, short y, short z, int face, int material, int rgb) {
                if (card == null || !writable) return;
                ByteBuffer record = ByteBuffer.allocate(CardRecords.BYTES);
                CardRecords.put(record, x, y, z, face, material, rgb);
                // The cell as the cloud keys it (1/32 block from the stored position).
                int cell = face << 27 | (x >> 5) << 18 | (y >> 5) << 9 | z >> 5;
                PENDING.computeIfAbsent(section, key -> new Waiting()).add(cell, record.array());
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
            long waiting = 0;
            for (Waiting w : PENDING.values()) waiting += w.left();
            Seepers.LOGGER.info("Card {} -> {}: {} points shown, {} records waiting, {} rewrites", card, next,
                    cloud == null ? 0 : cloud.count(PointSource.LIDAR), waiting / CardRecords.BYTES, REWRITE.size() + (rewrite != null ? 1 : 0));
            if (!net.neoforged.fml.loading.FMLEnvironment.production && cloud != null) {
                if (card != null && next == null) {
                    checkBefore = cloud.snapshot(PointSource.LIDAR);
                    checkCard = card;
                } else if (next != null && next.equals(checkCard) && checkBefore != null) {
                    checkPending = true;
                }
            }
            if (card != null && writable && !Objects.equals(next, card)) handOver(cloud);
            if (cloud != null) cloud.clearSource(PointSource.LIDAR);
            PENDING.clear();
            REWRITE.clear();
            HELD.clear();
            LIVE_CHANGED.clear();
            rewrite = null;
            OVERLAY.clear();
            if (next != null && next.equals(oldCard)) {
                for (var entry : OLD_PENDING.long2ObjectEntrySet()) {
                    Waiting w = entry.getValue();
                    OVERLAY.put(entry.getLongKey(), java.util.Arrays.copyOfRange(w.bytes.elements(), w.sent, w.bytes.size()));
                }
            }
            lastSectionTick = ticks;
        }
        card = next;
        epoch = payload.epoch();
        writable = payload.writable();
        PointSource.setLidarLimit(payload.capacity());
    }

    /** What the card going out had not yet received still goes to it: its rewrites (taken now, whole) and records. */
    private static void handOver(@Nullable PointCloud cloud) {
        if (!card.equals(oldCard)) {
            OLD_REWRITES.clear();
            OLD_PENDING.clear();
            OLD_LIVE.clear();
        }
        oldCard = card;
        oldUntil = ticks + OLD_CARD_TICKS;
        if (cloud != null) {
            // A rewrite under way starts over (its first part resets the server's copy); the queued ones are taken now.
            if (rewrite != null) REWRITE.add(rewrite.key);
            for (long key : REWRITE) {
                OLD_REWRITES.add(new Rewrite(card, key, cloud.exportRecords(key, PointSource.LIDAR), cloud.liveIn(key, PointSource.LIDAR)));
                PENDING.remove(key); // in the rewrite already
            }
        }
        while (!PENDING.isEmpty()) {
            long key = PENDING.firstLongKey();
            Waiting from = PENDING.removeFirst();
            Waiting to = OLD_PENDING.get(key);
            if (to == null) {
                OLD_PENDING.put(key, from);
            } else {
                to.index = null; // two batches of one section: no more merging, the later after the earlier
                to.bytes.addElements(to.bytes.size(), from.bytes.elements(), from.sent, from.left());
            }
            OLD_LIVE.put(key, cloud == null ? 0 : cloud.liveIn(key, PointSource.LIDAR));
        }
    }

    public static void section(CardSectionPayload payload) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || !payload.card().equals(card) || payload.epoch() != epoch) return;
        long key = payload.section();
        CardRecords.read(payload.records(), (x, y, z, face, material, rgb) -> cloud.addRecord(PointSource.LIDAR, key, x, y, z, face, material, rgb));
        HELD.addTo(key, payload.records().length / CardRecords.BYTES);
        lastSectionTick = ticks;
        if (payload.last()) {
            byte[] overlay = OVERLAY.remove(key);
            if (overlay != null) replay(cloud, key, overlay);
            if (writable) {
                checkBloat(key, cloud.liveIn(key, PointSource.LIDAR));
                // The card's count of this section follows what the replay left (it can differ from the last report).
                LIVE_CHANGED.add(key);
            }
        }
    }

    private static void replay(PointCloud cloud, long key, byte[] records) {
        CardRecords.read(records, (x, y, z, face, material, rgb) -> cloud.addRecord(PointSource.LIDAR, key, x, y, z, face, material, rgb));
    }

    public static void drop(CardDropPayload payload) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || !payload.card().equals(card)) return;
        for (long key : payload.sections()) {
            cloud.dropSection(key, PointSource.LIDAR);
            PENDING.remove(key);
            REWRITE.remove(key);
            HELD.remove(key);
            if (rewrite != null && rewrite.key == key) rewrite = null;
        }
    }

    /** The data refresh dropped wrong points from a section: the card gets the section anew. */
    public static void rewrite(long section) {
        if (card != null && writable) REWRITE.add(section);
    }

    private static void checkBloat(long key, int live) {
        int held = HELD.get(key);
        if (held > live * 2 + 512 || held > Math.max(HELD_LIMIT, live + 4096)) REWRITE.add(key);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % FLUSH_TICKS != 0) return;
        if (!OVERLAY.isEmpty() && ticks - lastSectionTick > STREAM_QUIET_TICKS) {
            PointCloud cloud = PointCloud.current();
            if (cloud != null) for (var entry : OVERLAY.long2ObjectEntrySet()) replay(cloud, entry.getLongKey(), entry.getValue());
            OVERLAY.clear();
        }
        if (checkPending && OVERLAY.isEmpty() && ticks - lastSectionTick > 100) {
            PointCloud cloud = PointCloud.current();
            if (cloud != null) CardCheck.compare(checkBefore, cloud.snapshot(PointSource.LIDAR));
            checkPending = false;
            checkBefore = null;
        }
        // A packet goes only when it fits, below the server's own limit (which drops what goes past it).
        double rate = ServerConfig.get(ServerConfig.LIDAR_CARD_UPLOAD_KB) * 1024 * UPLOAD_SHARE;
        allowance = Math.min(allowance + rate * FLUSH_TICKS / 20, rate);
        if (oldCard != null) sendOld();
        if (card == null || !writable) return;
        PointCloud cloud = PointCloud.current();
        if (cloud == null) return;
        if (rewrite == null && !REWRITE.isEmpty()) {
            // The section as it stands now holds its waiting records' points; sent after it, they would bring back
            // points gone since (buried, refreshed away). Records from here on wait for the rewrite.
            long key = REWRITE.removeFirstLong();
            rewrite = new Rewrite(card, key, cloud.exportRecords(key, PointSource.LIDAR), cloud.liveIn(key, PointSource.LIDAR));
            PENDING.remove(key);
        }
        while (rewrite != null && sendPart(rewrite)) {
            if (rewrite.offset >= rewrite.data.length) {
                HELD.put(rewrite.key, rewrite.data.length / CardRecords.BYTES);
                rewrite = null;
            }
        }
        int skipped = 0;
        while (!PENDING.isEmpty() && skipped < PENDING.size()) {
            long key = PENDING.firstLongKey();
            if (rewrite != null && key == rewrite.key) {
                // Points newer than the rewrite go after it.
                PENDING.getAndMoveToLast(key);
                skipped++;
                continue;
            }
            Waiting waiting = PENDING.get(key);
            int live = cloud.liveIn(key, PointSource.LIDAR);
            int before = waiting.sent;
            boolean sent = sendFront(card, key, waiting, live);
            HELD.addTo(key, (waiting.sent - before) / CardRecords.BYTES);
            if (!sent) break;
            LIVE_CHANGED.remove(key);
            if (waiting.done()) {
                PENDING.removeFirst();
                checkBloat(key, live);
            }
        }
        // Sections just replayed: the card's count follows what the replay left.
        while (!LIVE_CHANGED.isEmpty()) {
            long key = LIVE_CHANGED.firstLong();
            if (PENDING.containsKey(key)) {
                LIVE_CHANGED.removeFirstLong();
                continue;
            }
            if (!afford(0)) break;
            LIVE_CHANGED.removeFirstLong();
            PacketDistributor.sendToServer(new CardUploadPayload(card, key, cloud.liveIn(key, PointSource.LIDAR), CardUploadPayload.APPEND, new byte[0]));
        }
    }

    private static void sendOld() {
        if (ticks > oldUntil) {
            long left = 0;
            for (Waiting w : OLD_PENDING.values()) left += w.left();
            if (left > 0 || !OLD_REWRITES.isEmpty()) {
                Seepers.LOGGER.warn("Card {}: {} records and {} rewrites not sent in time after the card change", oldCard,
                        left / CardRecords.BYTES, OLD_REWRITES.size());
            }
            OLD_REWRITES.clear();
            OLD_PENDING.clear();
        }
        while (!OLD_REWRITES.isEmpty()) {
            Rewrite job = OLD_REWRITES.peekFirst();
            if (!sendPart(job)) return;
            if (job.offset >= job.data.length) OLD_REWRITES.removeFirst();
        }
        while (!OLD_PENDING.isEmpty()) {
            long key = OLD_PENDING.firstLongKey();
            Waiting waiting = OLD_PENDING.get(key);
            if (!sendFront(oldCard, key, waiting, OLD_LIVE.get(key))) return;
            if (waiting.done()) OLD_PENDING.removeFirst();
        }
        oldCard = null;
        OLD_LIVE.clear();
    }

    private static boolean afford(int bytes) {
        int cost = Math.max(bytes, 64);
        if (allowance < cost) return false;
        allowance -= cost;
        return true;
    }

    /** Sends the next packet of a section's waiting records. False if it did not fit. */
    private static boolean sendFront(UUID to, long section, Waiting waiting, int live) {
        int length = Math.min(STEP, waiting.left());
        if (!afford(length)) return false;
        byte[] front = new byte[length];
        waiting.bytes.getElements(waiting.sent, front, 0, length);
        PacketDistributor.sendToServer(new CardUploadPayload(to, section, live, CardUploadPayload.APPEND, front));
        waiting.sent += length;
        return true;
    }

    /** The next packet of a rewrite, if it fits; the last one commits it on the server. */
    private static boolean sendPart(Rewrite job) {
        int length = Math.min(STEP, job.data.length - job.offset);
        if (!afford(length)) return false;
        int mode = CardUploadPayload.REWRITE | (job.offset == 0 ? CardUploadPayload.FIRST : 0)
                | (job.offset + length >= job.data.length ? CardUploadPayload.LAST : 0);
        PacketDistributor.sendToServer(new CardUploadPayload(job.card, job.key, job.live, mode,
                java.util.Arrays.copyOfRange(job.data, job.offset, job.offset + length)));
        job.offset += length;
        if (length == 0) job.offset = 1; // an empty section: its one packet went
        return true;
    }

    public static void onLoggingOut() {
        card = null;
        writable = false;
        epoch = -1;
        PENDING.clear();
        REWRITE.clear();
        HELD.clear();
        rewrite = null;
        oldCard = null;
        OLD_REWRITES.clear();
        OLD_PENDING.clear();
        OLD_LIVE.clear();
        OVERLAY.clear();
        LIVE_CHANGED.clear();
        PointSource.setLidarLimit(0);
    }

    private CardSync() {}
}
