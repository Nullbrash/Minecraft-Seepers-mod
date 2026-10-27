package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.world.DistortedChunkGenerator;
import io.github.nullbrash.seepers.world.Distortion;
import io.github.nullbrash.seepers.world.ZonedBiomeSource;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /seepers debug zone [pos]} — distortion level and zone at a position, and the zone sizes. */
final class ZoneDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("zone")
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.literal("gravity").executes(context -> gravityShares(context.getSource())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        double level = Distortion.level(pos.getX(), pos.getZ());
        Distortion.Zones zones = Distortion.zones();
        String value = String.format(Locale.ROOT, "%.2f", level);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.zone", pos.toShortString(),
                Distortion.zone(level), value, zones.zone1(), zones.zone2(), zones.zone3()), false);
        if (source.getLevel().getChunkSource().getGenerator() instanceof DistortedChunkGenerator generator) {
            String shape = generator.describe(source.getLevel().getChunkSource().randomState(), pos.getX(), pos.getZ());
            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.zone.seam", shape), false);
            String biome = source.getLevel().getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("?");
            boolean gravity = generator.getBiomeSource() instanceof ZonedBiomeSource zoned
                    && zoned.brokenGravity(pos.getX(), pos.getZ(), Distortion.zone(level));
            source.sendSuccess(() -> Component.literal("Biome here: " + biome + ", broken gravity patch: " + gravity), false);
        }
        return Distortion.zone(level);
    }

    /** Share of each zone's area covered by broken gravity: 10 000 points per zone ring, nothing is generated. */
    private static int gravityShares(CommandSourceStack source) {
        if (!(source.getLevel().getChunkSource().getGenerator().getBiomeSource() instanceof ZonedBiomeSource zoned)) return 0;
        zoned.bind(source.getLevel().getChunkSource().randomState());
        Distortion.Zones zones = Distortion.zones();
        int[][] rings = {{zones.zone1(), zones.zone2()}, {zones.zone2(), zones.zone3()}, {zones.zone3(), zones.zone3() + (zones.zone3() - zones.zone2())}};
        java.util.Random random = new java.util.Random(1);
        StringBuilder text = new StringBuilder("broken gravity share:");
        for (int zone = 1; zone <= 3; zone++) {
            int hits = 0;
            for (int i = 0; i < 10_000; i++) {
                double r = rings[zone - 1][0] + random.nextDouble() * (rings[zone - 1][1] - rings[zone - 1][0]);
                double a = random.nextDouble() * Math.PI * 2;
                int x = (int) (r * Math.cos(a));
                int z = (int) (r * Math.sin(a));
                if (zoned.brokenGravity(x, z, Distortion.zone(Distortion.level(x, z)))) hits++;
            }
            text.append(String.format(Locale.ROOT, " zone %d %.1f%%", zone, hits / 100.0));
        }
        // Patch sizes: connected areas on an 8-block grid, 2048 × 2048 in zone 3; diameter of a disc of the same area.
        int step = 8;
        int cells = 2048 / step;
        int x0 = zones.zone3() + 200;
        boolean[][] in = new boolean[cells][cells];
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            int x = x0 + i * step;
            int z = -1024 + j * step;
            in[i][j] = zoned.brokenGravity(x, z, Distortion.zone(Distortion.level(x, z)));
        }
        java.util.List<Integer> areas = new java.util.ArrayList<>();
        boolean[][] seen = new boolean[cells][cells];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int i = 0; i < cells; i++) for (int j = 0; j < cells; j++) {
            if (!in[i][j] || seen[i][j]) continue;
            int area = 0;
            queue.add(new int[] {i, j});
            seen[i][j] = true;
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                area++;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int a = c[0] + d[0], b = c[1] + d[1];
                    if (a >= 0 && b >= 0 && a < cells && b < cells && in[a][b] && !seen[a][b]) {
                        seen[a][b] = true;
                        queue.add(new int[] {a, b});
                    }
                }
            }
            areas.add(area * step * step);
        }
        java.util.List<Integer> big = areas.stream().filter(a -> a >= 64 * 64).toList();
        double mean = big.stream().mapToDouble(a -> 2 * Math.sqrt(a / Math.PI)).average().orElse(0);
        double max = areas.stream().mapToDouble(a -> 2 * Math.sqrt(a / Math.PI)).max().orElse(0);
        String sizes = String.format(Locale.ROOT, "; zone 3 patches: %d (%d of them ≥ 64×64), mean diameter of those %.0f, largest %.0f",
                areas.size(), big.size(), mean, max);
        source.sendSuccess(() -> Component.literal(text + sizes), false);
        return 1;
    }

    private ZoneDebugCommand() {}
}
