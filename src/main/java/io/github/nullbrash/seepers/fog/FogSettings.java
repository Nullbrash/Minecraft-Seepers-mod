package io.github.nullbrash.seepers.fog;

import io.github.nullbrash.seepers.config.ServerConfig;

/**
 * Snapshot of the fog part of the server config (synced to clients, so both sides compute the same
 * field). Density is measured in "halvings": each +1 halves the distance you can see.
 */
public record FogSettings(
        double clearVisibility,
        double baseDensity,
        double layerDensity,
        int layerMinY,
        int layerMaxY,
        double layerThicknessMin,
        double layerThicknessMax,
        double layerScale,
        double driftBlocksPerMinute,
        double pocketScale,
        double pocketThreshold,
        double pocketClearing,
        double wavePeriodMinutes,
        double waveAmplitude,
        double stormIntervalMinDays,
        double stormIntervalMaxDays,
        double stormDurationMinMinutes,
        double stormDurationMaxMinutes,
        double stormWarningSeconds,
        double stormDensity,
        double rainDensity,
        double thunderDensity,
        int waterLayerHeight,
        double waterDensity,
        double distortionDensity,
        double nearFraction,
        double haloStrength,
        double underwaterVisibility,
        int renderDistanceMargin) {

    private static volatile FogSettings current = read();

    public static FogSettings current() {
        return current;
    }

    /** Called when the server config (re)loads on either side. */
    public static void reload() {
        current = read();
    }

    /** Blocks you can see through fog of the given density. */
    public double visibility(double density) {
        return clearVisibility * Math.pow(2.0, -Math.max(0.0, density));
    }

    private static FogSettings read() {
        return new FogSettings(
                ServerConfig.get(ServerConfig.FOG_CLEAR_VISIBILITY),
                ServerConfig.get(ServerConfig.FOG_BASE_DENSITY),
                ServerConfig.get(ServerConfig.FOG_LAYER_DENSITY),
                ServerConfig.get(ServerConfig.FOG_LAYER_MIN_Y),
                ServerConfig.get(ServerConfig.FOG_LAYER_MAX_Y),
                ServerConfig.get(ServerConfig.FOG_LAYER_THICKNESS_MIN),
                ServerConfig.get(ServerConfig.FOG_LAYER_THICKNESS_MAX),
                ServerConfig.get(ServerConfig.FOG_LAYER_SCALE),
                ServerConfig.get(ServerConfig.FOG_DRIFT),
                ServerConfig.get(ServerConfig.FOG_POCKET_SCALE),
                ServerConfig.get(ServerConfig.FOG_POCKET_THRESHOLD),
                ServerConfig.get(ServerConfig.FOG_POCKET_CLEARING),
                ServerConfig.get(ServerConfig.FOG_WAVE_PERIOD),
                ServerConfig.get(ServerConfig.FOG_WAVE_AMPLITUDE),
                ServerConfig.get(ServerConfig.FOG_STORM_INTERVAL_MIN),
                ServerConfig.get(ServerConfig.FOG_STORM_INTERVAL_MAX),
                ServerConfig.get(ServerConfig.FOG_STORM_DURATION_MIN),
                ServerConfig.get(ServerConfig.FOG_STORM_DURATION_MAX),
                ServerConfig.get(ServerConfig.FOG_STORM_WARNING),
                ServerConfig.get(ServerConfig.FOG_STORM_DENSITY),
                ServerConfig.get(ServerConfig.FOG_RAIN_DENSITY),
                ServerConfig.get(ServerConfig.FOG_THUNDER_DENSITY),
                ServerConfig.get(ServerConfig.FOG_WATER_HEIGHT),
                ServerConfig.get(ServerConfig.FOG_WATER_DENSITY),
                ServerConfig.get(ServerConfig.FOG_DISTORTION_DENSITY),
                ServerConfig.get(ServerConfig.FOG_NEAR_FRACTION),
                ServerConfig.get(ServerConfig.FOG_HALO_STRENGTH),
                ServerConfig.get(ServerConfig.FOG_UNDERWATER_VISIBILITY),
                ServerConfig.get(ServerConfig.FOG_RENDER_MARGIN));
    }
}
