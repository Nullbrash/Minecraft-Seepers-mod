package io.github.nullbrash.seepers.registry;

import com.mojang.serialization.MapCodec;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.world.DistortedChunkGenerator;
import io.github.nullbrash.seepers.world.DistortionFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation types used from data. Names are saved in datapacks — do not rename. */
public final class SeepersWorldgen {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Seepers.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<DistortionFunction>> DISTORTION =
            DENSITY_FUNCTION_TYPES.register("distortion", () -> DistortionFunction.MAP_CODEC);

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Seepers.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<DistortedChunkGenerator>> DISTORTED =
            CHUNK_GENERATORS.register("distorted", () -> DistortedChunkGenerator.CODEC);

    private SeepersWorldgen() {}
}
