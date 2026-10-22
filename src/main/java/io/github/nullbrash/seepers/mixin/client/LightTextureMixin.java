package io.github.nullbrash.seepers.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.nullbrash.seepers.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * True darkness in the lightmap (CLAUDE.md 4.1). No event exists for this: the lightmap is built only here.
 * Sodium renders with the same vanilla lightmap texture.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /** Low light levels become pure black; vanilla keeps a small brightness floor even at level 0. */
    @WrapOperation(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void seepers$blackLowLevels(NativeImage image, int blockLevel, int skyLevel, int color, Operation<Void> original) {
        int threshold = ServerConfig.get(ServerConfig.BLACK_LIGHT_LEVEL);
        original.call(image, blockLevel, skyLevel, blockLevel <= threshold && skyLevel <= threshold ? 0xFF000000 : color);
    }

    /** Night Vision stays as an effect but stops brightening; conduit power keeps working. */
    @WrapOperation(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean seepers$ignoreNightVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (effect.equals(MobEffects.NIGHT_VISION) && !ServerConfig.get(ServerConfig.NIGHT_VISION_BRIGHTENS)) {
            return false;
        }
        return original.call(player, effect);
    }

    /** Caps the gamma option here rather than in options, so editing options.txt cannot bypass it. */
    @WrapOperation(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object seepers$capGamma(OptionInstance<?> option, Operation<Object> original) {
        Object value = original.call(option);
        if (option == minecraft.options.gamma() && value instanceof Double gamma) {
            return Math.min(gamma, ServerConfig.get(ServerConfig.MAX_GAMMA));
        }
        return value;
    }
}
