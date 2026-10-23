package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.light.LightCategory;
import io.github.nullbrash.seepers.light.LightTable;
import io.github.nullbrash.seepers.light.Relighter;
import io.github.nullbrash.seepers.registry.SeepersTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/** {@code /seepers debug light [pos]} and {@code /seepers debug relight <radius>}. */
final class LightDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> light() {
        return Commands.literal("light")
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> relight() {
        return Commands.literal("relight")
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, Relighter.MAX_RADIUS))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            BlockPos center = BlockPos.containing(source.getPosition());
                            int chunks = Relighter.start(source, source.getLevel(), new ChunkPos(center),
                                    IntegerArgumentType.getInteger(context, "radius"));
                            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.relight.started",
                                    chunks, center.toShortString()), true);
                            return chunks;
                        }));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockState state = level.getBlockState(pos);
        LightCategory category = LightTable.categoryOf(state);
        String categoryName = category != null ? category.id
                : state.is(SeepersTags.Blocks.LIGHT_VANILLA) ? "vanilla" : "-";
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.light",
                pos.toShortString(),
                level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos),
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                categoryName,
                state.getLightEmission(level, pos),
                state.getLightBlock(level, pos)), false);
        return level.getBrightness(LightLayer.BLOCK, pos);
    }

    private LightDebugCommand() {}
}
