package io.github.nullbrash.seepers.config;

import io.github.nullbrash.seepers.light.LightCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    public static final ModConfigSpec.IntValue LIGHT_UNCATEGORIZED_MAX;
    public static final ModConfigSpec.DoubleValue LIGHT_WATERLOGGED_FACTOR;
    public static final ModConfigSpec.IntValue LIGHT_WATER_ABSORPTION;
    public static final Map<LightCategory, ModConfigSpec.ConfigValue<List<? extends Integer>>> LIGHT_LEVELS =
            new EnumMap<>(LightCategory.class);
    public static final ModConfigSpec.DoubleValue FOG_CLEAR_VISIBILITY;
    public static final ModConfigSpec.DoubleValue FOG_BASE_DENSITY;
    public static final ModConfigSpec.DoubleValue FOG_LAYER_DENSITY;
    public static final ModConfigSpec.IntValue FOG_LAYER_MIN_Y;
    public static final ModConfigSpec.IntValue FOG_LAYER_MAX_Y;
    public static final ModConfigSpec.DoubleValue FOG_LAYER_THICKNESS_MIN;
    public static final ModConfigSpec.DoubleValue FOG_LAYER_THICKNESS_MAX;
    public static final ModConfigSpec.DoubleValue FOG_LAYER_SCALE;
    public static final ModConfigSpec.DoubleValue FOG_DRIFT;
    public static final ModConfigSpec.DoubleValue FOG_POCKET_SCALE;
    public static final ModConfigSpec.DoubleValue FOG_POCKET_THRESHOLD;
    public static final ModConfigSpec.DoubleValue FOG_POCKET_CLEARING;
    public static final ModConfigSpec.DoubleValue FOG_WAVE_PERIOD;
    public static final ModConfigSpec.DoubleValue FOG_WAVE_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue FOG_STORM_INTERVAL_MIN;
    public static final ModConfigSpec.DoubleValue FOG_STORM_INTERVAL_MAX;
    public static final ModConfigSpec.DoubleValue FOG_STORM_DURATION_MIN;
    public static final ModConfigSpec.DoubleValue FOG_STORM_DURATION_MAX;
    public static final ModConfigSpec.DoubleValue FOG_STORM_WARNING;
    public static final ModConfigSpec.DoubleValue FOG_STORM_DENSITY;
    public static final ModConfigSpec.DoubleValue FOG_RAIN_DENSITY;
    public static final ModConfigSpec.DoubleValue FOG_THUNDER_DENSITY;
    public static final ModConfigSpec.IntValue FOG_WATER_HEIGHT;
    public static final ModConfigSpec.DoubleValue FOG_WATER_DENSITY;
    public static final ModConfigSpec.DoubleValue FOG_DISTORTION_START;
    public static final ModConfigSpec.DoubleValue FOG_DISTORTION_FULL;
    public static final ModConfigSpec.DoubleValue FOG_DISTORTION_DENSITY;

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

        builder.comment("Block light. Changes apply where light is recomputed (new chunks, changed blocks).")
                .translation("seepers.configuration.light").push("light");
        LIGHT_UNCATEGORIZED_MAX = builder
                .comment("Maximum light level of emitting blocks that are in no light category tag (e.g. from other mods).")
                .translation("seepers.configuration.uncategorizedMax")
                .defineInRange("uncategorizedMax", 13, 0, 15);
        LIGHT_WATERLOGGED_FACTOR = builder
                .comment("Light multiplier for waterlogged blocks (except tag #seepers:light/no_water_dimming).")
                .translation("seepers.configuration.waterloggedFactor")
                .defineInRange("waterloggedFactor", 0.5, 0.0, 1.0);
        LIGHT_WATER_ABSORPTION = builder
                .comment("Light levels lost per block of water (vanilla: 1).")
                .translation("seepers.configuration.waterAbsorption")
                .defineInRange("waterAbsorption", 3, 1, 15);
        builder.comment("Light level of each tag #seepers:light/<name> as [overworld, nether, end]; candle: [1, 2, 3, 4 candles].")
                .translation("seepers.configuration.categories").push("categories");
        for (LightCategory category : LightCategory.values()) {
            LIGHT_LEVELS.put(category, builder
                    .translation("seepers.configuration.light." + category.id)
                    .defineList(category.id, category.defaults, () -> 0,
                            value -> value instanceof Integer level && level >= 0 && level <= 15));
        }
        builder.pop(2);

        builder.comment("Fog density field. Density is in halvings: each +1 halves how far you can see.")
                .translation("seepers.configuration.fog").push("fog");
        FOG_CLEAR_VISIBILITY = fogDouble(builder, "clearVisibility", "Blocks visible at density 0.", 32.0, 1.0, 512.0);
        FOG_BASE_DENSITY = fogDouble(builder, "baseDensity", "Everywhere-fog density (1.0 = 16 blocks with clearVisibility 32).", 1.0, 0.0, 8.0);
        builder.push("layers");
        FOG_LAYER_DENSITY = fogDouble(builder, "density", "Extra density inside a fog layer.", 1.0, 0.0, 8.0);
        FOG_LAYER_MIN_Y = fogInt(builder, "minY", "Lowest layer height.", 40, -2048, 2048);
        FOG_LAYER_MAX_Y = fogInt(builder, "maxY", "Highest layer height.", 200, -2048, 2048);
        FOG_LAYER_THICKNESS_MIN = fogDouble(builder, "thicknessMin", "Minimum layer thickness, blocks.", 6.0, 1.0, 128.0);
        FOG_LAYER_THICKNESS_MAX = fogDouble(builder, "thicknessMax", "Maximum layer thickness, blocks.", 12.0, 1.0, 128.0);
        FOG_LAYER_SCALE = fogDouble(builder, "scale", "Horizontal size of layer undulations, blocks.", 256.0, 8.0, 4096.0);
        FOG_DRIFT = fogDouble(builder, "driftBlocksPerMinute", "How fast layers and pockets drift.", 10.0, 0.0, 600.0);
        builder.pop();
        builder.push("pockets");
        FOG_POCKET_SCALE = fogDouble(builder, "scale", "Size of clear pockets, blocks.", 24.0, 2.0, 1024.0);
        FOG_POCKET_THRESHOLD = fogDouble(builder, "threshold", "Noise threshold for a pocket: higher = rarer (-1..1).", 0.45, -1.0, 1.0);
        FOG_POCKET_CLEARING = fogDouble(builder, "clearing", "How clear a pocket is at its core (0..1).", 1.0, 0.0, 1.0);
        builder.pop();
        builder.push("waves");
        FOG_WAVE_PERIOD = fogDouble(builder, "periodMinutes", "Period of slow density waves.", 4.0, 0.1, 600.0);
        FOG_WAVE_AMPLITUDE = fogDouble(builder, "amplitude", "Wave amplitude (density).", 0.25, 0.0, 4.0);
        builder.pop();
        builder.push("storms");
        FOG_STORM_INTERVAL_MIN = fogDouble(builder, "intervalMinDays", "Minimum time between storms, game days.", 2.0, 0.05, 100.0);
        FOG_STORM_INTERVAL_MAX = fogDouble(builder, "intervalMaxDays", "Maximum time between storms, game days.", 3.0, 0.05, 100.0);
        FOG_STORM_DURATION_MIN = fogDouble(builder, "durationMinMinutes", "Minimum storm duration.", 3.0, 0.1, 600.0);
        FOG_STORM_DURATION_MAX = fogDouble(builder, "durationMaxMinutes", "Maximum storm duration.", 5.0, 0.1, 600.0);
        FOG_STORM_WARNING = fogDouble(builder, "warningSeconds", "Fog thickens gradually for this long before a storm.", 60.0, 0.0, 3600.0);
        FOG_STORM_DENSITY = fogDouble(builder, "density", "Extra density at full storm (2.4 = ~3 blocks).", 2.4, 0.0, 8.0);
        builder.pop();
        builder.push("weather");
        FOG_RAIN_DENSITY = fogDouble(builder, "rain", "Extra density in rain.", 0.3, 0.0, 8.0);
        FOG_THUNDER_DENSITY = fogDouble(builder, "thunder", "Extra density in a thunderstorm (on top of rain).", 0.4, 0.0, 8.0);
        builder.pop();
        builder.push("water");
        FOG_WATER_HEIGHT = fogInt(builder, "height", "Height of the dense layer above open water, blocks.", 4, 0, 64);
        FOG_WATER_DENSITY = fogDouble(builder, "density", "Extra density in the layer above water.", 1.0, 0.0, 8.0);
        builder.pop();
        builder.comment("Temporary distortion = distance from world spawn, until world generation adds real zones.")
                .push("distortion");
        FOG_DISTORTION_START = fogDouble(builder, "startDistance", "Distance from spawn where fog starts to grow.", 1000.0, 0.0, 1.0E7);
        FOG_DISTORTION_FULL = fogDouble(builder, "fullDistance", "Distance where the extra density reaches its maximum.", 10000.0, 1.0, 1.0E7);
        FOG_DISTORTION_DENSITY = fogDouble(builder, "density", "Extra density at full distortion.", 1.0, 0.0, 8.0);
        builder.pop(2);

        SPEC = builder.build();
    }

    // Fog keys share one translation scheme: seepers.configuration.fog.<key>.
    private static ModConfigSpec.DoubleValue fogDouble(ModConfigSpec.Builder builder, String key, String comment,
                                                       double def, double min, double max) {
        return builder.comment(comment).translation("seepers.configuration.fog." + key).defineInRange(key, def, min, max);
    }

    private static ModConfigSpec.IntValue fogInt(ModConfigSpec.Builder builder, String key, String comment,
                                                 int def, int min, int max) {
        return builder.comment(comment).translation("seepers.configuration.fog." + key).defineInRange(key, def, min, max);
    }

    /** For code that can run before the server config is loaded (e.g. client rendering in the main menu). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ServerConfig() {}
}
