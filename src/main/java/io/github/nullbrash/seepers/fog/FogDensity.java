package io.github.nullbrash.seepers.fog;

import io.github.nullbrash.seepers.world.Distortion;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Base fog density at a point (CLAUDE.md 4.4): layers, clear pockets, waves, storms, weather, the
 * layer above water and distortion. Pure function of position, day time, weather and
 * the fog seed, so client and server compute the same value.
 */
public final class FogDensity {
    /** All components, for the debug command; {@link #total} is what everything else uses. */
    public record Sample(double total, double base, double layers, double pocketFactor, double wave,
                        double storm, double weather, double water, double distortion) {}

    /**
     * Everything that does not depend on the position, computed once per tick (or per query) —
     * keeps a sample down to a few noise lookups.
     */
    public record Context(Level level, FogNoise noise, FogSettings settings, double time, double driftX,
                          double driftZ, double storm, double weather) {
        public static Context of(Level level, FogNoise noise) {
            return at(level, noise, level.getDayTime());
        }

        /** The field at another day time (catching up on unloaded chunks); weather stays the current one. */
        public static Context at(Level level, FogNoise noise, double time) {
            FogSettings s = FogSettings.current();
            double driftBlocks = time / 1200.0 * s.driftBlocksPerMinute();
            return new Context(level, noise, s, time, noise.driftX * driftBlocks, noise.driftZ * driftBlocks,
                    StormSchedule.intensity(noise, s, time) * s.stormDensity(),
                    level.getRainLevel(1.0F) * s.rainDensity() + level.getThunderLevel(1.0F) * s.thunderDensity());
        }
    }

    public static double at(Context c, double x, double y, double z) {
        return sample(c, x, y, z).total();
    }

    public static Sample sample(Context c, double x, double y, double z) {
        FogSettings s = c.settings();
        double dx = x + c.driftX();
        double dz = z + c.driftZ();

        double layers = layers(c.noise(), s, dx, y, dz, c.time());
        double pocketFactor = pocketFactor(c.noise(), s, dx, y, dz);
        double wave = s.waveAmplitude() * Math.sin(Math.PI * 2
                * (c.time() / (s.wavePeriodMinutes() * 1200.0) + c.noise().wavePhase.noise(x / 1024.0, 0, z / 1024.0)));
        double water = water(c.level(), s, x, y, z);
        double distortion = s.distortionDensity() * Distortion.level(x, z);

        // Pockets clear the ambient fog but not a storm: storms reach everywhere.
        double ambient = Math.max(0, s.baseDensity() + layers + wave + c.weather() + water + distortion);
        double total = ambient * pocketFactor + c.storm();
        return new Sample(total, s.baseDensity(), layers, pocketFactor, wave, c.storm(), c.weather(), water, distortion);
    }

    private static double layers(FogNoise noise, FogSettings s, double x, double y, double z, double time) {
        double slowTime = time / 24000.0;
        double span = Math.max(1, s.layerMaxY() - s.layerMinY());
        double hx = x / s.layerScale();
        double hz = z / s.layerScale();
        double result = 0;
        for (int i = 0; i < 3; i++) {
            if (i == 2 && noise.thirdLayerPresence.noise(hx, 0, hz) < 0) {
                continue; // 2-3 layers: the third exists only in some areas
            }
            double n = noise.layerHeight[i].noise(hx, slowTime + i * 17.0, hz);
            double center = s.layerMinY() + span * (i + 0.5 + 0.45 * n) / 3.0;
            double distance = Math.abs(y - center);
            if (distance > s.layerThicknessMax() / 2 + 2) {
                continue; // far from any thickness this layer can have: skip the thickness noise
            }
            double t = noise.layerThickness[i].noise(hx, slowTime, hz) * 0.5 + 0.5;
            double halfThickness = Mth.lerp(t, s.layerThicknessMin(), s.layerThicknessMax()) / 2;
            double edge = 2.0;
            double profile = Mth.clamp(1 - (distance - halfThickness + edge) / (2 * edge), 0, 1);
            result += s.layerDensity() * profile;
        }
        return result;
    }

    private static double pocketFactor(FogNoise noise, FogSettings s, double x, double y, double z) {
        double n = noise.pockets.noise(x / s.pocketScale(), y / s.pocketScale(), z / s.pocketScale());
        double t = Mth.clamp((n - s.pocketThreshold()) / 0.15, 0, 1);
        double clear = t * t * (3 - 2 * t);
        return 1 - clear * s.pocketClearing();
    }

    private static double water(Level level, FogSettings s, double x, double y, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (s.waterLayerHeight() <= 0 || !level.hasChunk(bx >> 4, bz >> 4)) {
            return 0;
        }
        // MOTION_BLOCKING counts fluids: its top is one above the water surface.
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        double above = y - top;
        if (above < -1 || above > s.waterLayerHeight() + 1) {
            return 0;
        }
        if (!level.getFluidState(new BlockPos(bx, top - 1, bz)).is(FluidTags.WATER)) {
            return 0;
        }
        double fade = Mth.clamp(s.waterLayerHeight() + 1 - above, 0, 1);
        return s.waterDensity() * fade;
    }

    private FogDensity() {}
}
