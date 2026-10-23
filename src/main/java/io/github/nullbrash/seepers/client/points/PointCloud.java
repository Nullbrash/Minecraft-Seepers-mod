package io.github.nullbrash.seepers.client.points;

import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

/**
 * The client's point cloud (CLAUDE.md 5): touch now, cane and lidar later. Kept only in memory. Points are
 * bucketed by 16³ section for rendering; each source is a ring buffer of its own capacity, so the oldest points
 * of a full share go first and other shares are untouched. Render thread only.
 */
public final class PointCloud {
    /** Positions are keyed at 1/32 block inside a section: touching the same spot again refreshes the point. */
    private static final int QUANT = 32;
    private static final int LIGHT_CHECK_TICKS = 20;
    private static final int LIGHT_SAMPLES_PER_TICK = 4096;

    private static @Nullable PointCloud current;

    final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final Map<PointSource, Ring> rings = new EnumMap<>(PointSource.class);
    private final ClientLevel level;
    private final long epochTick;
    private final LongArrayFIFOQueue lightQueue = new LongArrayFIFOQueue();
    private long nextLightPass;

    /** Points of one section; the renderer owns its buffer. */
    static final class Section {
        final long key;
        /** Local key → (source ordinal << 24 | ring slot). */
        final Int2IntOpenHashMap points = new Int2IntOpenHashMap();
        boolean dirty = true;
        int lightHash;
        @Nullable VertexBuffer buffer;

        Section(long key) {
            this.key = key;
            points.defaultReturnValue(-1);
        }
    }

    /** One source's points in arrival order. Slots whose section entry points elsewhere are dead. */
    static final class Ring {
        final int capacity;
        final long[] section;
        final int[] local;
        final float[] x;
        final float[] y;
        final float[] z;
        final byte[] face;
        final byte[] material;
        final float[] born;
        final float[] lifetime;
        int head;
        int size;

        Ring(int capacity) {
            this.capacity = capacity;
            section = new long[capacity];
            local = new int[capacity];
            x = new float[capacity];
            y = new float[capacity];
            z = new float[capacity];
            face = new byte[capacity];
            material = new byte[capacity];
            born = new float[capacity];
            lifetime = new float[capacity];
        }
    }

    private PointCloud(ClientLevel level) {
        this.level = level;
        this.epochTick = level.getGameTime();
    }

    /** The cloud of the current client level; a new level starts empty. */
    public static @Nullable PointCloud get() {
        ClientLevel level = Minecraft.getInstance().level;
        if (current != null && current.level != level) reset();
        if (current == null && level != null) current = new PointCloud(level);
        return current;
    }

    public static void reset() {
        if (current != null) {
            current.clear();
            current = null;
        }
    }

    /** Seconds since this cloud started, in game time (stops while the game is paused). */
    public float now(float partialTick) {
        return (level.getGameTime() - epochTick + partialTick) / 20.0F;
    }

