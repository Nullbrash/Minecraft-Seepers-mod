package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/seepers-client.toml — registered only by the client entry point. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LOG_NETWORK;
    public static final ModConfigSpec.IntValue FOG_CELLS_PER_TICK;
    public static final ModConfigSpec.DoubleValue POINT_SIZE;
    public static final ModConfigSpec.DoubleValue POINT_MIN_PIXELS;
    public static final ModConfigSpec.IntValue POINT_LIGHT_FADE_START;
    public static final ModConfigSpec.IntValue POINT_LIGHT_FADE_END;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Performance").translation("seepers.configuration.performance").push("performance");
        FOG_CELLS_PER_TICK = builder
                .comment("Fog grid cells recomputed per client tick (the grid around the player has ~8000 cells).")
                .translation("seepers.configuration.fogCellsPerTick")
                .defineInRange("fogCellsPerTick", 400, 0, 8125);
        builder.pop();

        builder.comment("Point cloud (touch, later cane and lidar)").translation("seepers.configuration.points").push("points");
        POINT_SIZE = builder.comment("Point diameter in blocks; points shrink with distance.")
                .translation("seepers.configuration.points.size").defineInRange("size", 0.0625, 0.01, 0.5);
        POINT_MIN_PIXELS = builder.comment("Far points never get smaller than this many pixels.")
                .translation("seepers.configuration.points.minPixels").defineInRange("minPixels", 1.5, 0.5, 8.0);
        POINT_LIGHT_FADE_START = builder.comment("Block light at which surface points start to fade (the surface becomes visible).")
                .translation("seepers.configuration.points.lightFadeStart").defineInRange("lightFadeStart", 4, 0, 15);
        POINT_LIGHT_FADE_END = builder.comment("Block light at which surface points are gone.")
                .translation("seepers.configuration.points.lightFadeEnd").defineInRange("lightFadeEnd", 8, 0, 15);
        builder.pop();

        builder.comment("Debugging and diagnostics").translation("seepers.configuration.debug").push("debug");
        LOG_NETWORK = builder
                .comment("Log Seepers packets received by the client.")
                .translation("seepers.configuration.logNetwork")
                .define("logNetwork", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
