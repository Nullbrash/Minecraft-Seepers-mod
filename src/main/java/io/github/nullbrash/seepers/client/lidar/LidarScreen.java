package io.github.nullbrash.seepers.client.lidar;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.lidar.LidarMenu;
import io.github.nullbrash.seepers.lidar.LidarModules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The lidar's own screen (CLAUDE.md 5.3), opened by its key while the lidar is in hand: the module slots, everything
 * the device knows about itself in the hologram's style, the scan pattern buttons (with the pattern module) and the
 * inventory below.
 */
public class LidarScreen extends AbstractContainerScreen<LidarMenu> {
    public static final KeyMapping KEY = new KeyMapping("key.seepers.lidar_screen", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.seepers");
    /** Held, standing still, with view III: the all-around scan. */
    public static final KeyMapping ROUND_KEY = new KeyMapping("key.seepers.lidar_round", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.seepers");
    /** Held: cranks the hand dynamo (with the dynamo module). */
    public static final KeyMapping DYNAMO_KEY = new KeyMapping("key.seepers.lidar_dynamo", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.seepers");

    private static final int STATS_X = 86;
    private static final int ROW = 11;
    /** The area and pattern rows, under the stats. */
    private static final int AREA_Y = 116;
    private static final int PATTERN_Y = 136;
    private static final int BUTTON = 18;
    private static final int LABEL_COLOR = 0x8FB4CC;
    private static final int BACKGROUND = 0xE00A1A26;
    /** Slot colours by module kind, matching the stone on each module: view, density, memory, analysis, filter, water,
     * pattern, echo, dynamo, refresh, beams. */
    private static final int[] KIND_COLORS = {0xA06EDC, 0xE1DCCD, 0xC8281E, 0x4FC46A, 0xB8B8B8, 0x3E7FE0, 0xDC3C32, 0x2AA89A, 0xBE6E46, 0xE8C040, 0x5CC8DC};

    private final LidarIconButton[] patternButtons = new LidarIconButton[LidarModules.Pattern.values().length];
    private LidarIconButton narrower;
    private LidarIconButton wider;

    public LidarScreen(LidarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 246;
        inventoryLabelX = LidarMenu.INVENTORY_X;
        inventoryLabelY = LidarMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        LidarModules.Pattern[] patterns = LidarModules.Pattern.values();
        int right = leftPos + imageWidth - 10;
        for (int i = 0; i < patterns.length; i++) {
            int id = i;
            int x = right - (patterns.length - i) * (BUTTON + 3) + 3;
            patternButtons[i] = addRenderableWidget(new LidarIconButton(x, topPos + PATTERN_Y, BUTTON, i, "",
                    Component.translatable("seepers.lidar.pattern." + patterns[i].id()), () -> pick(id)));
        }
        narrower = addRenderableWidget(new LidarIconButton(right - 3 * (BUTTON + 3) - 46 + 3, topPos + AREA_Y, BUTTON, -1, "<",
                Component.translatable("seepers.lidar.screen.area.narrower"), () -> area(-1)));
        wider = addRenderableWidget(new LidarIconButton(right - BUTTON, topPos + AREA_Y, BUTTON, -1, ">",
                Component.translatable("seepers.lidar.screen.area.wider"), () -> area(1)));
    }

    /** Steps the scan area narrower or wider within what the view module gives. */
    private void area(int step) {
        ItemStack lidar = menu.lidar();
        int count = LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).size();
        int next = LidarModules.areaIndex(lidar) + step;
        if (next >= 0 && next < count) pick(LidarMenu.AREA_BUTTON + next);
    }

    private void pick(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack lidar = menu.lidar();
        boolean module = LidarModules.level(lidar, LidarModules.Kind.PATTERN) > 0;
        LidarModules.Pattern current = LidarModules.pattern(lidar);
        for (int i = 0; i < patternButtons.length; i++) {
            patternButtons[i].visible = module;
            patternButtons[i].selected = current.ordinal() == i;
        }
        int index = LidarModules.areaIndex(lidar);
        narrower.active = index > 0;
        wider.active = index < LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).size() - 1;
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
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < LidarModules.SLOTS && menu.getCarried().isEmpty()) {
            LidarModules.Kind kind = LidarModules.Kind.values()[hoveredSlot.index];
            graphics.renderTooltip(font, Component.translatable("seepers.lidar.slot." + kind.id()), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int text = 0xFF000000 | LidarHud.COLOR;
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0x99000000 | LidarHud.COLOR);
        graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0x99000000 | LidarHud.COLOR);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            if (slot.index < LidarModules.SLOTS) {
                // Each module slot in its module's colour: frame bright, inside dim.
                int color = KIND_COLORS[slot.index];
                graphics.fill(x, y, x + 18, y + 18, 0xCC000000 | color);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF06121A);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x40000000 | color);
            } else {
                graphics.fill(x, y, x + 18, y + 18, 0x40000000 | LidarHud.COLOR);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF06121A);
            }
        }
        stats(graphics, text);
        areaRow(graphics, text);
        for (int row = topPos; row < topPos + imageHeight; row += 2) graphics.fill(leftPos, row, leftPos + imageWidth, row + 1, 0x10000000);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF000000 | LidarHud.COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF000000 | LABEL_COLOR, false);
    }

    /** Everything the device knows about itself, from its modules. */
    private void stats(GuiGraphics graphics, int text) {
        ItemStack lidar = menu.lidar();
        LidarModules.Stats stats = LidarModules.stats(lidar);
        PointCloud cloud = PointCloud.get();
        int memory = cloud == null ? 0 : cloud.count(PointSource.LIDAR);
        Component[][] rows = {
                {Component.translatable("seepers.lidar.screen.beam"), LidarHud.field(stats)},
                {Component.translatable("seepers.lidar.screen.pattern"), Component.translatable("seepers.lidar.pattern." + stats.pattern().id())},
                {Component.translatable("seepers.lidar.screen.range"),
                        Component.translatable("seepers.lidar.screen.range.value", LidarHud.number(ServerConfig.get(ServerConfig.LIDAR_RANGE)))},
                {Component.translatable("seepers.lidar.screen.rate"),
                        Component.translatable("seepers.lidar.screen.rate.value", stats.rate())},
                {Component.translatable("seepers.lidar.screen.memory"),
                        Component.translatable("seepers.lidar.screen.memory.value", LidarHud.grouped(memory), LidarHud.grouped(stats.pointLimit()))},
                {Component.translatable("seepers.lidar.screen.lifetime"), Component.translatable(stats.keepsMemory()
                        ? "seepers.lidar.screen.lifetime.kept" : "seepers.lidar.screen.lifetime.value")},
                {Component.translatable("seepers.lidar.screen.drain"),
                        Component.translatable("seepers.lidar.screen.drain.value", LidarHud.number(Math.round(stats.cost() * 100) / 100.0))},
                {Component.translatable("seepers.lidar.screen.battery"), battery(lidar, stats)},
                {Component.translatable("seepers.lidar.screen.session"),
                        Component.translatable("seepers.lidar.screen.session.value", LidarHud.grouped(LidarScan.sessionPoints()))},
        };
        int left = leftPos + STATS_X;
        int right = leftPos + imageWidth - 8;
        int y = topPos + 8;
        for (Component[] row : rows) {
            graphics.drawString(font, row[0], left, y, 0xFF000000 | LABEL_COLOR, false);
            graphics.drawString(font, row[1], right - font.width(row[1]), y, text, false);
            y += ROW;
        }
    }

    /** The scan area: its label, and its value in the drawn digits between the arrows. */
    private void areaRow(GuiGraphics graphics, int text) {
        ItemStack lidar = menu.lidar();
        LidarModules.Area area = LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).get(LidarModules.areaIndex(lidar));
        graphics.drawString(font, Component.translatable("seepers.lidar.screen.area"), leftPos + STATS_X, topPos + AREA_Y + 5, 0xFF000000 | LABEL_COLOR, false);
        String value = area.field() == LidarModules.Field.CONE ? LidarHud.number(area.value()) + "°" : Math.round(area.value() * 100) + "%";
        int left = narrower.getX() + BUTTON;
        int width = wider.getX() - left;
        int scale = 2;
        LidarGlyphs.draw(graphics, value, left + (width - LidarGlyphs.width(value) * scale) / 2, topPos + AREA_Y + (BUTTON - LidarGlyphs.GLYPH_HEIGHT * scale) / 2,
                scale, text);
        if (LidarModules.level(lidar, LidarModules.Kind.PATTERN) > 0) {
            graphics.drawString(font, Component.translatable("seepers.lidar.screen.pattern"), leftPos + STATS_X, topPos + PATTERN_Y + 5,
                    0xFF000000 | LABEL_COLOR, false);
        }
    }

    private static Component battery(ItemStack lidar, LidarModules.Stats stats) {
        Integer charge = LidarItem.battery(lidar);
        if (charge == null) return Component.translatable("seepers.lidar.screen.battery.none");
        return Component.translatable("seepers.lidar.screen.battery.value", (int) Math.floor(100.0 * charge / BatteryItem.capacity()),
                LidarHud.minutes(charge, stats));
    }
}
