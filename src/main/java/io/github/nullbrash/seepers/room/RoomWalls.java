package io.github.nullbrash.seepers.room;

import io.github.nullbrash.seepers.light.LightOverrides;
import io.github.nullbrash.seepers.registry.SeepersTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Which axes a block blocks for fog (CLAUDE.md 4.5): moving through a cell along an axis is blocked when
 * the block's collision shape, projected along that axis, covers the whole 1×1 square — so a pane blocks
 * across its plane, a door across its panel, a bottom slab vertically. Cached per block state.
 */
public final class RoomWalls {
    private static final int COMPUTED = 1 << 3;
    private static final double[] SAMPLES = {0.125, 0.375, 0.625, 0.875};

    public static boolean blocks(BlockState state, Direction.Axis axis) {
        LightOverrides cache = (LightOverrides) state;
        int bits = cache.seepers$roomWalls();
        if ((bits & COMPUTED) == 0) {
            bits = compute(state) | COMPUTED;
            cache.seepers$setRoomWalls(bits); // benign race: every thread computes the same bits
        }
        return (bits & (1 << axis.ordinal())) != 0;
    }

    /** Called when tags reload: the not-a-wall tag may have changed. */
    public static void clearCache(BlockState state) {
        ((LightOverrides) state).seepers$setRoomWalls(0);
    }

    private static int compute(BlockState state) {
        if (state.isAir() || state.is(SeepersTags.Blocks.ROOM_NOT_WALL)) return 0;
        List<AABB> boxes = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
        if (boxes.isEmpty()) return 0;
        int bits = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (coversProjection(boxes, axis)) bits |= 1 << axis.ordinal();
        }
        return bits;
    }

    /** 4×4 sample points on the plane perpendicular to the axis must all be covered by some box. */
    private static boolean coversProjection(List<AABB> boxes, Direction.Axis axis) {
        for (double u : SAMPLES) {
            for (double v : SAMPLES) {
                boolean covered = false;
                for (AABB box : boxes) {
                    double minU, maxU, minV, maxV;
                    switch (axis) {
                        case X -> { minU = box.minY; maxU = box.maxY; minV = box.minZ; maxV = box.maxZ; }
                        case Y -> { minU = box.minX; maxU = box.maxX; minV = box.minZ; maxV = box.maxZ; }
                        default -> { minU = box.minX; maxU = box.maxX; minV = box.minY; maxV = box.maxY; }
                    }
                    if (u >= minU && u <= maxU && v >= minV && v <= maxV) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) return false;
            }
        }
        return true;
    }

    private RoomWalls() {}
}
