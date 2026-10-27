package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.collapse.Collapses;
import io.github.nullbrash.seepers.collapse.Supports;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;

/**
 * {@code /seepers debug collapse [pos]} — hanging blocks around and how many props hold; {@code drop <pos>} starts a
 * collapse there through the normal path (warning, props, limit).
 */
final class CollapseDebugCommand {
    private static final int RADIUS = 8;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
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

    private CollapseDebugCommand() {}
}
