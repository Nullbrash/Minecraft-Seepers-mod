package io.github.nullbrash.seepers.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nullbrash.seepers.client.light.ClientDynamicLights;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smooth dynamic light in vanilla block rendering (CLAUDE.md 4.2): every block light lookup of the mesh builder goes
 * through here, and smooth lighting then blends it per vertex. Sodium asks here too but keeps only whole levels, so
 * its own pipelines are hooked per vertex instead ({@code SodiumLightPipelineMixin}) and its lookups are left alone.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererLightMixin {
    @ModifyReturnValue(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"))
    private static int seepers$dynamicLight(int packed, BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (!ClientDynamicLights.active() || level.getClass().getName().startsWith("net.caffeinemc.")) return packed;
        return ClientDynamicLights.packed(packed, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
