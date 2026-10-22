package io.github.nullbrash.seepers.data.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Global (stored with the overworld): positions of full base copies (CLAUDE.md 10.6) — for the minimum distance between them. */
public final class BaseCopyData extends WorldData {
    public static final String FILE_NAME = "seepers_base_copies";
    public static final SavedData.Factory<BaseCopyData> FACTORY = factory(BaseCopyData::new);

    public record FullCopy(ResourceKey<Level> dimension, BlockPos origin, long createdAt) {}

    private final List<FullCopy> copies = new ArrayList<>();

    public void add(FullCopy copy) {
        copies.add(copy);
        setDirty();
    }

    public List<FullCopy> copies() {
        return Collections.unmodifiableList(copies);
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (FullCopy copy : copies) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", copy.dimension().location().toString());
            entry.putLong("origin", copy.origin().asLong());
            entry.putLong("created_at", copy.createdAt());
            list.add(entry);
        }
        tag.put("copies", list);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        copies.clear();
        for (Tag element : tag.getList("copies", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension != null) {
                copies.add(new FullCopy(ResourceKey.create(Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("origin")), entry.getLong("created_at")));
            }
        }
    }
}
