package io.github.nullbrash.seepers.client.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /seepers report} — packs the diagnostics journal, the game log, the mod's configs and the latest crash
 * reports into one zip for a tester to send, with personal data replaced ({@link Anonymizer}). Open to everyone:
 * it only reads local files.
 */
public final class ReportCommand {
    private static final int CRASH_REPORTS = 2;

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("seepers").then(Commands.literal("report").executes(context -> {
            Diagnostics.event("report requested");
            Thread worker = new Thread(ReportCommand::pack, "seepers-report");
            worker.setDaemon(true);
            worker.start();
            return 1;
        })));
    }

    private static void pack() {
        Minecraft minecraft = Minecraft.getInstance();
        Path game = minecraft.gameDirectory.toPath();
        Path zip = game.resolve("seepers-reports").resolve("seepers-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        List<Path> files = new ArrayList<>();
        files.add(Diagnostics.file());
        files.add(Diagnostics.file().resolveSibling("seepers-diagnostics.prev.log"));
        files.add(game.resolve("logs").resolve("latest.log"));
        for (String config : List.of("seepers-client.toml", "seepers-common.toml", "seepers-server.toml")) files.add(game.resolve("config").resolve(config));
        try (Stream<Path> crashes = Files.exists(game.resolve("crash-reports")) ? Files.list(game.resolve("crash-reports")) : Stream.empty()) {
            crashes.sorted(Comparator.comparing(ReportCommand::modified).reversed()).limit(CRASH_REPORTS).forEach(files::add);
        } catch (IOException ignored) {
            // No crash reports to add.
        }
        try {
            Files.createDirectories(zip.getParent());
            try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(out)) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file)) continue;
                    archive.putNextEntry(new ZipEntry(game.relativize(file).toString().replace('\\', '/')));
                    // The game log is still being written: copy what is there now, without personal data.
                    String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    archive.write(Anonymizer.clean(text).getBytes(StandardCharsets.UTF_8));
                    archive.closeEntry();
                }
            }
            minecraft.execute(() -> message(Component.translatable("commands.seepers.report.done",
                    Component.literal(zip.getFileName().toString()).withStyle(ChatFormatting.UNDERLINE)
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, zip.getParent().toAbsolutePath().toString()))))));
        } catch (IOException e) {
            minecraft.execute(() -> message(Component.translatable("commands.seepers.report.failed", e.getMessage())));
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void message(Component text) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(text, false);
    }

    private ReportCommand() {}
}
