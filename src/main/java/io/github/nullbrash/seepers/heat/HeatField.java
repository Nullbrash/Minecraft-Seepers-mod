package io.github.nullbrash.seepers.heat;

import io.github.nullbrash.seepers.data.chunk.HeatSources;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fog cleared by heat (CLAUDE.md 4.5): each source subtracts strength * falloff * time factor, where the
 * time factor ramps up over the warm-up after lighting and back down over the cool-down after it goes out.
 * Reads only synced chunk data and game time, so client and server agree.
 */
public final class HeatField {
    /** Fog density modifier (negative). */
    public static double densityAt(Level level, double x, double y, double z, double baseDensity) {
        double[] result = {0};
        visit(level, x, y, z, (distance, radius, strength, timeFactor) ->
                result[0] -= strength * falloff(distance, radius) * timeFactor);
        return result[0];
    }

    /**
     * How far from the point the heated area extends (0 if the point is not inside one): fog rendering
     * starts the fog at the heated area's edge. Only counts sources at least half warm.
     */
    public static double edgeDistance(Level level, double x, double y, double z) {
        double[] edge = {0};
        visit(level, x, y, z, (distance, radius, strength, timeFactor) -> {
            if (timeFactor >= 0.5 && strength > 0) edge[0] = Math.max(edge[0], radius - distance);
        });
        return edge[0];
    }

    private interface Visitor {
        void accept(double distance, double radius, double strength, double timeFactor);
    }

    private static void visit(Level level, double x, double y, double z, Visitor visitor) {
        HeatSettings s = HeatSettings.current();
        double maxRadius = s.maxRadius();
        if (maxRadius <= 0) return;
        long now = level.getGameTime();
        int minCx = Mth.floor(x - maxRadius) >> 4;
        int maxCx = Mth.floor(x + maxRadius) >> 4;
        int minCz = Mth.floor(z - maxRadius) >> 4;
        int maxCz = Mth.floor(z + maxRadius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                HeatSources sources = chunk.getExistingDataOrNull(SeepersAttachments.HEAT_SOURCES.get());
                if (sources == null || sources.isEmpty()) continue;
                for (Int2ObjectMap.Entry<HeatSources.Source> entry : sources.raw().int2ObjectEntrySet()) {
                    int packed = entry.getIntKey();
                    HeatSources.Source source = entry.getValue();
                    int index = source.category().ordinal();
                    double radius = s.radius()[index];
                    double sx = (cx << 4) + (packed & 15) + 0.5;
                    double sy = (packed >> 8) + 0.5;
                    double sz = (cz << 4) + ((packed >> 4) & 15) + 0.5;
                    double distance = Math.sqrt(Mth.square(x - sx) + Mth.square(y - sy) + Mth.square(z - sz));
                    if (distance >= radius) continue;
                    double timeFactor = timeFactor(source, now, s);
                    if (timeFactor > 0) visitor.accept(distance, radius, s.strength()[index], timeFactor);
                }
            }
        }
    }

    /** Smooth: full at the source, 0 at the radius. */
    private static double falloff(double distance, double radius) {
        double t = 1 - distance / radius;
        return t * t * (3 - 2 * t);
    }

    private static double timeFactor(HeatSources.Source source, long now, HeatSettings s) {
        double warm = s.warmupTicks() <= 0 ? 1 : Mth.clamp((now - source.litSince()) / s.warmupTicks(), 0, 1);
        if (source.outSince() == HeatSources.LIT) return warm;
        double cooling = s.cooldownTicks() <= 0 ? 0 : 1 - Mth.clamp((now - source.outSince()) / s.cooldownTicks(), 0, 1);
        return Math.min(warm, 1) * cooling;
    }

    private HeatField() {}
}
