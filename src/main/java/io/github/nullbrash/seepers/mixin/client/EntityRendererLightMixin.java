package io.github.nullbrash.seepers.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nullbrash.seepers.client.light.ClientDynamicLights;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entities (and the player's own hands) are lit by the smooth dynamic light too, not by the hidden server light —
 * measured at the entity itself (a carrier gets its own full level), not at its block's centre, which stepped.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererLightMixin<T extends Entity> {
    @ModifyReturnValue(method = "getBlockLightLevel", at = @At("RETURN"))
    private int seepers$dynamicLight(int level, T entity, BlockPos pos) {
        if (!ClientDynamicLights.active()) return level;
        var eye = entity.getEyePosition();
        return Math.max(level, (int) Math.round(ClientDynamicLights.value(eye.x, eye.y, eye.z)));
    }
}
