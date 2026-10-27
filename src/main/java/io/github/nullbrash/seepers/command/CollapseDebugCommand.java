package io.github.nullbrash.seepers.command;

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
 * (hanging, as generated); {@code drop <pos>} starts a
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
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            if (!(level.getBlockState(pos).getBlock() instanceof FallingBlock)) continue;
            if (!FallingBlock.isFree(level.getBlockState(pos.below()))) continue;
            hanging++;
            if (Supports.holds(level, pos)) held++;
        }
        int hangingCount = hanging;
        int heldCount = held;
        BlockPos holder = Supports.holder(level, center);
        source.sendSuccess(() -> Component.literal("Hanging within " + RADIUS + ": " + hangingCount + ", held by props: " + heldCount
                + ", warning now: " + Collapses.pendingCount(level)), false);
        source.sendSuccess(() -> Component.literal("At " + center.toShortString() + ": "
                + (holder == null ? "no prop holds here" : "held by the prop topped at " + holder.toShortString())), false);
        return hangingCount;
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
