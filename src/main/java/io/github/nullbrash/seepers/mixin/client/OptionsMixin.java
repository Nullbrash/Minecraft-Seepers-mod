package io.github.nullbrash.seepers.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Automatic render distance (CLAUDE.md 4.7) without touching the slider: the slider stays the player's ceiling and
 * is saved as set. No event exists for this; everything that renders reads the distance from here.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    @Final
    private OptionInstance<Integer> renderDistance;

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void seepers$autoDistance(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(AutoRenderDistance.limit(cir.getReturnValueI()));
    }

    /** The server sends chunks only as far as the client asks: ask for the automatic distance, not the slider. */
    @WrapOperation(method = "buildPlayerInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object seepers$requestAutoDistance(OptionInstance<?> option, Operation<Object> original) {
        Object value = original.call(option);
        return option == renderDistance ? AutoRenderDistance.request((Integer) value) : value;
    }
}
