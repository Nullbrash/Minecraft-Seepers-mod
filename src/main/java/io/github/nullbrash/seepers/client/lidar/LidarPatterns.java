package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.lidar.LidarModules;
import net.minecraft.util.RandomSource;

/**
 * Scan patterns (CLAUDE.md 5.3): where in the field the beam points at a moment of pattern time, as (u, v) in [-1, 1]
 * (inside the unit disk for a round field). Every pattern drifts from pass to pass, so a held scan keeps landing in
 * new spots.
 */
final class LidarPatterns {
    /** Rosette: two counter-rotating circles, turns per second (irrational ratio: never repeats). */
    private static final double FAST_TURNS = 7.3;
    private static final double SLOW_TURNS = -4.9 * Math.sqrt(1.02);
    /** Spiral: passes per second from the edge to the centre, and turns per pass. */
    private static final double SPIRAL_PASSES = 1.2;
    private static final double SPIRAL_TURNS = 9;
    /** Lines: rows per pass and passes per second. */
    private static final int LINE_ROWS = 14;
    private static final double LINE_PASSES = 0.8;

    /** Writes the beam's (u, v) into out; false if this moment falls outside a round field (the ray is skipped). */
    static boolean sample(LidarModules.Pattern pattern, double time, boolean round, RandomSource random, double[] out) {
        switch (pattern) {
            case ROSETTE -> {
                double a = time * FAST_TURNS * Math.PI * 2;
                double b = time * SLOW_TURNS * Math.PI * 2;
                // Two half-radius circles: their sum sweeps petals through the centre, out to the edge.
                out[0] = (Math.cos(a) + Math.cos(b)) * 0.5;
                out[1] = (Math.sin(a) + Math.sin(b)) * 0.5;
            }
            case SPIRAL -> {
                double pass = time * SPIRAL_PASSES;
                double r = 1 - (pass - Math.floor(pass));
                // Each pass starts at a new angle (golden turn), so passes interleave.
                double angle = (pass - Math.floor(pass)) * SPIRAL_TURNS * Math.PI * 2 + Math.floor(pass) * 2.39996;
                out[0] = r * Math.cos(angle);
                out[1] = r * Math.sin(angle);
            }
            case LINES -> {
                double pass = time * LINE_PASSES;
                double within = (pass - Math.floor(pass)) * LINE_ROWS;
                int row = (int) within;
                double along = within - row;
                // Rows go top to bottom, back and forth; each pass shifts them by a fraction of a row.
                double shift = ((Math.floor(pass) * 0.618) % 1.0);
                out[0] = (row % 2 == 0 ? along : 1 - along) * 2 - 1;
                out[1] = 1 - (row + shift) / LINE_ROWS * 2;
            }
            case RANDOM -> {
                if (round) {
                    double r = Math.sqrt(random.nextDouble());
                    double angle = random.nextDouble() * Math.PI * 2;
                    out[0] = r * Math.cos(angle);
                    out[1] = r * Math.sin(angle);
                } else {
                    out[0] = random.nextDouble() * 2 - 1;
                    out[1] = random.nextDouble() * 2 - 1;
                }
            }
        }
        if (!round && (pattern == LidarModules.Pattern.ROSETTE || pattern == LidarModules.Pattern.SPIRAL)) toSquare(out);
        return !round || out[0] * out[0] + out[1] * out[1] <= 1.0001;
    }

    /** Stretches a point of the unit disk onto the square (elliptical grid mapping): disk patterns fill a screen's corners. */
    private static void toSquare(double[] p) {
        double u = p[0];
        double v = p[1];
        double d = u * u - v * v;
        double s = 2 * Math.sqrt(2);
        p[0] = 0.5 * Math.sqrt(Math.max(0, 2 + d + s * u)) - 0.5 * Math.sqrt(Math.max(0, 2 + d - s * u));
        p[1] = 0.5 * Math.sqrt(Math.max(0, 2 - d + s * v)) - 0.5 * Math.sqrt(Math.max(0, 2 - d - s * v));
    }

    private LidarPatterns() {}
}
