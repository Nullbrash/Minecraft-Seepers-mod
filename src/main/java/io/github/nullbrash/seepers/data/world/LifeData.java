package io.github.nullbrash.seepers.data.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Global: identity of player lives (CLAUDE.md 9, 13). Each life is a new person with its own tag
 * ({@code Name·3f2a}); past lives become predecessors.
 */
public final class LifeData extends WorldData {
    public static final String FILE_NAME = "seepers_lives";
    public static final SavedData.Factory<LifeData> FACTORY = factory(LifeData::new);

    public static final long ALIVE = -1L;

    /** {@code endedAt} is {@link #ALIVE} for the current life. */
    public record Life(int index, String tag, long startedAt, long endedAt) {}

    private final Map<UUID, List<Life>> lives = new HashMap<>();

    /** Ends the player's current life (if any) and starts the next one. */
    public Life startNewLife(UUID player, String tag, long gameTime) {
        List<Life> history = lives.computeIfAbsent(player, key -> new ArrayList<>());
        if (!history.isEmpty()) {
            Life last = history.getLast();
            if (last.endedAt() == ALIVE) {
                history.set(history.size() - 1, new Life(last.index(), last.tag(), last.startedAt(), gameTime));
            }
        }
        Life life = new Life(history.size(), tag, gameTime, ALIVE);
        history.add(life);
        setDirty();
        return life;
    }

    public @Nullable Life current(UUID player) {
        List<Life> history = lives.get(player);
        if (history == null || history.isEmpty()) return null;
        Life last = history.getLast();
        return last.endedAt() == ALIVE ? last : null;
    }

    public List<Life> history(UUID player) {
        return Collections.unmodifiableList(lives.getOrDefault(player, List.of()));
    }

    public int playerCount() {
        return lives.size();
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, List<Life>> entry : lives.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("player", entry.getKey());
            ListTag history = new ListTag();
            for (Life life : entry.getValue()) {
                CompoundTag nbt = new CompoundTag();
                nbt.putInt("index", life.index());
                nbt.putString("tag", life.tag());
                nbt.putLong("started_at", life.startedAt());
                nbt.putLong("ended_at", life.endedAt());
                history.add(nbt);
            }
            player.put("lives", history);
            players.add(player);
        }
        tag.put("players", players);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        lives.clear();
        for (Tag element : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) element;
            if (!player.hasUUID("player")) continue;
            List<Life> history = new ArrayList<>();
            for (Tag lifeElement : player.getList("lives", Tag.TAG_COMPOUND)) {
                CompoundTag nbt = (CompoundTag) lifeElement;
                history.add(new Life(nbt.getInt("index"), nbt.getString("tag"), nbt.getLong("started_at"), nbt.getLong("ended_at")));
            }
            lives.put(player.getUUID("player"), history);
        }
    }
}
