package io.github.nullbrash.seepers.client.points;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The client's point cloud (CLAUDE.md 5): touch now, cane and lidar later. Kept only in memory. Points are
 * bucketed by 16³ section for rendering; each source is a ring buffer of its own capacity, so the oldest points
 * of a full share go first and other shares are untouched. Render thread only.
 */
public final class PointCloud {
    /** Positions are keyed at 1/32 block inside a section: touching the same spot again refreshes the point. */
    private static final int QUANT = 32;
    /** Fresh points kept per section until its rebuild; more wait for the rebuild. */
    private static final int FRESH_LIMIT = 8192;
    private static final Direction[] DIRECTIONS = Direction.values();
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
    private int lightBudget;

    /** Points of one section; the renderer owns its buffer. */
    static final class Section {
        final long key;
        /** Local key → (source ordinal << 24 | ring slot). */
        final Int2IntOpenHashMap points = new Int2IntOpenHashMap();
        boolean dirty = true;
        int lightHash;
        /** When the renderer last rebuilt it (System.nanoTime). */
        long rebuiltAt;
        /**
         * Points added since the last rebuild (source << 24 | slot): a big section is rebuilt only now and then, these
         * show at once from a small buffer of their own (the scan would appear in jerks otherwise).
         */
        final it.unimi.dsi.fastutil.ints.IntArrayList fresh = new it.unimi.dsi.fastutil.ints.IntArrayList();
        boolean freshDirty;
        @Nullable PointRenderer.Mesh freshBuffer;
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
     * section in 1/1024 block, the face read from the local key, the lifetime from the source; 4 more for a colour.
     */
    static final class Ring {
        int limit;
        final int capacity;
        long[] section;
        int[] local;
        short[] x;
        short[] y;
        short[] z;
        byte[] material;
        /** A point's own colour (0xRRGGBB, lidar analysis); 0 = its material's. */
        int[] rgb;
        float[] born;
        int head;
        int size;
        int live;

        Ring(int limit, int capacity) {
            this.limit = limit;
            this.capacity = capacity;
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
            rgb = new int[slots];
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
            return DIRECTIONS[local[slot] >>> 27];
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
        if (current == null && level != null) {
            current = new PointCloud(level);
        }
        return current;
    }

    /** The current cloud without creating one: render threads and mixins must not reset or allocate it. */
    public static @Nullable PointCloud current() {
        return current;
    }

    public it.unimi.dsi.fastutil.longs.LongSet sectionKeys() {
        return sections.keySet();
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
        add(source, x, y, z, face, material, precise, 0);
    }

    /** As add, with the point's own colour (0xRRGGBB; 0 = the material's): lidar analysis. */
    public void addColored(PointSource source, double x, double y, double z, Direction face, int material, int rgb) {
        add(source, x, y, z, face, material, -1, rgb);
    }

    private void add(PointSource source, double x, double y, double z, Direction face, int material, int precise, int rgb) {
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
        if (source == PointSource.LIDAR && !takePixel(section, ring, face, local, rank(material, rgb))) return;
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
        ring.rgb[slot] = rgb;
        ring.born[slot] = now;

        section.points.put(local, source.ordinal() << 24 | slot);
        section.dirty = true;
        if (source == PointSource.LIDAR && !replaying && recorder != null) {
            recorder.record(sectionKey, ring.x[slot], ring.y[slot], ring.z[slot], face.ordinal(), material, rgb);
        }
        if (section.buffer != null && section.fresh.size() < FRESH_LIMIT) {
            section.fresh.add(source.ordinal() << 24 | slot);
            section.freshDirty = true;
        }
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
            if (section.freshBuffer != null) section.freshBuffer.close();
        }
        sections.clear();
        rings.clear();
        lightQueue.clear();
        return removed;
    }

    @Nullable Ring ringFor(PointSource source) {
        return rings.get(source);
    }

    Ring ringOf(int packed) {
        return rings.get(PointSource.VALUES[packed >>> 24]);
    }

