package io.github.nullbrash.seepers.config;

import io.github.nullbrash.seepers.heat.HeatCategory;
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
    public static final ModConfigSpec.IntValue EXEMPT_MAX_NEARBY;
    public static final ModConfigSpec.DoubleValue EXEMPT_RADIUS;
    public static final ModConfigSpec.DoubleValue EXEMPT_SPAWN_CHANCE;
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
    public static final ModConfigSpec.DoubleValue FOG_DISTORTION_DENSITY;
    public static final ModConfigSpec.DoubleValue FOG_NEAR_FRACTION;
    public static final ModConfigSpec.DoubleValue FOG_HALO_STRENGTH;
    public static final ModConfigSpec.DoubleValue FOG_UNDERWATER_VISIBILITY;
    public static final ModConfigSpec.IntValue FOG_RENDER_MARGIN;
    public static final ModConfigSpec.IntValue ROOM_MAX_VOLUME;
    public static final ModConfigSpec.DoubleValue ROOM_FILL_SECONDS;
    public static final ModConfigSpec.DoubleValue ROOM_CLEAR_SECONDS;
    public static final ModConfigSpec.DoubleValue ROOM_REFERENCE_VOLUME;
    public static final ModConfigSpec.IntValue ROOM_RECHECK_TICKS;
    public static final ModConfigSpec.IntValue TOUCH_POINT_LIMIT;
    public static final ModConfigSpec.DoubleValue TOUCH_POINT_LIFETIME_SECONDS;
    public static final ModConfigSpec.DoubleValue TOUCH_WARM_HEAT;
    public static final ModConfigSpec.IntValue TOUCH_FEET_RADIUS;
    public static final ModConfigSpec.DoubleValue TOUCH_HAND_REACH;
    public static final ModConfigSpec.DoubleValue TOUCH_HAND_CONE_DEGREES;
    public static final ModConfigSpec.IntValue TOUCH_HAND_POINTS;
    public static final ModConfigSpec.DoubleValue TOUCH_HAND_CLICKS_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TOUCH_LAVA_DAMAGE;
    public static final ModConfigSpec.DoubleValue CANE_REACH;
    public static final ModConfigSpec.DoubleValue SILHOUETTE_SECONDS;
    public static final ModConfigSpec.DoubleValue CANE_LAVA_BURN;
    public static final ModConfigSpec.DoubleValue CANE_SWEEP_MIN;
    public static final ModConfigSpec.DoubleValue CANE_SWEEP_MAX;
    public static final ModConfigSpec.IntValue CANE_SWEEP_TICKS;
    public static final ModConfigSpec.DoubleValue CANE_SWEEP_DEGREES;
    public static final ModConfigSpec.IntValue CANE_SWEEP_RAYS;
    public static final ModConfigSpec.IntValue CANE_POINTS;
    public static final ModConfigSpec.IntValue CANE_POINT_LIMIT;
    public static final ModConfigSpec.DoubleValue CANE_POINT_LIFETIME_SECONDS;
    public static final ModConfigSpec.DoubleValue FIRE_BLOW_OUT_START;
    public static final ModConfigSpec.DoubleValue FIRE_BLOW_OUT_FULL;
    public static final ModConfigSpec.DoubleValue FIRE_CHECK_SECONDS;
    public static final ModConfigSpec.IntValue FIRE_MAX_CHECKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue HEAT_WARMUP_SECONDS;
    public static final ModConfigSpec.DoubleValue HEAT_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue HEAT_MAX_SOURCES_PER_CHUNK;
    public static final ModConfigSpec.DoubleValue ANOMALY_RESISTANCE_NEAR;
    public static final ModConfigSpec.DoubleValue ANOMALY_RESISTANCE_FAR;
    public static final Map<HeatCategory, ModConfigSpec.DoubleValue> HEAT_STRENGTH = new EnumMap<>(HeatCategory.class);
    public static final Map<HeatCategory, ModConfigSpec.DoubleValue> HEAT_RADIUS = new EnumMap<>(HeatCategory.class);

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
        EXEMPT_MAX_NEARBY = builder.comment("Exempt hostiles (drowned) that may spawn naturally: at most this many within exemptRadius.")
                .translation("seepers.configuration.exemptMaxNearby").defineInRange("exemptMaxNearby", 2, 0, 70);
        EXEMPT_RADIUS = builder.comment("Radius for exemptMaxNearby, blocks.")
                .translation("seepers.configuration.exemptRadius").defineInRange("exemptRadius", 48.0, 8.0, 128.0);
        EXEMPT_SPAWN_CHANCE = builder.comment("Chance that a natural spawn attempt of an exempt hostile goes ahead (slows refilling).")
                .translation("seepers.configuration.exemptSpawnChance").defineInRange("exemptSpawnChance", 0.3, 0.0, 1.0);
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
        builder.comment("Fog grows with distortion (level k where zone k starts; zone sizes are world generation data).")
                .push("distortion");
        FOG_DISTORTION_DENSITY = fogDouble(builder, "perLevel", "Extra density per distortion level.", 0.33, 0.0, 4.0);
        builder.pop();
        builder.push("render");
        FOG_NEAR_FRACTION = fogDouble(builder, "nearFraction", "Fog starts at this fraction of the visibility distance.", 0.15, 0.0, 1.0);
        FOG_HALO_STRENGTH = fogDouble(builder, "haloStrength", "How much the fog lightens when you stand in light (0 = never).", 0.25, 0.0, 1.0);
        FOG_UNDERWATER_VISIBILITY = fogDouble(builder, "underwaterVisibility", "Blocks visible under water (more with water vision and conduit power).", 6.0, 1.0, 128.0);
        FOG_RENDER_MARGIN = fogInt(builder, "renderDistanceMargin", "Fog always ends this many blocks before the render distance.", 16, 0, 256);
        builder.pop(2);

        builder.comment("Closed rooms keep fog out; leaks let it creep back in.")
                .translation("seepers.configuration.rooms").push("rooms");
        ROOM_MAX_VOLUME = builder.comment("Largest room, in air blocks; bigger spaces count as open.")
                .translation("seepers.configuration.rooms.maxVolume").defineInRange("maxVolume", 16384, 1, 262144);
        ROOM_FILL_SECONDS = builder.comment("Seconds for fog to fill a reference-size room through one leak; more leaks per volume - faster.")
                .translation("seepers.configuration.rooms.fillSeconds").defineInRange("fillSeconds", 60.0, 1.0, 3600.0);
        ROOM_CLEAR_SECONDS = builder.comment("Seconds for fog to clear after a room is closed.")
                .translation("seepers.configuration.rooms.clearSeconds").defineInRange("clearSeconds", 120.0, 0.0, 3600.0);
        ROOM_REFERENCE_VOLUME = builder.comment("Room volume (blocks) for which one leak takes fillSeconds.")
                .translation("seepers.configuration.rooms.referenceVolume").defineInRange("referenceVolume", 64.0, 1.0, 262144.0);
        ROOM_RECHECK_TICKS = builder.comment("Ticks between room checks around players.")
                .translation("seepers.configuration.rooms.recheckTicks").defineInRange("recheckTicks", 20, 1, 1200);
        builder.pop();

        builder.comment("Touch: the first sense, drawn as points (CLAUDE.md 5.1). Balance values for difficulty presets.")
                .translation("seepers.configuration.touch").push("touch");
        TOUCH_POINT_LIMIT = builder.comment("Touch points kept at once; the oldest go first. Lidar upgrades do not raise it.")
                .translation("seepers.configuration.touch.pointLimit").defineInRange("pointLimit", 8192, 0, 65536);
        TOUCH_POINT_LIFETIME_SECONDS = builder.comment("Seconds a touch point lasts; it fades over the last third.")
                .translation("seepers.configuration.touch.pointLifetimeSeconds").defineInRange("pointLifetimeSeconds", 60.0, 1.0, 3600.0);
        TOUCH_WARM_HEAT = builder.comment("Heat (fog density cleared by heat sources) from which a surface feels warm.")
                .translation("seepers.configuration.touch.warmHeat").defineInRange("warmHeat", 0.4, 0.0, 16.0);
        TOUCH_FEET_RADIUS = builder.comment("Floor felt by the feet around a walking or crouching player, blocks (running: only underfoot).")
                .translation("seepers.configuration.touch.feetRadius").defineInRange("feetRadius", 1, 0, 4);
        TOUCH_HAND_REACH = builder.comment("How far a click reaches with the hand, blocks.")
                .translation("seepers.configuration.touch.handReach").defineInRange("handReach", 3.0, 0.5, 6.0);
        TOUCH_HAND_CONE_DEGREES = builder.comment("Full angle of the cone a click feels along the view, degrees.")
                .translation("seepers.configuration.touch.handConeDegrees").defineInRange("handConeDegrees", 25.0, 0.0, 90.0);
        TOUCH_HAND_POINTS = builder.comment("Rays (points) per click.")
                .translation("seepers.configuration.touch.handPoints").defineInRange("handPoints", 6, 1, 64);
        TOUCH_HAND_CLICKS_PER_SECOND = builder.comment("Most clicks per second that feel; faster ones are ignored.")
                .translation("seepers.configuration.touch.handClicksPerSecond").defineInRange("handClicksPerSecond", 20.0, 0.5, 20.0);
        TOUCH_LAVA_DAMAGE = builder.comment("Damage for touching lava with the hand (no fire), at most once a second.")
                .translation("seepers.configuration.touch.lavaDamage").defineInRange("lavaDamage", 1.0, 0.0, 40.0);
        builder.pop();

        builder.comment("Cane (CLAUDE.md 5.2): taps along the view, sweeps on the floor ahead. Balance values for presets.")
                .translation("seepers.configuration.cane").push("cane");
        CANE_REACH = builder.comment("How far an aimed tap reaches, blocks.")
                .translation("seepers.configuration.cane.reach").defineInRange("reach", 4.0, 1.0, 8.0);
        CANE_SWEEP_MIN = builder.comment("Nearest floor a sweep taps, blocks ahead.")
                .translation("seepers.configuration.cane.sweepMin").defineInRange("sweepMin", 1.0, 0.5, 8.0);
        CANE_SWEEP_MAX = builder.comment("Farthest floor a sweep taps, blocks ahead.")
                .translation("seepers.configuration.cane.sweepMax").defineInRange("sweepMax", 4.0, 0.5, 8.0);
        CANE_SWEEP_TICKS = builder.comment("Ticks between sweeps (6 = about 3 a second).")
                .translation("seepers.configuration.cane.sweepTicks").defineInRange("sweepTicks", 6, 3, 100);
        CANE_SWEEP_DEGREES = builder.comment("Width of the fan a sweep scans straight ahead, degrees.")
                .translation("seepers.configuration.cane.sweepDegrees").defineInRange("sweepDegrees", 30.0, 5.0, 120.0);
        CANE_SWEEP_RAYS = builder.comment("Rays (points) per sweep, floor to knee.")
                .translation("seepers.configuration.cane.sweepRays").defineInRange("sweepRays", 24, 1, 128);
        CANE_POINTS = builder.comment("Points per aimed tap.")
                .translation("seepers.configuration.cane.points").defineInRange("points", 16, 1, 64);
        CANE_POINT_LIMIT = builder.comment("Cane points kept at once; the oldest go first.")
                .translation("seepers.configuration.cane.pointLimit").defineInRange("pointLimit", 8192, 0, 65536);
        CANE_LAVA_BURN = builder.comment("Share of a wooden cane's full durability lost when it touches lava (an iron tip does not burn).")
                .translation("seepers.configuration.cane.lavaBurn").defineInRange("lavaBurn", 0.05, 0.0, 1.0);
        SILHOUETTE_SECONDS = builder.comment("Seconds a mob's silhouette lasts after a blow (it fades over the last third).")
                .translation("seepers.configuration.cane.silhouetteSeconds").defineInRange("silhouetteSeconds", 8.0, 1.0, 120.0);
        CANE_POINT_LIFETIME_SECONDS = builder.comment("Seconds a cane point lasts; it fades over the last third.")
                .translation("seepers.configuration.cane.pointLifetimeSeconds").defineInRange("pointLifetimeSeconds", 60.0, 1.0, 3600.0);
        builder.pop();

        builder.comment("Fog blows out torches, breaks campfires and puts out candles (tags #seepers:fire/*). Lanterns are safe.")
                .translation("seepers.configuration.fires").push("fires");
        FIRE_BLOW_OUT_START = builder.comment("Fog density (with heat and rooms) at the fire below which it is safe.")
                .translation("seepers.configuration.fires.blowOutStart").defineInRange("blowOutStart", 1.5, 0.0, 16.0);
        FIRE_BLOW_OUT_FULL = builder.comment("Density from which every check puts the fire out; the chance grows linearly from blowOutStart.")
                .translation("seepers.configuration.fires.blowOutFull").defineInRange("blowOutFull", 2.5, 0.0, 16.0);
        FIRE_CHECK_SECONDS = builder.comment("Seconds between checks of each fire near players.")
                .translation("seepers.configuration.fires.checkSeconds").defineInRange("checkSeconds", 10.0, 1.0, 600.0);
        FIRE_MAX_CHECKS_PER_TICK = builder.comment("Fog samples per tick for fire checks; the rest wait for the next tick.")
                .translation("seepers.configuration.fires.maxChecksPerTick").defineInRange("maxChecksPerTick", 256, 1, 65536);
        builder.pop();

        builder.comment("Heat clears fog around its sources (tags #seepers:heat/<name>).")
                .translation("seepers.configuration.heat").push("heat");
        HEAT_WARMUP_SECONDS = builder.comment("Seconds until a newly lit source clears fog fully.")
                .translation("seepers.configuration.heat.warmupSeconds").defineInRange("warmupSeconds", 30.0, 0.0, 3600.0);
        HEAT_COOLDOWN_SECONDS = builder.comment("Seconds for the fog to return after a source goes out (inertia).")
                .translation("seepers.configuration.heat.cooldownSeconds").defineInRange("cooldownSeconds", 120.0, 0.0, 36000.0);
        HEAT_MAX_SOURCES_PER_CHUNK = builder.comment("Heat sources tracked per chunk (lava lakes and the like are capped).")
                .translation("seepers.configuration.heat.maxSourcesPerChunk").defineInRange("maxSourcesPerChunk", 64, 0, 4096);
        for (HeatCategory category : HeatCategory.values()) {
            builder.push(category.id);
            HEAT_STRENGTH.put(category, builder.comment("Fog density cleared at the source (halvings).")
                    .translation("seepers.configuration.heat.strength").defineInRange("strength", category.defaultStrength, 0.0, 16.0));
            HEAT_RADIUS.put(category, builder.comment("Radius of the heated area, blocks.")
                    .translation("seepers.configuration.heat.radius").defineInRange("radius", category.defaultRadius, 0.0, 16.0));
            builder.pop();
        }
        builder.pop();

        builder.comment("Anomalous rock (only explosions break it)").translation("seepers.configuration.anomaly").push("anomaly");
        ANOMALY_RESISTANCE_NEAR = builder.comment("Explosion resistance at the start of zone 1 (stone is 6: TNT and creepers break it).")
                .translation("seepers.configuration.anomaly.resistanceNear").defineInRange("resistanceNear", 6.0, 0.0, 3600000.0);
        ANOMALY_RESISTANCE_FAR = builder.comment("Explosion resistance from zone 3 on (about 14: only the core of a TNT blast).")
                .translation("seepers.configuration.anomaly.resistanceFar").defineInRange("resistanceFar", 14.0, 0.0, 3600000.0);
        builder.pop();

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
