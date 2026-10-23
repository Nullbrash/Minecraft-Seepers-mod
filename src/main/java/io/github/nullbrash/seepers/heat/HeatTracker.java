package io.github.nullbrash.seepers.heat;

import io.github.nullbrash.seepers.data.chunk.HeatSources;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Server side: keeps each chunk's {@link HeatSources} in step with its blocks. Changes come from the
 * neighbor-notify event (fired for practically every block change, incl. furnaces lighting up);
 * a chunk is reconciled with its blocks when it loads, so misses do not persist.
 */
public final class HeatTracker {
    public static @Nullable HeatCategory categoryOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
            return null;
        }
        for (HeatCategory category : HeatCategory.values()) {
            if (state.is(category.tag)) {
                // Lava: only source blocks, flowing lava would flood the per-chunk limit.
                if (category == HeatCategory.LAVA && !state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
                    return null;
                }
                return category;
            }
        }
        return null;
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            update(level, event.getPos(), event.getState());
        }
    }

    public static void update(ServerLevel level, BlockPos pos, BlockState state) {
        LevelChunk chunk = level.getChunkAt(pos);
        HeatCategory category = categoryOf(state);
        HeatSources sources = category != null ? chunk.getData(SeepersAttachments.HEAT_SOURCES)
                : chunk.getExistingDataOrNull(SeepersAttachments.HEAT_SOURCES.get());
        if (sources == null) return;
        HeatSettings settings = HeatSettings.current();
        long now = level.getGameTime();
        boolean changed = category != null
                ? sources.light(pos, category, now, settings.maxSourcesPerChunk())
                : sources.extinguish(pos, now);
        changed |= sources.forgetCooled(now, settings.cooldownTicks());
        if (changed) {
            chunk.syncData(SeepersAttachments.HEAT_SOURCES.get());
        }
    }

    /** Sources already in the world (generated lava, a base left burning) count as fully warm. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        HeatSettings settings = HeatSettings.current();
        long now = level.getGameTime();
        List<BlockPos> found = new ArrayList<>();
        chunk.findBlocks(state -> categoryOf(state) != null, (pos, state) -> found.add(pos.immutable()));
        HeatSources existing = chunk.getExistingDataOrNull(SeepersAttachments.HEAT_SOURCES.get());
        if (found.isEmpty() && existing == null) return;

        HeatSources sources = chunk.getData(SeepersAttachments.HEAT_SOURCES);
        boolean changed = false;
        for (BlockPos pos : found) {
            HeatSources.Source known = sources.get(pos);
            long litSince = known != null && known.outSince() == HeatSources.LIT ? known.litSince() : HeatSources.LONG_AGO;
            changed |= sources.light(pos, categoryOf(chunk.getBlockState(pos)), litSince, settings.maxSourcesPerChunk());
        }
        // Burning entries whose block is gone (changed while we could not see it): start cooling now.
        List<BlockPos> stale = new ArrayList<>();
        sources.forEach((pos, source) -> {
            if (source.outSince() == HeatSources.LIT && categoryOf(chunk.getBlockState(pos)) != source.category()) {
                stale.add(pos);
            }
        });
        for (BlockPos pos : stale) {
            changed |= sources.extinguish(pos, now);
        }
        changed |= sources.forgetCooled(now, settings.cooldownTicks());
        if (changed) {
            chunk.syncData(SeepersAttachments.HEAT_SOURCES.get());
        }
    }

    private HeatTracker() {}
}
