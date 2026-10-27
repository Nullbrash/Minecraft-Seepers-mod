package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.Seepers;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * {@code /seepers debug heightmap <x> <z> <size> <step> [raw]} — top-down map of the generated terrain height (noise
 * only, nothing has to be loaded) written to {@code debug/heightmap_<x>_<z>.png}. {@code raw}: the terrain shape
 * from the noise router alone (warp included, island shifts not) — much faster. Runs off the server thread.
 */
final class HeightmapDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("heightmap")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.argument("size", IntegerArgumentType.integer(16, 16384))
                                        .then(Commands.argument("step", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> start(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"),
                                                        IntegerArgumentType.getInteger(context, "size"), IntegerArgumentType.getInteger(context, "step"), false))
                                                .then(Commands.literal("raw").executes(context -> start(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z"),
                                                        IntegerArgumentType.getInteger(context, "size"), IntegerArgumentType.getInteger(context, "step"), true)))))));
    }

    private static int start(CommandSourceStack source, int cx, int cz, int size, int step, boolean raw) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int pixels = size / step;
        Path file = level.getServer().getServerDirectory().resolve("debug").resolve("heightmap_" + cx + "_" + cz + (raw ? "_raw" : "") + ".png");
        DensityFunction density = random.router().initialDensityWithoutJaggedness();
        Thread worker = new Thread(() -> {
            long started = System.nanoTime();
            BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
            int sea = generator.getSeaLevel();
            for (int px = 0; px < pixels; px++) {
                for (int pz = 0; pz < pixels; pz++) {
                    int x = cx - size / 2 + px * step;
                    int z = cz - size / 2 + pz * step;
                    int floor = raw ? rawHeight(density, x, z) : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    image.setRGB(px, pz, color(floor, sea));
                }
            }
            try {
                Files.createDirectories(file.getParent());
                ImageIO.write(image, "png", file.toFile());
                String seconds = String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e9);
                level.getServer().execute(() -> source.sendSuccess(() -> Component.literal("Heightmap written: " + file + " (" + seconds + " s)"), false));
            } catch (IOException e) {
                Seepers.LOGGER.error("Heightmap write failed", e);
            }
        }, "seepers-heightmap");
        worker.setDaemon(true);
        worker.start();
        source.sendSuccess(() -> Component.literal("Heightmap " + pixels + "x" + pixels + " started"), false);
        return 1;
    }

    /** Highest y where the initial density turns solid: coarse steps down, then block by block. */
    private static int rawHeight(DensityFunction density, int x, int z) {
        int y = 320;
        while (y > -64 && density.compute(new DensityFunction.SinglePointContext(x, y, z)) <= 0) y -= 8;
        int top = Math.min(320, y + 8);
        while (top > y && density.compute(new DensityFunction.SinglePointContext(x, top, z)) <= 0) top--;
        return top;
    }

    /** Water depth in blues; land from green through brown to white with height; voids (no ground) black. */
    private static int color(int floor, int sea) {
        if (floor <= -120) return 0;
        if (floor < sea) {
            float t = Mth.clamp((sea - floor) / 40.0F, 0, 1);
            return rgb(0.15F - 0.1F * t, 0.35F - 0.2F * t, 0.75F - 0.35F * t);
        }
        float t = Mth.clamp((floor - sea) / 120.0F, 0, 1);
        if (t < 0.5F) {
            float u = t / 0.5F;
            return rgb(0.25F + 0.35F * u, 0.55F - 0.15F * u, 0.2F + 0.05F * u);
        }
        float u = (t - 0.5F) / 0.5F;
        return rgb(0.6F + 0.4F * u, 0.4F + 0.6F * u, 0.25F + 0.75F * u);
    }

    private static int rgb(float r, float g, float b) {
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private HeightmapDebugCommand() {}
}
