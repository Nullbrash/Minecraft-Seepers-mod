package io.github.nullbrash.seepers.mixin;

import io.github.nullbrash.seepers.collapse.Collapses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Collapses (CLAUDE.md 4.10): there is no event for a block starting to fall, so the scheduled tick that drops it
 * asks props and the warning first; placement marks blocks that fall at once; the base falling() hook (blocks that
 * do not override it — sand, gravel, concrete powder) gives the falling block its damage.
 */
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void seepers$collapse(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (Collapses.onFallTick(state, level, pos)) ci.cancel();
    }

    @Inject(method = "onPlace", at = @At("HEAD"))
    private void seepers$placed(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving, CallbackInfo ci) {
        if (level instanceof ServerLevel server) Collapses.onPlace(server, pos);
    }

    @Inject(method = "falling", at = @At("HEAD"))
    private void seepers$hurts(FallingBlockEntity entity, CallbackInfo ci) {
        Collapses.onFalling(entity);
    }
}
