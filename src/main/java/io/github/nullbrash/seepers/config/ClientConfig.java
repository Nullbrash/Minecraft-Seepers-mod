package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/seepers-client.toml — registered only by the client entry point. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LOG_NETWORK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
