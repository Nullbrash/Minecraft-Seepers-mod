package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * What the hand just felt (CLAUDE.md 5.1): the block's name and, under it, the material tinted like the points.
 * A separate GUI layer ({@code seepers:touch_caption}) placed by the client config, so it can be moved or turned
 * off without touching the touch itself.
 */
public final class TouchCaption {
    public enum Position { CROSSHAIR, ABOVE_HOTBAR, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private static final long SHOW_MILLIS = 1500;
    private static final long FADE_MILLIS = 500;
    private static final int NAME_COLOR = 0xE0E0E0;
    private static final int MARGIN = 6;

    private static @Nullable Component name;
    private static @Nullable Component detail;
    private static int detailColor;
    private static long shownAt;

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Seepers.id("touch_caption"), TouchCaption::render);
    }

    /** name may be null (nothing to name, e.g. lava says only "Hot!"). */
    static void show(@Nullable Component blockName, Component material, TouchMaterial tint) {
        name = blockName;
        detail = material;
        detailColor = tint.red << 16 | tint.green << 8 | tint.blue;
        shownAt = System.currentTimeMillis();
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (detail == null || minecraft.options.hideGui || !ClientConfig.TOUCH_CAPTION.get()) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MILLIS) {
            detail = null;
            return;
        }
        float fade = Mth.clamp((SHOW_MILLIS - age) / (float) FADE_MILLIS, 0, 1);
        int alpha = Math.max(4, Math.round(fade * 255)) << 24; // below 4 vanilla text rendering treats alpha as opaque
        boolean withName = name != null && ClientConfig.TOUCH_CAPTION_NAMES.get();
        Font font = minecraft.font;
        int lines = withName ? 2 : 1;
        int height = lines * (font.lineHeight + 1);
        int width = Math.max(withName ? font.width(name) : 0, font.width(detail));
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        // Anchor point and alignment per position; text lines stack downward from y.
        Position position = ClientConfig.TOUCH_CAPTION_POSITION.get();
        int y = switch (position) {
            case CROSSHAIR -> h / 2 + 20; // below the attack indicator, which vanilla draws right under the crosshair
            case ABOVE_HOTBAR -> h - 72 - height;
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> h - MARGIN - height;
        };
        for (int i = 0; i < lines; i++) {
            Component text = withName && i == 0 ? name : detail;
            int color = withName && i == 0 ? NAME_COLOR : detailColor;
            int x = switch (position) {
                case CROSSHAIR, ABOVE_HOTBAR -> w / 2 - font.width(text) / 2;
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
                case TOP_RIGHT, BOTTOM_RIGHT -> w - MARGIN - width + (width - font.width(text));
            };
            graphics.drawString(font, text, x, y + i * (font.lineHeight + 1), alpha | color);
        }
    }

    private TouchCaption() {}
}
