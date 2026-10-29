package io.github.nullbrash.seepers.client.points;

import io.github.nullbrash.seepers.config.ServerConfig;

/**
 * Where a point came from. Each source has its own share of the cloud (CLAUDE.md 5.1): filling one share
 * never pushes out another's points.
 */
public enum PointSource {
    TOUCH,
    CANE,
    /** Silhouettes of mobs left by blows. */
    SILHOUETTE,
    /** Lidar scans kept on the memory card in the lidar: never fade by time, only pushed out by newer ones (CLAUDE.md 5.3). */
    LIDAR,
    /** Foreign data (milestone 16): no share yet. */
    FOREIGN,
    /** {@code /seepers points test}: large share for render benchmarks. */
    DEBUG,
    /** Lidar scans with no card to keep them (or a locked one): gone in seconds. */
    LIDAR_UNSAVED;

    /** values() without the copy: looked up per point. */
    public static final PointSource[] VALUES = values();

    private static final int DEBUG_CAPACITY = 4_194_304;
    private static final int SILHOUETTE_CAPACITY = 4096;
    private static final double DEBUG_LIFETIME_SECONDS = 600;
    /** "Forever" for the shader's fade (it starts after two thirds of the lifetime): months of play. */
    private static final double LIDAR_LIFETIME_SECONDS = 1.0E7;

    /** The lidar share's limit: the active memory card's capacity (0 = no card). */
    private static int lidarLimit = 0;
    /** Slots the lidar share may grow to whatever its limit: twice the largest memory, so a new limit keeps the points. */
    public static final int LIDAR_MAX_LIMIT = 8_388_608;

    public static void setLidarLimit(int limit) {
        lidarLimit = Math.min(limit, LIDAR_MAX_LIMIT);
    }

    /** Whether a change of this share's limit keeps its points (the ring is sized for the largest). */
    public boolean resizable() {
        return this == LIDAR;
    }

    public int capacity() {
        return switch (this) {
            case TOUCH -> ServerConfig.get(ServerConfig.TOUCH_POINT_LIMIT);
            case CANE -> ServerConfig.get(ServerConfig.CANE_POINT_LIMIT);
            case SILHOUETTE -> SILHOUETTE_CAPACITY;
            case LIDAR -> lidarLimit;
            case LIDAR_UNSAVED -> ServerConfig.get(ServerConfig.LIDAR_UNSAVED_LIMIT);
            case DEBUG -> DEBUG_CAPACITY;
            default -> 0;
        };
    }

    public float lifetimeSeconds() {
        return (float) switch (this) {
            case TOUCH -> ServerConfig.get(ServerConfig.TOUCH_POINT_LIFETIME_SECONDS);
            case CANE -> ServerConfig.get(ServerConfig.CANE_POINT_LIFETIME_SECONDS);
            case SILHOUETTE -> ServerConfig.get(ServerConfig.SILHOUETTE_SECONDS);
            case DEBUG -> DEBUG_LIFETIME_SECONDS;
            case LIDAR -> LIDAR_LIFETIME_SECONDS;
            case LIDAR_UNSAVED -> ServerConfig.get(ServerConfig.LIDAR_UNSAVED_SECONDS);
            default -> 60.0;
        };
    }
}
