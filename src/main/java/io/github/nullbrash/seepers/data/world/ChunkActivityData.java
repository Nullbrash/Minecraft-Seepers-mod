package io.github.nullbrash.seepers.data.world;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Per dimension: habitation score of chunks (CLAUDE.md 13) — the most lived-in ones are the source for base copies (10.6). */
public final class ChunkActivityData extends WorldData {
    public static final String FILE_NAME = "seepers_chunk_activity";
    public static final SavedData.Factory<ChunkActivityData> FACTORY = factory(ChunkActivityData::new);

    private final Long2LongOpenHashMap scores = new Long2LongOpenHashMap();

    public void add(ChunkPos pos, long delta) {
        scores.addTo(pos.toLong(), delta);
        setDirty();
    }

    public long score(ChunkPos pos) {
        return scores.get(pos.toLong());
    }

    public int size() {
        return scores.size();
    }

    /** Highest scores first. Sorts the whole map — call rarely. */
    public List<ChunkPos> top(int limit) {
        return scores.long2LongEntrySet().stream()
                .sorted(Comparator.comparingLong(Long2LongMap.Entry::getLongValue).reversed())
                .limit(limit)
                .map(entry -> new ChunkPos(entry.getLongKey()))
                .toList();
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        long[] chunks = new long[scores.size()];
        long[] values = new long[scores.size()];
        int i = 0;
        for (Long2LongMap.Entry entry : scores.long2LongEntrySet()) {
            chunks[i] = entry.getLongKey();
            values[i] = entry.getLongValue();
            i++;
        }
        tag.putLongArray("chunks", chunks);
        tag.putLongArray("scores", values);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        scores.clear();
        long[] chunks = tag.getLongArray("chunks");
        long[] values = tag.getLongArray("scores");
        for (int i = 0; i < Math.min(chunks.length, values.length); i++) {
            scores.put(chunks[i], values[i]);
        }
    }
}
