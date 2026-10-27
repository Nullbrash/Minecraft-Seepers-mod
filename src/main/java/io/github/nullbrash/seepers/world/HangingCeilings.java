package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.function.DoubleBinaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.neoforged.neoforge.common.Tags;

/**
 * Hanging sand and gravel (CLAUDE.md 4.10): patches in cave ceilings and the undersides of overhangs, below the
 * surface only, more of them deeper in distortion; sand under sandy biomes, gravel elsewhere. Abandoned mineshafts
 * teach props: every corridor ceiling is a hanging layer of gravel and some supports are gone — where one is missing
 * the ceiling comes down. Placed straight into the chunk (no ticks), so it all hangs until disturbed.
 */
public final class HangingCeilings {
    public record Settings(int cellWidth, int cellHeight, float shareNear, float shareFar, int thicknessMin, int thicknessMax,
                           float mineshaftPropLoss) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("cell_width").forGetter(Settings::cellWidth),
                Codec.INT.fieldOf("cell_height").forGetter(Settings::cellHeight),
                Codec.FLOAT.fieldOf("share_near").forGetter(Settings::shareNear),
                Codec.FLOAT.fieldOf("share_far").forGetter(Settings::shareFar),
                Codec.INT.fieldOf("thickness_min").forGetter(Settings::thicknessMin),
                Codec.INT.fieldOf("thickness_max").forGetter(Settings::thicknessMax),
                Codec.FLOAT.fieldOf("mineshaft_prop_loss").forGetter(Settings::mineshaftPropLoss)
        ).apply(instance, Settings::new));
    }

    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();

    /** For the debug survey: support blocks seen in corridors since start, and how many were taken out. */
    public static final java.util.concurrent.atomic.AtomicLong SUPPORTS_SEEN = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SUPPORTS_REMOVED = new java.util.concurrent.atomic.AtomicLong();

    private final Settings settings;
    private final long seed;
    private final DoubleBinaryOperator distortion;

    public HangingCeilings(Settings settings, long seed, DoubleBinaryOperator distortion) {
        this.settings = settings;
        this.seed = seed;
        this.distortion = distortion;
    }

    /** Share of ceiling cells that hang here: near spawn to zone 3, linear in distortion. */
    double share(int x, int z) {
        double t = Mth.clamp(distortion.applyAsDouble(x, z) / 3.0, 0, 1);
        return Mth.lerp(t, settings.shareNear(), settings.shareFar());
    }

    public void place(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        mineshafts(level, chunk, structures);
        ceilings(chunk);
    }

    private void ceilings(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = pos.getMinBlockX() + lx;
                int z = pos.getMinBlockZ() + lz;
                double share = share(x, z);
                // Below the column's own surface only: the overhangs of cliffs are, open-air ledges are not.
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz) - 2;
                BlockState below = get(chunk, lx, top, lz);
                for (int y = top; y > minY + 1; y--) {
                    BlockState here = below;
                    below = get(chunk, lx, y - 1, lz);
                    if (!below.isAir() || !here.is(BlockTags.BASE_STONE_OVERWORLD)) continue;
                    long cell = cellHash(x, y, z);
                    if ((cell >>> 11) * 0x1.0p-53 >= share) continue;
                    int span = settings.thicknessMax() - settings.thicknessMin() + 1;
                    int thickness = settings.thicknessMin() + (int) Long.remainderUnsigned(cell >>> 3, Math.max(1, span));
                    BlockState loose = looseFor(chunk.getNoiseBiome(lx >> 2, y >> 2, lz >> 2));
                    for (int i = 0; i < thickness; i++) {
                        if (!get(chunk, lx, y + i, lz).is(BlockTags.BASE_STONE_OVERWORLD)) break;
                        set(chunk, lx, y + i, lz, loose);
                    }
                }
            }
        }
    }

    /** The cell of a ceiling spot, nudged by a block or so per column so patches have ragged edges. */
    private long cellHash(int x, int y, int z) {
        long jitter = HashCommon.mix(seed ^ (x * 0x632BE59BD9B4E019L) ^ (z * 0x9E3779B97F4A7C15L));
        int jx = (int) Long.remainderUnsigned(jitter, 3) - 1;
        int jz = (int) Long.remainderUnsigned(jitter >>> 8, 3) - 1;
        int cx = Math.floorDiv(x + jx, settings.cellWidth());
        int cy = Math.floorDiv(y, settings.cellHeight());
        int cz = Math.floorDiv(z + jz, settings.cellWidth());
        return HashCommon.mix(seed * 31 + ((long) cx * 73856093L ^ (long) cy * 19349663L ^ (long) cz * 83492791L));
    }

    private static BlockState looseFor(Holder<Biome> biome) {
        if (biome.is(Tags.Biomes.IS_BADLANDS)) return RED_SAND;
        if (biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_DESERT) || biome.is(Tags.Biomes.IS_BEACH)) return SAND;
        return GRAVEL;
    }

    private void mineshafts(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        SectionPos section = SectionPos.bottomOf(chunk);
        BoundingBox chunkBox = new BoundingBox(chunk.getPos().getMinBlockX(), chunk.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                chunk.getPos().getMaxBlockX(), chunk.getMaxBuildHeight() - 1, chunk.getPos().getMaxBlockZ());
        for (var key : new net.minecraft.resources.ResourceKey[] {BuiltinStructures.MINESHAFT, BuiltinStructures.MINESHAFT_MESA}) {
            @SuppressWarnings("unchecked")
            Structure structure = registry.get((net.minecraft.resources.ResourceKey<Structure>) key);
            if (structure == null) continue;
            for (StructureStart start : structures.startsForStructure(section, structure)) {
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof MineshaftPieces.MineShaftCorridor && piece.getBoundingBox().intersects(chunkBox)) {
                        corridor(chunk, piece.getBoundingBox(), piece.getOrientation(), chunkBox);
                    }
                }
            }
        }
    }

    /**
     * A vanilla corridor is 3 wide and 3 high with a support every 5 blocks along it (fences, a beam of planks under
     * the ceiling). Its ceiling becomes hanging gravel; a share of the supports is gone, a bit of plank left on the
     * floor. Each support is decided by its world position, so both chunks of a corridor agree.
     */
    private void corridor(ChunkAccess chunk, BoundingBox box, Direction orientation, BoundingBox chunkBox) {
        if (orientation == null) return;
        boolean alongZ = orientation.getAxis() == Direction.Axis.Z;
        int ceiling = box.maxY() + 1;
        int floor = box.minY();
        for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
            for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
                int lx = x & 15;
                int lz = z & 15;
                BlockState above = get(chunk, lx, ceiling, lz);
                if (above.is(BlockTags.BASE_STONE_OVERWORLD) || above.is(BlockTags.DIRT) || above.is(Blocks.GRAVEL)) {
                    set(chunk, lx, ceiling, lz, GRAVEL);
                }
                int along = alongZ ? z : x;
                BlockState beam = get(chunk, lx, floor + 2, lz);
                if (!beam.is(BlockTags.PLANKS) && !get(chunk, lx, floor, lz).is(BlockTags.WOODEN_FENCES)) continue;
                SUPPORTS_SEEN.incrementAndGet();
                // One decision per support: the corridor (its side and floor) and the position along it.
                long hash = HashCommon.mix(seed ^ 0x5DEECE66DL ^ ((alongZ ? box.minX() : box.minZ()) * 0x9E3779B97F4A7C15L)
                        ^ (along * 0xC2B2AE3D27D4EB4FL) ^ ((long) floor << 40));
                if ((hash >>> 11) * 0x1.0p-53 >= settings.mineshaftPropLoss()) continue;
                SUPPORTS_REMOVED.incrementAndGet();
                BlockState planks = beam.is(BlockTags.PLANKS) ? beam : null;
                for (int y = floor; y <= floor + 2; y++) {
                    BlockState s = get(chunk, lx, y, lz);
                    if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_FENCES)) set(chunk, lx, y, lz, CAVE_AIR);
                }
                // A broken bit of the beam on the floor, in the middle of the corridor's width.
                boolean middle = alongZ ? x == box.minX() + 1 : z == box.minZ() + 1;
                if (middle && planks != null && get(chunk, lx, floor, lz).isAir()) {
                    BlockState slab = slabOf(planks);
                    if (slab != null) set(chunk, lx, floor, lz, slab);
                }
            }
        }
    }

    private static BlockState slabOf(BlockState planks) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(planks.getBlock());
        ResourceLocation slabId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_planks", "_slab"));
        Block slab = BuiltInRegistries.BLOCK.get(slabId);
        return slab instanceof SlabBlock ? slab.defaultBlockState() : null;
    }

    private static BlockState get(ChunkAccess chunk, int x, int y, int z) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return CAVE_AIR;
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        return section.hasOnlyAir() ? CAVE_AIR : section.getBlockState(x, y & 15, z);
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(x, y & 15, z, state, false);
    }
}