    /**
     * Adds a point lying on the given face of a block (x, y, z on the face plane). Re-adding the same spot
     * replaces the old point.
     */
    public void add(PointSource source, double x, double y, double z, Direction face, int material) {
        Ring ring = ring(source);
        if (ring == null) return;
        long sectionKey = SectionPos.asLong(Mth.floor(x) >> 4, Mth.floor(y) >> 4, Mth.floor(z) >> 4);
        double ox = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey));
        double oy = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey));
        double oz = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey));
        float lx = (float) (x - ox);
        float ly = (float) (y - oy);
        float lz = (float) (z - oz);
        int local = face.ordinal() << 27 | quant(lx) << 18 | quant(ly) << 9 | quant(lz);

        if (ring.size == ring.capacity) removeSlot(source, ring, ring.head);
        int slot = (ring.head + ring.size) % ring.capacity;
        ring.size++;
        ring.section[slot] = sectionKey;
        ring.local[slot] = local;
        ring.x[slot] = lx;
        ring.y[slot] = ly;
        ring.z[slot] = lz;
        ring.face[slot] = (byte) face.ordinal();
        ring.material[slot] = (byte) material;
        ring.born[slot] = now(0);
        ring.lifetime[slot] = source.lifetimeSeconds();

        Section section = sections.computeIfAbsent(sectionKey, Section::new);
        section.points.put(local, source.ordinal() << 24 | slot); // an older point at the spot is now dead
        section.dirty = true;
    }

    /** Drops expired points and re-checks the light over points now and then (a lamp placed, a torch blown out). */
    public void tick() {
        float now = now(0);
        for (Map.Entry<PointSource, Ring> entry : rings.entrySet()) {
            Ring ring = entry.getValue();
            while (ring.size > 0) {
                int slot = ring.head;
                if (isAlive(entry.getKey(), ring, slot) && ring.born[slot] + ring.lifetime[slot] > now) break;
                removeSlot(entry.getKey(), ring, slot);
            }
        }
        checkLight();
    }

    public int count(PointSource source) {
        Ring ring = rings.get(source);
        if (ring == null) return 0;
        int alive = 0;
        for (int i = 0; i < ring.size; i++) {
            if (isAlive(source, ring, (ring.head + i) % ring.capacity)) alive++;
        }
        return alive;
    }

    public int clear() {
        int removed = 0;
        for (Section section : sections.values()) {
            removed += section.points.size();
            if (section.buffer != null) section.buffer.close();
        }
        sections.clear();
        rings.clear();
        lightQueue.clear();
        return removed;
    }

    Ring ringOf(int packed) {
        return rings.get(PointSource.values()[packed >>> 24]);
    }

    /** Block light on the open side of the point's face (a solid block's own light is always 0). */
    int lightAt(long sectionKey, Ring ring, int slot) {
        Direction face = Direction.values()[ring.face[slot]];
        double x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)) + ring.x[slot] + face.getStepX() * 0.5;
        double y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)) + ring.y[slot] + face.getStepY() * 0.5;
        double z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)) + ring.z[slot] + face.getStepZ() * 0.5;
        return level.getBrightness(LightLayer.BLOCK, BlockPos.containing(x, y, z));
    }

    int lightHash(Section section) {
        int hash = 1;
        for (int packed : section.points.values()) {
            hash = hash * 31 + lightAt(section.key, ringOf(packed), packed & 0xFFFFFF);
        }
        return hash;
    }

    private void checkLight() {
        long tick = level.getGameTime();
        if (lightQueue.isEmpty()) {
            if (tick < nextLightPass) return;
            nextLightPass = tick + LIGHT_CHECK_TICKS;
            for (long key : sections.keySet()) lightQueue.enqueue(key);
        }
        int budget = LIGHT_SAMPLES_PER_TICK;
        while (budget > 0 && !lightQueue.isEmpty()) {
            Section section = sections.get(lightQueue.dequeueLong());
            if (section == null || section.dirty) continue;
            budget -= section.points.size();
            if (lightHash(section) != section.lightHash) section.dirty = true;
        }
    }

    private @Nullable Ring ring(PointSource source) {
        int capacity = source.capacity();
        Ring ring = rings.get(source);
        if (ring != null && ring.capacity == capacity) return ring;
        if (ring != null) {
            while (ring.size > 0) removeSlot(source, ring, ring.head); // the limit changed: start this share over
            rings.remove(source);
        }
        if (capacity <= 0) return null;
        ring = new Ring(capacity);
        rings.put(source, ring);
        return ring;
    }

    private boolean isAlive(PointSource source, Ring ring, int slot) {
        Section section = sections.get(ring.section[slot]);
        return section != null && section.points.get(ring.local[slot]) == (source.ordinal() << 24 | slot);
    }

    /** Frees the ring's head slot (only the head is ever removed) and its section entry if still alive. */
    private void removeSlot(PointSource source, Ring ring, int slot) {
        if (isAlive(source, ring, slot)) {
            Section section = sections.get(ring.section[slot]);
            section.points.remove(ring.local[slot]);
            section.dirty = true;
        }
        ring.head = (ring.head + 1) % ring.capacity;
        ring.size--;
    }

    private static int quant(float local) {
        return Mth.clamp((int) (local * QUANT), 0, 16 * QUANT - 1);
    }
}
