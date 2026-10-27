package io.github.nullbrash.seepers.collapse;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersTags;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Broken gravity (CLAUDE.md 4.10): in the {@code seepers:broken_gravity} biome, ground of the tag
 * {@code seepers:broken_gravity/unstable} (dirt, grass, snow, clay...) behaves like sand. Vanilla never wakes these
 * blocks, so a neighbour notification does: a change below always, beside or above by the spread chance. They then
 * take the same path as sand and gravel (props, warning, limit) through their own short queue.
 */
public final class LooseGround {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Seepers.id("broken_gravity"));
    /** Ticks after a wake before the block is looked at, as a falling block's tick after a neighbour change. */
    private static final int WAKE_DELAY = 2;
    private static final Map<ServerLevel, Long2LongOpenHashMap> QUEUE = new WeakHashMap<>();

    public static boolean isLoose(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.is(SeepersTags.Blocks.BROKEN_GRAVITY_UNSTABLE) && level.getBiome(pos).is(BIOME);
    }

    /** Asks the block to be looked at in this many ticks (an earlier request wins). */
    static void wake(ServerLevel level, BlockPos pos, int delay) {
        long at = level.getGameTime() + Math.max(1, delay);
        QUEUE.computeIfAbsent(level, l -> new Long2LongOpenHashMap()).merge(pos.asLong(), at, Math::min);
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ServerConfig.get(ServerConfig.COLLAPSE_ENABLED)) return;
        BlockPos changed = event.getPos();
        for (Direction side : event.getNotifiedSides()) {
            BlockPos pos = changed.relative(side);
            BlockState state = level.getBlockState(pos);
            if (!isLoose(level, pos, state) || !FallingBlock.isFree(level.getBlockState(pos.below()))) continue;
            // side UP: the changed block is right under this one.
            if (side == Direction.UP || Collapses.spreads(level, pos)) wake(level, pos, WAKE_DELAY);
        }
    }

    /** Placed loose ground in the biome falls at once if nothing holds it, like placed sand. */
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ServerConfig.get(ServerConfig.COLLAPSE_ENABLED)) return;
        BlockPos pos = event.getPos();
        if (!isLoose(level, pos, event.getPlacedBlock())) return;
        Collapses.onPlace(level, pos);
        wake(level, pos, WAKE_DELAY);
    }

    /** Called every tick: looks at every block due now. */
    static void tick(ServerLevel level) {
        Long2LongOpenHashMap queue = QUEUE.get(level);
        if (queue == null || queue.isEmpty()) return;
        long now = level.getGameTime();
        LongArrayList due = new LongArrayList();
        queue.long2LongEntrySet().removeIf(entry -> {
            if (entry.getLongValue() > now) return false;
            due.add(entry.getLongKey());
            return true;
        });
        for (int i = 0; i < due.size(); i++) {
            BlockPos pos = BlockPos.of(due.getLong(i));
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isLoose(level, pos, state)) continue;
            long wait = Collapses.decide(state, level, pos);
            if (wait > 0) wake(level, pos, (int) wait);
            else if (wait == 0) Collapses.fall(level, pos, state);
        }
    }

    private LooseGround() {}
}
