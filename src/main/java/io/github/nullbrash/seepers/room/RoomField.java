package io.github.nullbrash.seepers.room;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Fog inside rooms: the field is scaled by the room's fog factor (0 = clear, 1 = like outside). */
public final class RoomField {
    private static final double MAX_REACH = 64;

    public static double densityAt(Level level, double x, double y, double z, double baseDensity) {
        Room room = RoomRegistry.of(level).roomAt(BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
        if (room == null) return 0;
        return -baseDensity * (1 - room.fogFactor(level.getGameTime(), RoomSettings.current()));
    }

    /** Distance to which a mostly clear room around the point extends (0 if none): fog starts beyond it. */
    public static double reach(Level level, double x, double y, double z) {
        Room room = RoomRegistry.of(level).roomAt(BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
        if (room == null || room.fogFactor(level.getGameTime(), RoomSettings.current()) > 0.5) return 0;
        return Math.min(MAX_REACH, room.farthestCorner(x, y, z));
    }

    private RoomField() {}
}
