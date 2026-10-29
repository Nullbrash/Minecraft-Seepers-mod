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
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_ENABLED;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_MIN_MOVE_TICKS;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_MAX_SOURCES;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_OVERLAP_TICKS;
    public static final ModConfigSpec.DoubleValue DYNAMIC_LIGHT_KEEP_DISTANCE;
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
    public static final ModConfigSpec.DoubleValue LIDAR_RANGE;
    public static final ModConfigSpec.DoubleValue LIDAR_CONE_DEGREES;
    public static final ModConfigSpec.IntValue LIDAR_POINTS_PER_SECOND;
    public static final ModConfigSpec.IntValue LIDAR_UNSAVED_LIMIT;
    public static final ModConfigSpec.DoubleValue LIDAR_UNSAVED_SECONDS;
    public static final ModConfigSpec.IntValue LIDAR_BATTERY_MINUTES;
    public static final ModConfigSpec.DoubleValue LIDAR_REDSTONE_CHARGE;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_CONE_DEGREES;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_SCREEN_2;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_SCREEN_3;
    public static final ModConfigSpec.DoubleValue LIDAR_WIDE_COST;
    public static final ModConfigSpec.DoubleValue LIDAR_PASS_SPEED_BONUS;
    public static final ModConfigSpec.IntValue LIDAR_ROUND_PASS_TICKS;
    public static final ModConfigSpec.IntValue LIDAR_ROUND_PAUSE_TICKS;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_COST_1;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_COST_2;
    public static final ModConfigSpec.DoubleValue LIDAR_VIEW_COST_3;
    public static final ModConfigSpec.IntValue LIDAR_DENSITY_1;
    public static final ModConfigSpec.IntValue LIDAR_DENSITY_2;
    public static final ModConfigSpec.IntValue LIDAR_DENSITY_3;
    public static final ModConfigSpec.DoubleValue LIDAR_DENSITY_COST_1;
    public static final ModConfigSpec.DoubleValue LIDAR_DENSITY_COST_2;
    public static final ModConfigSpec.DoubleValue LIDAR_DENSITY_COST_3;
    public static final ModConfigSpec.DoubleValue LIDAR_BEAMS_COST_1;
    public static final ModConfigSpec.DoubleValue LIDAR_BEAMS_COST_2;
    public static final ModConfigSpec.DoubleValue LIDAR_BEAMS_COST_3;
    public static final ModConfigSpec.IntValue LIDAR_CARD_1;
    public static final ModConfigSpec.IntValue LIDAR_CARD_2;
    public static final ModConfigSpec.IntValue LIDAR_CARD_3;
    public static final ModConfigSpec.IntValue LIDAR_CARD_UPLOAD_KB;
    public static final ModConfigSpec.DoubleValue LIDAR_WIRING_DRAIN_MIN;
    public static final ModConfigSpec.DoubleValue LIDAR_WIRING_DRAIN_MAX;
    public static final ModConfigSpec.DoubleValue LIDAR_WIRING_CHARGE_MIN;
    public static final ModConfigSpec.DoubleValue LIDAR_WIRING_CHARGE_MAX;
    public static final ModConfigSpec.IntValue LIDAR_CARD_DOWNLOAD_KB;
    public static final ModConfigSpec.DoubleValue LIDAR_DYNAMO_RATE;
    public static final ModConfigSpec.DoubleValue LIDAR_SCHEMATIC_2_CHANCE;
    public static final ModConfigSpec.DoubleValue LIDAR_SCHEMATIC_3_CHANCE;
    public static final ModConfigSpec.DoubleValue FIRE_BLOW_OUT_START;
    public static final ModConfigSpec.DoubleValue FIRE_BLOW_OUT_FULL;
    public static final ModConfigSpec.DoubleValue FIRE_CHECK_SECONDS;
    public static final ModConfigSpec.IntValue FIRE_MAX_CHECKS_PER_TICK;
    public static final ModConfigSpec.DoubleValue HEAT_WARMUP_SECONDS;
    public static final ModConfigSpec.DoubleValue HEAT_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue HEAT_MAX_SOURCES_PER_CHUNK;
    public static final ModConfigSpec.DoubleValue ANOMALY_RESISTANCE_NEAR;
    public static final ModConfigSpec.DoubleValue ANOMALY_RESISTANCE_FAR;
    public static final ModConfigSpec.BooleanValue REGROWTH_ENABLED;
    public static final ModConfigSpec.DoubleValue REGROWTH_WALL_SECONDS;
    public static final ModConfigSpec.DoubleValue REGROWTH_GEODE_SECONDS;
    public static final ModConfigSpec.IntValue REGROWTH_MAX_CATCH_UP;
    public static final ModConfigSpec.DoubleValue CHISEL_ROCK_SECONDS;
    public static final ModConfigSpec.DoubleValue CHISEL_BLOCK_SECONDS;
    public static final ModConfigSpec.BooleanValue COLLAPSE_ENABLED;
    public static final ModConfigSpec.IntValue COLLAPSE_FENCE_RADIUS;
    public static final ModConfigSpec.IntValue COLLAPSE_WALL_RADIUS;
    public static final ModConfigSpec.IntValue COLLAPSE_SUPPORT_REACH;
    public static final ModConfigSpec.IntValue COLLAPSE_WARN_MIN_TICKS;
    public static final ModConfigSpec.IntValue COLLAPSE_WARN_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue COLLAPSE_DAMAGE_PER_BLOCK;
    public static final ModConfigSpec.IntValue COLLAPSE_DAMAGE_MAX;
    public static final ModConfigSpec.IntValue COLLAPSE_MAX_FALLING_PER_CHUNK;
    public static final ModConfigSpec.DoubleValue COLLAPSE_SPREAD_CHANCE;
    public static final ModConfigSpec.BooleanValue TREMOR_ENABLED;
    public static final ModConfigSpec.DoubleValue TREMOR_INTERVAL_NEAR_MIN;
    public static final ModConfigSpec.DoubleValue TREMOR_INTERVAL_NEAR_MAX;
    public static final ModConfigSpec.DoubleValue TREMOR_INTERVAL_FAR_MIN;
    public static final ModConfigSpec.DoubleValue TREMOR_INTERVAL_FAR_MAX;
    public static final ModConfigSpec.IntValue TREMOR_RADIUS_NEAR;
    public static final ModConfigSpec.IntValue TREMOR_RADIUS_FAR;
    public static final ModConfigSpec.DoubleValue TREMOR_SHARE_NEAR;
    public static final ModConfigSpec.DoubleValue TREMOR_SHARE_FAR;
    public static final ModConfigSpec.IntValue TREMOR_SHAKE_MIN_TICKS;
    public static final ModConfigSpec.IntValue TREMOR_SHAKE_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue CANE_NEAR_SHARE;
    public static final ModConfigSpec.DoubleValue CANE_DIRECT_SHARE;
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
        DYNAMIC_LIGHT_ENABLED = builder.comment("Dynamic light: lights carried by players (held items, helmet lamp) and flares, as invisible light blocks.")
                .translation("seepers.configuration.dynamicLight").define("dynamicLight", true);
        DYNAMIC_LIGHT_MIN_MOVE_TICKS = builder.comment("Dynamic light follows its carrier into the next block at most this often, in ticks (0 = at once).")
                .translation("seepers.configuration.dynamicLightMinMoveTicks").defineInRange("dynamicLightMinMoveTicks", 0, 0, 40);
        DYNAMIC_LIGHT_MAX_SOURCES = builder.comment("At most this many dynamic lights in the world at once.")
                .translation("seepers.configuration.dynamicLightMaxSources").defineInRange("dynamicLightMaxSources", 256, 0, 4096);
        DYNAMIC_LIGHT_OVERLAP_TICKS = builder.comment("After a step the old dynamic light stays this many ticks: the new one is in before the old goes (no dark blink).")
                .translation("seepers.configuration.dynamicLightOverlapTicks").defineInRange("dynamicLightOverlapTicks", 3, 0, 40);
        DYNAMIC_LIGHT_KEEP_DISTANCE = builder.comment("Dynamic light stays put until its carrier's eyes are this far from it, in blocks (fewer steps).")
                .translation("seepers.configuration.dynamicLightKeepDistance").defineInRange("dynamicLightKeepDistance", 1.5, 0.0, 4.0);
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

        builder.comment("Lidar (CLAUDE.md 5.3): while held, the beam traces a never-repeating rosette, so scans paint surfaces densely. Upgrades widen and deepen it, never the range.")
                .translation("seepers.configuration.lidar").push("lidar");
        LIDAR_RANGE = builder.comment("How far the rays reach, blocks (one range for every mode).")
                .translation("seepers.configuration.lidar.range").defineInRange("range", 48.0, 4.0, 128.0);
        LIDAR_CONE_DEGREES = builder.comment("Width of the basic beam, degrees.")
                .translation("seepers.configuration.lidar.coneDegrees").defineInRange("coneDegrees", 20.0, 1.0, 120.0);
        LIDAR_POINTS_PER_SECOND = builder.comment("Rays (points) per second of the basic beam while scanning.")
                .translation("seepers.configuration.lidar.pointsPerSecond").defineInRange("pointsPerSecond", 320, 20, 20000);
        LIDAR_UNSAVED_LIMIT = builder.comment("Lidar points shown at once with no memory card in the lidar (or a locked one); they fade quickly.")
                .translation("seepers.configuration.lidar.unsavedLimit").defineInRange("unsavedLimit", 262_144, 0, 4_194_304);
        LIDAR_UNSAVED_SECONDS = builder.comment("Seconds lidar points last with no memory card to keep them.")
                .translation("seepers.configuration.lidar.unsavedSeconds").defineInRange("unsavedSeconds", 10.0, 1.0, 600.0);
        LIDAR_BATTERY_MINUTES = builder.comment("Minutes of continuous basic scanning a full battery lasts.")
                .translation("seepers.configuration.lidar.batteryMinutes").defineInRange("batteryMinutes", 60, 1, 1440);
        LIDAR_REDSTONE_CHARGE = builder.comment("Share of a full battery one redstone dust adds.")
                .translation("seepers.configuration.lidar.redstoneCharge").defineInRange("redstoneCharge", 0.10, 0.001, 1.0);
        builder.comment("Lidar modules: what each level gives and how much faster it drains the battery.")
                .translation("seepers.configuration.lidar.modules").push("modules");
        LIDAR_VIEW_CONE_DEGREES = builder.comment("View I: width of the cone, degrees.")
                .translation("seepers.configuration.lidar.modules.viewConeDegrees")
                .defineInRange("viewConeDegrees", 40.0, 1.0, 120.0);
        LIDAR_VIEW_SCREEN_2 = builder.comment("View II: share of the screen's width and height its scan covers, centred.")
                .translation("seepers.configuration.lidar.modules.viewScreen2").defineInRange("viewScreen2", 0.5, 0.05, 1.0);
        LIDAR_VIEW_SCREEN_3 = builder.comment("View III: share of the screen's width and height its ordinary scan covers, centred.")
                .translation("seepers.configuration.lidar.modules.viewScreen3").defineInRange("viewScreen3", 0.9, 0.05, 1.0);
        LIDAR_PASS_SPEED_BONUS = builder.comment("The view key's 180 and 360 scans lay this much more points per second than the lidar's ordinary scan (0.4 = 40%).")
                .translation("seepers.configuration.lidar.modules.passSpeedBonus").defineInRange("passSpeedBonus", 0.4, 0.0, 10.0);
        LIDAR_WIDE_COST = builder.comment("View II 180 scan: battery drain multiplier while its passes run.")
                .translation("seepers.configuration.lidar.modules.wideCost").defineInRange("wideCost", 4.5, 0.0, 100.0);
        LIDAR_ROUND_PASS_TICKS = builder.comment("View III all-around scan: ticks one pass is spread over.")
                .translation("seepers.configuration.lidar.modules.roundPassTicks").defineInRange("roundPassTicks", 5, 1, 100);
        LIDAR_ROUND_PAUSE_TICKS = builder.comment("View III all-around scan: ticks between passes while the key is held.")
                .translation("seepers.configuration.lidar.modules.roundPauseTicks").defineInRange("roundPauseTicks", 10, 0, 200);
        LIDAR_VIEW_COST_1 = builder.comment("View I: battery drain multiplier.").translation("seepers.configuration.lidar.modules.viewCost1").defineInRange("viewCost1", 1.5, 0.0, 100.0);
        LIDAR_VIEW_COST_2 = builder.comment("View II: battery drain multiplier.").translation("seepers.configuration.lidar.modules.viewCost2").defineInRange("viewCost2", 3.0, 0.0, 100.0);
        LIDAR_VIEW_COST_3 = builder.comment("View III: battery drain multiplier.").translation("seepers.configuration.lidar.modules.viewCost3").defineInRange("viewCost3", 6.0, 0.0, 100.0);
        LIDAR_DENSITY_1 = builder.comment("Density I: points per second.").translation("seepers.configuration.lidar.modules.density1").defineInRange("density1", 640, 20, 20000);
        LIDAR_DENSITY_2 = builder.comment("Density II: points per second.").translation("seepers.configuration.lidar.modules.density2").defineInRange("density2", 1280, 20, 20000);
        LIDAR_DENSITY_3 = builder.comment("Density III: points per second.").translation("seepers.configuration.lidar.modules.density3").defineInRange("density3", 2560, 20, 20000);
        LIDAR_DENSITY_COST_1 = builder.comment("Density I: battery drain multiplier.").translation("seepers.configuration.lidar.modules.densityCost1").defineInRange("densityCost1", 1.5, 0.0, 100.0);
        LIDAR_DENSITY_COST_2 = builder.comment("Density II: battery drain multiplier.").translation("seepers.configuration.lidar.modules.densityCost2").defineInRange("densityCost2", 2.0, 0.0, 100.0);
        LIDAR_DENSITY_COST_3 = builder.comment("Density III: battery drain multiplier.").translation("seepers.configuration.lidar.modules.densityCost3").defineInRange("densityCost3", 3.0, 0.0, 100.0);
        LIDAR_BEAMS_COST_1 = builder.comment("Beams I (2 beams): battery drain multiplier.").translation("seepers.configuration.lidar.modules.beamsCost1").defineInRange("beamsCost1", 1.5, 0.0, 100.0);
        LIDAR_BEAMS_COST_2 = builder.comment("Beams II (4 beams): battery drain multiplier.").translation("seepers.configuration.lidar.modules.beamsCost2").defineInRange("beamsCost2", 2.0, 0.0, 100.0);
        LIDAR_BEAMS_COST_3 = builder.comment("Beams III (8 beams): battery drain multiplier.").translation("seepers.configuration.lidar.modules.beamsCost3").defineInRange("beamsCost3", 3.0, 0.0, 100.0);
        // Ring slots are twice the limit and must fit 24 bits (PointCloud).
        LIDAR_CARD_1 = builder.comment("Memory card I: points it keeps (the oldest go when it is full).").translation("seepers.configuration.lidar.modules.card1").defineInRange("card1", 1_048_576, 1, 8_388_608);
        LIDAR_CARD_2 = builder.comment("Memory card II: points it keeps.").translation("seepers.configuration.lidar.modules.card2").defineInRange("card2", 4_194_304, 1, 8_388_608);
        LIDAR_CARD_3 = builder.comment("Memory card III: points it keeps.").translation("seepers.configuration.lidar.modules.card3").defineInRange("card3", 8_388_608, 1, 8_388_608);
        LIDAR_CARD_UPLOAD_KB = builder.comment("Memory cards: kilobytes of new points per second a player may send to the server.")
                .translation("seepers.configuration.lidar.modules.cardUploadKb").defineInRange("cardUploadKb", 512, 16, 16384);
        LIDAR_WIRING_DRAIN_MIN = builder.comment("Wiring mini-game: battery drain cut of a solved but long wiring (twice the shortest or more).")
                .translation("seepers.configuration.lidar.modules.wiringDrainMin").defineInRange("wiringDrainMin", 0.05, 0.0, 0.9);
        LIDAR_WIRING_DRAIN_MAX = builder.comment("Wiring mini-game: battery drain cut of the shortest wiring.")
                .translation("seepers.configuration.lidar.modules.wiringDrainMax").defineInRange("wiringDrainMax", 0.25, 0.0, 0.9);
        LIDAR_WIRING_CHARGE_MIN = builder.comment("Wiring mini-game, with the dynamo module: crank power added by a solved but long wiring.")
                .translation("seepers.configuration.lidar.modules.wiringChargeMin").defineInRange("wiringChargeMin", 0.10, 0.0, 5.0);
        LIDAR_WIRING_CHARGE_MAX = builder.comment("Wiring mini-game, with the dynamo module: crank power added by the shortest wiring.")
                .translation("seepers.configuration.lidar.modules.wiringChargeMax").defineInRange("wiringChargeMax", 0.50, 0.0, 5.0);
        LIDAR_CARD_DOWNLOAD_KB = builder.comment("Memory cards: kilobytes per second the server sends a player of the card they put in.")
                .translation("seepers.configuration.lidar.modules.cardDownloadKb").defineInRange("cardDownloadKb", 2048, 16, 65536);
        LIDAR_DYNAMO_RATE = builder.comment("Hand dynamo: seconds of basic scanning charged per second of cranking.")
                .translation("seepers.configuration.lidar.modules.dynamoRate")
                .defineInRange("dynamoRate", 9.0, 0.0, 600.0);
        LIDAR_SCHEMATIC_2_CHANCE = builder.comment("Chance a mineshaft or dungeon chest holds a level II schematic.")
                .translation("seepers.configuration.lidar.modules.schematic2Chance")
                .defineInRange("schematic2Chance", 0.25, 0.0, 1.0);
        LIDAR_SCHEMATIC_3_CHANCE = builder.comment("Chance an ancient city or stronghold chest holds a level III schematic.")
                .translation("seepers.configuration.lidar.modules.schematic3Chance")
                .defineInRange("schematic3Chance", 0.2, 0.0, 1.0);
        builder.pop();
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
        REGROWTH_ENABLED = builder.comment("Blasted rock grows back, layer by layer from intact neighbours.")
                .translation("seepers.configuration.anomaly.regrowth").define("regrowth", true);
        REGROWTH_WALL_SECONDS = builder.comment("Seconds per regrown layer of wall and cliff rock.")
                .translation("seepers.configuration.anomaly.wallLayerSeconds").defineInRange("wallLayerSeconds", 300.0, 1.0, 86400.0);
        REGROWTH_GEODE_SECONDS = builder.comment("Seconds per regrown layer of geode rock.")
                .translation("seepers.configuration.anomaly.geodeLayerSeconds").defineInRange("geodeLayerSeconds", 150.0, 1.0, 86400.0);
        REGROWTH_MAX_CATCH_UP = builder.comment("At most this many layers are caught up at once after time away.")
                .translation("seepers.configuration.anomaly.maxCatchUp").defineInRange("maxCatchUp", 64, 1, 4096);
        CHISEL_ROCK_SECONDS = builder.comment("Holding the chisel this long hews a block of anomalous rock (0 = at once).")
                .translation("seepers.configuration.anomaly.chiselRockSeconds").defineInRange("chiselRockSeconds", 1.0, 0.0, 60.0);
        CHISEL_BLOCK_SECONDS = builder.comment("Holding the chisel this long turns an ordinary block into its polished or chiseled form (0 = at once).")
                .translation("seepers.configuration.anomaly.chiselBlockSeconds").defineInRange("chiselBlockSeconds", 0.5, 0.0, 60.0);
        builder.pop();

        builder.comment("Collapses and props").translation("seepers.configuration.collapse").push("collapse");
        COLLAPSE_ENABLED = builder.comment("Hanging blocks warn before they fall and props hold them; off = vanilla falling.")
                .translation("seepers.configuration.collapse.enabled").define("enabled", true);
        COLLAPSE_FENCE_RADIUS = builder.comment("A fence prop holds hanging blocks this far around it (2 = the step of 5 of a vanilla mineshaft).")
                .translation("seepers.configuration.collapse.fenceRadius").defineInRange("fenceRadius", 2, 0, 16);
        COLLAPSE_WALL_RADIUS = builder.comment("A stone wall prop holds hanging blocks this far around it.")
                .translation("seepers.configuration.collapse.wallRadius").defineInRange("wallRadius", 4, 0, 16);
        COLLAPSE_SUPPORT_REACH = builder.comment("A prop holds hanging blocks up to this many blocks above its top.")
                .translation("seepers.configuration.collapse.supportReach").defineInRange("supportReach", 4, 0, 16);
        COLLAPSE_WARN_MIN_TICKS = builder.comment("Warning (dust, crack) before a hanging block falls: shortest, in ticks.")
                .translation("seepers.configuration.collapse.warnMinTicks").defineInRange("warnMinTicks", 20, 0, 200);
        COLLAPSE_WARN_MAX_TICKS = builder.comment("Warning before a hanging block falls: longest, in ticks.")
                .translation("seepers.configuration.collapse.warnMaxTicks").defineInRange("warnMaxTicks", 40, 0, 200);
        COLLAPSE_DAMAGE_PER_BLOCK = builder.comment("Damage of falling sand, gravel and the like per block fallen.")
                .translation("seepers.configuration.collapse.damagePerBlock").defineInRange("damagePerBlock", 1.0, 0.0, 40.0);
        COLLAPSE_DAMAGE_MAX = builder.comment("Most damage one falling block deals (6 = three hearts).")
                .translation("seepers.configuration.collapse.damageMax").defineInRange("damageMax", 6, 0, 40);
        COLLAPSE_MAX_FALLING_PER_CHUNK = builder.comment("Falling blocks at once per chunk; the rest is set straight where it lands.")
                .translation("seepers.configuration.collapse.maxFallingPerChunk").defineInRange("maxFallingPerChunk", 32, 0, 1024);
        COLLAPSE_SPREAD_CHANCE = builder.comment("Chance a hanging block comes down when a neighbour beside or above it changes (1 = vanilla: a whole sheet goes).")
                .translation("seepers.configuration.collapse.spreadChance").defineInRange("spreadChance", 0.25, 0.0, 1.0);
        TREMOR_ENABLED = builder.comment("Tremors now and then around players shake hanging blocks loose.")
                .translation("seepers.configuration.collapse.tremorEnabled").define("tremorEnabled", true);
        TREMOR_INTERVAL_NEAR_MIN = builder.comment("Minutes between tremors near spawn (zone 0): shortest.")
                .translation("seepers.configuration.collapse.tremorIntervalNearMin").defineInRange("tremorIntervalNearMin", 40.0, 0.1, 1440.0);
        TREMOR_INTERVAL_NEAR_MAX = builder.comment("Minutes between tremors near spawn: longest.")
                .translation("seepers.configuration.collapse.tremorIntervalNearMax").defineInRange("tremorIntervalNearMax", 60.0, 0.1, 1440.0);
        TREMOR_INTERVAL_FAR_MIN = builder.comment("Minutes between tremors from zone 3 on: shortest (linear in distortion between).")
                .translation("seepers.configuration.collapse.tremorIntervalFarMin").defineInRange("tremorIntervalFarMin", 20.0, 0.1, 1440.0);
        TREMOR_INTERVAL_FAR_MAX = builder.comment("Minutes between tremors from zone 3 on: longest.")
                .translation("seepers.configuration.collapse.tremorIntervalFarMax").defineInRange("tremorIntervalFarMax", 20.0, 0.1, 1440.0);
        TREMOR_RADIUS_NEAR = builder.comment("Blocks around the player a tremor reaches near spawn.")
                .translation("seepers.configuration.collapse.tremorRadiusNear").defineInRange("tremorRadiusNear", 16, 1, 64);
        TREMOR_RADIUS_FAR = builder.comment("Blocks around the player a tremor reaches from zone 3 on.")
                .translation("seepers.configuration.collapse.tremorRadiusFar").defineInRange("tremorRadiusFar", 32, 1, 64);
        TREMOR_SHARE_NEAR = builder.comment("Share of hanging blocks a tremor shakes loose near spawn.")
                .translation("seepers.configuration.collapse.tremorShareNear").defineInRange("tremorShareNear", 0.05, 0.0, 1.0);
        TREMOR_SHARE_FAR = builder.comment("Share of hanging blocks a tremor shakes loose from zone 3 on.")
                .translation("seepers.configuration.collapse.tremorShareFar").defineInRange("tremorShareFar", 0.30, 0.0, 1.0);
        TREMOR_SHAKE_MIN_TICKS = builder.comment("The shake itself: shortest, in ticks.")
                .translation("seepers.configuration.collapse.tremorShakeMinTicks").defineInRange("tremorShakeMinTicks", 40, 1, 1200);
        TREMOR_SHAKE_MAX_TICKS = builder.comment("The shake itself: longest, in ticks.")
                .translation("seepers.configuration.collapse.tremorShakeMaxTicks").defineInRange("tremorShakeMaxTicks", 80, 1, 1200);
        CANE_NEAR_SHARE = builder.comment("Chance a cane tap on rock brings down the hanging blocks within 2 of it.")
                .translation("seepers.configuration.collapse.caneNearShare").defineInRange("caneNearShare", 0.10, 0.0, 1.0);
        CANE_DIRECT_SHARE = builder.comment("Chance a cane tap on a hanging block itself brings it and its neighbours down.")
                .translation("seepers.configuration.collapse.caneDirectShare").defineInRange("caneDirectShare", 0.50, 0.0, 1.0);
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
