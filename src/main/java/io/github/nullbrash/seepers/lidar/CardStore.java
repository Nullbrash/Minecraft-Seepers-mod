package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.Seepers;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Memory cards' points in the world (CLAUDE.md 5.3, 10.2, 14): one folder per card, one file per 16³ section holding
 * the point records the client sent, appended in order — the server never reads points; the client replays a section
 * the way it scanned it (a better pixel replaces a worse). An index keeps each section's live point count (as the
 * client reported it) and its last write: a card over its capacity drops its least recently written sections.
 * Copy on write: a copied card points at the source's section files (owner) instead of copying them; whichever card
 * changes a shared section first, the copies get their own file of it before. Erase deletes the folder, after handing
 * copies their shared sections. Main thread only.
 */
public final class CardStore {
    private static final String INDEX = "index.dat";
    /** A section file larger than this takes no more appends, only a rewrite (a client that keeps sending). */
    private static final long MAX_SECTION_BYTES = 8L << 20;

    /** owner: the card whose folder holds the file (a copy not yet written there); null = this card's own. */
    public record Section(int live, long written, long bytes, @Nullable UUID owner) {
        Section own() {
            return new Section(live, written, bytes, null);
        }
    }

    private static final class Card {
        final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
        /** Copies that may still point at this card's files. */
        final java.util.Set<UUID> sharers = new java.util.HashSet<>();
        long live;
        boolean dirty;
    }

    private static @Nullable CardStore instance;

    private final Path root;
    private final Map<UUID, Card> cards = new HashMap<>();

    private CardStore(Path root) {
        this.root = root;
    }

    public static CardStore get(MinecraftServer server) {
        if (instance == null) instance = new CardStore(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(Seepers.MOD_ID).resolve("cards"));
        return instance;
    }

    /** On server stop: everything written, the next world starts fresh. */
    public static void close() {
        if (instance != null) instance.saveAll();
        instance = null;
    }

    public void saveAll() {
        cards.forEach((id, card) -> {
            if (card.dirty) save(id, card);
        });
    }

    public long live(UUID id) {
        return card(id).live;
    }

    /** The card's sections, for streaming to a player: key → info. */
    public Long2ObjectMap<Section> sections(UUID id) {
        return card(id).sections;
    }

