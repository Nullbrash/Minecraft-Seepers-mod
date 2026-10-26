package io.github.nullbrash.seepers.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code seepers:distortion}: the distortion level at a column. The registered instance
 * {@code seepers:distortion} (data) is also where the zone sizes of the world come from.
 */
public record DistortionFunction(Distortion.Zones zones) implements DensityFunction.SimpleFunction {
    public static final MapCodec<DistortionFunction> MAP_CODEC =
            Distortion.Zones.CODEC.fieldOf("zones").xmap(DistortionFunction::new, DistortionFunction::zones);
    public static final KeyDispatchDataCodec<DistortionFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return Distortion.level(zones, context.blockX(), context.blockZ());
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return Distortion.MAX_LEVEL;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
