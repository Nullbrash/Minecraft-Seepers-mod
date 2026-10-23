package io.github.nullbrash.seepers.fog;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/** Seeded noise of one dimension. Same fog seed + dimension = same noise on client and server. */
public final class FogNoise {
    private static final Map<Long, FogNoise> CACHE = new ConcurrentHashMap<>();
    // Hot path: one lookup by interned dimension key instead of hashing the seed every sample.
    private static final Map<ResourceKey<Level>, FogNoise> BY_DIMENSION = new ConcurrentHashMap<>();

    final long seed;
    private final long fogSeed;
    final ImprovedNoise[] layerHeight = new ImprovedNoise[3];
    final ImprovedNoise[] layerThickness = new ImprovedNoise[3];
    final ImprovedNoise thirdLayerPresence;
    final ImprovedNoise pockets;
    final ImprovedNoise wavePhase;
    /** Unit drift direction, per dimension. */
    final double driftX;
    final double driftZ;

    private FogNoise(long seed, long fogSeed) {
        this.seed = seed;
        this.fogSeed = fogSeed;
        RandomSource random = RandomSource.create(seed);
        for (int i = 0; i < 3; i++) {
            layerHeight[i] = new ImprovedNoise(random);
            layerThickness[i] = new ImprovedNoise(random);
        }
        thirdLayerPresence = new ImprovedNoise(random);
        pockets = new ImprovedNoise(random);
        wavePhase = new ImprovedNoise(random);
        double angle = random.nextDouble() * Math.PI * 2;
        driftX = Math.cos(angle);
        driftZ = Math.sin(angle);
    }

    public static FogNoise of(long fogSeed, ResourceKey<Level> dimension) {
        FogNoise cached = BY_DIMENSION.get(dimension);
        if (cached != null && cached.fogSeed == fogSeed) {
            return cached;
        }
        long seed = HashCommon.murmurHash3(fogSeed ^ dimension.location().toString().hashCode());
        FogNoise noise = CACHE.computeIfAbsent(seed, s -> new FogNoise(s, fogSeed));
        BY_DIMENSION.put(dimension, noise);
        return noise;
    }
}
