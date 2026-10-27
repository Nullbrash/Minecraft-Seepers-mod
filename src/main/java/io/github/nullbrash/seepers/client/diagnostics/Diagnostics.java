package io.github.nullbrash.seepers.client.diagnostics;

import com.mojang.blaze3d.platform.GlUtil;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.world.Distortion;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Local diagnostics journal for testers ({@code logs/seepers-diagnostics.log}): the system at start, a summary
 * every minute in a world (frame rate, server TPS, render distance, points, memory), notable events and warnings.
 * Nothing leaves the computer; {@code /seepers report} packs it for sending by hand. Personal data is
 * replaced by placeholders ({@link Anonymizer}).
 */
public final class Diagnostics {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SUMMARY_TICKS = 20 * 60;
    /** Warnings copied from the game log, per session: a flood of one error must not bloat the journal. */
    private static final int MAX_CAPTURED = 300;

    private static @Nullable Path file;
    private static boolean started;
    private static int ticks;
    private static int frames;
    private static int fpsSum;
    private static int fpsMin = Integer.MAX_VALUE;
    private static int captured;

    public static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("seepers-diagnostics.log");
    }

    /** First client tick: the render system is up, so the GPU can be named. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!started) {
            started = true;
            open();
            writeStart(minecraft);
            captureWarnings();
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) return;
        if (++ticks % 20 == 0) {
            int fps = minecraft.getFps();
            frames++;
            fpsSum += fps;
            fpsMin = Math.min(fpsMin, fps);
        }
        if (ticks % SUMMARY_TICKS == 0) writeSummary(minecraft);
    }

    public static void event(String text) {
        if (enabled() && started) write("EVENT " + text);
    }

    private static boolean enabled() {
        return ClientConfig.SPEC.isLoaded() && ClientConfig.DIAGNOSTICS.get();
    }

    private static void open() {
        Path target = file();
        try {
            Files.createDirectories(target.getParent());
            // The previous session stays next to it: a tester often sends logs after restarting the game.
            if (Files.exists(target)) Files.move(target, target.resolveSibling("seepers-diagnostics.prev.log"), StandardCopyOption.REPLACE_EXISTING);
            file = target;
        } catch (IOException e) {
            file = null;
        }
    }

    private static void writeStart(Minecraft minecraft) {
        Runtime runtime = Runtime.getRuntime();
        String version = ModList.get().getModContainerById("seepers").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        write("START Seepers " + version + ", Minecraft " + SharedConstants.getCurrentVersion().getName()
                + ", NeoForge " + ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        write("SYSTEM " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
                + ", Java " + System.getProperty("java.version") + ", memory max " + mb(runtime.maxMemory()) + " MB, cores " + runtime.availableProcessors());
        write("CPU " + GlUtil.getCpuInfo());
        write("GPU " + GlUtil.getRenderer() + " | " + GlUtil.getVendor() + " | OpenGL " + GlUtil.getOpenGLVersion());
        write(String.format(Locale.ROOT, "WINDOW %dx%d, fullscreen %s, frame limit %d, vsync %s, render distance %d, simulation %d, gui scale %d",
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), minecraft.getWindow().isFullscreen(),
                minecraft.options.framerateLimit().get(), minecraft.options.enableVsync().get(), minecraft.options.renderDistance().get(),
                minecraft.options.simulationDistance().get(), minecraft.options.guiScale().get()));
        write("MODS " + ModList.get().getMods().stream().map(m -> m.getModId() + " " + m.getVersion()).sorted().collect(Collectors.joining(", ")));
    }

    private static void writeSummary(Minecraft minecraft) {
        Runtime runtime = Runtime.getRuntime();
        PointCloud cloud = PointCloud.get();
        LocalPlayer player = minecraft.player;
        double level = player == null ? 0 : Distortion.level(player.getX(), player.getZ());
        String points = cloud == null ? "-" : cloud.count(PointSource.TOUCH) + " touch / " + cloud.count(PointSource.CANE) + " cane / "
                + cloud.count(PointSource.SILHOUETTE) + " silhouettes / " + cloud.count(PointSource.DEBUG) + " test";
        write(String.format(Locale.ROOT, "MINUTE fps avg %d min %d, %s, points %s, memory %d/%d MB, %s, zone %d (%.2f)",
                frames == 0 ? 0 : fpsSum / frames, fpsMin == Integer.MAX_VALUE ? 0 : fpsMin, AutoRenderDistance.summary(), points,
                mb(runtime.totalMemory() - runtime.freeMemory()), mb(runtime.maxMemory()),
                minecraft.isLocalServer() ? "singleplayer" : "multiplayer", Distortion.zone(level), level));
        frames = 0;
        fpsSum = 0;
        fpsMin = Integer.MAX_VALUE;
    }

    /** Warnings and errors of the whole game, copied while they happen (the game log rotates and gets long). */
    private static void captureWarnings() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        AbstractAppender appender = new AbstractAppender("SeepersDiagnostics", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (captured >= MAX_CAPTURED || !event.getLevel().isMoreSpecificThan(Level.WARN)) return;
                captured++;
                Throwable thrown = event.getThrown();
                write(event.getLevel() + " [" + event.getLoggerName() + "] " + event.getMessage().getFormattedMessage()
                        + (thrown == null ? "" : " — " + thrown));
                if (captured == MAX_CAPTURED) write("WARN further warnings not copied (limit " + MAX_CAPTURED + ")");
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        context.updateLoggers();
    }

    private static synchronized void write(String line) {
        Path target = file;
        if (target == null) return;
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(LocalDateTime.now().format(TIME) + " " + Anonymizer.clean(line) + System.lineSeparator());
        } catch (IOException ignored) {
            // A journal that cannot be written must never disturb the game.
        }
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    private Diagnostics() {}
}
