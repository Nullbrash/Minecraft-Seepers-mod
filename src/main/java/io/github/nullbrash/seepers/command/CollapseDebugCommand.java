package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.collapse.Collapses;
import io.github.nullbrash.seepers.collapse.Supports;
import io.github.nullbrash.seepers.collapse.Tremors;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;

/**
 * {@code /seepers debug collapse [pos]} — hanging blocks around and how many props hold; {@code tremor <player>} starts a
 * tremor now, {@code tremor info} — time to the next one; {@code hang <from> <to> <block>} sets blocks without updates
 * (hanging, as generated); {@code survey <radius>} — share of ceilings that hang; {@code drop <pos>} starts a
 * collapse there through the normal path (warning, props, limit).
 */
final class CollapseDebugCommand {
    private static final int RADIUS = 8;

    static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("collapse")
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.literal("drop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> {
                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
                            boolean started = Collapses.trigger(context.getSource().getLevel(), pos);
                            context.getSource().sendSuccess(() -> Component.literal(started
                                    ? "Collapse started at " + pos.toShortString()
                                    : "Nothing hanging at " + pos.toShortString()), false);
                            return started ? 1 : 0;
                        })))
                .then(Commands.literal("hang").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                        .executes(command -> hang(command.getSource(), BlockPosArgument.getLoadedBlockPos(command, "from"),
                                                BlockPosArgument.getLoadedBlockPos(command, "to"),
                                                BlockStateArgument.getBlock(command, "block").getState()))))))
                .then(Commands.literal("survey").then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(context -> survey(context.getSource(), BlockPos.containing(context.getSource().getPosition()),
                                IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("tremor")
                        .then(Commands.literal("info").executes(context -> {
                            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                long ticks = Tremors.ticksUntil(player);
                                context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName()
                                        + ": next tremor in " + (ticks < 0 ? "?" : ticks / 20 + " s")), false);
                            }
                            return 1;
                        }))
                        .then(Commands.argument("target", EntityArgument.player()).executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "target");
                            Tremors.start(player);
                            context.getSource().sendSuccess(() -> Component.literal("Tremor started around " + player.getGameProfile().getName()), false);
                            return 1;
                        })))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos center) {
        ServerLevel level = source.getLevel();
        int hanging = 0;
        int held = 0;
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            if (!Collapses.isHanging(level, pos)) continue;
            hanging++;
            if (nearest == null || pos.distSqr(center) < nearest.distSqr(center)) nearest = pos.immutable();
            if (Supports.holds(level, pos)) held++;
        }
        BlockPos nearestHanging = nearest;
        int hangingCount = hanging;
        int heldCount = held;
        BlockPos holder = Supports.holder(level, center);
        source.sendSuccess(() -> Component.literal("Hanging within " + RADIUS + ": " + hangingCount + ", held by props: " + heldCount
                + ", warning now: " + Collapses.pendingCount(level)
                + (nearestHanging == null ? "" : ", nearest " + nearestHanging.toShortString() + " " + level.getBlockState(nearestHanging).getBlock().getName().getString())), false);
        source.sendSuccess(() -> Component.literal("At " + center.toShortString() + ": "
                + (holder == null ? "no prop holds here" : "held by the prop topped at " + holder.toShortString())), false);
        return hangingCount;
    }

    /**
     * Ceilings below the surface around (a block with air under it, natural stone or loose) and how many of them
     * hang (sand, gravel... over air), over loaded chunks only.
     */
    private static int survey(CommandSourceStack source, BlockPos center, int radius) {
        ServerLevel level = source.getLevel();
        long ceilings = 0;
        long hanging = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 2;
                for (int y = top; y > level.getMinBuildHeight() + 1; y--) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos.below()).isAir()) continue;
                    BlockState here = level.getBlockState(pos);
                    boolean loose = Collapses.isFallable(level, pos, here);
                    if (!loose && !here.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)) continue;
                    ceilings++;
                    if (loose) hanging++;
                }
            }
        }
        long c = ceilings;
        long h = hanging;
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT, "Ceilings within %d: %d, hanging %d (%.1f%%)",
                radius, c, h, c == 0 ? 0.0 : 100.0 * h / c)), false);
        long seen = io.github.nullbrash.seepers.world.HangingCeilings.SUPPORTS_SEEN.get();
        long removed = io.github.nullbrash.seepers.world.HangingCeilings.SUPPORTS_REMOVED.get();
        source.sendSuccess(() -> Component.literal("Mineshaft support columns generated since start: " + seen + ", taken out " + removed), false);
        return (int) Math.min(Integer.MAX_VALUE, h);
    }

    /**
     * Sets blocks the way world generation does — straight into the chunk section, no ticks, no neighbour updates —
     * so sand and gravel stay hanging until something disturbs them.
     */
    private static int hang(CommandSourceStack source, BlockPos from, BlockPos to, BlockState state) {
        ServerLevel level = source.getLevel();
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            LevelChunk chunk = level.getChunkAt(pos);
            chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
            chunk.setUnsaved(true);
            level.getChunkSource().blockChanged(pos);
            level.getChunkSource().getLightEngine().checkBlock(pos);
            count++;
        }
        int placed = count;
        source.sendSuccess(() -> Component.literal("Hung " + placed + " blocks"), false);
        return placed;
    }

    private CollapseDebugCommand() {}
}
