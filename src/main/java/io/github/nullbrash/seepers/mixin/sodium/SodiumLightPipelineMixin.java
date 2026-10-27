package io.github.nullbrash.seepers.mixin.sodium;

import io.github.nullbrash.seepers.client.light.ClientDynamicLights;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.light.flat.FlatLightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth dynamic light under Sodium (CLAUDE.md 4.2, optional integration): Sodium reads block light as whole levels
 * and keeps its own pipelines, so the light is added at their output, per vertex at its exact position. Applied only
 * when Sodium is present (separate mixin config).
 */
@Pseudo
@Mixin(value = {SmoothLightPipeline.class, FlatLightPipeline.class}, remap = false)
public abstract class SodiumLightPipelineMixin {
    @Inject(method = "calculate", at = @At("TAIL"), require = 0)
    private void seepers$dynamicLight(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace,
                                      boolean shade, boolean enhanced, CallbackInfo ci) {
        if (!ClientDynamicLights.active()) return;
        for (int i = 0; i < 4; i++) {
            out.lm[i] = ClientDynamicLights.packed(out.lm[i], pos.getX() + quad.getX(i), pos.getY() + quad.getY(i), pos.getZ() + quad.getZ(i));
        }
    }
}
