package io.github.nullbrash.seepers.data.world;

import io.github.nullbrash.seepers.data.VersionedData;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Base for level-wide data in {@code <world>/[DIM]/data/<name>.dat}.
 * Mutators must call {@link #setDirty()} or the change is not saved.
 */
public abstract class WorldData extends SavedData implements VersionedData {
    @Override
    public final CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return VersionedData.save(this, tag, registries);
    }

    protected static <T extends WorldData> SavedData.Factory<T> factory(Supplier<T> constructor) {
        return new SavedData.Factory<>(constructor, (tag, registries) -> VersionedData.load(constructor.get(), tag, registries));
    }
}