    public byte[] read(UUID id, long section) {
        Section entry = card(id).sections.get(section);
        Path file = sectionFile(entry == null || entry.owner() == null ? id : entry.owner(), section);
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not read card {} section {}", id, section, e);
            return new byte[0];
        }
    }

    /**
     * Appends (or with replace, rewrites) a section's records and sets its live count; then drops the least recently
     * written sections while the card holds more than capacity. Returns the dropped sections.
     */
    public LongArrayList write(UUID id, long section, byte[] records, int live, boolean replace, long now, int capacity) {
        Card card = card(id);
        Section old = card.sections.get(section);
        long bytes = (old == null || replace ? 0 : old.bytes()) + records.length;
        if (!replace && old != null && bytes > MAX_SECTION_BYTES) return new LongArrayList();
        try {
            Path file = sectionFile(id, section);
            Files.createDirectories(file.getParent());
            if (old != null && old.owner() != null) {
                // A copy writes to a shared section: its own file first (appending needs the source's records).
                if (!replace) Files.copy(sectionFile(old.owner(), section), file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                handOver(id, section);
            }
            if (replace) Files.write(file, records);
            else Files.write(file, records, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not write card {} section {}", id, section, e);
            return new LongArrayList();
        }
        card.live += live - (old == null ? 0 : old.live());
        card.sections.put(section, new Section(live, now, bytes, null));
        card.dirty = true;
        LongArrayList dropped = new LongArrayList();
        while (card.live > capacity && card.sections.size() > 1) {
            long oldest = 0;
            long when = Long.MAX_VALUE;
            for (Long2ObjectMap.Entry<Section> entry : card.sections.long2ObjectEntrySet()) {
                if (entry.getLongKey() != section && entry.getValue().written() < when) {
                    when = entry.getValue().written();
                    oldest = entry.getLongKey();
                }
            }
            if (when == Long.MAX_VALUE) break;
            drop(id, card, oldest);
            dropped.add(oldest);
        }
        return dropped;
    }

    public void erase(UUID id) {
        Card card = card(id);
        for (Long2ObjectMap.Entry<Section> entry : card.sections.long2ObjectEntrySet()) {
            if (entry.getValue().owner() == null) handOver(id, entry.getLongKey());
        }
        card.sections.clear();
        card.live = 0;
        card.dirty = false;
        delete(folder(id));
    }

    /** Fills an empty card with another's points (a crafted copy getting its id): it points at the same files. */
    public void copy(UUID from, UUID to) {
        Card source = card(from);
        Card copy = new Card();
        for (Long2ObjectMap.Entry<Section> entry : source.sections.long2ObjectEntrySet()) {
            Section section = entry.getValue();
            UUID owner = section.owner() != null ? section.owner() : from;
            copy.sections.put(entry.getLongKey(), new Section(section.live(), section.written(), section.bytes(), owner));
            Card holder = card(owner);
            if (holder.sharers.add(to)) holder.dirty = true;
        }
        copy.live = source.live;
        copy.dirty = true;
        cards.put(to, copy);
        save(to, copy);
    }

    /** Before this card's own file of a section changes or goes: copies still pointing at it get their own. */
    private void handOver(UUID id, long section) {
        Card card = card(id);
        if (card.sharers.isEmpty()) return;
        Path file = sectionFile(id, section);
        for (UUID sharer : card.sharers) {
            Card other = card(sharer);
            Section entry = other.sections.get(section);
            if (entry == null || !id.equals(entry.owner())) continue;
            try {
                Path own = sectionFile(sharer, section);
                Files.createDirectories(own.getParent());
                if (Files.exists(file)) Files.copy(file, own, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Seepers.LOGGER.warn("Could not hand card {} section {} to its copy {}", id, section, sharer, e);
            }
            other.sections.put(section, entry.own());
            other.dirty = true;
        }
    }

    private void drop(UUID id, Card card, long section) {
        Section old = card.sections.remove(section);
        if (old != null) card.live -= old.live();
        if (old != null && old.owner() == null) {
            handOver(id, section);
            try {
                Files.deleteIfExists(sectionFile(id, section));
            } catch (IOException e) {
                Seepers.LOGGER.warn("Could not drop card {} section {}", id, section, e);
            }
        }
        card.dirty = true;
    }

    private Card card(UUID id) {
        return cards.computeIfAbsent(id, this::load);
    }

    private Card load(UUID id) {
        Card card = new Card();
        Path index = folder(id).resolve(INDEX);
        if (!Files.exists(index)) return card;
        try {
            CompoundTag tag = NbtIo.read(index);
            if (tag == null) return card;
            for (Tag entry : tag.getList("sections", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) entry;
                Section section = new Section(s.getInt("live"), s.getLong("written"), s.getLong("bytes"), s.hasUUID("owner") ? s.getUUID("owner") : null);
                card.sections.put(s.getLong("key"), section);
                card.live += section.live();
            }
            for (Tag sharer : tag.getList("sharers", Tag.TAG_INT_ARRAY)) card.sharers.add(net.minecraft.nbt.NbtUtils.loadUUID(sharer));
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not read card {} index", id, e);
        }
        return card;
    }

    private void save(UUID id, Card card) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<Section> entry : card.sections.long2ObjectEntrySet()) {
            CompoundTag s = new CompoundTag();
            s.putLong("key", entry.getLongKey());
            s.putInt("live", entry.getValue().live());
            s.putLong("written", entry.getValue().written());
            s.putLong("bytes", entry.getValue().bytes());
            if (entry.getValue().owner() != null) s.putUUID("owner", entry.getValue().owner());
            list.add(s);
        }
        tag.put("sections", list);
        ListTag sharers = new ListTag();
        for (UUID sharer : card.sharers) sharers.add(net.minecraft.nbt.NbtUtils.createUUID(sharer));
        tag.put("sharers", sharers);
        try {
            Files.createDirectories(folder(id));
            NbtIo.write(tag, folder(id).resolve(INDEX));
            card.dirty = false;
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not save card {} index", id, e);
        }
    }

    private Path folder(UUID id) {
        return root.resolve(id.toString());
    }

    private Path sectionFile(UUID id, long section) {
        return folder(id).resolve(String.format("%016x.bin", section));
    }

    private static void delete(Path folder) {
        if (!Files.isDirectory(folder)) return;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator) Files.deleteIfExists(file);
            Files.deleteIfExists(folder);
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not erase card folder {}", folder, e);
        }
    }
}
