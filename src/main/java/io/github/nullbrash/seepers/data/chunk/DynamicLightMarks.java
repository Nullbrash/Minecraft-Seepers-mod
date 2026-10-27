package io.github.nullbrash.seepers.data.chunk;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * Light blocks the dynamic light put in this chunk. Saved, so a light left behind by a crash or a stop is found and
 * taken out when the chunk loads again; light blocks of map makers are never in here and stay.
 */
public final class DynamicLightMarks extends ChunkData {
    private final IntOpenHashSet positions = new IntOpenHashSet();

    public DynamicLightMarks(IAttachmentHolder holder) {
        super(holder);
    }

    public void add(BlockPos pos) {
        if (positions.add(packLocal(pos))) markDirty();
    }

    public void remove(BlockPos pos) {
        if (positions.remove(packLocal(pos))) markDirty();
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(packLocal(pos));
    }

    public List<BlockPos> all() {
        List<BlockPos> list = new ArrayList<>(positions.size());
        for (int packed : positions) list.add(unpackLocal(chunkPos(), packed));
        return list;
    }

    @Override
    public boolean isEmpty() {
        return positions.isEmpty();
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray("positions", positions.toIntArray());
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        positions.clear();
        positions.addAll(IntOpenHashSet.of(tag.getIntArray("positions")));
    }
}
