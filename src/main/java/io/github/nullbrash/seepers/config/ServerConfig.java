package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * config/seepers-server.toml, loaded on server start and synced to clients.
 * A copy in &lt;world&gt;/serverconfig/ overrides it for that world only.
 */
public final class ServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_COMMANDS;
    public static final ModConfigSpec.IntValue BLACK_LIGHT_LEVEL;
    public static final ModConfigSpec.DoubleValue MAX_GAMMA;
    public static final ModConfigSpec.BooleanValue NIGHT_VISION_BRIGHTENS;
    public static final ModConfigSpec.BooleanValue DISABLE_NATURAL_HOSTILE_SPAWNS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Debugging and diagnostics").translation("seepers.configuration.debug").push("debug");
        DEBUG_COMMANDS = builder
                .comment("Enable /seepers debug commands for operators (permission level 2).")
                .translation("seepers.configuration.debugCommands")
                .define("debugCommands", true);
        builder.pop();

        builder.comment("True darkness (applied on clients; set by the server)").translation("seepers.configuration.darkness").push("darkness");
        BLACK_LIGHT_LEVEL = builder
                .comment("Light levels up to this value render completely black. Visible radius of a light source = its level - this - 1.")
                .translation("seepers.configuration.blackLightLevel")
                .defineInRange("blackLightLevel", 2, 0, 15);
        MAX_GAMMA = builder
                .comment("Upper limit for the Brightness (gamma) video option: 0.0 = Moody, 1.0 = Bright.")
                .translation("seepers.configuration.maxGamma")
                .defineInRange("maxGamma", 0.0, 0.0, 1.0);
        NIGHT_VISION_BRIGHTENS = builder
                .comment("If false, the Night Vision effect still exists but does not brighten the world.")
                .translation("seepers.configuration.nightVisionBrightens")
                .define("nightVisionBrightens", false);
        builder.pop();

        builder.comment("Mob spawning").translation("seepers.configuration.mobs").push("mobs");
        DISABLE_NATURAL_HOSTILE_SPAWNS = builder
                .comment("Block natural spawning of hostile mobs (also patrols and phantoms). Exceptions: entity tag #seepers:natural_spawn_exempt.")
                .translation("seepers.configuration.disableNaturalHostileSpawns")
                .define("disableNaturalHostileSpawns", true);
        builder.pop();

        SPEC = builder.build();
    }

    /** For code that can run before the server config is loaded (e.g. client rendering in the main menu). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ServerConfig() {}
}
