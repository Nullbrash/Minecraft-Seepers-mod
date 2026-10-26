package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import java.util.EnumSet;

/**
 * The overworld generator (CLAUDE.md 4.8): vanilla noise terrain, then distortion on top. Zone 1 "seams": every
 * chunk is moved up or down as a whole — bedrock, caves and water with it — right after its surface is built,
 * so carvers and features already work on the moved terrain. Structures ask the moved height too.
 */
public final class DistortedChunkGenerator extends NoiseBasedChunkGenerator {
    /** Seams, data-driven: at the start of zone 1 a fraction of chunks moves by up to startMax; by zone 2, all by up to fullMax. */
    public record Seams(float startFraction, int startMax, int fullMax) {
        public static final Codec<Seams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.floatRange(0, 1).fieldOf("start_fraction").forGetter(Seams::startFraction),
                Codec.intRange(1, 48).fieldOf("start_max").forGetter(Seams::startMax),
                Codec.intRange(1, 48).fieldOf("full_max").forGetter(Seams::fullMax))
                .apply(instance, Seams::new));
    }

    public static final MapCodec<DistortedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("distortion").forGetter(generator -> generator.distortion),
            Seams.CODEC.fieldOf("seams").forGetter(generator -> generator.seams))
            .apply(instance, DistortedChunkGenerator::new));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final DensityFunction distortion;
    private final Seams seams;

    public DistortedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, DensityFunction distortion, Seams seams) {
        super(biomeSource, settings);
        this.distortion = distortion;
        this.seams = seams;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public double distortionAt(int x, int z) {
        return distortion.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /** Vertical shift of a whole chunk; deterministic from the world seed and the chunk position. */
    public int seamOffset(RandomState random, int chunkX, int chunkZ) {
        double level = distortionAt(chunkX * 16 + 8, chunkZ * 16 + 8);
        if (level < 1) return 0;
        float t = (float) Mth.clamp(level - 1, 0, 1);
        RandomSource source = random.getOrCreateRandomFactory(Seepers.id("seams")).at(chunkX, 0, chunkZ);
        if (source.nextFloat() >= Mth.lerp(t, seams.startFraction(), 1.0F)) return 0;
        int max = Math.max(1, Math.round(Mth.lerp(t, seams.startMax(), seams.fullMax())));
        int size = 1 + source.nextInt(max);
        return source.nextBoolean() ? size : -size;
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        super.buildSurface(level, structureManager, random, chunk);
        int offset = seamOffset(random, chunk.getPos().x, chunk.getPos().z);
        if (offset != 0) shift(chunk, offset);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int height = super.getBaseHeight(x, z, type, level, random);
        return height <= level.getMinBuildHeight() ? height : height + seamOffset(random, x >> 4, z >> 4);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = super.getBaseColumn(x, z, height, random);
        int offset = seamOffset(random, x >> 4, z >> 4);
        if (offset == 0) return column;
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = column.getBlock(height.getMinBuildHeight() + i - offset);
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    /** Moves every block of the chunk by offset; what comes in from outside the world is air. */
    private static void shift(ChunkAccess chunk, int offset) {
        int min = chunk.getMinBuildHeight();
        int max = chunk.getMaxBuildHeight();
        BlockState[] column = new BlockState[max - min];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = min; y < max; y++) column[y - min] = get(chunk, x, y, z);
                for (int y = min; y < max; y++) {
                    int from = y - offset;
                    BlockState state = from >= min && from < max ? column[from - min] : AIR;
                    if (state != column[y - min]) set(chunk, x, y, z, state);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private static BlockState get(ChunkAccess chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        return section.hasOnlyAir() ? AIR : section.getBlockState(x, y & 15, z);
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }
}
