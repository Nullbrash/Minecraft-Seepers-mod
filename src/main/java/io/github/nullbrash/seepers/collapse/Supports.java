package io.github.nullbrash.seepers.collapse;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Props (CLAUDE.md 4.10): a column of fences or stone walls standing on solid ground and pressing into the
 * ceiling, or into a beam right under it, holds hanging blocks above within its radius (fences 2 — the step of 5 of
 * a vanilla mineshaft; walls 4). Checked only when a block is about to fall, never in the background.
 */
public final class Supports {
    private static final int MAX_COLUMN = 32;

    /** The head of the prop holding this spot (the block on top of its column), or null. */
    public static @Nullable BlockPos holder(BlockGetter level, BlockPos pos) {
        int fence = ServerConfig.get(ServerConfig.COLLAPSE_FENCE_RADIUS);
        int wall = ServerConfig.get(ServerConfig.COLLAPSE_WALL_RADIUS);
        int reach = ServerConfig.get(ServerConfig.COLLAPSE_SUPPORT_REACH);
        int r = Math.max(fence, wall);
        BlockPos.MutableBlockPos post = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                // The head sits at the hanging spot or up to "reach" blocks below it.
                for (int headY = pos.getY(); headY >= pos.getY() - reach; headY--) {
                    post.set(pos.getX() + dx, headY - 1, pos.getZ() + dz);
                    int radius = radius(level.getBlockState(post), fence, wall);
                    if (radius < distance) continue;
                    if (isProp(level, post, fence, wall)) return post.above();
                }
            }
        }
        return null;
    }

    public static boolean holds(BlockGetter level, BlockPos pos) {
        return holder(level, pos) != null;
    }

    /** {@code top} is the top post of a column: is it a working prop? */
    private static boolean isProp(BlockGetter level, BlockPos top, int fence, int wall) {
        BlockPos headPos = top.above();
        BlockState head = level.getBlockState(headPos);
        if (radius(head, fence, wall) >= 0) return false;
        // Head: the ceiling itself or a beam pressed against it — solid, with no gap above.
        if (!head.isFaceSturdy(level, headPos, Direction.DOWN) && !(head.getBlock() instanceof FallingBlock)) return false;
        if (FallingBlock.isFree(level.getBlockState(headPos.above()))) return false;
        BlockPos.MutableBlockPos foot = top.mutable();
        for (int i = 0; i < MAX_COLUMN && radius(level.getBlockState(foot.below()), fence, wall) >= 0; i++) foot.move(Direction.DOWN);
        BlockPos ground = foot.below();
        return level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP);
    }

    /** Hold radius of a post block, or -1 when it is no post. */
    private static int radius(BlockState state, int fence, int wall) {
        if (state.is(SeepersTags.Blocks.SUPPORT_WALL)) return wall;
        if (state.is(SeepersTags.Blocks.SUPPORT_FENCE)) return fence;
        return -1;
    }

    private Supports() {}
}
