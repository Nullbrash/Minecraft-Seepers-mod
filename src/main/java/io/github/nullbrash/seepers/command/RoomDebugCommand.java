package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.room.Room;
import io.github.nullbrash.seepers.room.RoomRegistry;
import io.github.nullbrash.seepers.room.RoomSettings;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** {@code /seepers debug room [pos]} — the enclosed room at a position (server's registry). */
final class RoomDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("room")
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        RoomRegistry registry = RoomRegistry.of(level);
        Room room = registry.discover(level, pos);
        String fillMs = String.format(Locale.ROOT, "%.2f", registry.lastFillNanos() / 1.0e6);
        if (room == null) {
            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.room.none", pos.toShortString(), fillMs), false);
            return 0;
        }
        String fog = String.format(Locale.ROOT, "%.0f", room.fogFactor(level.getGameTime(), RoomSettings.current()) * 100);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.room.found", pos.toShortString(),
                Component.translatable(room.isLeaking() ? "commands.seepers.debug.room.leaking" : "commands.seepers.debug.room.closed"),
                room.volume(), room.leaks(), fog, fillMs), false);
        return room.volume();
    }

    private RoomDebugCommand() {}
}
