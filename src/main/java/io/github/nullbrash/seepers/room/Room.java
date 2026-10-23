package io.github.nullbrash.seepers.room;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * An enclosed space found by flood fill. {@link #fogFactor} is the share of outside fog inside: it falls
 * to 0 while the room is closed and rises to 1 while it leaks, both continuous in game time.
 */
public final class Room {
    LongOpenHashSet cells;
    int minX, minY, minZ, maxX, maxY, maxZ;
    boolean leaking;
    int leaks;
    private double factorAtChange;
    private long changedAt;

    Room(LongOpenHashSet cells, double initialFactor, long now) {
        setCells(cells);
        this.factorAtChange = initialFactor;
        this.changedAt = now;
    }

    void setCells(LongOpenHashSet cells) {
        this.cells = cells;
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        var iterator = cells.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
    }

    /** Switches between closed and leaking, keeping the fog factor continuous. */
    void setLeaking(boolean leaking, int leaks, long now, RoomSettings settings) {
        if (this.leaking == leaking && this.leaks == leaks) return;
        factorAtChange = fogFactor(now, settings);
        changedAt = now;
        this.leaking = leaking;
        this.leaks = leaks;
    }

    public double fogFactor(long now, RoomSettings settings) {
        double elapsed = Math.max(0, now - changedAt);
        if (leaking) {
            double t = Mth.clamp(elapsed / settings.fillTicks(leaks, volume()), 0, 1);
            return factorAtChange + (1 - factorAtChange) * t;
        }
        double t = settings.clearTicks() <= 0 ? 1 : Mth.clamp(elapsed / settings.clearTicks(), 0, 1);
        return factorAtChange * (1 - t);
    }

    public int volume() {
        return cells.size();
    }

    public boolean isLeaking() {
        return leaking;
    }

    public int leaks() {
        return leaks;
    }

    boolean nearBox(int x, int y, int z) {
        return x >= minX - 1 && x <= maxX + 1 && y >= minY - 1 && y <= maxY + 1 && z >= minZ - 1 && z <= maxZ + 1;
    }

    /** Farthest corner of the room's box from a point — everything inside the room is nearer. */
    public double farthestCorner(double x, double y, double z) {
        double dx = Math.max(Math.abs(x - minX), Math.abs(x - (maxX + 1)));
        double dy = Math.max(Math.abs(y - minY), Math.abs(y - (maxY + 1)));
        double dz = Math.max(Math.abs(z - minZ), Math.abs(z - (maxZ + 1)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
