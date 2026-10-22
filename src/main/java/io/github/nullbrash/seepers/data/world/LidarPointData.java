package io.github.nullbrash.seepers.data.world;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per dimension: stored lidar points with source and time (CLAUDE.md 10.2, 13).
 * Placeholder format: the whole store is rewritten on every save and has no size limit yet —
 * to be split by region and capped before real use (lidar / memory cells stages).
 */
public final class LidarPointData extends WorldData {
    public static final String FILE_NAME = "seepers_lidar_points";
    public static final SavedData.Factory<LidarPointData> FACTORY = factory(LidarPointData::new);

    // Sources (player or memory cell ids) are interned so each point stores a small index.
    private final List<UUID> sources = new ArrayList<>();
    private final Object2IntOpenHashMap<UUID> sourceIndex = new Object2IntOpenHashMap<>();
    private final LongArrayList positions = new LongArrayList();
    private final IntArrayList pointSources = new IntArrayList();
    private final LongArrayList times = new LongArrayList();

    public void add(BlockPos pos, UUID source, long gameTime) {
        positions.add(pos.asLong());
        pointSources.add(intern(source));
        times.add(gameTime);
        setDirty();
    }

    public int size() {
        return positions.size();
    }

    public int sourceCount() {
        return sources.size();
    }

    private int intern(UUID source) {
        return sourceIndex.computeIfAbsent(source, (UUID key) -> {
            sources.add(key);
            return sources.size() - 1;
        });
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sourceList = new ListTag();
        for (UUID source : sources) {
            sourceList.add(NbtUtils.createUUID(source));
        }
        tag.put("sources", sourceList);
        tag.putLongArray("positions", positions.toLongArray());
        tag.putIntArray("point_sources", pointSources.toIntArray());
        tag.putLongArray("times", times.toLongArray());
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        sources.clear();
        sourceIndex.clear();
        positions.clear();
        pointSources.clear();
        times.clear();
        for (Tag source : tag.getList("sources", Tag.TAG_INT_ARRAY)) {
            intern(NbtUtils.loadUUID(source));
        }
        long[] pos = tag.getLongArray("positions");
        int[] src = tag.getIntArray("point_sources");
        long[] time = tag.getLongArray("times");
        int count = Math.min(pos.length, Math.min(src.length, time.length));
        for (int i = 0; i < count; i++) {
            if (src[i] >= 0 && src[i] < sources.size()) {
                positions.add(pos[i]);
                pointSources.add(src[i]);
                times.add(time[i]);
            }
        }
    }
}
