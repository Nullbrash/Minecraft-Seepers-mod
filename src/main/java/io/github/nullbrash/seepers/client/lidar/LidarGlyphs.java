package io.github.nullbrash.seepers.client.lidar;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nullbrash.seepers.Seepers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The lidar's own drawn numbers and pattern icons (style of the device screen): 5x7 pixel digits with °, %, a point
 * and arrows, and 12x12 pattern icons, all white, tinted when drawn.
 */
final class LidarGlyphs {
    private static final ResourceLocation DIGITS = Seepers.id("textures/gui/lidar_digits.png");
    private static final ResourceLocation PATTERNS = Seepers.id("textures/gui/lidar_patterns.png");
    private static final String ORDER = "0123456789°%.<>xlc";
    static final int GLYPH_WIDTH = 6;
    static final int GLYPH_HEIGHT = 7;
    static final int PATTERN_SIZE = 12;

    /** Width of the text in pixels at scale 1 (unknown characters are a gap). */
    static int width(String text) {
        return text.length() * GLYPH_WIDTH - 1;
    }

    /** Draws the text with the glyphs at (x, y), scaled, in the colour. */
    static void draw(GuiGraphics graphics, String text, int x, int y, int scale, int color) {
        tint(graphics, color);
        for (int i = 0; i < text.length(); i++) {
            int index = ORDER.indexOf(text.charAt(i));
            if (index < 0) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(x + i * GLYPH_WIDTH * scale, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.blit(DIGITS, 0, 0, index * GLYPH_WIDTH, 0, 5, GLYPH_HEIGHT, ORDER.length() * GLYPH_WIDTH, GLYPH_HEIGHT);
            graphics.pose().popPose();
        }
        graphics.setColor(1, 1, 1, 1);
    }

    static void pattern(GuiGraphics graphics, int index, int x, int y, int color) {
        tint(graphics, color);
        graphics.blit(PATTERNS, x, y, index * PATTERN_SIZE, 0, PATTERN_SIZE, PATTERN_SIZE, PATTERN_SIZE * 4, PATTERN_SIZE);
        graphics.setColor(1, 1, 1, 1);
    }

    private static void tint(GuiGraphics graphics, int color) {
        RenderSystem.enableBlend();
        graphics.setColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                ((color >>> 24) & 0xFF) / 255.0F);
    }

    private LidarGlyphs() {}
}
