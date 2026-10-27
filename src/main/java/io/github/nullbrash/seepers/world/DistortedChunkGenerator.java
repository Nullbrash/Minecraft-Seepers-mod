package io.github.nullbrash.seepers.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
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

/**
 * The overworld generator (CLAUDE.md 4.8): vanilla noise terrain, then distortion on top. Zone 1: islands moved up
 * or down (column by column, so borders can slope), torn-out island tops floating over their pits — both right
 * after the surface is built, so carvers and features work on the moved land; lost pockets after decoration.
 * Structures ask the moved height too.
 */
public final class DistortedChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<DistortedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.originalSettings),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("distortion").forGetter(generator -> generator.distortion),
            // Optional: worlds made before a feature existed keep generating as they did (no warp, no Far Lands).
            DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("warp").forGetter(generator -> generator.warp),
            Islands.Settings.CODEC.fieldOf("islands").forGetter(generator -> generator.islands),
            LostPockets.Settings.CODEC.fieldOf("lost_pockets").forGetter(generator -> generator.pockets),
            FarLands.Settings.CODEC.optionalFieldOf("far_lands").forGetter(generator -> generator.farLands))
            .apply(instance, DistortedChunkGenerator::new));

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Noises that read the column position; zone 2 warps where they sample (see {@link CoordinateWarp}). */
    private static final Set<ResourceLocation> SAMPLERS = Set.of(
            ResourceLocation.withDefaultNamespace("noise"), ResourceLocation.withDefaultNamespace("shifted_noise"),
            ResourceLocation.withDefaultNamespace("shift_a"), ResourceLocation.withDefaultNamespace("shift_b"),
            ResourceLocation.withDefaultNamespace("shift"), ResourceLocation.withDefaultNamespace("weird_scaled_sampler"),
            ResourceLocation.withDefaultNamespace("old_blended_noise"));

    private final Holder<NoiseGeneratorSettings> originalSettings;
    private final Holder<NoiseGeneratorSettings> warpedSettings;
    private final DensityFunction distortion;
    private final Optional<DensityFunction> warp;
    private final Islands.Settings islands;
    private final LostPockets.Settings pockets;
    private final Optional<FarLands.Settings> farLands;
    /** Noises depend on the world seed: one set per RandomState (one per world). */
    private final Map<RandomState, Islands> islandFields = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<RandomState, LostPockets> pocketFields = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<RandomState, FarLands> farLandFields = Collections.synchronizedMap(new WeakHashMap<>());

    public DistortedChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, DensityFunction distortion,
                                   Optional<DensityFunction> warp, Islands.Settings islands, LostPockets.Settings pockets,
                                   Optional<FarLands.Settings> farLands) {
        super(biomeSource, settings);
        this.originalSettings = settings;
        this.distortion = distortion;
        this.warp = warp;
        // Terrain, caves, aquifers and biome climate all come from the router: warp every sampler in it, in
        // memory only (the world saves the original settings).
        NoiseGeneratorSettings s = settings.value();
        NoiseRouter router = s.noiseRouter().mapAll(function -> {
            if (function instanceof DensityFunctions.HolderHolder) return function; // a reference: has no type of its own
            ResourceLocation type = BuiltInRegistries.DENSITY_FUNCTION_TYPE.getKey(function.codec().codec());
            return type != null && SAMPLERS.contains(type) && warp.isPresent() ? new CoordinateWarp.Transformed(function, warp.get()) : function;
        });
        this.warpedSettings = Holder.direct(new NoiseGeneratorSettings(s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), router,
                s.surfaceRule(), s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifersEnabled(), s.oreVeinsEnabled(),
                s.useLegacyRandomSource()));
        this.islands = islands;
        this.pockets = pockets;
        this.farLands = farLands;
    }

    /** The world's random state (noises, climate) is built from this: the warped router. */
    @Override
    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return warpedSettings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public double distortionAt(int x, int z) {
        return distortion.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    private Islands islands(RandomState random) {
        return islandFields.computeIfAbsent(random, r -> new Islands(islands, r, this::distortionAt));
    }

    private @Nullable FarLands farLands(RandomState random) {
        return farLands.map(settings -> farLandFields.computeIfAbsent(random, r -> new FarLands(settings, r, this::distortionAt))).orElse(null);
    }

    private LostPockets pockets(RandomState random) {
        return pocketFields.computeIfAbsent(random, r -> new LostPockets(pockets, r, this::distortionAt));
    }

    /** For the debug command: what the generator does to the column at x, z. */
    public String describe(RandomState random, int x, int z) {
        Islands.Column column = islands(random).column(x, z, new Long2ObjectOpenHashMap<>());
        LostPockets.Pocket nearest = pockets(random).pocketsNear(x - 128, z - 128, x + 128, z + 128).stream()
                .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x() - x, p.z() - z))).orElse(null);
        String warped = "none";
        if (warp.orElse(null) instanceof DensityFunctions.HolderHolder holder && holder.function().value() instanceof CoordinateWarp w
                && w.noise().noiseData().unwrapKey().isPresent()) {
            CoordinateWarp seeded = new CoordinateWarp(w.distortion(), new DensityFunction.NoiseHolder(w.noise().noiseData(),
                    random.getOrCreateNoise(w.noise().noiseData().unwrapKey().get())), w.settings());
            CoordinateWarp.Result r = seeded.lookup(x, z);
            warped = String.format(java.util.Locale.ROOT, "from %.0f %.0f (stretch x%.2f at %.0f°, repeat %d %d)", r.x(), r.z(), r.stretch(), r.angle(), r.repeatDx(), r.repeatDz());
        }
        FarLands.Column far = farLands(random) == null ? FarLands.Column.NONE : farLands(random).column(x, z);
        String farText = far.wall() ? "wall " + far.height() + (far.passage() ? " passage" : "") : far.empty() ? "void" : "none";
        return String.format(java.util.Locale.ROOT, "shift %d, border %.1f, torn %s, nearest lost pocket %s, terrain %s, far lands %s",
                column.offset(), column.border(), column.lift() > 0 ? "lift " + column.lift() + " thick " + column.thickness() : "no",
                nearest == null ? "none" : nearest.x() + " " + nearest.y() + " " + nearest.z() + " r " + nearest.rx() + "/" + nearest.ry() + "/" + nearest.rz(),
                warped, farText);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        super.buildSurface(level, structureManager, random, chunk);
        Islands field = islands(random);
        FarLands far = farLands(random);
        Long2ObjectOpenHashMap<Islands.Cell> cache = new Long2ObjectOpenHashMap<>();
        int min = chunk.getMinBuildHeight();
        int max = chunk.getMaxBuildHeight();
        BlockState[] column = new BlockState[max - min];
        BlockState[] moved = new BlockState[max - min];
        boolean changed = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunk.getPos().getMinBlockX() + x;
                int wz = chunk.getPos().getMinBlockZ() + z;
                Islands.Column shape = field.column(wx, wz, cache);
                FarLands.Column farShape = far == null ? FarLands.Column.NONE : far.column(wx, wz);
                if (shape.offset() == 0 && shape.lift() == 0 && farShape == FarLands.Column.NONE) continue;
                for (int y = min; y < max; y++) column[y - min] = get(chunk, x, y, z);
                transform(column, moved, shape);
                if (far != null) far.apply(moved, min, farShape);
                for (int y = min; y < max; y++) {
                    if (moved[y - min] != column[y - min]) set(chunk, x, y, z, moved[y - min]);
                }
                changed = true;
            }
        }
        if (changed) {
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        }
    }

    /**
     * One column: a torn island's top slab goes up by its lift and leaves a pit; then the whole column moves by
     * the island offset. Whatever comes from outside the world is air.
     */
    private static void transform(BlockState[] column, BlockState[] out, Islands.Column shape) {
        int n = column.length;
        BlockState[] source = column;
        if (shape.lift() > 0) {
            int top = n - 1;
            while (top >= 0 && column[top].isAir()) top--;
            if (top >= 0) {
                int bottom = Math.max(0, top - shape.thickness() + 1);
                BlockState[] torn = column.clone();
                for (int i = bottom; i <= top; i++) torn[i] = AIR;
                for (int i = bottom; i <= top; i++) {
                    int to = i + shape.lift();
                    if (to < n) torn[to] = column[i];
                }
                source = torn;
            }
        }
        for (int i = 0; i < n; i++) {
            int from = i - shape.offset();
            out[i] = from >= 0 && from < n ? source[from] : AIR;
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (level instanceof WorldGenRegion region) {
            pockets(region.getLevel().getChunkSource().randomState()).carve(chunk);
        }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int height = super.getBaseHeight(x, z, type, level, random);
        if (height <= level.getMinBuildHeight()) return height;
        Islands.Column shape = islands(random).column(x, z, new Long2ObjectOpenHashMap<>());
        return height + shape.offset() + shape.lift();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        NoiseColumn column = super.getBaseColumn(x, z, height, random);
        int offset = islands(random).column(x, z, new Long2ObjectOpenHashMap<>()).offset();
        if (offset == 0) return column;
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = column.getBlock(height.getMinBuildHeight() + i - offset);
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    private static BlockState get(ChunkAccess chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        return section.hasOnlyAir() ? AIR : section.getBlockState(x, y & 15, z);
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }
}
