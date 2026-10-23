package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/** What the hand just felt, briefly under the crosshair (CLAUDE.md 5.1), tinted like the points. */
public final class TouchCaption {
    private static final long SHOW_MILLIS = 1500;
    private static final long FADE_MILLIS = 500;

    private static @Nullable Component text;
    private static int color;
    private static long shownAt;

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Seepers.id("touch_caption"), TouchCaption::render);
    }

    static void show(Component caption, TouchMaterial material) {
        text = caption;
        color = material.red << 16 | material.green << 8 | material.blue;
        shownAt = System.currentTimeMillis();
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (text == null || minecraft.options.hideGui) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MILLIS) {
            text = null;
            return;
        }
        float fade = Mth.clamp((SHOW_MILLIS - age) / (float) FADE_MILLIS, 0, 1);
        int alpha = Math.max(4, Math.round(fade * 255)); // below 4 vanilla text rendering treats alpha as opaque
        graphics.drawCenteredString(minecraft.font, text, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 12,
                alpha << 24 | color);
    }

    private TouchCaption() {}
}
