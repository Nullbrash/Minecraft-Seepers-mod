package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/seepers-client.toml — registered only by the client entry point. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LOG_NETWORK;
    public static final ModConfigSpec.IntValue FOG_CELLS_PER_TICK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Performance").translation("seepers.configuration.performance").push("performance");
        FOG_CELLS_PER_TICK = builder
                .comment("Fog grid cells recomputed per client tick (the grid around the player has ~8000 cells).")
                .translation("seepers.configuration.fogCellsPerTick")
                .defineInRange("fogCellsPerTick", 400, 0, 8125);
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
