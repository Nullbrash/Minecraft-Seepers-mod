package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Zone 3 "Far Lands" (CLAUDE.md 4.8): rows of layered lattice walls standing on the land, each region with its own
 * direction and spacing; a ground-level passage through every wall now and then; deep voids between the walls.
 * Grows from the start of zone 3 to one zone width further. Works on block columns after the islands.
 */
final class FarLands {
    record Settings(int regionSize, int spacingMin, int spacingMax, int thicknessMin, int thicknessMax, int heightMax,
                    int layerPeriod, int layerSolid, int ribPeriod, int ribWidth, int passageEvery, int passageWidth,
                    int passageHeight, float voidShare, int voidBottom) {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(64, 4096).fieldOf("region_size").forGetter(Settings::regionSize),
                Codec.intRange(8, 512).fieldOf("spacing_min").forGetter(Settings::spacingMin),
                Codec.intRange(8, 512).fieldOf("spacing_max").forGetter(Settings::spacingMax),
                Codec.intRange(1, 64).fieldOf("thickness_min").forGetter(Settings::thicknessMin),
                Codec.intRange(1, 64).fieldOf("thickness_max").forGetter(Settings::thicknessMax),
                Codec.intRange(1, 256).fieldOf("height_max").forGetter(Settings::heightMax),
                Codec.intRange(2, 32).fieldOf("layer_period").forGetter(Settings::layerPeriod),
                Codec.intRange(1, 32).fieldOf("layer_solid").forGetter(Settings::layerSolid),
                Codec.intRange(2, 64).fieldOf("rib_period").forGetter(Settings::ribPeriod),
                Codec.intRange(1, 32).fieldOf("rib_width").forGetter(Settings::ribWidth),
                Codec.intRange(8, 1024).fieldOf("passage_every").forGetter(Settings::passageEvery),
                Codec.intRange(1, 32).fieldOf("passage_width").forGetter(Settings::passageWidth),
                Codec.intRange(2, 32).fieldOf("passage_height").forGetter(Settings::passageHeight),
                Codec.floatRange(0, 1).fieldOf("void_share").forGetter(Settings::voidShare),
                Codec.intRange(-2048, 2048).fieldOf("void_bottom").forGetter(Settings::voidBottom))
                .apply(instance, Settings::new));
    }

    /** What zone 3 does to a column: a wall (with its lattice phase) or a void, or nothing. */
    record Column(boolean wall, int height, double along, int layerPhase, boolean passage, boolean empty) {
        static final Column NONE = new Column(false, 0, 0, 0, false, false);
    }

    private record Region(double angle, int spacing, int thickness, double phase, int layerPhase) {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    private final Settings settings;
    private final ToDoubleBiFunction<Integer, Integer> distortion;
    private final PositionalRandomFactory regions;
    private final SimplexNoise presence;
    private final SimplexNoise wobble;
    private final SimplexNoise heights;
    private final SimplexNoise voids;

    FarLands(Settings settings, RandomState random, ToDoubleBiFunction<Integer, Integer> distortion) {
        this.settings = settings;
        this.distortion = distortion;
        this.regions = random.getOrCreateRandomFactory(Seepers.id("far_lands"));
        PositionalRandomFactory noises = random.getOrCreateRandomFactory(Seepers.id("far_lands_noise"));
        this.presence = new SimplexNoise(noises.at(1, 0, 0));
        this.wobble = new SimplexNoise(noises.at(2, 0, 0));
        this.heights = new SimplexNoise(noises.at(3, 0, 0));
        this.voids = new SimplexNoise(noises.at(4, 0, 0));
    }

    private Region region(int i, int j) {
        RandomSource r = regions.at(i, 0, j);
        double angle = r.nextDouble() * Math.PI;
        int spacing = settings.spacingMin() + r.nextInt(Math.max(1, settings.spacingMax() - settings.spacingMin() + 1));
        int thickness = settings.thicknessMin() + r.nextInt(Math.max(1, settings.thicknessMax() - settings.thicknessMin() + 1));
        return new Region(angle, spacing, thickness, r.nextDouble() * spacing, r.nextInt(settings.layerPeriod()));
    }

    Column column(int x, int z) {
        double level = distortion.applyAsDouble(x, z);
        if (level < 3) return Column.NONE;
        double t = Mth.clamp(level - 3, 0, 1);
        int size = settings.regionSize();
        // Ragged region borders, so wall rows do not end on a grid.
        double rx = x + size * 0.2 * wobble.getValue(x / 300.0, z / 300.0);
        double rz = z + size * 0.2 * wobble.getValue(z / 300.0 + 50, x / 300.0);
        Region region = region(Mth.floor(rx / size), Mth.floor(rz / size));
        double cos = Math.cos(region.angle());
        double sin = Math.sin(region.angle());
        double across = x * cos + z * sin + 6 * wobble.getValue(x / 40.0, z / 40.0);
        double along = -x * sin + z * cos;
        double offset = Mth.positiveModulo(across - region.phase(), region.spacing());
        double fromWall = Math.min(offset, region.spacing() - offset);
        // Walls thin in where the zone starts: patchy at first, everywhere by the end.
        boolean present = (presence.getValue(x / 256.0, z / 256.0) + 1) / 2 < 0.25 + 0.75 * t;
        if (present && fromWall < region.thickness() / 2.0) {
            int height = (int) Math.round(settings.heightMax() * (0.35 + 0.65 * t) * (0.6 + 0.4 * (heights.getValue(along / 80.0, across / 80.0) + 1) / 2));
            boolean passage = Mth.positiveModulo(along + 1000 * region.angle(), settings.passageEvery()) < settings.passageWidth();
            return new Column(true, height, along, region.layerPhase(), passage, false);
        }
        boolean empty = (voids.getValue(x / 64.0, z / 64.0) + 1) / 2 > 1 - settings.voidShare() * t && fromWall > region.thickness();
        return empty ? new Column(false, 0, along, 0, false, true) : Column.NONE;
    }

    /** column[0] is world y = minY. */
    void apply(BlockState[] column, int minY, Column shape) {
        if (shape == Column.NONE) return;
        int top = column.length - 1;
        while (top >= 0 && column[top].isAir()) top--;
        if (top < 0) return;
        if (shape.empty()) {
            // A deep void: the land is simply not there down to the void bottom.
            for (int i = Math.max(0, settings.voidBottom() - minY); i <= top; i++) column[i] = AIR;
            return;
        }
        int ground = top + 1;
        int peak = Math.min(column.length - 1, ground + shape.height());
        int rib = Mth.positiveModulo((int) Math.floor(shape.along()), settings.ribPeriod());
        for (int i = ground; i <= peak; i++) {
            int y = minY + i;
            if (shape.passage() && i < ground + settings.passageHeight()) continue;
            boolean layer = Mth.positiveModulo(y + shape.layerPhase(), settings.layerPeriod()) < settings.layerSolid();
            if (layer || rib < settings.ribWidth()) column[i] = y < 0 ? DEEPSLATE : STONE;
        }
    }
}
