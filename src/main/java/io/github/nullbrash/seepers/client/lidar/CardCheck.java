package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.Seepers;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;

/**
 * Dev runs only: what a card's replay brought back against what was shown when the card came out, in the log. A point
 * is a cell key in its section; missing ones are told apart by whether a neighbour in their pixel (2x2 keys, depth +-1)
 * holds the spot now.
 */
final class CardCheck {
    private static final int SAMPLES = 12;

    static void compare(Long2ObjectOpenHashMap<Int2IntOpenHashMap> before, Long2ObjectOpenHashMap<Int2IntOpenHashMap> after) {
        long total = 0;
        long missingCovered = 0;
        long missingBare = 0;
        long changed = 0;
        long extra = 0;
        StringBuilder samples = new StringBuilder();
        int sampled = 0;
        for (Long2ObjectMap.Entry<Int2IntOpenHashMap> entry : before.long2ObjectEntrySet()) {
            Int2IntOpenHashMap now = after.get(entry.getLongKey());
            for (Int2IntMap.Entry point : entry.getValue().int2IntEntrySet()) {
                total++;
                int key = point.getIntKey();
                if (now != null && now.containsKey(key)) {
                    if (now.get(key) != point.getIntValue()) changed++;
                    continue;
                }
                int neighbour = now == null ? -1 : neighbour(now, key);
                if (neighbour >= 0) missingCovered++;
                else missingBare++;
                if (sampled++ < SAMPLES) {
                    samples.append(String.format("%n  missing %s %s was %08x, neighbour %s", section(entry.getLongKey()), cell(key),
                            point.getIntValue(), neighbour < 0 ? "none" : cell(neighbour) + " " + String.format("%08x", now.get(neighbour))));
                }
            }
        }
        for (Long2ObjectMap.Entry<Int2IntOpenHashMap> entry : after.long2ObjectEntrySet()) {
            Int2IntOpenHashMap was = before.get(entry.getLongKey());
            for (int key : entry.getValue().keySet()) {
                if (was != null && was.containsKey(key)) continue;
                extra++;
                if (sampled++ < SAMPLES * 2) {
                    samples.append(String.format("%n  extra %s %s is %08x, neighbour before %s", section(entry.getLongKey()), cell(key),
                            entry.getValue().get(key), was == null || neighbour(was, key) < 0 ? "none" : cell(neighbour(was, key))));
                }
            }
        }
        Seepers.LOGGER.info("Card check: {} points before; missing {} (a neighbour holds the pixel) + {} (nothing there), changed {}, extra {}{}",
                total, missingCovered, missingBare, changed, extra, samples);
    }

    /** A key of the same pixel (2x2 keys on the face, depth +-1) present in the map, or -1. */
    private static int neighbour(Int2IntOpenHashMap points, int local) {
        int face = local >>> 27;
        int[] q = {local >>> 18 & 0x1FF, local >>> 9 & 0x1FF, local & 0x1FF};
        int perpendicular = face / 2; // Direction ordinal: down, up, north, south, west, east -> axis y, y, z, z, x, x
        perpendicular = perpendicular == 0 ? 1 : perpendicular == 1 ? 2 : 0;
        int a = perpendicular == 0 ? 1 : 0;
        int b = perpendicular == 2 ? 1 : 2;
        for (int dp = -1; dp <= 1; dp++) {
            for (int da = 0; da < 2; da++) {
                for (int db = 0; db < 2; db++) {
                    int[] k = q.clone();
                    k[perpendicular] = q[perpendicular] + dp;
                    k[a] = q[a] & ~1 | da;
                    k[b] = q[b] & ~1 | db;
                    int key = face << 27 | k[0] << 18 | k[1] << 9 | k[2];
                    if (key != local && points.containsKey(key)) return key;
                }
            }
        }
        return -1;
    }

    private static String section(long key) {
        return SectionPos.x(key) + "," + SectionPos.y(key) + "," + SectionPos.z(key);
    }

    private static String cell(int local) {
        return "f" + (local >>> 27) + "(" + (local >>> 18 & 0x1FF) + "," + (local >>> 9 & 0x1FF) + "," + (local & 0x1FF) + ")";
    }

    private CardCheck() {}
}
