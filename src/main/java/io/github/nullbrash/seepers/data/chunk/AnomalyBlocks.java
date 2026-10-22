package io.github.nullbrash.seepers.data.chunk;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * Anomalous rock in this chunk (CLAUDE.md 4.8, 13): blasted blocks waiting to regrow (with blast time)
 * and chiseled blocks that never regrow.
 */
public final class AnomalyBlocks extends ChunkData {
    private final Int2LongOpenHashMap blasted = new Int2LongOpenHashMap();
    private final IntOpenHashSet chiseled = new IntOpenHashSet();

    public AnomalyBlocks(IAttachmentHolder holder) {
        super(holder);
    }

    public void markBlasted(BlockPos pos, long gameTime) {
        blasted.put(packLocal(pos), gameTime);
        markDirty();
    }

    public boolean clearBlasted(BlockPos pos) {
        boolean removed = blasted.containsKey(packLocal(pos));
        if (removed) {
            blasted.remove(packLocal(pos));
            markDirty();
        }
        return removed;
    }

    public boolean markChiseled(BlockPos pos) {
        boolean added = chiseled.add(packLocal(pos));
        if (added) markDirty();
        return added;
    }

    public boolean isChiseled(BlockPos pos) {
        return chiseled.contains(packLocal(pos));
    }

    public int blastedCount() {
        return blasted.size();
    }

    public int chiseledCount() {
        return chiseled.size();
    }

    @Override
    public boolean isEmpty() {
        return blasted.isEmpty() && chiseled.isEmpty();
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        int[] positions = new int[blasted.size()];
        long[] times = new long[blasted.size()];
        int i = 0;
        for (Int2LongMap.Entry entry : blasted.int2LongEntrySet()) {
            positions[i] = entry.getIntKey();
            times[i] = entry.getLongValue();
            i++;
        }
        tag.putIntArray("blasted_positions", positions);
        tag.putLongArray("blasted_times", times);
        tag.putIntArray("chiseled", chiseled.toIntArray());
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        blasted.clear();
        chiseled.clear();
        int[] positions = tag.getIntArray("blasted_positions");
        long[] times = tag.getLongArray("blasted_times");
        for (int i = 0; i < Math.min(positions.length, times.length); i++) {
            blasted.put(positions[i], times[i]);
        }
        chiseled.addAll(IntOpenHashSet.of(tag.getIntArray("chiseled")));
    }
}
