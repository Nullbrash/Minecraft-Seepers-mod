package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.PingTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SeepersCommands {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("seepers")
                .then(Commands.literal("debug")
                        // Re-checked on every execution; tab-completion uses the tree sent on login, so it updates after a relog.
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS) && ServerConfig.DEBUG_COMMANDS.get())
                        .then(Commands.literal("ping")
                                .executes(context -> {
                                    PingTracker.send(context.getSource(), context.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> {
                                            PingTracker.send(context.getSource(), EntityArgument.getPlayer(context, "target"));
                                            return 1;
                                        })))
                        .then(StorageDebugCommand.build())));
    }

    private SeepersCommands() {}
}
