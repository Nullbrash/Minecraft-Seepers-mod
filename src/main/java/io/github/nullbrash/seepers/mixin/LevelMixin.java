package io.github.nullbrash.seepers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.nullbrash.seepers.world.WeatherDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Weather without skylight (CLAUDE.md 4.1). Vanilla decides thunder and "is it raining here" from
 * skylight; we keep the global canSeeSky untouched (mobs, daylight detectors) and only change these two.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Shadow
    public abstract DimensionType dimensionType();

    @WrapOperation(method = "isThundering",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;hasSkyLight()Z"))
    private boolean seepers$thunderWithoutSkylight(DimensionType type, Operation<Boolean> original) {
        return original.call(type) || WeatherDimensions.hasWeather(type);
    }

    /** The heightmap check right after this call already decides whether the spot is under open sky. */
    @WrapOperation(method = "isRainingAt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean seepers$rainWithoutSkylight(Level level, BlockPos pos, Operation<Boolean> original) {
        return original.call(level, pos) || WeatherDimensions.hasWeather(dimensionType());
    }
}
