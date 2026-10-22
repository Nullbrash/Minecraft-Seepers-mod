package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * config/seepers-server.toml, loaded on server start and synced to clients.
 * A copy in &lt;world&gt;/serverconfig/ overrides it for that world only.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Debugging and diagnostics").translation("seepers.configuration.debug").push("debug");
        DEBUG_COMMANDS = builder
                .comment("Enable /seepers debug commands for operators (permission level 2).")
                .translation("seepers.configuration.debugCommands")
                .define("debugCommands", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}
}
