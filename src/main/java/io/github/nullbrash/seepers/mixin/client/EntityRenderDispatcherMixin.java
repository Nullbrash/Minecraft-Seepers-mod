package io.github.nullbrash.seepers.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nullbrash.seepers.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Entities in full darkness are not drawn (CLAUDE.md 4.1, 7.2): there they are black anyway and would only show
 * as silhouettes against the glowing points. Lit, burning or glowing entities, and the camera entity, are drawn.
 * No event covers every kind of entity, hence the mixin on the "should render" check.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private <E extends Entity> boolean seepers$hideInDarkness(boolean original, E entity, Frustum frustum,
                                                              double camX, double camY, double camZ) {
        if (!original || entity == Minecraft.getInstance().getCameraEntity()) return original;
        if (entity.isOnFire() || entity.isCurrentlyGlowing()) return true;
        int black = ServerConfig.get(ServerConfig.BLACK_LIGHT_LEVEL);
        return entity.level().getBrightness(LightLayer.BLOCK, entity.blockPosition()) > black
                || entity.level().getBrightness(LightLayer.BLOCK, entity.blockPosition().above()) > black;
    }
}
