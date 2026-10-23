package io.github.nullbrash.seepers.heat;

import io.github.nullbrash.seepers.config.ServerConfig;

/** Snapshot of the heat part of the server config (synced to clients). Radius is capped at 16 blocks. */
public record HeatSettings(double warmupTicks, double cooldownTicks, int maxSourcesPerChunk,
                           double[] strength, double[] radius, double maxRadius) {
    private static volatile HeatSettings current = read();

    public static HeatSettings current() {
        return current;
    }

    public static void reload() {
        current = read();
    }

    private static HeatSettings read() {
        HeatCategory[] categories = HeatCategory.values();
        double[] strength = new double[categories.length];
        double[] radius = new double[categories.length];
        double maxRadius = 0;
        for (HeatCategory category : categories) {
            strength[category.ordinal()] = ServerConfig.get(ServerConfig.HEAT_STRENGTH.get(category));
            radius[category.ordinal()] = ServerConfig.get(ServerConfig.HEAT_RADIUS.get(category));
            maxRadius = Math.max(maxRadius, radius[category.ordinal()]);
        }
        return new HeatSettings(ServerConfig.get(ServerConfig.HEAT_WARMUP_SECONDS) * 20.0,
                ServerConfig.get(ServerConfig.HEAT_COOLDOWN_SECONDS) * 20.0,
                ServerConfig.get(ServerConfig.HEAT_MAX_SOURCES_PER_CHUNK), strength, radius, maxRadius);
    }
}
