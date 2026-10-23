package io.github.nullbrash.seepers.data.chunk;

import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of torches and campfires in this chunk (CLAUDE.md 4.6, 13): checked in batches for fog blow-out.
 * {@code lastCheckedTime} lets a reloaded chunk catch up on the fog it missed while unloaded.
 */
public final class FireSources extends ChunkData {
    /** Ids are saved to disk — never renumber. */
    public enum Kind {
        /** Blown out: drops as an item. */
        TORCH(0),
        /** Broken apart: drops its (overridden) loot. */
        CAMPFIRE(1),
        /** Goes out in place. */
        CANDLE(2);

        public final byte id;

        Kind(int id) {
            this.id = (byte) id;
        }

        static @Nullable Kind byId(byte id) {
            for (Kind kind : values()) {
                if (kind.id == id) return kind;
            }
            return null;
        }
    }

    private static final long NEVER = -1L;

    private final Int2ByteOpenHashMap sources = new Int2ByteOpenHashMap();
    private long lastCheckedTime = NEVER;

    public FireSources(IAttachmentHolder holder) {
        super(holder);
    }

    public void put(BlockPos pos, Kind kind) {
        sources.put(packLocal(pos), kind.id);
        markDirty();
    }

    public boolean remove(BlockPos pos) {
        boolean removed = sources.containsKey(packLocal(pos));
        if (removed) {
            sources.remove(packLocal(pos));
            markDirty();
        }
        return removed;
    }

    public @Nullable Kind get(BlockPos pos) {
        int packed = packLocal(pos);
        return sources.containsKey(packed) ? Kind.byId(sources.get(packed)) : null;
    }

    public int size() {
        return sources.size();
    }

    public void forEach(BiConsumer<BlockPos, Kind> action) {
        ChunkPos chunkPos = chunkPos();
        for (Int2ByteMap.Entry entry : sources.int2ByteEntrySet()) {
            Kind kind = Kind.byId(entry.getByteValue());
            if (kind != null) action.accept(unpackLocal(chunkPos, entry.getIntKey()), kind);
        }
    }

    /** Game time of the last fog check, or -1 if never checked. */
    public long lastCheckedTime() {
        return lastCheckedTime;
    }

    public void setLastCheckedTime(long gameTime) {
        lastCheckedTime = gameTime;
        markDirty();
    }

    @Override
    public boolean isEmpty() {
        return sources.isEmpty(); // the check time means nothing without fires
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        int[] positions = new int[sources.size()];
        byte[] kinds = new byte[sources.size()];
        int i = 0;
        for (Int2ByteMap.Entry entry : sources.int2ByteEntrySet()) {
            positions[i] = entry.getIntKey();
            kinds[i] = entry.getByteValue();
            i++;
        }
        tag.putIntArray("positions", positions);
        tag.putByteArray("kinds", kinds);
        tag.putLong("last_checked", lastCheckedTime);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        sources.clear();
        int[] positions = tag.getIntArray("positions");
        byte[] kinds = tag.getByteArray("kinds");
        for (int i = 0; i < Math.min(positions.length, kinds.length); i++) {
            if (Kind.byId(kinds[i]) != null) {
                sources.put(positions[i], kinds[i]);
            }
        }
        lastCheckedTime = tag.contains("last_checked") ? tag.getLong("last_checked") : NEVER;
    }
}
