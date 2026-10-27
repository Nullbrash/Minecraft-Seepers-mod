package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Anomalous rock in generation (CLAUDE.md 4.8): barrier walls (rings on the zone 1→2 and 2→3 borders with
 * breaches, loose segments in zones 2–3), cladding on cliff faces between islands (rarely a "flat mountain" — a
 * thin wall with a mountain's silhouette), and geodes underground. Walls and cladding: column pass after the
 * islands; geodes: after decoration, so ores and caves inside are rock too.
 */
final class AnomalyRock {
    record Walls(int ringThicknessMin, int ringThicknessMax, int heightMin, int heightMax, int breachEveryMin,
                 int breachEveryMax, int breachWidthMin, int breachWidthMax, int segmentCell, float segmentChance,
                 int segmentLengthMin, int segmentLengthMax) {
        static final Codec<Walls> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("ring_thickness_min").forGetter(Walls::ringThicknessMin),
                Codec.intRange(1, 64).fieldOf("ring_thickness_max").forGetter(Walls::ringThicknessMax),
                Codec.intRange(0, 256).fieldOf("height_min").forGetter(Walls::heightMin),
                Codec.intRange(0, 256).fieldOf("height_max").forGetter(Walls::heightMax),
                Codec.intRange(16, 4096).fieldOf("breach_every_min").forGetter(Walls::breachEveryMin),
                Codec.intRange(16, 4096).fieldOf("breach_every_max").forGetter(Walls::breachEveryMax),
                Codec.intRange(1, 64).fieldOf("breach_width_min").forGetter(Walls::breachWidthMin),
                Codec.intRange(1, 64).fieldOf("breach_width_max").forGetter(Walls::breachWidthMax),
                Codec.intRange(64, 8192).fieldOf("segment_cell").forGetter(Walls::segmentCell),
                Codec.floatRange(0, 1).fieldOf("segment_chance").forGetter(Walls::segmentChance),
                Codec.intRange(8, 4096).fieldOf("segment_length_min").forGetter(Walls::segmentLengthMin),
                Codec.intRange(8, 4096).fieldOf("segment_length_max").forGetter(Walls::segmentLengthMax))
                .apply(instance, Walls::new));
    }

    record Cliffs(int minDrop, int layer, float mountainShare, int mountainHeightMin, int mountainHeightMax) {
        static final Codec<Cliffs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 256).fieldOf("min_drop").forGetter(Cliffs::minDrop),
                Codec.intRange(1, 16).fieldOf("layer").forGetter(Cliffs::layer),
                Codec.floatRange(0, 1).fieldOf("mountain_share").forGetter(Cliffs::mountainShare),
                Codec.intRange(1, 256).fieldOf("mountain_height_min").forGetter(Cliffs::mountainHeightMin),
                Codec.intRange(1, 256).fieldOf("mountain_height_max").forGetter(Cliffs::mountainHeightMax))
                .apply(instance, Cliffs::new));
    }

    record Geodes(int cell, float chunksStart, float chunksFar, int sizeMin, int sizeMax, int sizeFar, int minY, int maxY) {
        static final Codec<Geodes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(16, 512).fieldOf("cell").forGetter(Geodes::cell),
                Codec.floatRange(1, 100_000).fieldOf("chunks_per_geode_start").forGetter(Geodes::chunksStart),
                Codec.floatRange(1, 100_000).fieldOf("chunks_per_geode_far").forGetter(Geodes::chunksFar),
                Codec.intRange(2, 96).fieldOf("size_min").forGetter(Geodes::sizeMin),
                Codec.intRange(2, 96).fieldOf("size_max").forGetter(Geodes::sizeMax),
                Codec.intRange(2, 96).fieldOf("size_far").forGetter(Geodes::sizeFar),
                Codec.intRange(-2048, 2048).fieldOf("min_y").forGetter(Geodes::minY),
                Codec.intRange(-2048, 2048).fieldOf("max_y").forGetter(Geodes::maxY))
                .apply(instance, Geodes::new));
    }

    record Settings(Walls walls, Cliffs cliffs, Geodes geodes) {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Walls.CODEC.fieldOf("walls").forGetter(Settings::walls),
                Cliffs.CODEC.fieldOf("cliffs").forGetter(Settings::cliffs),
                Geodes.CODEC.fieldOf("geodes").forGetter(Settings::geodes))
                .apply(instance, Settings::new));
    }

    /** What a column gets: nothing, a barrier wall (height above ground), or cliff cladding (with a mountain above). */
    record Column(boolean wall, int wallHeight, boolean cladding, int mountain) {
        static final Column NONE = new Column(false, 0, false, 0);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final Walls walls;
    private final Cliffs cliffs;
    private final Geodes geodes;
    private final ToDoubleBiFunction<Integer, Integer> distortion;
    private final int ring1;
    private final int ring2;
    private final PositionalRandomFactory randoms;
    private final SimplexNoise wobble;
    private final SimplexNoise heights;
    private final SimplexNoise shape;

    AnomalyRock(Settings settings, RandomState random, ToDoubleBiFunction<Integer, Integer> distortion, Distortion.Zones zones) {
        this.walls = settings.walls();
        this.cliffs = settings.cliffs();
        this.geodes = settings.geodes();
        this.distortion = distortion;
        this.ring1 = zones.zone2();
        this.ring2 = zones.zone3();
        this.randoms = random.getOrCreateRandomFactory(Seepers.id("anomaly_rock"));
        PositionalRandomFactory noises = random.getOrCreateRandomFactory(Seepers.id("anomaly_rock_noise"));
        this.wobble = new SimplexNoise(noises.at(1, 0, 0));
        this.heights = new SimplexNoise(noises.at(2, 0, 0));
        this.shape = new SimplexNoise(noises.at(3, 0, 0));
    }

    static BlockState rock(AnomalyRockBlock.Kind kind) {
        return SeepersBlocks.ANOMALY_ROCK.get().defaultBlockState().setValue(AnomalyRockBlock.KIND, kind);
    }

    Column column(int x, int z, Islands.Column island) {
        if (inWall(x, z)) {
            int height = (int) Math.round(Mth.lerp((heights.getValue(x / 90.0, z / 90.0) + 1) / 2, walls.heightMin(), walls.heightMax()));
            return new Column(true, height, false, 0);
        }
        // Cladding: the high side of a sheer island border (no slope here), the last few blocks before the drop.
        if (island.lift() == 0 && !island.ramp() && island.border() < cliffs.layer()
                && island.offset() - island.neighbourOffset() >= cliffs.minDrop()) {
            int mountain = 0;
            RandomSource r = randoms.at((int) island.pair(), 7, (int) (island.pair() >>> 32));
            if (r.nextFloat() < cliffs.mountainShare()) {
                int peak = cliffs.mountainHeightMin() + r.nextInt(Math.max(1, cliffs.mountainHeightMax() - cliffs.mountainHeightMin() + 1));
                // A mountain's outline cut out flat: broad swell with a jagged edge.
                double swell = (heights.getValue(x / 48.0 + 300, z / 48.0) + 1) / 2;
                double jag = 0.15 * shape.getValue(x / 6.0, 0, z / 6.0);
                mountain = (int) Math.max(0, Math.round(peak * Math.pow(swell, 1.6) + peak * jag));
            }
            return new Column(false, 0, true, mountain);
        }
        return Column.NONE;
    }

    private boolean inWall(int x, int z) {
        double level = distortion.applyAsDouble(x, z);
        if (level < 1.8) return false;
        double r = Math.sqrt((double) x * x + (double) z * z);
        double angle = Math.atan2(z, x);
        for (int ring = 0; ring < 2; ring++) {
            int radius = ring == 0 ? ring1 : ring2;
            double drift = 10 * wobble.getValue(angle * radius / 120.0, ring * 50.0);
            double thickness = Mth.lerp((wobble.getValue(angle * radius / 40.0, ring * 50.0 + 20) + 1) / 2,
                    walls.ringThicknessMin(), walls.ringThicknessMax());
            if (Math.abs(r - radius - drift) < thickness / 2 && !breach(ring, angle * radius)) return true;
        }
        return level >= 2 && inSegment(x, z);
    }

    /** Breaches along a ring: slots of random length, one gap of random width in each. */
    private boolean breach(int ring, double arc) {
        int slotSize = (walls.breachEveryMin() + walls.breachEveryMax()) / 2;
        int slot = Mth.floor(arc / slotSize);
        RandomSource r = randoms.at(slot, ring, 11);
        int every = walls.breachEveryMin() + r.nextInt(Math.max(1, walls.breachEveryMax() - walls.breachEveryMin() + 1));
        int width = walls.breachWidthMin() + r.nextInt(Math.max(1, walls.breachWidthMax() - walls.breachWidthMin() + 1));
        double at = r.nextDouble() * Math.max(1, every - width);
        double into = arc - slot * (double) slotSize;
        return into >= at && into < at + width;
    }

    private boolean inSegment(int x, int z) {
        int cell = walls.segmentCell();
        int ci = Mth.floorDiv(x, cell);
        int cj = Mth.floorDiv(z, cell);
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                RandomSource r = randoms.at(i, 3, j);
                if (r.nextFloat() >= walls.segmentChance()) continue;
                double cx = (i + r.nextDouble()) * cell;
                double cz = (j + r.nextDouble()) * cell;
                double angle = r.nextDouble() * Math.PI;
                double half = (walls.segmentLengthMin() + r.nextInt(Math.max(1, walls.segmentLengthMax() - walls.segmentLengthMin() + 1))) / 2.0;
                double thickness = Mth.lerp(r.nextDouble(), walls.ringThicknessMin(), walls.ringThicknessMax());
                double dx = x - cx;
                double dz = z - cz;
                double along = Mth.clamp(dx * Math.cos(angle) + dz * Math.sin(angle), -half, half);
                double px = cx + along * Math.cos(angle);
                double pz = cz + along * Math.sin(angle);
                double wob = 2 * wobble.getValue(x / 20.0, z / 20.0);
                if (Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz)) + wob < thickness / 2) return true;
            }
        }
        return false;
    }

    /** column[0] is world y = minY. Walls stand from the bottom of the ground; cladding replaces the cliff face. */
    void apply(BlockState[] column, Column shape) {
        if (shape == Column.NONE) return;
        int top = column.length - 1;
        while (top >= 0 && column[top].isAir()) top--;
        int bottom = 0;
        while (bottom < column.length && column[bottom].isAir()) bottom++;
        if (top < 0 || bottom > top) return;
        BlockState rock = rock(AnomalyRockBlock.Kind.WALL);
        int peak = Math.min(column.length - 1, top + (shape.wall() ? shape.wallHeight() : shape.mountain()));
        // Bedrock stays: rock under it would let a blast open the floor of the world.
        for (int i = bottom; i <= peak; i++) if (!column[i].is(Blocks.BEDROCK)) column[i] = rock;
    }

    /** A geode: center and radius. */
    record Geode(int x, int y, int z, double radius) {}

    /** Geodes whose cell lies near the box (deterministic from the seed). */
    java.util.List<Geode> geodesNear(int minX, int minZ, int maxX, int maxZ) {
        int size = geodes.cell();
        int reach = geodes.sizeFar() / 2 + 4;
        java.util.List<Geode> out = new java.util.ArrayList<>();
        for (int i = Mth.floorDiv(minX - reach, size); i <= Mth.floorDiv(maxX + reach, size); i++) {
            for (int j = Mth.floorDiv(minZ - reach, size); j <= Mth.floorDiv(maxZ + reach, size); j++) {
                double level = distortion.applyAsDouble(i * size + size / 2, j * size + size / 2);
                if (level < 1) continue;
                double t = Mth.clamp((level - 1) / 2, 0, 1);
                double expected = (size / 16.0) * (size / 16.0) / Mth.lerp(t, geodes.chunksStart(), geodes.chunksFar());
                RandomSource r = randoms.at(i, 5, j);
                int count = (int) expected + (r.nextDouble() < expected - (int) expected ? 1 : 0);
                for (int n = 0; n < count; n++) {
                    int maxSize = (int) Math.round(Mth.lerp(t, geodes.sizeMax(), geodes.sizeFar()));
                    int diameter = geodes.sizeMin() + r.nextInt(Math.max(1, maxSize - geodes.sizeMin() + 1));
                    int gx = i * size + r.nextInt(size);
                    int gz = j * size + r.nextInt(size);
                    int gy = geodes.minY() + r.nextInt(Math.max(1, geodes.maxY() - geodes.minY() + 1));
                    out.add(new Geode(gx, gy, gz, diameter / 2.0));
                }
            }
        }
        return out;
    }

    /** Geodes: solid lumps of rock, carved in after decoration (so nothing inside stays). */
    void placeGeodes(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockState rock = rock(AnomalyRockBlock.Kind.GEODE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Geode g : geodesNear(minX, minZ, minX + 15, minZ + 15)) {
            int reachXZ = (int) Math.ceil(g.radius() * 1.3);
            for (int x = Math.max(minX, g.x() - reachXZ); x <= Math.min(minX + 15, g.x() + reachXZ); x++) {
                for (int z = Math.max(minZ, g.z() - reachXZ); z <= Math.min(minZ + 15, g.z() + reachXZ); z++) {
                    for (int y = Math.max(chunk.getMinBuildHeight(), g.y() - reachXZ); y <= Math.min(chunk.getMaxBuildHeight() - 1, g.y() + reachXZ); y++) {
                        double d = Math.sqrt((x - g.x()) * (x - g.x()) + (y - g.y()) * (y - g.y()) * 1.4 + (z - g.z()) * (z - g.z()));
                        if (d < g.radius() * (1 + 0.25 * shape.getValue(x / 5.0, y / 5.0, z / 5.0))) {
                            pos.set(x, y, z);
                            if (!chunk.getBlockState(pos).is(Blocks.BEDROCK)) chunk.setBlockState(pos, rock, false);
                        }
                    }
                }
            }
        }
    }
}
