package io.github.nullbrash.seepers.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/seepers-client.toml — registered only by the client entry point. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LOG_NETWORK;
    public static final ModConfigSpec.BooleanValue DIAGNOSTICS;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue TREMOR_VOLUME;
    public static final ModConfigSpec.IntValue FOG_CELLS_PER_TICK;
    public static final ModConfigSpec.DoubleValue POINT_SIZE;
    public static final ModConfigSpec.DoubleValue POINT_MIN_PIXELS;
    public static final ModConfigSpec.IntValue POINT_LIGHT_FADE_START;
    public static final ModConfigSpec.IntValue POINT_LIGHT_FADE_END;
    public static final ModConfigSpec.BooleanValue TOUCH_CAPTION;
    public static final ModConfigSpec.BooleanValue TOUCH_CAPTION_NAMES;
    public static final ModConfigSpec.EnumValue<io.github.nullbrash.seepers.client.touch.TouchCaption.Position> TOUCH_CAPTION_POSITION;
    public static final ModConfigSpec.BooleanValue RENDER_AUTO;
    public static final ModConfigSpec.IntValue RENDER_MIN;
    public static final ModConfigSpec.IntValue RENDER_SMOOTHING_SECONDS;
    public static final ModConfigSpec.IntValue RENDER_SETTLE_SECONDS;
    public static final ModConfigSpec.IntValue RENDER_RAISE_SECONDS;
    public static final ModConfigSpec.IntValue RENDER_MOVING_MAX;
    public static final ModConfigSpec.IntValue RENDER_STILL_SECONDS;
    public static final ModConfigSpec.IntValue RENDER_STILL_RAISE_SECONDS;
    public static final ModConfigSpec.DoubleValue RENDER_TPS_LOWER;
    public static final ModConfigSpec.DoubleValue RENDER_TPS_RAISE;
    public static final ModConfigSpec.DoubleValue RENDER_FPS_LOWER;
    public static final ModConfigSpec.DoubleValue RENDER_FPS_RAISE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Performance").translation("seepers.configuration.performance").push("performance");
        FOG_CELLS_PER_TICK = builder
                .comment("Fog grid cells recomputed per client tick (the grid around the player has ~8000 cells).")
                .translation("seepers.configuration.fogCellsPerTick")
                .defineInRange("fogCellsPerTick", 400, 0, 8125);
        builder.pop();

        builder.comment("Automatic render distance: from the minimum up to the slider, never past the server")
                .translation("seepers.configuration.renderDistance").push("renderDistance");
        RENDER_AUTO = builder.comment("Lower the render distance on steady lag and raise it back when the game keeps up.")
                .translation("seepers.configuration.renderDistance.auto").define("auto", true);
        RENDER_MIN = builder.comment("Never below this many chunks.")
                .translation("seepers.configuration.renderDistance.min").defineInRange("min", 2, 2, 32);
        RENDER_SMOOTHING_SECONDS = builder.comment("Frame rate is averaged over this many seconds; single hitches do not count.")
                .translation("seepers.configuration.renderDistance.smoothingSeconds").defineInRange("smoothingSeconds", 5, 1, 30);
        RENDER_SETTLE_SECONDS = builder.comment("After any change, measurements are ignored this long (rebuilding the world mesh is a hitch itself).")
                .translation("seepers.configuration.renderDistance.settleSeconds").defineInRange("settleSeconds", 10, 0, 60);
        RENDER_RAISE_SECONDS = builder.comment("The distance grows by at most one chunk this often.")
                .translation("seepers.configuration.renderDistance.raiseSeconds").defineInRange("raiseSeconds", 15, 1, 300);
        RENDER_MOVING_MAX = builder.comment("On the move the distance stays at most this many chunks (new chunks keep loading).")
                .translation("seepers.configuration.renderDistance.movingMax").defineInRange("movingMax", 8, 2, 32);
        RENDER_STILL_SECONDS = builder.comment("Standing this long counts as standing still: the distance may grow up to the slider.")
                .translation("seepers.configuration.renderDistance.stillSeconds").defineInRange("stillSeconds", 3, 1, 60);
        RENDER_STILL_RAISE_SECONDS = builder.comment("Standing still, the distance grows by one chunk this often.")
                .translation("seepers.configuration.renderDistance.stillRaiseSeconds").defineInRange("stillRaiseSeconds", 5, 1, 60);
        RENDER_TPS_LOWER = builder.comment("Server ticks per second (20 = no lag) below which the distance drops. Checked first.")
                .translation("seepers.configuration.renderDistance.tpsLower").defineInRange("tpsLower", 18.0, 0.0, 20.0);
        RENDER_TPS_RAISE = builder.comment("The distance grows only while the server keeps at least this TPS.")
                .translation("seepers.configuration.renderDistance.tpsRaise").defineInRange("tpsRaise", 19.5, 0.0, 20.0);
        RENDER_FPS_LOWER = builder.comment("Average frames per second below which the distance drops.")
                .translation("seepers.configuration.renderDistance.fpsLower").defineInRange("fpsLower", 30.0, 0.0, 1000.0);
        RENDER_FPS_RAISE = builder.comment("The distance grows only above this average frame rate (lowered to fit a frame rate limit).")
                .translation("seepers.configuration.renderDistance.fpsRaise").defineInRange("fpsRaise", 50.0, 0.0, 1000.0);
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

        builder.comment("What the hand felt, shown on screen").translation("seepers.configuration.touchCaption").push("touchCaption");
        TOUCH_CAPTION = builder.comment("Show what the hand felt (block name and material).")
                .translation("seepers.configuration.touchCaption.enabled").define("enabled", true);
        TOUCH_CAPTION_NAMES = builder.comment("Include the block's name.")
                .translation("seepers.configuration.touchCaption.names").define("names", true);
        TOUCH_CAPTION_POSITION = builder.comment("Where on the screen.")
                .translation("seepers.configuration.touchCaption.position")
                .defineEnum("position", io.github.nullbrash.seepers.client.touch.TouchCaption.Position.CROSSHAIR);
        builder.pop();

        CAMERA_SHAKE = builder.comment("Camera shake of collapses and tremors: 1 = as designed, 0 = off.")
                .translation("seepers.configuration.cameraShake").defineInRange("cameraShake", 1.0, 0.0, 2.0);
        TREMOR_VOLUME = builder.comment("Volume of the tremor rumble: 1 = as designed, 0.2 = 80% quieter, 1.2 = 20% louder.")
                .translation("seepers.configuration.tremorVolume").defineInRange("tremorVolume", 1.0, 0.2, 1.2);

        builder.comment("Debugging and diagnostics").translation("seepers.configuration.debug").push("debug");
        LOG_NETWORK = builder
                .comment("Log Seepers packets received by the client.")
                .translation("seepers.configuration.logNetwork")
                .define("logNetwork", false);
        DIAGNOSTICS = builder
                .comment("Keep logs/seepers-diagnostics.log (system, frame rate, warnings; personal data replaced). /seepers report packs it for sending.")
                .translation("seepers.configuration.diagnostics")
                .define("diagnostics", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}
}
