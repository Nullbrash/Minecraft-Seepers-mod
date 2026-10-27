package io.github.nullbrash.seepers.client.light;

import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.light.ClientLightSide;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The client half of dynamic light (CLAUDE.md 4.2): every carrier the server reported is drawn smoothly — light
 * falls off with the exact distance from its eyes, not in whole blocks — and the meshes around a moving carrier are
 * rebuilt each tick. Renderers read it from worker threads, so they get an immutable snapshot of the sources.
 * Mechanics that read block light on the client (points, entity visibility, fog halo, touch) add it too.
 */
public final class ClientDynamicLights {
    /** Carriers moving less than this since the last rebuild keep their meshes. */
    private static final double MOVE_EPSILON = 0.05;

    private static final Int2IntOpenHashMap LEVELS = new Int2IntOpenHashMap();
    private static final Int2ObjectOpenHashMap<double[]> BUILT = new Int2ObjectOpenHashMap<>();
    /** x, y, z, level per source, for render threads. */
    private static volatile double[] sources = new double[0];

    public static void set(int entityId, int level) {
        if (level <= 0) LEVELS.remove(entityId);
        else LEVELS.put(entityId, Math.min(15, level));
    }

    public static boolean active() {
        return sources.length > 0 && ClientLightSide.smooth();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        ClientLightSide.setSmooth(ClientConfig.DYNAMIC_LIGHT_SMOOTH.get());
        if (level == null) {
            LEVELS.clear();
            BUILT.clear();
            sources = new double[0];
            return;
        }
        double[] next = new double[LEVELS.size() * 4];
        int n = 0;
        var it = LEVELS.int2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity entity = level.getEntity(entry.getIntKey());
            if (entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            Vec3 eye = entity.getEyePosition();
            int light = entry.getIntValue();
            double[] built = BUILT.get(entry.getIntKey());
            if (built == null || built[3] != light || Math.abs(built[0] - eye.x) + Math.abs(built[1] - eye.y) + Math.abs(built[2] - eye.z) > MOVE_EPSILON) {
                if (built != null) dirty(minecraft, built[0], built[1], built[2], (int) built[3]);
                dirty(minecraft, eye.x, eye.y, eye.z, light);
                BUILT.put(entry.getIntKey(), new double[] {eye.x, eye.y, eye.z, light});
            }
            next[n++] = eye.x;
            next[n++] = eye.y;
            next[n++] = eye.z;
            next[n++] = light;
        }
        // Carriers gone or dark: rebuild where their light was.
        var built = BUILT.int2ObjectEntrySet().fastIterator();
        while (built.hasNext()) {
            var entry = built.next();
            if (LEVELS.containsKey(entry.getIntKey())) continue;
            double[] b = entry.getValue();
            dirty(minecraft, b[0], b[1], b[2], (int) b[3]);
            built.remove();
        }
        sources = n == next.length ? next : java.util.Arrays.copyOf(next, n);
    }

    /** Smooth dynamic light at a point, 0-15 with fractions. */
    public static double value(double x, double y, double z) {
        if (!ClientLightSide.smooth()) return 0;
        double[] s = sources;
        double best = 0;
        for (int i = 0; i < s.length; i += 4) {
            double dx = x - s[i];
            double dy = y - s[i + 1];
            double dz = z - s[i + 2];
            double v = s[i + 3] - Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (v > best) best = v;
        }
        return Math.min(15, best);
    }

    /** Whole-level dynamic light at a block, for mechanics (points, entity visibility, halo, touch). */
    public static int level(BlockPos pos) {
        return (int) Math.round(value(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    /** Block light combined into packed lightmap coordinates (block part in 1/16 steps: the lightmap is filtered). */
    public static int packed(int packed, double x, double y, double z) {
        double v = value(x, y, z);
        if (v <= 0) return packed;
        int dynamic = Mth.clamp((int) (v * 16), 0, 240);
        int block = packed & 0xFFFF;
        return dynamic > block ? (packed & 0xFFFF0000) | dynamic : packed;
    }

    private static void dirty(Minecraft minecraft, double x, double y, double z, int radius) {
        int r = radius + 1;
        for (int sx = Mth.floor(x - r) >> 4; sx <= Mth.floor(x + r) >> 4; sx++) {
            for (int sy = Mth.floor(y - r) >> 4; sy <= Mth.floor(y + r) >> 4; sy++) {
                for (int sz = Mth.floor(z - r) >> 4; sz <= Mth.floor(z + r) >> 4; sz++) {
                    minecraft.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    private ClientDynamicLights() {}
}
