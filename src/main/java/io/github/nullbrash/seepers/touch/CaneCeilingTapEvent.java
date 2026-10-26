package io.github.nullbrash.seepers.touch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the game bus when a cane strikes a ceiling (CLAUDE.md 5.2: "a tap may bring it down"). Nothing
 * listens yet: collapses come with milestone 8 and hook in here.
 */
public class CaneCeilingTapEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos pos;

    public CaneCeilingTapEvent(ServerPlayer player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The ceiling block that was struck. */
    public BlockPos getPos() {
        return pos;
    }
}
