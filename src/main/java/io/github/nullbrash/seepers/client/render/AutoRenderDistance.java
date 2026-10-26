package io.github.nullbrash.seepers.client.render;

import com.mojang.brigadier.arguments.FloatArgumentType;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Automatic render distance (CLAUDE.md 4.7): from the minimum up to the slider (and never past the server), one
 * chunk at a time. Server lag (TPS) decides first, frame rate second; averages over a few seconds, so single
 * hitches do not count. Every change rebuilds all chunk meshes — a hitch of its own — so the measurement restarts
 * after it.
 */
public final class AutoRenderDistance {
    private static final int OFF = Integer.MAX_VALUE;
    private static final int SAMPLE_TICKS = 20;
    /** Server reports come every second; older ones mean no data (a server without the report, or a stall). */
    private static final int TPS_STALE_TICKS = 100;

    private static volatile int current = OFF;
    /** The vanilla distance (slider capped by the server), seen on the last render. */
    private static volatile int ceiling;
    private static int ticks;
    private static final double STILL_RADIUS = 0.5;
    private static Vec3 anchor;
    private static int stillTicks;
    private static int lastChange;
    private static float[] fpsSamples = new float[0];
    private static int sampleCount;
    private static float serverTps = Float.NaN;
    private static int serverTpsTick;
    private static String lastReason = "";
    private static float simulatedTps = Float.NaN;
    private static float simulatedFps = Float.NaN;

    /** Render distance actually used; raw = the vanilla value. Called by the options mixin, every frame. */
    public static int limit(int raw) {
        ceiling = raw;
        return Math.min(raw, current);
    }

    /** Distance asked from the server, instead of the slider. */
    public static int request(int slider) {
        return Math.min(slider, current);
    }

    /** Slider caption with the distance in use appended, while in a world with the automatic mode on. */
    public static Component sliderCaption(Component caption) {
        int distance = current;
        if (distance == OFF || ceiling <= 0) return caption;
        return Component.translatable("seepers.renderDistance.caption", caption, Math.min(distance, ceiling));
    }

    public static void onServerLoad(float tps) {
        serverTps = tps;
        serverTpsTick = ticks;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) return;
        if (!ClientConfig.RENDER_AUTO.get()) {
            if (current != OFF) change(minecraft, OFF, "off");
            return;
        }
        int top = ceiling;
        if (top <= 0) return;
        int bottom = Math.min(ClientConfig.RENDER_MIN.get(), top);
        Vec3 position = minecraft.player.position();
        if (anchor == null || position.distanceToSqr(anchor) > STILL_RADIUS * STILL_RADIUS) {
            anchor = position;
            stillTicks = 0;
        } else {
            stillTicks++;
        }
        // "Freeze and look": on the move new chunks keep loading, so the distance stays at the moving cap.
        boolean still = stillTicks >= ClientConfig.RENDER_STILL_SECONDS.get() * 20;
        int roof = still ? top : Math.min(top, ClientConfig.RENDER_MOVING_MAX.get());
        if (current == OFF) {
            change(minecraft, roof, "start");
        } else if (current > roof) {
            change(minecraft, roof, still ? "ceiling" : "moving");
        }
        ticks++;
        if (ticks % SAMPLE_TICKS != 0) return;
        sample(minecraft);
        boolean settled = ticks - lastChange >= ClientConfig.RENDER_SETTLE_SECONDS.get() * 20 && sampleCount >= fpsSamples.length;
        // Standing still, growth does not wait for the full pause: the player is waiting to see further.
        boolean canRaise = still
                ? sampleCount > 0 && ticks - lastChange >= ClientConfig.RENDER_STILL_RAISE_SECONDS.get() * 20
                : settled && ticks - lastChange >= ClientConfig.RENDER_RAISE_SECONDS.get() * 20;
        if (!settled && !canRaise) return;

        float tps = tps();
        float fps = averageFps();
        int limit = minecraft.options.framerateLimit().get();
        // A player capping frames low would otherwise read as permanent lag.
        boolean capped = limit < Options.UNLIMITED_FRAMERATE_CUTOFF;
        double fpsLower = capped ? Math.min(ClientConfig.RENDER_FPS_LOWER.get(), limit * 0.8) : ClientConfig.RENDER_FPS_LOWER.get();
        double fpsRaise = capped ? Math.min(ClientConfig.RENDER_FPS_RAISE.get(), limit * 0.9) : ClientConfig.RENDER_FPS_RAISE.get();
        boolean tpsKnown = !Float.isNaN(tps);

