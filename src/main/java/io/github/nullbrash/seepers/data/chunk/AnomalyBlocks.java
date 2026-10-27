package io.github.nullbrash.seepers.data.chunk;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * Anomalous rock in this chunk (CLAUDE.md 4.8, 13): blasted blocks waiting to regrow (with blast time and whether
 * they were geode rock) and chiseled blocks that never regrow; when each kind last grew a layer here.
 */
public final class AnomalyBlocks extends ChunkData {
    private final Int2LongOpenHashMap blasted = new Int2LongOpenHashMap();
    private final IntOpenHashSet geode = new IntOpenHashSet();
    private final IntOpenHashSet chiseled = new IntOpenHashSet();
    /** Game time of the last layer, walls / geodes; 0 = not yet (the first pass starts from the oldest blast). */
    private long wallLayerTime;
    private long geodeLayerTime;

    public AnomalyBlocks(IAttachmentHolder holder) {
        super(holder);
    }

    public void markBlasted(BlockPos pos, long gameTime, boolean isGeode) {
        int key = packLocal(pos);
        blasted.put(key, gameTime);
        if (isGeode) geode.add(key);
        else geode.remove(key);
        markDirty();
    }

    public boolean clearBlasted(BlockPos pos) {
        int key = packLocal(pos);
        boolean removed = blasted.containsKey(key);
        if (removed) {
            blasted.remove(key);
            geode.remove(key);
            markDirty();
        }
        return removed;
    }

    /** (position, blast time) of every blasted block of one kind. */
    public void forEachBlasted(boolean isGeode, BiConsumer<BlockPos, Long> action) {
        for (Int2LongMap.Entry entry : blasted.int2LongEntrySet()) {
            if (geode.contains(entry.getIntKey()) == isGeode) action.accept(unpackLocal(chunkPos(), entry.getIntKey()), entry.getLongValue());
        }
    }

    public long oldestBlast(boolean isGeode) {
        long oldest = Long.MAX_VALUE;
        for (Int2LongMap.Entry entry : blasted.int2LongEntrySet()) {
            if (geode.contains(entry.getIntKey()) == isGeode) oldest = Math.min(oldest, entry.getLongValue());
        }
        return oldest;
    }

    public long layerTime(boolean isGeode) {
        return isGeode ? geodeLayerTime : wallLayerTime;
    }

    public void setLayerTime(boolean isGeode, long time) {
        if (isGeode) geodeLayerTime = time;
        else wallLayerTime = time;
        markDirty();
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
        return 2;
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
        tag.putIntArray("blasted_geode", geode.toIntArray());
        tag.putIntArray("chiseled", chiseled.toIntArray());
        tag.putLong("wall_layer_time", wallLayerTime);
        tag.putLong("geode_layer_time", geodeLayerTime);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        blasted.clear();
        geode.clear();
        chiseled.clear();
        int[] positions = tag.getIntArray("blasted_positions");
        long[] times = tag.getLongArray("blasted_times");
        for (int i = 0; i < Math.min(positions.length, times.length); i++) {
            blasted.put(positions[i], times[i]);
        }
        // Version 1 had no kinds and no layer clocks: everything counts as wall, clocks start on the first pass.
        geode.addAll(IntOpenHashSet.of(tag.getIntArray("blasted_geode")));
        chiseled.addAll(IntOpenHashSet.of(tag.getIntArray("chiseled")));
        wallLayerTime = tag.getLong("wall_layer_time");
        geodeLayerTime = tag.getLong("geode_layer_time");
    }
}
