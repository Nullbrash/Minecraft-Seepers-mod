package io.github.nullbrash.seepers.room;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Enclosed rooms of one level (CLAUDE.md 4.5), found by flood fill bounded by {@link RoomWalls} with a volume
 * limit. Client and server keep separate registries (one per Level object) built by the same rules; used
 * from that level's own thread only. Not saved: rooms are rediscovered, unknown history counts as clear.
 */
public final class RoomRegistry {
    private static final Map<Level, RoomRegistry> REGISTRIES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_ROOMS = 256;

    private final List<Room> rooms = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Room> cellToRoom = new Long2ObjectOpenHashMap<>();
    /** Cells known to be open (fill escaped); dropped when blocks nearby change. */
    private final LongOpenHashSet openCells = new LongOpenHashSet();
    private long lastFillNanos;

    public static RoomRegistry of(Level level) {
        return REGISTRIES.computeIfAbsent(level, l -> new RoomRegistry());
    }

    public @Nullable Room roomAt(BlockPos pos) {
        return cellToRoom.get(pos.asLong());
    }

    public @Nullable Room roomAt(long packedPos) {
        return cellToRoom.get(packedPos);
    }

    public long lastFillNanos() {
        return lastFillNanos;
    }

    /** Finds (or confirms) the room containing pos; null if pos is open space or solid. */
    public @Nullable Room discover(Level level, BlockPos pos) {
        long key = pos.asLong();
        Room known = cellToRoom.get(key);
        if (known != null) return known;
        if (openCells.contains(key)) return null;
        // Under open sky (above the heightmap): never a room, no fill needed.
        if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return null;
        }
        RoomSettings settings = RoomSettings.current();
        FillResult result = fill(level, pos, settings.maxVolume());
        if (result == null) return null;
        if (!result.closed()) {
            if (openCells.size() > 200_000) openCells.clear();
            openCells.addAll(result.cells());
            return null;
        }
        // Space that was open a moment ago (player just closed the door) starts fogged.
        boolean wasOpen = false;
        var it = result.cells().iterator();
        while (it.hasNext() && !wasOpen) wasOpen = openCells.contains(it.nextLong());
        Room room = new Room(result.cells(), wasOpen ? 1.0 : 0.0, level.getGameTime());
        register(room);
        return room;
    }

    /** A block changed: re-check rooms around it and forget nearby open-space results. */
    public void blockChanged(Level level, BlockPos pos) {
        if (touchesOpenCells(pos)) openCells.clear();
        List<Room> affected = new ArrayList<>();
        for (Room room : rooms) {
            if (room.nearBox(pos.getX(), pos.getY(), pos.getZ())) affected.add(room);
        }
        for (Room room : affected) recheck(level, room);
    }

    /** Re-fills a known room: it may have closed, opened (leaking) or changed shape. */
    public void recheck(Level level, Room room) {
        RoomSettings settings = RoomSettings.current();
        long now = level.getGameTime();
        BlockPos start = null;
        var it = room.cells.iterator();
        while (it.hasNext()) {
            BlockPos candidate = BlockPos.of(it.nextLong());
            if (!blocksAll(level.getBlockState(candidate))) {
                start = candidate;
                break;
            }
        }
        if (start == null) {
            unregister(room); // filled in completely
            return;
        }
        FillResult result = fill(level, start, settings.maxVolume());
        if (result != null && result.closed()) {
            unregisterCells(room);
            room.setCells(result.cells());
            room.setLeaking(false, 0, now, settings);
            registerCells(room);
        } else {
            room.setLeaking(true, Math.max(1, countLeaks(level, room)), now, settings);
        }
    }

    /** Client side has no block events: forget open-space results now and then. */
    public void forgetOpenCells() {
        openCells.clear();
    }

    public @Nullable Room roomOrNull(Room room) {
        return rooms.contains(room) ? room : null;
    }

    private boolean touchesOpenCells(BlockPos pos) {
        if (openCells.isEmpty()) return false;
        long key = pos.asLong();
        if (openCells.contains(key)) return true;
        for (Direction direction : Direction.values()) {
            if (openCells.contains(BlockPos.offset(key, direction))) return true;
        }
        return false;
    }

    private void register(Room room) {
        if (rooms.size() >= MAX_ROOMS) unregister(rooms.getFirst());
        rooms.add(room);
        registerCells(room);
    }

    private void unregister(Room room) {
        rooms.remove(room);
        unregisterCells(room);
    }

    private void registerCells(Room room) {
        var it = room.cells.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            Room previous = cellToRoom.put(key, room);
            if (previous != null && previous != room) {
                rooms.remove(previous); // an old room merged into this one
                var old = previous.cells.iterator();
                while (old.hasNext()) {
                    long c = old.nextLong();
                    if (cellToRoom.get(c) == previous) cellToRoom.remove(c);
                }
            }
        }
    }

    private void unregisterCells(Room room) {
        var it = room.cells.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (cellToRoom.get(key) == room) cellToRoom.remove(key);
        }
    }

    private record FillResult(LongOpenHashSet cells, boolean closed) {}

    /** Null if the start cell itself is solid. Escaping into unloaded chunks counts as open. */
    private @Nullable FillResult fill(Level level, BlockPos start, int maxVolume) {
        long started = System.nanoTime();
        try {
            if (blocksAll(level.getBlockState(start))) return null;
            LongOpenHashSet visited = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            visited.add(start.asLong());
            queue.enqueue(start.asLong());
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (!queue.isEmpty()) {
                long current = queue.dequeueLong();
                cursor.set(current);
                BlockState from = level.getBlockState(cursor);
                for (Direction direction : Direction.values()) {
                    long next = BlockPos.offset(current, direction);
                    if (visited.contains(next)) continue;
                    Direction.Axis axis = direction.getAxis();
                    if (RoomWalls.blocks(from, axis)) continue;
                    cursor.set(next);
                    if (!level.isLoaded(cursor) || level.isOutsideBuildHeight(cursor)) {
                        return new FillResult(visited, false);
                    }
                    if (RoomWalls.blocks(level.getBlockState(cursor), axis)) continue;
                    visited.add(next);
                    if (visited.size() > maxVolume) return new FillResult(visited, false);
                    queue.enqueue(next);
                }
            }
            return new FillResult(visited, true);
        } finally {
            lastFillNanos = System.nanoTime() - started;
        }
    }

    /** Open faces from the room to cells outside it: each is a leak (a 2-high open door = 2). */
    private int countLeaks(Level level, Room room) {
        int leaks = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        var it = room.cells.iterator();
        while (it.hasNext()) {
            long current = it.nextLong();
            cursor.set(current);
            BlockState from = level.getBlockState(cursor);
            for (Direction direction : Direction.values()) {
                long next = BlockPos.offset(current, direction);
                if (room.cells.contains(next)) continue;
                Direction.Axis axis = direction.getAxis();
                if (RoomWalls.blocks(from, axis)) continue;
                cursor.set(next);
                if (!RoomWalls.blocks(level.getBlockState(cursor), axis)) leaks++;
            }
        }
        return leaks;
    }

    private static boolean blocksAll(BlockState state) {
        return RoomWalls.blocks(state, Direction.Axis.X) && RoomWalls.blocks(state, Direction.Axis.Y)
                && RoomWalls.blocks(state, Direction.Axis.Z);
    }
}
