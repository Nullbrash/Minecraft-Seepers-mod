package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.network.payload.ClientCommandPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@code /seepers debug client <player> <command>} — the player's client runs {@code /seepers <command>}. */
final class ClientDebugCommand {
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("client")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    PacketDistributor.sendToPlayer(target,
                                            new ClientCommandPayload("seepers " + StringArgumentType.getString(context, "command")));
                                    return 1;
                                })));
    }

    private ClientDebugCommand() {}
}