        if (tpsKnown && tps < ClientConfig.RENDER_TPS_LOWER.get()) {
            if (settled && current > bottom) change(minecraft, current - 1, String.format(Locale.ROOT, "-1 TPS %.1f", tps));
        } else if (fps < fpsLower) {
            if (settled && current > bottom) change(minecraft, current - 1, String.format(Locale.ROOT, "-1 FPS %.0f", fps));
        } else if (canRaise && (!tpsKnown || tps >= ClientConfig.RENDER_TPS_RAISE.get()) && fps > fpsRaise && current < roof) {
            change(minecraft, current + 1, String.format(Locale.ROOT, still ? "+1 still, FPS %.0f" : "+1 FPS %.0f", fps));
        }
    }

    private static void sample(Minecraft minecraft) {
        int size = ClientConfig.RENDER_SMOOTHING_SECONDS.get();
        if (fpsSamples.length != size) {
            fpsSamples = new float[size];
            sampleCount = 0;
        }
        float fps = Float.isNaN(simulatedFps) ? minecraft.getFps() : simulatedFps;
        fpsSamples[sampleCount % size] = fps;
        sampleCount++;
    }

    private static float tps() {
        if (!Float.isNaN(simulatedTps)) return simulatedTps;
        return ticks - serverTpsTick > TPS_STALE_TICKS ? Float.NaN : serverTps;
    }

    private static float averageFps() {
        int n = Math.min(sampleCount, fpsSamples.length);
        if (n == 0) return Float.NaN;
        float sum = 0;
        for (int i = 0; i < n; i++) sum += fpsSamples[i];
        return sum / n;
    }

    private static void change(Minecraft minecraft, int distance, String reason) {
        boolean asked = distance != current;
        current = distance;
        lastChange = ticks;
        sampleCount = 0;
        lastReason = reason;
        // The server sends chunks by the distance the client reports.
        if (asked) minecraft.options.broadcastOptions();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = OFF;
        ceiling = 0;
        anchor = null;
        stillTicks = 0;
        serverTps = Float.NaN;
        sampleCount = 0;
        lastReason = "";
    }

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (current != OFF) event.getLeft().add(status());
    }

    private static String status() {
        float tps = tps();
        String since = String.format(Locale.ROOT, "%ds", (ticks - lastChange) / 20);
        var level = Minecraft.getInstance().level;
        return String.format(Locale.ROOT, "Seepers render: auto %d/%d%s, chunks %d, TPS %s, FPS %.0f (%ds avg), last: %s %s ago%s",
                current, ceiling, stillTicks >= ClientConfig.RENDER_STILL_SECONDS.get() * 20 ? " still" : "", level == null ? 0 : level.getChunkSource().getLoadedChunksCount(), Float.isNaN(tps) ? "?" : String.format(Locale.ROOT, "%.1f", tps), averageFps(),
                fpsSamples.length, lastReason, since,
                Float.isNaN(simulatedTps) && Float.isNaN(simulatedFps) ? "" : " [simulated]");
    }

    /** {@code /seepers render [simulate <tps> <fps> | simulate off]} — status, and faked lag for testing. */
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("seepers")
                .then(Commands.literal("render")
                        .requires(AutoRenderDistance::allowed)
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("screen").executes(context -> openVideoSettings()))
                        .then(Commands.literal("simulate")
                                .then(Commands.literal("off").executes(context -> {
                                    simulatedTps = Float.NaN;
                                    simulatedFps = Float.NaN;
                                    sampleCount = 0;
                                    return report(context.getSource());
                                }))
                                .then(Commands.argument("tps", FloatArgumentType.floatArg(0, 20))
                                        .then(Commands.argument("fps", FloatArgumentType.floatArg(0, 1000))
                                                .executes(context -> {
                                                    simulatedTps = FloatArgumentType.getFloat(context, "tps");
                                                    simulatedFps = FloatArgumentType.getFloat(context, "fps");
                                                    sampleCount = 0;
                                                    return report(context.getSource());
                                                }))))));
    }

    /** Debug: open the video settings (Sodium's when installed — found by name, no dependency) to see the caption. */
    private static int openVideoSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            Screen screen;
            try {
                screen = (Screen) Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                        .getMethod("createScreen", Screen.class).invoke(null, (Screen) null);
            } catch (ReflectiveOperationException | LinkageError e) {
                screen = new VideoSettingsScreen(null, minecraft, minecraft.options);
            }
            minecraft.setScreen(screen);
        });
        return 1;
    }

    private static boolean allowed(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS) && ServerConfig.get(ServerConfig.DEBUG_COMMANDS);
    }

    private static int report(CommandSourceStack source) {
        String line = current == OFF ? "Seepers render: auto off" : status();
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private AutoRenderDistance() {}
}
