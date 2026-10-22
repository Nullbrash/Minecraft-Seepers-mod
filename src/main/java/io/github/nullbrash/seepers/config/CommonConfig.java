package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/seepers-common.toml — settings shared by both sides, not synced. */
public final class CommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue VERBOSE_LOGGING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Debugging and diagnostics").translation("seepers.configuration.debug").push("debug");
        VERBOSE_LOGGING = builder
                .comment("Log extra diagnostic messages from Seepers systems.")
                .translation("seepers.configuration.verboseLogging")
                .define("verboseLogging", false);
        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {}
}