    /** Block light on the open side of the point's face (a solid block's own light is always 0). */
    /**
     * The light over a point: of the air cell in front of its face. Points of one face share cells by the hundred, so
     * {@code cells} remembers each cell's light for one pass over a section (a world lookup per point was the cost).
     */
    int lightAt(long sectionKey, Ring ring, int slot, Long2IntOpenHashMap cells) {
        Direction face = ring.face(slot);
        double x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)) + ring.x(slot) + face.getStepX() * 0.5;
        double y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)) + ring.y(slot) + face.getStepY() * 0.5;
        double z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)) + ring.z(slot) + face.getStepZ() * 0.5;
        long cell = BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int known = cells.get(cell);
        if (known >= 0) return known;
        BlockPos at = BlockPos.of(cell);
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, at), io.github.nullbrash.seepers.client.light.ClientDynamicLights.level(at));
        cells.put(cell, light);
        return light;
    }

    /** Takes every new card point as it lands (client.lidar.CardSync uploads them). */
    public interface Recorder {
        void record(long section, short x, short y, short z, int face, int material, int rgb);

        /** A section's card points changed other than by new ones (the data refresh dropped some). */
        void rewrite(long section);
    }

    private static @Nullable Recorder recorder;
    /** Points replayed from a card are not recorded back. */
    private boolean replaying;

    public static void setRecorder(@Nullable Recorder value) {
        recorder = value;
    }

    /** One point record from a card, at its exact place (section-relative, 1/1024 block). */
    public void addRecord(PointSource source, long sectionKey, short x, short y, short z, int face, int material, int rgb) {
        double ox = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey));
        double oy = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey));
        double oz = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey));
        replaying = true;
        try {
            add(source, ox + x / POSITION_SCALE, oy + y / POSITION_SCALE, oz + z / POSITION_SCALE, DIRECTIONS[face % 6], material, -1, rgb);
        } finally {
            replaying = false;
        }
    }

    /** All of one source's points go (another card went in, or none). */
    public void clearSource(PointSource source) {
        Ring ring = rings.remove(source);
        if (ring == null) return;
        for (int i = 0; i < ring.size; i++) {
            int slot = (ring.head + i) % ring.slots();
            Section section = sections.get(ring.section[slot]);
            if (section != null && section.points.get(ring.local[slot]) == (source.ordinal() << 24 | slot)) {
                section.points.remove(ring.local[slot]);
                section.dirty = true;
            }
        }
    }

    /** One source's points in a section go (a full card let the section go). */
    public void dropSection(long sectionKey, PointSource source) {
        Section section = sections.get(sectionKey);
        Ring ring = rings.get(source);
        if (section == null || ring == null) return;
        it.unimi.dsi.fastutil.ints.IntArrayList gone = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (Int2IntMap.Entry entry : section.points.int2IntEntrySet()) {
            if (entry.getIntValue() >>> 24 == source.ordinal()) gone.add(entry.getIntKey());
        }
        for (int i = 0; i < gone.size(); i++) section.points.remove(gone.getInt(i));
        ring.live -= gone.size();
        if (!gone.isEmpty()) section.dirty = true;
    }

    /** How many of a source's points a section holds. */
    public int liveIn(long sectionKey, PointSource source) {
        Section section = sections.get(sectionKey);
        if (section == null) return 0;
        int count = 0;
        for (int packed : section.points.values()) if (packed >>> 24 == source.ordinal()) count++;
        return count;
    }

    /** A section's points of one source as card records (lidar.CardRecords), for rewriting it compactly. */
    public byte[] exportRecords(long sectionKey, PointSource source) {
        Section section = sections.get(sectionKey);
        Ring ring = rings.get(source);
        if (section == null || ring == null) return new byte[0];
        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(section.points.size() * io.github.nullbrash.seepers.lidar.CardRecords.BYTES);
        for (int packed : section.points.values()) {
            if (packed >>> 24 != source.ordinal()) continue;
            int slot = packed & 0xFFFFFF;
            io.github.nullbrash.seepers.lidar.CardRecords.put(out, ring.x[slot], ring.y[slot], ring.z[slot], ring.local[slot] >>> 27,
                    ring.material[slot], ring.rgb[slot]);
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    /** A fresh cell memo for lightAt. */
    static Long2IntOpenHashMap lightCells() {
        Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
        cells.defaultReturnValue(-1);
        return cells;
    }

    int lightHash(Section section) {
        int hash = 1;
        Long2IntOpenHashMap cells = lightCells();
        for (int packed : section.points.values()) {
            hash = hash * 31 + lightAt(section.key, ringOf(packed), packed & 0xFFFFFF, cells);
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
        // A dense section is checked whole and runs the budget into debt that later ticks pay off: on average no more
        // than the budget per tick, however big the sections a scan has filled.
        lightBudget = Math.min(lightBudget + LIGHT_SAMPLES_PER_TICK, LIGHT_SAMPLES_PER_TICK);
        while (lightBudget > 0 && !lightQueue.isEmpty()) {
            Section section = sections.get(lightQueue.dequeueLong());
            if (section == null || section.dirty) continue;
            lightBudget -= section.points.size();
            if (dropBuried(section) || lightHash(section) != section.lightHash) section.dirty = true;
        }
    }

    /**
     * Points on a block face that another block now covers whole (sand fell on the felt floor) go before their time:
     * they share the plane with the new block's face and would flicker through it. Only points lying on the block
     * boundary — the top of a slab inside its block is never covered by the block above.
     */
    /**
     * Lidar data refresh (CLAUDE.md 5.3): drops this section's lidar points whose surface is gone — the shape behind
     * the point no longer reaches it (block broken, moved or replaced). Returns the points checked; the caller spends
     * a budget on them.
     */
    public int refreshSection(long key) {
        Section section = sections.get(key);
        if (section == null || section.points.isEmpty()) return 0;
        double ox = SectionPos.sectionToBlockCoord(SectionPos.x(key));
        double oy = SectionPos.sectionToBlockCoord(SectionPos.y(key));
        double oz = SectionPos.sectionToBlockCoord(SectionPos.z(key));
        BlockPos.MutableBlockPos behind = new BlockPos.MutableBlockPos();
        IntArrayList stale = null;
        int checked = 0;
        for (Int2IntMap.Entry entry : section.points.int2IntEntrySet()) {
            int packed = entry.getIntValue();
            if (packed >>> 24 != PointSource.LIDAR.ordinal()) continue;
            checked++;
            Ring ring = ringOf(packed);
            int slot = packed & 0xFFFFFF;
            Direction face = ring.face(slot);
            double x = ox + ring.x(slot);
            double y = oy + ring.y(slot);
            double z = oz + ring.z(slot);
            behind.set(Mth.floor(x - face.getStepX() * 0.02), Mth.floor(y - face.getStepY() * 0.02), Mth.floor(z - face.getStepZ() * 0.02));
            if (!level.isLoaded(behind) || onSurface(behind, x, y, z)) continue;
            if (stale == null) stale = new IntArrayList();
            stale.add(entry.getIntKey());
        }
        if (stale != null) {
            if (recorder != null) recorder.rewrite(key);
            for (int i = 0; i < stale.size(); i++) {
                int packed = section.points.remove(stale.getInt(i));
                ringOf(packed).live--;
            }
            section.dirty = true;
        }
        return checked;
    }

    /** Whether the block's drawn shape (or a fluid's surface) still reaches the point, give or take a hair. */
    private boolean onSurface(BlockPos pos, double x, double y, double z) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return true;
        for (AABB box : state.getShape(level, pos).toAabbs()) {
            if (box.move(pos).inflate(0.06).contains(x, y, z)) return true;
        }
        return false;
    }

    private boolean dropBuried(Section section) {
        IntArrayList buried = null;
        double ox = SectionPos.sectionToBlockCoord(SectionPos.x(section.key));
        double oy = SectionPos.sectionToBlockCoord(SectionPos.y(section.key));
        double oz = SectionPos.sectionToBlockCoord(SectionPos.z(section.key));
        BlockPos.MutableBlockPos front = new BlockPos.MutableBlockPos();
        // Whether a cell covers a face, by cell and face: points of one face share cells by the hundred.
        Long2IntOpenHashMap covers = lightCells();
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
            long cell = front.asLong() * 6 + face.ordinal();
            int covered = covers.get(cell);
            if (covered < 0) {
                BlockState state = level.getBlockState(front);
                covered = Block.isFaceFull(state.getCollisionShape(level, front), face.getOpposite()) ? 1 : 0;
                covers.put(cell, covered);
            }
            if (covered == 0) continue;
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
        if (ring != null && source.resizable() && capacity > 0) {
            // Same ring, new limit: the oldest live points beyond it go now.
            ring.limit = capacity;
            while (ring.live > ring.limit) removeSlot(source, ring, ring.head);
            return ring;
        }
        if (ring != null) {
            while (ring.size > 0) removeSlot(source, ring, ring.head); // the limit changed: start this share over
            rings.remove(source);
        }
        if (capacity <= 0) return null;
        ring = new Ring(capacity, source.resizable() ? PointSource.LIDAR_MAX_LIMIT * 2 : capacity * 2);
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
        int[] rgb = ring.rgb;
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
            ring.rgb[i] = rgb[from];
            ring.born[i] = born[from];
            if (alive[i]) sections.get(section[from]).points.put(local[from], source.ordinal() << 24 | i);
        }
        ring.head = 0;
    }

    /** What a lidar point tells: a texture pixel (analysis III) over a coloured point (an ore) over a plain one. */
    private static int rank(int material, int rgb) {
        return material == TouchMaterial.TEXEL.ordinal() ? 2 : rgb != 0 ? 1 : 0;
    }

    /**
     * A lidar point claims its texture pixel on the face (1/16 block, 2x2 keys): less telling lidar points there go,
     * and a more telling one already there keeps the pixel — the new point is dropped (false). Otherwise a scan with
     * a weaker analysis would bury the better data under its points.
     */
    private boolean takePixel(Section section, Ring ring, Direction face, int local, int rank) {
        int[] q = {local >>> 18 & 0x1FF, local >>> 9 & 0x1FF, local & 0x1FF};
        int perpendicular = face.getAxis().ordinal();
        int a = perpendicular == 0 ? 1 : 0;
        int b = perpendicular == 2 ? 1 : 2;
        boolean changed = false;
        // Hits on one face land a hair either side of its plane: look one key deeper and shallower too.
        for (int dp = -1; dp <= 1; dp++) {
            int depth = q[perpendicular] + dp;
            if (depth < 0 || depth >= 16 * QUANT) continue;
            for (int da = 0; da < 2; da++) {
                for (int db = 0; db < 2; db++) {
                    int[] k = q.clone();
                    k[perpendicular] = depth;
                    k[a] = q[a] & ~1 | da;
                    k[b] = q[b] & ~1 | db;
                    int key = face.ordinal() << 27 | k[0] << 18 | k[1] << 9 | k[2];
                    if (key == local) continue; // the same key is replaced by add() itself
                    int packed = section.points.get(key);
                    if (packed == -1 || ringOf(packed) != ring) continue;
                    int slot = packed & 0xFFFFFF;
                    int other = rank(ring.material[slot], ring.rgb[slot]);
                    if (other > rank) return false;
                    if (other < rank) {
                        section.points.remove(key);
                        ring.live--;
                        changed = true;
                    }
                }
            }
        }
        // add() would replace a more telling point at the very same key.
        int same = section.points.get(local);
        if (same != -1 && ringOf(same) == ring && rank(ring.material[same & 0xFFFFFF], ring.rgb[same & 0xFFFFFF]) > rank) return false;
        if (changed) section.dirty = true;
        return true;
    }

    private static int quant(float local) {
        return Mth.clamp((int) (local * QUANT), 0, 16 * QUANT - 1);
    }
}
