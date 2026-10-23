package io.github.nullbrash.seepers.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nullbrash.seepers.light.LightDimension;
import io.github.nullbrash.seepers.light.LightOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Block light by category and dimension, and stronger light absorption in water (CLAUDE.md 4.2-4.3).
 * Light emission is baked into each state at startup and NeoForge offers no event to change it; the
 * light engine, chunk light-source scan and relight checks all go through the two methods below.
 * Values are precomputed per state by LightTable, so these hot paths only read a field.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements LightOverrides {
    @Unique
    private int seepers$packedLight;
    @Unique
    private int seepers$lightBlockOverride;
    @Unique
    private int seepers$roomWalls;

    @Override
    public void seepers$setPackedLight(int packed) {
        seepers$packedLight = packed;
    }

    @Override
    public void seepers$setLightBlockOverride(int opacityPlusOne) {
        seepers$lightBlockOverride = opacityPlusOne;
    }

    @Override
    public int seepers$roomWalls() {
        return seepers$roomWalls;
    }

    @Override
    public void seepers$setRoomWalls(int bits) {
        seepers$roomWalls = bits;
    }

    /** Overrides IBlockStateExtension#getLightEmission(BlockGetter, BlockPos) (a class method beats the interface default). */
    public int getLightEmission(BlockGetter level, BlockPos pos) {
        int packed = seepers$packedLight;
        BlockState self = (BlockState) (Object) this;
        if (packed == 0) {
            return self.getBlock().getLightEmission(self, level, pos);
        }
        return (packed >> (4 * LightDimension.of(level))) & 15;
    }

    @ModifyReturnValue(method = "getLightBlock", at = @At("RETURN"))
    private int seepers$waterAbsorption(int original) {
        int override = seepers$lightBlockOverride;
        return override != 0 ? override - 1 : original;
    }
}
