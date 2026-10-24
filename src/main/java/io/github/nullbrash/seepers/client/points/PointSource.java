package io.github.nullbrash.seepers.client.points;

import io.github.nullbrash.seepers.config.ServerConfig;

/**
 * Where a point came from. Each source has its own share of the cloud (CLAUDE.md 5.1): filling one share
 * never pushes out another's points.
 */
public enum PointSource {
    TOUCH,
    CANE,
    /** Lidar (milestone 10) and foreign data (15): no share yet. */
    LIDAR,
    FOREIGN,
    /** {@code /seepers points test}: large share for render benchmarks. */
    DEBUG;

    private static final int DEBUG_CAPACITY = 262_144;
    private static final double DEBUG_LIFETIME_SECONDS = 600;

    public int capacity() {
        return switch (this) {
            case TOUCH -> ServerConfig.get(ServerConfig.TOUCH_POINT_LIMIT);
            case CANE -> ServerConfig.get(ServerConfig.CANE_POINT_LIMIT);
            case DEBUG -> DEBUG_CAPACITY;
            default -> 0;
        };
    }

    public float lifetimeSeconds() {
        return (float) switch (this) {
            case TOUCH -> ServerConfig.get(ServerConfig.TOUCH_POINT_LIFETIME_SECONDS);
            case CANE -> ServerConfig.get(ServerConfig.CANE_POINT_LIFETIME_SECONDS);
            case DEBUG -> DEBUG_LIFETIME_SECONDS;
            default -> 60.0;
        };
    }
}
