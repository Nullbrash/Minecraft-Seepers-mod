package io.github.nullbrash.seepers.client.render;

import com.mojang.brigadier.arguments.FloatArgumentType;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
        if (current == OFF) {
            change(minecraft, top, "start");
        } else if (current > top) {
            change(minecraft, top, "ceiling");
        }
        ticks++;
        if (ticks % SAMPLE_TICKS != 0) return;
        sample(minecraft);
        if (ticks - lastChange < ClientConfig.RENDER_SETTLE_SECONDS.get() * 20 || sampleCount < fpsSamples.length) return;

        float tps = tps();
        float fps = averageFps();
        int limit = minecraft.options.framerateLimit().get();
        // A player capping frames low would otherwise read as permanent lag.
        boolean capped = limit < Options.UNLIMITED_FRAMERATE_CUTOFF;
        double fpsLower = capped ? Math.min(ClientConfig.RENDER_FPS_LOWER.get(), limit * 0.8) : ClientConfig.RENDER_FPS_LOWER.get();
        double fpsRaise = capped ? Math.min(ClientConfig.RENDER_FPS_RAISE.get(), limit * 0.9) : ClientConfig.RENDER_FPS_RAISE.get();
        boolean tpsKnown = !Float.isNaN(tps);

        if (tpsKnown && tps < ClientConfig.RENDER_TPS_LOWER.get()) {
            if (current > bottom) change(minecraft, current - 1, String.format(Locale.ROOT, "-1 TPS %.1f", tps));
        } else if (fps < fpsLower) {
            if (current > bottom) change(minecraft, current - 1, String.format(Locale.ROOT, "-1 FPS %.0f", fps));
        } else if ((!tpsKnown || tps >= ClientConfig.RENDER_TPS_RAISE.get()) && fps > fpsRaise
                && current < top && ticks - lastChange >= ClientConfig.RENDER_RAISE_SECONDS.get() * 20) {
            change(minecraft, current + 1, String.format(Locale.ROOT, "+1 FPS %.0f", fps));
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
        return String.format(Locale.ROOT, "Seepers render: auto %d/%d, chunks %d, TPS %s, FPS %.0f (%ds avg), last: %s %s ago%s",
                current, ceiling, level == null ? 0 : level.getChunkSource().getLoadedChunksCount(), Float.isNaN(tps) ? "?" : String.format(Locale.ROOT, "%.1f", tps), averageFps(),
                fpsSamples.length, lastReason, since,
                Float.isNaN(simulatedTps) && Float.isNaN(simulatedFps) ? "" : " [simulated]");
    }

    /** {@code /seepers render [simulate <tps> <fps> | simulate off]} — status, and faked lag for testing. */
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("seepers")
                .then(Commands.literal("render")
                        .requires(AutoRenderDistance::allowed)
                        .executes(context -> report(context.getSource()))
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
