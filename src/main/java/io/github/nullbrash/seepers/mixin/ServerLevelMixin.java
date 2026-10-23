package io.github.nullbrash.seepers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.nullbrash.seepers.world.WeatherDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps the weather cycle running without skylight (CLAUDE.md 4.1: rain and snow stay); no event covers it. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @WrapOperation(method = "advanceWeatherCycle",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/DimensionType;hasSkyLight()Z"))
    private boolean seepers$weatherWithoutSkylight(DimensionType type, Operation<Boolean> original) {
        return original.call(type) || WeatherDimensions.hasWeather(type);
    }
}
