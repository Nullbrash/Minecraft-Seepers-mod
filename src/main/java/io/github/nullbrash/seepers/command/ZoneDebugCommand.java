package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.world.Distortion;
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
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        double level = Distortion.level(pos.getX(), pos.getZ());
        Distortion.Zones zones = Distortion.zones();
        String value = String.format(Locale.ROOT, "%.2f", level);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.zone", pos.toShortString(),
                Distortion.zone(level), value, zones.zone1(), zones.zone2(), zones.zone3()), false);
        return Distortion.zone(level);
    }

    private ZoneDebugCommand() {}
}
