package io.github.nullbrash.seepers.data;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Mod data saved as NBT. Every save carries a format version so later stages can migrate old worlds. */
public interface VersionedData {
    String VERSION_KEY = "format_version";

    int formatVersion();

    void write(CompoundTag tag, HolderLookup.Provider registries);

    /** {@code version} is the one found in the save; lower than {@link #formatVersion()} means an old save. */
    void read(CompoundTag tag, int version, HolderLookup.Provider registries);

    static CompoundTag save(VersionedData data, CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(VERSION_KEY, data.formatVersion());
        data.write(tag, registries);
        return tag;
    }

    static <T extends VersionedData> T load(T data, CompoundTag tag, HolderLookup.Provider registries) {
        int version = tag.getInt(VERSION_KEY);
        if (version > data.formatVersion()) {
            Seepers.LOGGER.warn("{} was saved by a newer Seepers (format {} > {}); unknown fields are dropped",
                    data.getClass().getSimpleName(), version, data.formatVersion());
        }
        data.read(tag, version, registries);
        return data;
    }
}
