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
 * client reported it) and its last write: a card over its capacity drops its least recently written sections. Copy and
 * erase are a folder copy and delete. Main thread only.
 */
public final class CardStore {
    private static final String INDEX = "index.dat";
    /** A section file larger than this takes no more appends, only a rewrite (a client that keeps sending). */
    private static final long MAX_SECTION_BYTES = 8L << 20;

    public record Section(int live, long written, long bytes) {}

    private static final class Card {
        final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
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
        Path file = sectionFile(id, section);
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
            if (replace) Files.write(file, records);
            else Files.write(file, records, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not write card {} section {}", id, section, e);
            return new LongArrayList();
        }
        card.live += live - (old == null ? 0 : old.live());
        card.sections.put(section, new Section(live, now, bytes));
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
        card.sections.clear();
        card.live = 0;
        card.dirty = false;
        delete(folder(id));
    }

    /** Fills an empty card with another's points (a crafted copy getting its id). */
    public void copy(UUID from, UUID to) {
        Card source = card(from);
        if (source.dirty) save(from, source);
        Path src = folder(from);
        Path dst = folder(to);
        if (!Files.isDirectory(src)) return;
        try (Stream<Path> files = Files.list(src)) {
            Files.createDirectories(dst);
            for (Path file : (Iterable<Path>) files::iterator) Files.copy(file, dst.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not copy card {} to {}", from, to, e);
        }
        cards.remove(to);
    }

    private void drop(UUID id, Card card, long section) {
        Section old = card.sections.remove(section);
        if (old != null) card.live -= old.live();
        try {
            Files.deleteIfExists(sectionFile(id, section));
        } catch (IOException e) {
            Seepers.LOGGER.warn("Could not drop card {} section {}", id, section, e);
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
                Section section = new Section(s.getInt("live"), s.getLong("written"), s.getLong("bytes"));
                card.sections.put(s.getLong("key"), section);
                card.live += section.live();
            }
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
            list.add(s);
        }
        tag.put("sections", list);
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
