package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/**
 * Density function {@code seepers:warp} (data): where the terrain at a column is taken from. Zone 2 (CLAUDE.md 4.8):
 * stretch — inside each large region the land is drawn out along its own direction, up to {@code stretchMax}
 * by the end of the zone; repeats — some ragged patches show a copy of a patch a few cells away. Identity
 * below level 2. Its own value is 0: it is used through {@link Transformed}, never as a density.
 */
public record CoordinateWarp(DensityFunction distortion, DensityFunction.NoiseHolder noise, Settings settings)
        implements DensityFunction.SimpleFunction {
    public record Settings(int regionSize, float stretchMax, int repeatCell, float repeatShareMax, int repeatMinCells,
                           int repeatMaxCells, int repeatWarp) {
        static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(32, 8192).fieldOf("region_size").forGetter(Settings::regionSize),
                Codec.floatRange(1, 16).fieldOf("stretch_max").forGetter(Settings::stretchMax),
                Codec.intRange(16, 1024).fieldOf("repeat_cell").forGetter(Settings::repeatCell),
                Codec.floatRange(0, 1).fieldOf("repeat_share_max").forGetter(Settings::repeatShareMax),
                Codec.intRange(1, 16).fieldOf("repeat_min_cells").forGetter(Settings::repeatMinCells),
                Codec.intRange(1, 16).fieldOf("repeat_max_cells").forGetter(Settings::repeatMaxCells),
                Codec.intRange(0, 64).fieldOf("repeat_warp").forGetter(Settings::repeatWarp))
                .apply(instance, Settings::new));
    }

    public static final MapCodec<CoordinateWarp> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("distortion").forGetter(CoordinateWarp::distortion),
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(CoordinateWarp::noise),
            Settings.CODEC.fieldOf("settings").forGetter(CoordinateWarp::settings))
            .apply(instance, CoordinateWarp::new));
    public static final KeyDispatchDataCodec<CoordinateWarp> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Result of a lookup; also says why, for the debug command. */
    public record Result(double x, double z, double stretch, double angle, int repeatDx, int repeatDz) {
        boolean identity() {
            return stretch == 1 && repeatDx == 0 && repeatDz == 0;
        }
    }

    private static final Result NONE = new Result(0, 0, 1, 0, 0, 0);
    /** One noise lookup chain per column is shared by all the noises sampled there. */
    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[] {Double.NaN, Double.NaN, 0, 0, 0});

    /** Closer to (0, 0) than this the warp is identity (below level 2): one multiply instead of a lookup. */
    double identityRadiusSq() {
        DensityFunction d = distortion instanceof DensityFunctions.HolderHolder holder ? holder.function().value() : distortion;
        if (d instanceof DistortionFunction f) return (double) f.zones().zone2() * f.zones().zone2();
        return 0;
    }

    @Override
    public double compute(FunctionContext context) {
        return 0;
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 0;
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CoordinateWarp(distortion.mapAll(visitor), visitor.visitNoise(noise), settings));
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /** Writes the source column for (x, z) into out[0], out[1]; returns false if it is (x, z) itself. */
    boolean apply(int x, int z, double[] out) {
        double[] last = LAST.get();
        if (last[0] == x && last[1] == z && last[4] == System.identityHashCode(this)) {
            out[0] = last[2];
            out[1] = last[3];
            return out[0] != x || out[1] != z;
        }
        Result result = lookup(x, z);
        out[0] = result.identity() ? x : result.x();
        out[1] = result.identity() ? z : result.z();
        last[0] = x;
        last[1] = z;
        last[2] = out[0];
        last[3] = out[1];
        last[4] = System.identityHashCode(this);
        return !result.identity();
    }

    public Result lookup(int x, int z) {
        double level = distortion.compute(new SinglePointContext(x, 0, z));
        if (level <= 2 || noise.noise() == null) return NONE;
        double t = Mth.clamp(level - 2, 0, 1);
        long seed = Double.doubleToLongBits(noise.getValue(0.5, 0.5, 0.5));

        // Repeats: ragged cells, some of them show the land of another cell a few cells away.
        int cell = settings.repeatCell();
        // The noise already runs at 1/32 of block scale: x * 0.4 gives wobbles of ~80 blocks along the edges.
        double wx = x + settings.repeatWarp() * noise.getValue(x * 0.4, 7.0, z * 0.4);
        double wz = z + settings.repeatWarp() * noise.getValue(x * 0.4, 13.0, z * 0.4);
        int ci = Mth.floor(wx / cell);
        int cj = Mth.floor(wz / cell);
        int dx = 0;
        int dz = 0;
        long h = mix(seed, ci, cj);
        if (unit(h) < settings.repeatShareMax() * t) {
            long h2 = mix(h, 1, 2);
            double angle = unit(h2) * Math.PI * 2;
            int span = settings.repeatMinCells() + (int) (unit(mix(h2, 3, 4)) * (settings.repeatMaxCells() - settings.repeatMinCells() + 1));
            dx = (int) Math.round(Math.cos(angle) * span) * cell;
            dz = (int) Math.round(Math.sin(angle) * span) * cell;
        }
        double qx = x - dx;
        double qz = z - dz;

        // Stretch: each region draws its land out along its own direction around the region center.
        int size = settings.regionSize();
        // Region borders ragged too, at the region's own scale.
        double rx = qx + size * 0.25 * noise.getValue(qx * 0.08, 21.0, qz * 0.08);
        double rz = qz + size * 0.25 * noise.getValue(qx * 0.08, 29.0, qz * 0.08);
        int gi = Mth.floor(rx / size);
        int gj = Mth.floor(rz / size);
        double angle = unit(mix(seed, gi + 7_000_003L, gj)) * Math.PI;
        // Stretched as at the source, so a copy matches its original exactly.
        double sourceLevel = dx == 0 && dz == 0 ? level : distortion.compute(new SinglePointContext(Mth.floor(qx), 0, Mth.floor(qz)));
        double stretch = Mth.lerp(Mth.clamp(sourceLevel - 2, 0, 1), 1.0, settings.stretchMax());
        double cx = (gi + 0.5) * size;
        double cz = (gj + 0.5) * size;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double ux = qx - cx;
        double uz = qz - cz;
        double along = (ux * cos + uz * sin) / stretch;
        double across = -ux * sin + uz * cos;
        return new Result(cx + along * cos - across * sin, cz + along * sin + across * cos, stretch, Math.toDegrees(angle), dx, dz);
    }

    private static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Samples a coordinate-reading density function (noise, shifted noise, shifts …) at the warped column. A
     * context that is already warped passes through, so nested samplers are not warped twice.
     */
    public record Transformed(DensityFunction sampler, DensityFunction warp, double identityRadiusSq) implements DensityFunction {
        public Transformed(DensityFunction sampler, DensityFunction warp) {
            this(sampler, warp, warp instanceof CoordinateWarp w ? w.identityRadiusSq() : 0);
        }

        @Override
        public double compute(FunctionContext context) {
            // Called for every noise sample of every block: the common case (zones 0–1) must stay this cheap.
            double x = context.blockX();
            double z = context.blockZ();
            if (x * x + z * z < identityRadiusSq || context instanceof Warped || !(warp instanceof CoordinateWarp coordinates)) {
                return sampler.compute(context);
            }
            double[] out = new double[2];
            if (!coordinates.apply(context.blockX(), context.blockZ(), out)) return sampler.compute(context);
            return sampler.compute(new Warped(Mth.floor(out[0]), context.blockY(), Mth.floor(out[1])));
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Transformed(sampler.mapAll(visitor), unwrap(warp.mapAll(visitor))));
        }

        @Override
        public double minValue() {
            return sampler.minValue();
        }

        @Override
        public double maxValue() {
            return sampler.maxValue();
        }

        /** Never written to data: the generator wraps the router in memory only. */
        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return sampler.codec();
        }

        private static DensityFunction unwrap(DensityFunction function) {
            return function instanceof DensityFunctions.HolderHolder holder ? holder.function().value() : function;
        }
    }

    /** Marker context: coordinates already warped. */
    record Warped(int blockX, int blockY, int blockZ) implements FunctionContext {}
}
