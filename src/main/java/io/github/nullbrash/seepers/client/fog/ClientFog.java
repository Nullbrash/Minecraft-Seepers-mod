package io.github.nullbrash.seepers.client.fog;

import io.github.nullbrash.seepers.config.ClientConfig;
import java.util.Arrays;
import io.github.nullbrash.seepers.fog.FogDensity;
import io.github.nullbrash.seepers.fog.FogField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client copy of the fog field: the seed from the server and a grid of densities around the player.
 * Cells are anchored to world coordinates (a ring buffer tagged with each cell's position), so walking
 * around only recomputes new cells; the grid is refreshed a few hundred cells per tick.
 */
public final class ClientFog {
    public static final int CELL = 4;
    private static final int RADIUS_XZ = 12;
    private static final int RADIUS_Y = 6;
    private static final int SIZE_XZ = RADIUS_XZ * 2 + 1;
    private static final int SIZE_Y = RADIUS_Y * 2 + 1;
    private static final int CELLS = SIZE_XZ * SIZE_XZ * SIZE_Y;

    private static volatile boolean hasSeed;
    private static volatile long seed;

    private static final float[] values = new float[CELLS];
    private static final long[] tags = new long[CELLS];
    private static int cursor;
    private static long lastNanosPerCell;
    private static FogDensity.Context tickContext;

    static {
        Arrays.fill(tags, Long.MIN_VALUE);
    }

    public static void setSeed(long fogSeed) {
        seed = fogSeed;
        hasSeed = true;
        Arrays.fill(tags, Long.MIN_VALUE);
    }

    public static boolean hasSeed() {
        return hasSeed;
    }

    /** Direct computation; NaN before the seed arrived. */
    public static double densityDirect(ClientLevel level, double x, double y, double z) {
        return hasSeed ? FogField.density(level, seed, x, y, z) : Double.NaN;
    }

    /** Trilinear sample from the grid; falls back to direct computation for cells not computed yet. */
    public static double density(ClientLevel level, double x, double y, double z) {
        if (!hasSeed) return Double.NaN;
        double gx = x / CELL - 0.5;
        double gy = y / CELL - 0.5;
        double gz = z / CELL - 0.5;
        int x0 = Mth.floor(gx);
        int y0 = Mth.floor(gy);
        int z0 = Mth.floor(gz);
        double fx = gx - x0;
        double fy = gy - y0;
        double fz = gz - z0;
        double result = 0;
        for (int i = 0; i < 8; i++) {
            int cx = x0 + (i & 1);
            int cy = y0 + (i >> 1 & 1);
            int cz = z0 + (i >> 2 & 1);
            double weight = ((i & 1) != 0 ? fx : 1 - fx) * ((i >> 1 & 1) != 0 ? fy : 1 - fy) * ((i >> 2 & 1) != 0 ? fz : 1 - fz);
            result += weight * cell(level, cx, cy, cz);
        }
        return result;
    }

    /** Grid value if this cell was computed, else NaN (debug). */
    public static double gridOnly(double x, double y, double z) {
        int cx = Mth.floor(x / CELL);
        int cy = Mth.floor(y / CELL);
        int cz = Mth.floor(z / CELL);
        int index = index(cx, cy, cz);
        return tags[index] == tag(cx, cy, cz) ? values[index] : Double.NaN;
    }

    public static long lastNanosPerCell() {
        return lastNanosPerCell;
    }

    private static double cell(ClientLevel level, int cx, int cy, int cz) {
        int index = index(cx, cy, cz);
        long tag = tag(cx, cy, cz);
        if (tags[index] != tag) {
            values[index] = (float) FogField.density(context(level), (cx + 0.5) * CELL, (cy + 0.5) * CELL, (cz + 0.5) * CELL);
            tags[index] = tag;
        }
        return values[index];
    }

    /** Position-independent part, rebuilt every tick (time, storms, weather barely move within one). */
    private static FogDensity.Context context(ClientLevel level) {
        FogDensity.Context context = tickContext;
        if (context == null || context.level() != level) {
            context = FogField.context(level, seed);
            tickContext = context;
        }
        return context;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!hasSeed || level == null || minecraft.player == null || minecraft.isPaused()) return;
        Vec3 eye = minecraft.player.getEyePosition();
        int centerX = Mth.floor(eye.x / CELL);
        int centerY = Mth.floor(eye.y / CELL);
        int centerZ = Mth.floor(eye.z / CELL);
        int budget = ClientConfig.FOG_CELLS_PER_TICK.get();
        long started = System.nanoTime();
        tickContext = FogField.context(level, seed);
        FogDensity.Context context = tickContext;
        for (int n = 0; n < budget; n++) {
            int i = cursor;
            cursor = (cursor + 1) % CELLS;
            int cx = centerX - RADIUS_XZ + i % SIZE_XZ;
            int cz = centerZ - RADIUS_XZ + i / SIZE_XZ % SIZE_XZ;
            int cy = centerY - RADIUS_Y + i / (SIZE_XZ * SIZE_XZ);
            int index = index(cx, cy, cz);
            values[index] = (float) FogField.density(context, (cx + 0.5) * CELL, (cy + 0.5) * CELL, (cz + 0.5) * CELL);
            tags[index] = tag(cx, cy, cz);
        }
        if (budget > 0) {
            lastNanosPerCell = (System.nanoTime() - started) / budget;
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        hasSeed = false;
        tickContext = null;
        Arrays.fill(tags, Long.MIN_VALUE);
    }

    private static int index(int cx, int cy, int cz) {
        return Math.floorMod(cx, SIZE_XZ) + Math.floorMod(cz, SIZE_XZ) * SIZE_XZ + Math.floorMod(cy, SIZE_Y) * SIZE_XZ * SIZE_XZ;
    }

    private static long tag(int cx, int cy, int cz) {
        return ((long) cx & 0x3FFFFFL) << 42 | ((long) cz & 0x3FFFFFL) << 20 | ((long) cy & 0xFFFFFL);
    }

    private ClientFog() {}
}
