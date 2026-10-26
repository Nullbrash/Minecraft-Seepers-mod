package io.github.nullbrash.seepers.mixin.client;

import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The render distance slider also shows the automatic distance in use (CLAUDE.md 4.7). The caption is built only
 * here, from a lambda inside the Options constructor; no event covers it. Sodium's screen: SodiumConfig.
 */
@Mixin(OptionInstance.OptionInstanceSliderButton.class)
public abstract class OptionInstanceSliderButtonMixin extends AbstractOptionSliderButton {
    @Shadow
    @Final
    private OptionInstance<?> instance;

    private OptionInstanceSliderButtonMixin(Options options, int x, int y, int width, int height, double value) {
        super(options, x, y, width, height, value);
    }

    @Inject(method = "updateMessage", at = @At("TAIL"))
    private void seepers$autoCaption(CallbackInfo ci) {
        if (instance == Minecraft.getInstance().options.renderDistance()) {
            Component caption = AutoRenderDistance.sliderCaption(getMessage());
            if (caption != getMessage()) setMessage(caption);
        }
    }
}
