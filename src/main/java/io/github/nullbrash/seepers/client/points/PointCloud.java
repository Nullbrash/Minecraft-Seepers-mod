package io.github.nullbrash.seepers.client.points;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
    /** A point touched again sooner than this is left as is: constant touching must not churn the ring. */
    private static final float REFRESH_MIN_AGE_SECONDS = 2;
    private static final int INITIAL_SLOTS = 8192;
    /** Positions inside a section are kept in 1/1024 block (0..16 fits a short). */
    static final float POSITION_SCALE = 1024;

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
        @Nullable PointRenderer.Mesh buffer;

        Section(long key) {
            this.key = key;
            points.defaultReturnValue(-1);
        }
    }

    /**
     * One source's points in arrival order. Slots whose section entry points elsewhere are dead (refreshed or
     * replaced). The limit counts live points; twice as many slots leave room for the dead ones, so refreshing
     * old points does not push out other live ones early. Arrays grow on demand up to that: a lidar share of
     * millions would otherwise take all its memory on the first point. A slot is 21 bytes: the position inside its
     * section in 1/1024 block, the face read from the local key, the lifetime from the source.
     */
    static final class Ring {
        final int limit;
        final int capacity;
        long[] section;
        int[] local;
        short[] x;
        short[] y;
        short[] z;
        byte[] material;
        float[] born;
        int head;
        int size;
        int live;

        Ring(int limit) {
            this.limit = limit;
            this.capacity = limit * 2;
            allocate(Math.min(capacity, INITIAL_SLOTS));
        }

        int slots() {
            return local.length;
        }

        void allocate(int slots) {
            section = new long[slots];
            local = new int[slots];
            x = new short[slots];
            y = new short[slots];
            z = new short[slots];
            material = new byte[slots];
            born = new float[slots];
        }

        float x(int slot) {
            return x[slot] / POSITION_SCALE;
        }

        float y(int slot) {
            return y[slot] / POSITION_SCALE;
        }

        float z(int slot) {
            return z[slot] / POSITION_SCALE;
        }

        Direction face(int slot) {
            return Direction.values()[local[slot] >>> 27];
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

    /** The current cloud without creating one: render threads and mixins must not reset or allocate it. */
    public static @Nullable PointCloud current() {
        return current;
    }

    public boolean hasPoints() {
        return !sections.isEmpty();
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
        add(source, x, y, z, face, material, -1);
    }

    /**
     * As {@link #add(PointSource, double, double, double, Direction, int)}, for a coarse touch that knows the precise
     * material but shows less: a point already showing {@code precise} keeps it — walking upright over ground felt
     * while crouching must not blur what was learned (and a changed block no longer matches, so it is redrawn).
     */
    public void add(PointSource source, double x, double y, double z, Direction face, int material, int precise) {
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

        Section section = sections.computeIfAbsent(sectionKey, Section::new);
        int existing = section.points.get(local);
        float now = now(0);
        if (existing != -1) {
            Ring owner = ringOf(existing);
            if (precise >= 0 && owner.material[existing & 0xFFFFFF] == precise) material = precise;
            if (owner == ring && now - ring.born[existing & 0xFFFFFF] < REFRESH_MIN_AGE_SECONDS) return;
            section.points.remove(local); // its slot turns dead now, before any eviction below
            owner.live--;
        }
        while (ring.live >= ring.limit) removeSlot(source, ring, ring.head); // drop the oldest live point
        if (ring.size == ring.slots()) {
            if (ring.slots() < ring.capacity) grow(source, ring);
            else removeSlot(source, ring, ring.head);
        }
        int slot = (ring.head + ring.size) % ring.slots();
        ring.size++;
        ring.live++;
        ring.section[slot] = sectionKey;
        ring.local[slot] = local;
        ring.x[slot] = (short) Math.round(lx * POSITION_SCALE);
        ring.y[slot] = (short) Math.round(ly * POSITION_SCALE);
        ring.z[slot] = (short) Math.round(lz * POSITION_SCALE);
        ring.material[slot] = (byte) material;
        ring.born[slot] = now;

        section.points.put(local, source.ordinal() << 24 | slot);
        section.dirty = true;
    }

    /** Drops expired points and re-checks the light over points now and then (a lamp placed, a torch blown out). */
    public void tick() {
        float now = now(0);
        for (Map.Entry<PointSource, Ring> entry : rings.entrySet()) {
            Ring ring = entry.getValue();
            float lifetime = entry.getKey().lifetimeSeconds();
            while (ring.size > 0) {
                int slot = ring.head;
                if (isAlive(entry.getKey(), ring, slot) && ring.born[slot] + lifetime > now) break;
                removeSlot(entry.getKey(), ring, slot);
            }
        }
        checkLight();
    }

    public int count(PointSource source) {
        Ring ring = rings.get(source);
        return ring == null ? 0 : ring.live;
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
        Direction face = ring.face(slot);
        double x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)) + ring.x(slot) + face.getStepX() * 0.5;
        double y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)) + ring.y(slot) + face.getStepY() * 0.5;
        double z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)) + ring.z(slot) + face.getStepZ() * 0.5;
        BlockPos at = BlockPos.containing(x, y, z);
        return Math.max(level.getBrightness(LightLayer.BLOCK, at), io.github.nullbrash.seepers.client.light.ClientDynamicLights.level(at));
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
            if (dropBuried(section) || lightHash(section) != section.lightHash) section.dirty = true;
        }
    }

    /**
     * Points on a block face that another block now covers whole (sand fell on the felt floor) go before their time:
     * they share the plane with the new block's face and would flicker through it. Only points lying on the block
     * boundary — the top of a slab inside its block is never covered by the block above.
     */
    private boolean dropBuried(Section section) {
        IntArrayList buried = null;
        double ox = SectionPos.sectionToBlockCoord(SectionPos.x(section.key));
        double oy = SectionPos.sectionToBlockCoord(SectionPos.y(section.key));
        double oz = SectionPos.sectionToBlockCoord(SectionPos.z(section.key));
        BlockPos.MutableBlockPos front = new BlockPos.MutableBlockPos();
        for (Int2IntMap.Entry entry : section.points.int2IntEntrySet()) {
            int packed = entry.getIntValue();
            Ring ring = ringOf(packed);
            int slot = packed & 0xFFFFFF;
            Direction face = ring.face(slot);
            double x = ox + ring.x(slot);
            double y = oy + ring.y(slot);
            double z = oz + ring.z(slot);
            double along = face.getAxis().choose(x, y, z);
            if (Math.abs(along - Math.rint(along)) > 1.0E-3) continue;
            front.set(Mth.floor(x + face.getStepX() * 0.5), Mth.floor(y + face.getStepY() * 0.5), Mth.floor(z + face.getStepZ() * 0.5));
            BlockState state = level.getBlockState(front);
            if (!Block.isFaceFull(state.getCollisionShape(level, front), face.getOpposite())) continue;
            if (buried == null) buried = new IntArrayList();
            buried.add(entry.getIntKey());
        }
        if (buried == null) return false;
        for (int i = 0; i < buried.size(); i++) {
            int packed = section.points.remove(buried.getInt(i));
            ringOf(packed).live--;
        }
        return true;
    }

    private @Nullable Ring ring(PointSource source) {
        int capacity = source.capacity();
        Ring ring = rings.get(source);
        if (ring != null && ring.limit == capacity) return ring;
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
            ring.live--;
        }
        ring.head = (ring.head + 1) % ring.slots();
        ring.size--;
    }

    /** Doubles a full ring's arrays (up to its capacity), unrolled from the head; live points' entries follow. */
    private void grow(PointSource source, Ring ring) {
        int old = ring.slots();
        long[] section = ring.section;
        int[] local = ring.local;
        short[] x = ring.x;
        short[] y = ring.y;
        short[] z = ring.z;
        byte[] material = ring.material;
        float[] born = ring.born;
        boolean[] alive = new boolean[ring.size];
        for (int i = 0; i < ring.size; i++) alive[i] = isAlive(source, ring, (ring.head + i) % old);
        ring.allocate(Math.min(ring.capacity, old * 2));
        for (int i = 0; i < ring.size; i++) {
            int from = (ring.head + i) % old;
            ring.section[i] = section[from];
            ring.local[i] = local[from];
            ring.x[i] = x[from];
            ring.y[i] = y[from];
            ring.z[i] = z[from];
            ring.material[i] = material[from];
            ring.born[i] = born[from];
            if (alive[i]) sections.get(section[from]).points.put(local[from], source.ordinal() << 24 | i);
        }
        ring.head = 0;
    }

    private static int quant(float local) {
        return Mth.clamp((int) (local * QUANT), 0, 16 * QUANT - 1);
    }
}
