package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.fire.FireTracker;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /seepers debug fire <pos>} — a registered fire as the next fog check would see it. */
final class FireDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fire")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        FireTracker.Inspection fire = FireTracker.inspect(source.getLevel(), pos);
        if (fire == null) {
            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fire.none", pos.toShortString()), false);
            return 0;
        }
        String density = String.format(Locale.ROOT, "%.2f", fire.density());
        String chance = String.format(Locale.ROOT, "%.0f", fire.chance() * 100);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fire.found",
                Component.translatable("commands.seepers.debug.fire.kind." + fire.kind().name().toLowerCase(Locale.ROOT)),
                pos.toShortString(),
                Component.translatable(fire.burning() ? "commands.seepers.debug.fire.lit" : "commands.seepers.debug.fire.unlit"),
                density, chance, fire.chunkFires(), fire.sinceCheck()), false);
        return 1;
    }

    private FireDebugCommand() {}
}
