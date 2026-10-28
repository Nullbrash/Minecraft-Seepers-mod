package io.github.nullbrash.seepers.client.points;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.nullbrash.seepers.client.touch.HandTouch;
import io.github.nullbrash.seepers.config.ServerConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /seepers points test <radius> | clear | stats | touch | lidar [ticks]} — client-side debugging of the point cloud. Other
 * {@code /seepers} subcommands fall through to the server. Needs the server's debug commands and operator rights:
 * covering everything around with points would reveal the dark.
 */
public final class PointCommands {
    private static final int GRID = 3;

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("seepers")
                .then(Commands.literal("points")
                        .requires(PointCommands::allowed)
                        .then(Commands.literal("test")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 48))
                                        .executes(context -> test(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
                        .then(Commands.literal("clear").executes(context -> clear(context.getSource())))
                        .then(Commands.literal("touch").executes(context -> HandTouch.debugTouch() ? 1 : 0))
                        .then(Commands.literal("lidar")
                                .executes(context -> io.github.nullbrash.seepers.client.lidar.LidarScan.debugScan(20) ? 1 : 0)
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> io.github.nullbrash.seepers.client.lidar.LidarScan.debugScan(
                                                IntegerArgumentType.getInteger(context, "ticks")) ? 1 : 0)))
                        .then(Commands.literal("refresh").executes(context -> refresh(context.getSource())))
                        .then(Commands.literal("stats").executes(context -> stats(context.getSource())))));
    }

    private static boolean allowed(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS) && ServerConfig.get(ServerConfig.DEBUG_COMMANDS);
    }

    /** Covers every visible block face within the radius with a jittered grid of points. */
    private static int test(CommandSourceStack source, int radius) {
        PointCloud cloud = PointCloud.get();
        ClientLevel level = Minecraft.getInstance().level;
        if (cloud == null || level == null || Minecraft.getInstance().player == null) return 0;
        long started = System.nanoTime();
        BlockPos center = Minecraft.getInstance().player.blockPosition();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        int faces = 0;
        int points = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    pos.setWithOffset(center, dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getBlock() instanceof LiquidBlock) continue;
                    for (Direction face : Direction.values()) {
                        neighbor.setWithOffset(pos, face);
                        if (!Block.shouldRenderFace(state, level, pos, face, neighbor)) continue;
                        faces++;
                        points += coverFace(cloud, random, level, pos, state, face);
                    }
                }
            }
        }
        int added = points;
        int covered = faces;
        String millis = String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / 1e6);
        source.sendSuccess(() -> Component.translatable("commands.seepers.points.test", added, covered, millis), false);
        return added;
    }

    /** A jittered grid on the given side of every box of the block's visible shape (slabs, carpets, dripstone...). */
    private static int coverFace(PointCloud cloud, RandomSource random, ClientLevel level, BlockPos pos, BlockState state,
                                 Direction face) {
        int added = 0;
        for (AABB box : state.getShape(level, pos).toAabbs()) {
            Direction.Axis axis = face.getAxis();
            Direction.Axis uAxis = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
            Direction.Axis vAxis = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
            double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    double[] local = new double[3];
                    local[axis.ordinal()] = plane;
                    local[uAxis.ordinal()] = Mth.lerp((i + 0.5 + (random.nextDouble() - 0.5) * 0.6) / GRID, box.min(uAxis), box.max(uAxis));
                    local[vAxis.ordinal()] = Mth.lerp((j + 0.5 + (random.nextDouble() - 0.5) * 0.6) / GRID, box.min(vAxis), box.max(vAxis));
                    cloud.add(PointSource.DEBUG, pos.getX() + local[0], pos.getY() + local[1], pos.getZ() + local[2], face, 0);
                    added++;
                }
            }
        }
        return added;
    }

    /** The data refresh module's check over the whole memory at once: how many lidar points were stale. */
    private static int refresh(CommandSourceStack source) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null) return 0;
        int before = cloud.count(PointSource.LIDAR);
        int checked = 0;
        for (long key : cloud.sectionKeys().toLongArray()) checked += cloud.refreshSection(key);
        int removed = before - cloud.count(PointSource.LIDAR);
        int seen = checked;
        source.sendSuccess(() -> Component.literal("Lidar refresh: checked " + seen + ", removed " + removed), false);
        return removed;
    }

    private static int clear(CommandSourceStack source) {
        PointCloud cloud = PointCloud.get();
        int removed = cloud == null ? 0 : cloud.clear();
        source.sendSuccess(() -> Component.translatable("commands.seepers.points.clear", removed), false);
        return removed;
    }

    private static int stats(CommandSourceStack source) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null) return 0;
        int touch = cloud.count(PointSource.TOUCH);
        int debug = cloud.count(PointSource.DEBUG);
        int lidar = cloud.count(PointSource.LIDAR);
        int buffers = 0;
        for (PointCloud.Section section : cloud.sections.values()) {
            if (section.buffer != null) buffers++;
        }
        int withBuffers = buffers;
        String[] render = PointRenderer.stats();
        source.sendSuccess(() -> Component.translatable("commands.seepers.points.stats", touch + debug + lidar, touch,
                PointSource.TOUCH.capacity(), lidar, PointSource.LIDAR.capacity(), debug, cloud.sections.size(), withBuffers, render[0], render[1], render[2],
                Minecraft.getInstance().getFps(), javaMegabytes()), false);
        return touch + debug + lidar;
    }

    /** Heap in use, MB (after the last collection: a rough figure for benchmarks). */
    private static long javaMegabytes() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private PointCommands() {}
}
