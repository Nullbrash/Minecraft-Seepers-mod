package io.github.nullbrash.seepers.data.world;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import org.jetbrains.annotations.Nullable;

/** Global: player remains — corpse, then skeleton, then grave (CLAUDE.md 9, 13). */
public final class RemainsData extends WorldData {
    public static final String FILE_NAME = "seepers_remains";
    public static final SavedData.Factory<RemainsData> FACTORY = factory(RemainsData::new);

    /** Saved by name — do not rename constants. */
    public enum Stage { CORPSE, SKELETON, GRAVE }

    /** {@code life} is the owner's life index from {@link LifeData}. */
    public record Remains(UUID id, UUID owner, int life, ResourceKey<Level> dimension, BlockPos pos, long diedAt, Stage stage) {
        public Remains withStage(Stage newStage) {
            return new Remains(id, owner, life, dimension, pos, diedAt, newStage);
        }
    }

    private final Map<UUID, Remains> remains = new LinkedHashMap<>();

    public void put(Remains entry) {
        remains.put(entry.id(), entry);
        setDirty();
    }

    public @Nullable Remains get(UUID id) {
        return remains.get(id);
    }

    public boolean remove(UUID id) {
        boolean removed = remains.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }

    public Collection<Remains> all() {
        return Collections.unmodifiableCollection(remains.values());
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Remains entry : remains.values()) {
            CompoundTag nbt = new CompoundTag();
            nbt.putUUID("id", entry.id());
            nbt.putUUID("owner", entry.owner());
            nbt.putInt("life", entry.life());
            nbt.putString("dimension", entry.dimension().location().toString());
            nbt.putLong("pos", entry.pos().asLong());
            nbt.putLong("died_at", entry.diedAt());
            nbt.putString("stage", entry.stage().name());
            list.add(nbt);
        }
        tag.put("remains", list);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        remains.clear();
        for (Tag element : tag.getList("remains", Tag.TAG_COMPOUND)) {
            CompoundTag nbt = (CompoundTag) element;
            ResourceLocation dimension = ResourceLocation.tryParse(nbt.getString("dimension"));
            Stage stage = parseStage(nbt.getString("stage"));
            if (dimension == null || stage == null || !nbt.hasUUID("id") || !nbt.hasUUID("owner")) {
                continue;
            }
            Remains entry = new Remains(nbt.getUUID("id"), nbt.getUUID("owner"), nbt.getInt("life"),
                    ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(nbt.getLong("pos")),
                    nbt.getLong("died_at"), stage);
            remains.put(entry.id(), entry);
        }
    }

    private static @Nullable Stage parseStage(String name) {
        for (Stage stage : Stage.values()) {
            if (stage.name().equals(name)) return stage;
        }
        return null;
    }
}
