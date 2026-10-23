package io.github.nullbrash.seepers.room;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server side: rooms are found where players are (and, in the torch checks, where fires are) and
 * re-checked when blocks around them change.
 */
public final class RoomTracker {
    private static int ticks;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % Math.max(1, RoomSettings.current().recheckTicks()) != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            RoomRegistry registry = RoomRegistry.of(level);
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            if (registry.discover(level, eye) == null) {
                registry.discover(level, player.blockPosition());
            }
        }
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RoomRegistry.of(level).blockChanged(level, event.getPos());
        }
    }

    private RoomTracker() {}
}
