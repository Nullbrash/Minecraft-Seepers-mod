package io.github.nullbrash.seepers.data.chunk;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/** Blocks placed by players in this chunk (CLAUDE.md 13): feeds the habitation rating and base copies. */
public final class PlayerPlacedBlocks extends ChunkData {
    private final IntOpenHashSet positions = new IntOpenHashSet();

    public PlayerPlacedBlocks(IAttachmentHolder holder) {
        super(holder);
    }

    public boolean add(BlockPos pos) {
        boolean added = positions.add(packLocal(pos));
        if (added) markDirty();
        return added;
    }

    public boolean remove(BlockPos pos) {
        boolean removed = positions.remove(packLocal(pos));
        if (removed) markDirty();
        return removed;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(packLocal(pos));
    }

    public int size() {
        return positions.size();
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
