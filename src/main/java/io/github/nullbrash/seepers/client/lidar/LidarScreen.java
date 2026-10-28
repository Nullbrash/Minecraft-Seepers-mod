package io.github.nullbrash.seepers.client.lidar;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The lidar's own screen (CLAUDE.md 5.3), opened by its key while the lidar is in hand: everything the device knows
 * about itself, in the hologram's style. Battery and modules get their rows when phases 3-4 bring them.
 */
public class LidarScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.seepers.lidar_screen", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.seepers");

    private static final int PANEL_WIDTH = 240;
    private static final int ROW = 14;
    private static final int PAD = 10;
    private static final int LABEL_COLOR = 0x8FB4CC;

    public LidarScreen() {
        super(Component.translatable("seepers.lidar.screen.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        PointCloud cloud = PointCloud.get();
        int memory = cloud == null ? 0 : cloud.count(PointSource.LIDAR);
        int limit = PointSource.LIDAR.capacity();
        Component none = Component.translatable("seepers.lidar.screen.later");
        Component[][] rows = {
                {Component.translatable("seepers.lidar.screen.beam"),
                        Component.translatable("seepers.lidar.screen.beam.value", LidarHud.number(ServerConfig.get(ServerConfig.LIDAR_CONE_DEGREES)))},
                {Component.translatable("seepers.lidar.screen.range"),
                        Component.translatable("seepers.lidar.screen.range.value", LidarHud.number(ServerConfig.get(ServerConfig.LIDAR_RANGE)))},
                {Component.translatable("seepers.lidar.screen.rate"),
                        Component.translatable("seepers.lidar.screen.rate.value", ServerConfig.get(ServerConfig.LIDAR_POINTS_PER_SECOND))},
                {Component.translatable("seepers.lidar.screen.memory"),
                        Component.translatable("seepers.lidar.screen.memory.value", memory, limit)},
                {Component.translatable("seepers.lidar.screen.lifetime"),
                        Component.translatable("seepers.lidar.screen.lifetime.value")},
                {Component.translatable("seepers.lidar.screen.session"),
                        Component.translatable("seepers.lidar.screen.session.value", LidarScan.sessionPoints())},
                {Component.translatable("seepers.lidar.screen.battery"), none},
                {Component.translatable("seepers.lidar.screen.modules"), Component.translatable("seepers.lidar.screen.none")},
        };
        int panelHeight = PAD * 2 + 16 + rows.length * ROW + 6;
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - panelHeight) / 2;
        int text = 0xFF000000 | LidarHud.COLOR;
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, 0xC00A1A26);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0x99000000 | LidarHud.COLOR);
        graphics.fill(left, top + panelHeight - 1, left + PANEL_WIDTH, top + panelHeight, 0x99000000 | LidarHud.COLOR);
        graphics.drawString(font, title, left + PAD, top + PAD, text, false);
        int y = top + PAD + 16;
        for (int i = 0; i < rows.length; i++) {
            graphics.drawString(font, rows[i][0], left + PAD, y, 0xFF000000 | LABEL_COLOR, false);
            graphics.drawString(font, rows[i][1], left + PANEL_WIDTH - PAD - font.width(rows[i][1]), y, text, false);
            y += ROW;
            if (i == 3) {
                int bar = PANEL_WIDTH - PAD * 2;
                int filled = limit <= 0 ? 0 : (int) Math.round(bar * Math.min(1.0, memory / (double) limit));
                graphics.fill(left + PAD, y - 4, left + PAD + bar, y - 2, 0x40000000 | LidarHud.COLOR);
                graphics.fill(left + PAD, y - 4, left + PAD + Math.max(filled, memory > 0 ? 1 : 0), y - 2, text);
                y += 6;
            }
        }
        for (int row = top; row < top + panelHeight; row += 2) graphics.fill(left, row, left + PANEL_WIDTH, row + 1, 0x14000000);
    }
}
