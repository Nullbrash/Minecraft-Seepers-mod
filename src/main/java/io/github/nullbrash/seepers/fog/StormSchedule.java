package io.github.nullbrash.seepers.fog;

import it.unimi.dsi.fastutil.HashCommon;

/**
 * Deterministic fog storms: storm k (k >= 0) starts around (k + 1) * meanInterval — so a new world does
 * not open with a storm — jittered so that the gap between storms stays within [min, max].
 * Time is the level's day time (ticks).
 */
public final class StormSchedule {
    private static final double TICKS_PER_DAY = 24000.0;
    private static final double TICKS_PER_MINUTE = 1200.0;

    public record Storm(long index, double start, double end) {}

    /** Storm intensity at a moment: 0 outside, rises over the warning period, 1 during, fades out. */
    public static double intensity(FogNoise noise, FogSettings settings, double time) {
        double warning = settings.stormWarningSeconds() * 20.0;
        double fade = warning / 2.0;
        long k = Math.floorDiv((long) time, (long) meanInterval(settings)) - 1;
        double result = 0;
        for (long i = Math.max(0, k - 1); i <= k + 1; i++) {
            Storm storm = storm(noise, settings, i);
            double value;
            if (time < storm.start() - warning || time > storm.end() + fade) {
                value = 0;
            } else if (time < storm.start()) {
                value = smooth((time - (storm.start() - warning)) / Math.max(1, warning));
            } else if (time <= storm.end()) {
                value = 1;
            } else {
                value = 1 - smooth((time - storm.end()) / Math.max(1, fade));
            }
            result = Math.max(result, value);
        }
        return result;
    }

    /** The first storm whose end is after {@code time}. */
    public static Storm next(FogNoise noise, FogSettings settings, double time) {
        long k = Math.max(0, Math.floorDiv((long) time, (long) meanInterval(settings)) - 2);
        Storm storm = storm(noise, settings, k);
        while (storm.end() < time) {
            storm = storm(noise, settings, ++k);
        }
        return storm;
    }

    private static Storm storm(FogNoise noise, FogSettings settings, long index) {
        double min = Math.min(settings.stormIntervalMinDays(), settings.stormIntervalMaxDays()) * TICKS_PER_DAY;
        double max = Math.max(settings.stormIntervalMinDays(), settings.stormIntervalMaxDays()) * TICKS_PER_DAY;
        double mean = (min + max) / 2;
        // Jitter of ±(max - min) / 4 keeps consecutive gaps within [min, max].
        double start = (index + 1) * mean + unit(noise.seed, index, 1) * (max - min) / 4;
        double durationMin = settings.stormDurationMinMinutes() * TICKS_PER_MINUTE;
        double durationMax = Math.max(durationMin, settings.stormDurationMaxMinutes() * TICKS_PER_MINUTE);
        double duration = durationMin + (unit(noise.seed, index, 2) * 0.5 + 0.5) * (durationMax - durationMin);
        return new Storm(index, start, start + duration);
    }

    private static double meanInterval(FogSettings settings) {
        return Math.max(1.0, (settings.stormIntervalMinDays() + settings.stormIntervalMaxDays()) / 2 * TICKS_PER_DAY);
    }

    /** Deterministic value in [-1, 1). */
    private static double unit(long seed, long index, int salt) {
        long hash = HashCommon.murmurHash3(seed ^ HashCommon.murmurHash3(index * 31 + salt));
        return ((hash >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    private static double smooth(double t) {
        t = Math.clamp(t, 0.0, 1.0);
        return t * t * (3 - 2 * t);
    }

    private StormSchedule() {}
}
