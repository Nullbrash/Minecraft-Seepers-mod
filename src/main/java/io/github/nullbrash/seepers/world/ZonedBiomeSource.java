package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import com.google.common.base.Suppliers;
import java.util.Optional;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Biome source {@code seepers:zoned} (CLAUDE.md 4.8): the base (vanilla) biome, replaced in zones 1–3 by its
 * distorted twin {@code seepers:<zone>/<path>} — generated at build time from the game's own biomes, same
 * vegetation and structures, broken colours and their own air. Biomes without a twin (other mods) stay.
 */
public final class ZonedBiomeSource extends BiomeSource {
    public static final MapCodec<ZonedBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("base").forGetter(source -> source.base),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("distortion").forGetter(source -> source.distortion),
            Codec.STRING.listOf().fieldOf("zones").forGetter(source -> source.zones),
            Gravity.CODEC.optionalFieldOf("broken_gravity").forGetter(source -> source.gravity),
            RegistryOps.retrieveGetter(Registries.BIOME))
            .apply(instance, ZonedBiomeSource::new));

    private final BiomeSource base;
    private final DensityFunction distortion;
    private final List<String> zones;
    private final Optional<Gravity> gravity;
    /** Seeded patch noise; the source has no seed of its own, the generator binds it (first chunk of the world). */
    private volatile SimplexNoise @Nullable [] patches;
    /**
     * Per zone 1..n: base biome -> twin. Built on first use: while the preset is being read, the base source's
     * biome list is not bound yet.
     */
    private final Supplier<List<Map<Holder<Biome>, Holder<Biome>>>> twins;

    /** Broken gravity patches (CLAUDE.md 4.10): the biome, patch scale and the noise threshold in zones 1..3. */
    public record Gravity(Holder<Biome> biome, float scale, List<Float> thresholds) {
        static final Codec<Gravity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Biome.CODEC.fieldOf("biome").forGetter(Gravity::biome),
                Codec.floatRange(8, 4096).fieldOf("scale").forGetter(Gravity::scale),
                Codec.floatRange(-2, 2).listOf().fieldOf("thresholds").forGetter(Gravity::thresholds))
                .apply(instance, Gravity::new));
    }

    private ZonedBiomeSource(BiomeSource base, DensityFunction distortion, List<String> zones, Optional<Gravity> gravity,
                             HolderGetter<Biome> biomes) {
        this.base = base;
        this.distortion = distortion;
        this.zones = zones;
        this.gravity = gravity;
        this.twins = Suppliers.memoize(() -> {
            List<Holder<Biome>> possible = base.possibleBiomes().stream().toList();
            List<Map<Holder<Biome>, Holder<Biome>>> all = new ArrayList<>();
            for (String zone : zones) {
                Map<Holder<Biome>, Holder<Biome>> map = new IdentityHashMap<>();
                for (Holder<Biome> biome : possible) {
                    biome.unwrapKey().ifPresent(key -> biomes.get(ResourceKey.create(Registries.BIOME,
                                    Seepers.id(zone + "/" + key.location().getPath())))
                            .ifPresent(twin -> map.put(biome, twin)));
                }
                all.add(map);
            }
            return all;
        });
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(Stream.concat(base.possibleBiomes().stream(), twins.get().stream().flatMap(map -> map.values().stream())),
                gravity.stream().map(Gravity::biome)).distinct();
    }

    public void bind(RandomState random) {
        if (patches == null) {
            PositionalRandomFactory factory = random.getOrCreateRandomFactory(Seepers.id("broken_gravity"));
            patches = new SimplexNoise[] {new SimplexNoise(factory.at(0, 0, 0)), new SimplexNoise(factory.at(1, 0, 0))};
        }
    }

    /** Block coordinates. Ragged patches: a coarse noise with a finer one on its edge. */
    public boolean brokenGravity(int x, int z, int zone) {
        SimplexNoise[] noise = patches;
        if (noise == null || gravity.isEmpty() || zone < 1) return false;
        Gravity g = gravity.get();
        List<Float> thresholds = g.thresholds();
        float threshold = thresholds.get(Math.min(zone, thresholds.size()) - 1);
        double n = noise[0].getValue(x / g.scale(), z / g.scale()) + 0.1 * noise[1].getValue(x / 24.0, z / 24.0);
        return n > threshold;
    }

    /** Coordinates are quarts (4 blocks). */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> biome = base.getNoiseBiome(x, y, z, sampler);
        int zone = Distortion.zone(distortion.compute(new DensityFunction.SinglePointContext(x << 2, 0, z << 2)));
        List<Map<Holder<Biome>, Holder<Biome>>> all = twins.get();
        if (zone == 0 || zone > all.size()) return biome;
        if (brokenGravity(x << 2, z << 2, zone)) return gravity.get().biome();
        return all.get(zone - 1).getOrDefault(biome, biome);
    }
}
