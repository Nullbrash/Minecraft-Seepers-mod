package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Lost pockets (CLAUDE.md 4.8): closed voids where the world is simply missing. Carved after decoration, so
 * nothing grows or hangs inside; a stone shell seals off caves that would cut into them. Some reach the surface.
 */
final class LostPockets {
    record Settings(int cellSize, float chunksPerPocketStart, float chunksPerPocketFar,
                    int widthMin, int widthMax, int heightMin, int heightMax, int minY, int maxY) {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(16, 256).fieldOf("cell_size").forGetter(Settings::cellSize),
                Codec.floatRange(1, 10_000).fieldOf("chunks_per_pocket_start").forGetter(Settings::chunksPerPocketStart),
                Codec.floatRange(1, 10_000).fieldOf("chunks_per_pocket_far").forGetter(Settings::chunksPerPocketFar),
                Codec.intRange(4, 96).fieldOf("width_min").forGetter(Settings::widthMin),
                Codec.intRange(4, 96).fieldOf("width_max").forGetter(Settings::widthMax),
                Codec.intRange(2, 64).fieldOf("height_min").forGetter(Settings::heightMin),
                Codec.intRange(2, 64).fieldOf("height_max").forGetter(Settings::heightMax),
                Codec.intRange(-2048, 2048).fieldOf("min_y").forGetter(Settings::minY),
                Codec.intRange(-2048, 2048).fieldOf("max_y").forGetter(Settings::maxY))
                .apply(instance, Settings::new));
    }

    record Pocket(int x, int y, int z, int rx, int ry, int rz) {}

    /** Ragged walls: the pocket radius varies by this fraction. */
    private static final double RAGGED = 0.2;
    private static final double SHELL = 2.0;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final Settings settings;
    private final ToDoubleBiFunction<Integer, Integer> distortion;
    private final PositionalRandomFactory cells;
    private final SimplexNoise shape;

    LostPockets(Settings settings, RandomState random, ToDoubleBiFunction<Integer, Integer> distortion) {
        this.settings = settings;
        this.distortion = distortion;
        this.cells = random.getOrCreateRandomFactory(Seepers.id("lost_pockets"));
        this.shape = new SimplexNoise(random.getOrCreateRandomFactory(Seepers.id("lost_pocket_shape")).at(0, 0, 0));
    }

    List<Pocket> pocketsNear(int minX, int minZ, int maxX, int maxZ) {
        int size = settings.cellSize();
        int reach = settings.widthMax() / 2 + 8;
        List<Pocket> pockets = new ArrayList<>();
        for (int i = Mth.floorDiv(minX - reach, size); i <= Mth.floorDiv(maxX + reach, size); i++) {
            for (int j = Mth.floorDiv(minZ - reach, size); j <= Mth.floorDiv(maxZ + reach, size); j++) {
                addCell(i, j, pockets);
            }
        }
        return pockets;
    }

    private void addCell(int i, int j, List<Pocket> out) {
        int size = settings.cellSize();
        double level = distortion.applyAsDouble(i * size + size / 2, j * size + size / 2);
        if (level < 1) return;
        float chunksPer = Mth.lerp((float) Mth.clamp((level - 1) / 2, 0, 1), settings.chunksPerPocketStart(), settings.chunksPerPocketFar());
        double expected = (size / 16.0) * (size / 16.0) / chunksPer;
        RandomSource r = cells.at(i, 0, j);
        int count = (int) expected + (r.nextDouble() < expected - (int) expected ? 1 : 0);
        for (int n = 0; n < count; n++) {
            int rx = (settings.widthMin() + r.nextInt(Math.max(1, settings.widthMax() - settings.widthMin() + 1))) / 2;
            int rz = (settings.widthMin() + r.nextInt(Math.max(1, settings.widthMax() - settings.widthMin() + 1))) / 2;
            int ry = (settings.heightMin() + r.nextInt(Math.max(1, settings.heightMax() - settings.heightMin() + 1))) / 2;
            int y = settings.minY() + r.nextInt(Math.max(1, settings.maxY() - settings.minY() + 1));
            out.add(new Pocket(i * size + r.nextInt(size), y, j * size + r.nextInt(size), Math.max(2, rx), Math.max(1, ry), Math.max(2, rz)));
        }
    }

    void carve(ChunkAccess chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        List<Pocket> pockets = pocketsNear(minX, minZ, minX + 15, minZ + 15);
        if (pockets.isEmpty()) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Shells first, then all interiors: a shell must never fill an overlapping pocket.
        for (boolean interior : new boolean[] {false, true}) {
            for (Pocket p : pockets) {
                int reachX = (int) Math.ceil(p.rx() * (1 + RAGGED) + SHELL);
                int reachY = (int) Math.ceil(p.ry() * (1 + RAGGED) + SHELL);
                int reachZ = (int) Math.ceil(p.rz() * (1 + RAGGED) + SHELL);
                int x0 = Math.max(minX, p.x() - reachX), x1 = Math.min(minX + 15, p.x() + reachX);
                int z0 = Math.max(minZ, p.z() - reachZ), z1 = Math.min(minZ + 15, p.z() + reachZ);
                int y0 = Math.max(chunk.getMinBuildHeight(), p.y() - reachY), y1 = Math.min(chunk.getMaxBuildHeight() - 1, p.y() + reachY);
                double shell = SHELL / Math.min(p.rx(), Math.min(p.ry(), p.rz()));
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
                        for (int y = y0; y <= y1; y++) {
                            double dx = (x - p.x()) / (double) p.rx();
                            double dy = (y - p.y()) / (double) p.ry();
                            double dz = (z - p.z()) / (double) p.rz();
                            double e = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            double limit = 1 + RAGGED * shape.getValue(x / 10.0, y / 10.0, z / 10.0);
                            pos.set(x, y, z);
                            BlockState state = chunk.getBlockState(pos);
                            if (state.is(Blocks.BEDROCK)) continue;
                            if (interior) {
                                if (e < limit && !state.isAir()) chunk.setBlockState(pos, AIR, false);
                            } else if (e >= limit && e < limit + shell && y < floor
                                    && (state.isAir() || !state.getFluidState().isEmpty())) {
                                // Seal caves and underground water that touch the pocket.
                                chunk.setBlockState(pos, y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), false);
                            }
                        }
                    }
                }
            }
        }
    }
}
