package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Zone 1 seams as islands (CLAUDE.md 4.8): the land is split into ragged cells (jittered grid, warped borders),
 * each moved up or down as a whole. Borders are slopes in some stretches and cliffs in others; some islands are
 * torn out — their top layer floats above the pit it left. Deterministic from the world seed.
 */
final class Islands {
    record Settings(int cellSize, int warp, float startFraction, int startMax, int fullMax,
                    int rampMin, int rampMax, float rampShareStart, float rampShareEnd,
                    float tornStartLevel, float tornShareFull, float tornShareFar,
                    int liftMin, int liftMax, int thicknessMin, int thicknessMax) {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(16, 512).fieldOf("cell_size").forGetter(Settings::cellSize),
                Codec.intRange(0, 64).fieldOf("warp").forGetter(Settings::warp),
                Codec.floatRange(0, 1).fieldOf("start_fraction").forGetter(Settings::startFraction),
                Codec.intRange(1, 48).fieldOf("start_max").forGetter(Settings::startMax),
                Codec.intRange(1, 48).fieldOf("full_max").forGetter(Settings::fullMax),
                Codec.intRange(1, 64).fieldOf("ramp_min").forGetter(Settings::rampMin),
                Codec.intRange(1, 64).fieldOf("ramp_max").forGetter(Settings::rampMax),
                Codec.floatRange(0, 1).fieldOf("ramp_share_start").forGetter(Settings::rampShareStart),
                Codec.floatRange(0, 1).fieldOf("ramp_share_end").forGetter(Settings::rampShareEnd),
                Codec.floatRange(0, 8).fieldOf("torn_start_level").forGetter(Settings::tornStartLevel),
                Codec.floatRange(0, 1).fieldOf("torn_share_full").forGetter(Settings::tornShareFull),
                Codec.floatRange(0, 1).fieldOf("torn_share_far").forGetter(Settings::tornShareFar),
                Codec.intRange(1, 128).fieldOf("lift_min").forGetter(Settings::liftMin),
                Codec.intRange(1, 128).fieldOf("lift_max").forGetter(Settings::liftMax),
                Codec.intRange(1, 64).fieldOf("thickness_min").forGetter(Settings::thicknessMin),
                Codec.intRange(1, 64).fieldOf("thickness_max").forGetter(Settings::thicknessMax))
                .apply(instance, Settings::new));
    }

    /** One island: its seed point, vertical shift, and whether its top is torn out. */
    record Cell(double x, double z, int offset, int lift, int thickness) {}

    /** What happens to one block column. lift/thickness 0 = not torn. */
    record Column(int offset, int lift, int thickness, double border) {}

    /** Torn slabs thin out toward the island edge over this many blocks. */
    private static final double TORN_TAPER = 12;

    private final Settings settings;
    private final ToDoubleBiFunction<Integer, Integer> distortion;
    private final PositionalRandomFactory cells;
    private final SimplexNoise warpX;
    private final SimplexNoise warpZ;
    private final SimplexNoise rampShare;
    private final SimplexNoise rampWidth;
    private final SimplexNoise roughness;

    Islands(Settings settings, RandomState random, ToDoubleBiFunction<Integer, Integer> distortion) {
        this.settings = settings;
        this.distortion = distortion;
        this.cells = random.getOrCreateRandomFactory(Seepers.id("islands"));
        PositionalRandomFactory noises = random.getOrCreateRandomFactory(Seepers.id("island_noise"));
        this.warpX = new SimplexNoise(noises.at(1, 0, 0));
        this.warpZ = new SimplexNoise(noises.at(2, 0, 0));
        this.rampShare = new SimplexNoise(noises.at(3, 0, 0));
        this.rampWidth = new SimplexNoise(noises.at(4, 0, 0));
        this.roughness = new SimplexNoise(noises.at(5, 0, 0));
    }

    /** Cells are cheap but asked many times per chunk: callers keep a small cache per chunk. */
    Cell cell(int i, int j, Long2ObjectOpenHashMap<Cell> cache) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Cell cell = cache.get(key);
        if (cell == null) {
            cell = makeCell(i, j);
            cache.put(key, cell);
        }
        return cell;
    }

    private Cell makeCell(int i, int j) {
        RandomSource r = cells.at(i, 0, j);
        int size = settings.cellSize();
        double x = (double) i * size + r.nextInt(size);
        double z = (double) j * size + r.nextInt(size);
        double level = distortion.applyAsDouble((int) x, (int) z);
        int offset = 0;
        int lift = 0;
        int thickness = 0;
        if (level >= 1) {
            float t = (float) Mth.clamp(level - 1, 0, 1);
            if (r.nextFloat() < Mth.lerp(t, settings.startFraction(), 1.0F)) {
                int max = Math.max(1, Math.round(Mth.lerp(t, settings.startMax(), settings.fullMax())));
                int shift = 1 + r.nextInt(max);
                offset = r.nextBoolean() ? shift : -shift;
            }
            if (r.nextFloat() < tornShare(level)) {
                lift = settings.liftMin() + r.nextInt(Math.max(1, settings.liftMax() - settings.liftMin() + 1));
                thickness = settings.thicknessMin() + r.nextInt(Math.max(1, settings.thicknessMax() - settings.thicknessMin() + 1));
            }
        }
        return new Cell(x, z, offset, lift, thickness);
    }

    /** None before the torn start level; the "full" share by the end of zone 1; more further out. */
    private float tornShare(double level) {
        float start = settings.tornStartLevel();
        if (level < start) return 0;
        if (level < 2) return settings.tornShareFull() * (float) ((level - start) / Math.max(0.01, 2 - start));
        return Mth.lerp((float) Mth.clamp(level - 2, 0, 1), settings.tornShareFull(), settings.tornShareFar());
    }

    Column column(int x, int z, Long2ObjectOpenHashMap<Cell> cache) {
        int size = settings.cellSize();
        // Warped lookup: island borders come out ragged instead of straight Voronoi edges.
        double wx = x + settings.warp() * warpX.getValue(x / 48.0, z / 48.0);
        double wz = z + settings.warp() * warpZ.getValue(x / 48.0, z / 48.0);
        int ci = Mth.floor(wx / size);
        int cj = Mth.floor(wz / size);
        Cell first = null;
        Cell second = null;
        double d1 = Double.MAX_VALUE;
        double d2 = Double.MAX_VALUE;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                Cell cell = cell(i, j, cache);
                double d = Mth.square(cell.x() - wx) + Mth.square(cell.z() - wz);
                if (d < d1) {
                    second = first;
                    d2 = d1;
                    first = cell;
                    d1 = d;
                } else if (d < d2) {
                    second = cell;
                    d2 = d;
                }
            }
        }
        // Exact distance to the bisector between the two nearest seed points.
        double gap = Math.sqrt(Mth.square(second.x() - first.x()) + Mth.square(second.z() - first.z()));
        double border = (d2 - d1) / (2 * Math.max(1e-6, gap));

        double level = distortion.applyAsDouble(x, z);
        float share = Mth.lerp((float) Mth.clamp(level - 1, 0, 1), settings.rampShareStart(), settings.rampShareEnd());
        double offset = first.offset();
        // Along one border a slope gives way to a cliff and back: the share of slopes drops through zone 1.
        if ((rampShare.getValue(x / 64.0, z / 64.0) + 1) / 2 < share) {
            double width = Mth.lerp((rampWidth.getValue(x / 32.0, z / 32.0) + 1) / 2, settings.rampMin(), settings.rampMax());
            if (border < width / 2) {
                double t = Mth.smoothstep(0.5 + border / width);
                offset = Mth.lerp(t, second.offset(), first.offset());
            }
        }
        int thickness = 0;
        if (first.lift() > 0) {
            double taper = Math.sqrt(Mth.clamp(border / TORN_TAPER, 0, 1));
            thickness = (int) Math.round(first.thickness() * taper + 3 * roughness.getValue(x / 6.0, z / 6.0));
        }
        return thickness > 0
                ? new Column((int) Math.round(offset), first.lift(), thickness, border)
                : new Column((int) Math.round(offset), 0, 0, border);
    }
}
