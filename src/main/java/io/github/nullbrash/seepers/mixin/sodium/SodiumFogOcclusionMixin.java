package io.github.nullbrash.seepers.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nullbrash.seepers.client.points.PointCloud;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium's fog occlusion skips every chunk past the end of an opaque fog — ours is always opaque and short, so far
 * walls were never drawn, left no depth, and lidar points behind them showed through. While the cloud holds points,
 * chunks are drawn out to the full render distance (as vanilla does). Applied only when Sodium is present.
 */
@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumFogOcclusionMixin {
    @Shadow
    private float getRenderDistance() {
        throw new AssertionError();
    }

    @ModifyReturnValue(method = "getEffectiveRenderDistance", at = @At("RETURN"), require = 0)
    private float seepers$depthForPoints(float distance) {
        PointCloud cloud = PointCloud.current();
        return cloud != null && cloud.hasPoints() ? Math.max(distance, getRenderDistance()) : distance;
    }
}
