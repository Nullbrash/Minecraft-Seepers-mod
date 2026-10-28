package io.github.nullbrash.seepers.client.lidar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A button of the lidar's screen drawn in the hologram's style: a frame and a drawn icon instead of a label (pattern
 * icon, or one of the pixel glyphs). A selected button is lit.
 */
final class LidarIconButton extends AbstractButton {
    private final int pattern;
    private final String glyph;
    private final Runnable press;
    boolean selected;

    /** pattern >= 0: that pattern icon; else glyph is drawn with the pixel digits. */
    LidarIconButton(int x, int y, int size, int pattern, String glyph, Component name, Runnable press) {
        super(x, y, size, size, name);
        this.pattern = pattern;
        this.glyph = glyph;
        this.press = press;
        setTooltip(Tooltip.create(name));
    }

    @Override
    public void onPress() {
        press.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int frame = selected ? 0xFF000000 | LidarHud.COLOR : isHoveredOrFocused() ? 0xCC000000 | LidarHud.COLOR : 0x66000000 | LidarHud.COLOR;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, frame);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, selected ? 0xFF16384A : 0xFF06121A);
        int icon = (active ? 0xFF000000 : 0x66000000) | LidarHud.COLOR;
        if (pattern >= 0) {
            LidarGlyphs.pattern(graphics, pattern, getX() + (width - LidarGlyphs.PATTERN_SIZE) / 2, getY() + (height - LidarGlyphs.PATTERN_SIZE) / 2, icon);
        } else {
            LidarGlyphs.draw(graphics, glyph, getX() + (width - LidarGlyphs.width(glyph)) / 2, getY() + (height - LidarGlyphs.GLYPH_HEIGHT) / 2, 1, icon);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
