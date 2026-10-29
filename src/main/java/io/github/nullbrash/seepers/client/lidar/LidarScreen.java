package io.github.nullbrash.seepers.client.lidar;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.MemoryCardItem;
import io.github.nullbrash.seepers.lidar.CardData;
import io.github.nullbrash.seepers.lidar.LidarMenu;
import io.github.nullbrash.seepers.lidar.LidarModules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The lidar's own screen (CLAUDE.md 5.3), opened by its key while the lidar is in hand: the device itself, as if held
 * — a glowing display of everything it knows in the middle, module sockets along its sides (a lit diode by each module
 * in), the memory card's reader with its erase button and its author's locks, the battery compartment, physical
 * buttons for the scan area and pattern — and the inventory below.
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

    private static final int DEVICE_HEIGHT = 158;
    /** The display, and the button row under it. */
    private static final int DISPLAY_LEFT = 58;
    private static final int DISPLAY_TOP = 10;
    private static final int DISPLAY_RIGHT = 282;
    private static final int DISPLAY_BOTTOM = 116;
    private static final int ROW = 10;
    private static final int CONTROLS_Y = 122;
    private static final int BUTTON = 18;
    /** The erase button wants a second press within this many ticks. */
    private static final int ERASE_CONFIRM_TICKS = 60;

    private static final int LABEL_COLOR = 0x8FB4CC;
    private static final int BODY = 0xFF1C2329;
    private static final int PANEL = 0xFF232C33;
    private static final int EDGE_LIGHT = 0xFF3A4650;
    private static final int EDGE_DARK = 0xFF0C1013;
    private static final int RECESS = 0xFF06121A;
    /** Socket colours by module kind, matching the stone on each module: view, density, memory card, analysis, filter,
     * water, pattern, echo, dynamo, refresh, beams. */
    private static final int[] KIND_COLORS = {0xA06EDC, 0xE1DCCD, 0xD6AA46, 0x4FC46A, 0xB8B8B8, 0x3E7FE0, 0xDC3C32, 0x2AA89A, 0xBE6E46, 0xE8C040, 0x5CC8DC};

    private final LidarIconButton[] patternButtons = new LidarIconButton[LidarModules.Pattern.values().length];
    private LidarIconButton narrower;
    private LidarIconButton wider;
    private LidarIconButton erase;
    private LidarIconButton lockWrite;
    private LidarIconButton lockCopy;
    private int eraseArmed;

    public LidarScreen(LidarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340;
        imageHeight = LidarMenu.INVENTORY_Y + 76;
        titleLabelX = 14;
        titleLabelY = 6;
        inventoryLabelX = LidarMenu.INVENTORY_X;
        inventoryLabelY = LidarMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        LidarModules.Pattern[] patterns = LidarModules.Pattern.values();
        for (int i = 0; i < patterns.length; i++) {
            int id = i;
            int x = leftPos + DISPLAY_RIGHT - (patterns.length - i) * (BUTTON + 2) + 2;
            patternButtons[i] = addRenderableWidget(new LidarIconButton(x, topPos + CONTROLS_Y, BUTTON, i, "",
                    Component.translatable("seepers.lidar.pattern." + patterns[i].id()), () -> pick(id)));
        }
        narrower = addRenderableWidget(new LidarIconButton(leftPos + DISPLAY_LEFT + 2, topPos + CONTROLS_Y, BUTTON, -1, "<",
                Component.translatable("seepers.lidar.screen.area.narrower"), () -> area(-1)));
        wider = addRenderableWidget(new LidarIconButton(leftPos + DISPLAY_LEFT + 2 + BUTTON + 56, topPos + CONTROLS_Y, BUTTON, -1, ">",
                Component.translatable("seepers.lidar.screen.area.wider"), () -> area(1)));
        int[] reader = LidarMenu.SOCKETS[LidarModules.Kind.MEMORY.ordinal()];
        erase = addRenderableWidget(new LidarIconButton(leftPos + reader[0] - 23, topPos + reader[1] - 1, BUTTON, -1, "x",
                Component.translatable("seepers.lidar.screen.card.erase"), this::erase));
        lockWrite = addRenderableWidget(new LidarIconButton(leftPos + reader[0] - 23, topPos + reader[1] + 21, BUTTON, -1, "l",
                Component.translatable("seepers.lidar.screen.card.lock_write"), () -> pick(LidarMenu.LOCK_WRITE_BUTTON)));
        lockCopy = addRenderableWidget(new LidarIconButton(leftPos + reader[0] - 1, topPos + reader[1] + 21, BUTTON, -1, "c",
                Component.translatable("seepers.lidar.screen.card.lock_copy"), () -> pick(LidarMenu.LOCK_COPY_BUTTON)));
    }

    /** Steps the scan area narrower or wider within what the view module gives. */
    private void area(int step) {
        ItemStack lidar = menu.lidar();
        int count = LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).size();
        int next = LidarModules.areaIndex(lidar) + step;
        if (next >= 0 && next < count) pick(LidarMenu.AREA_BUTTON + next);
    }

    /** First press arms the erase, a second one soon after erases. */
    private void erase() {
        if (eraseArmed > 0) {
            eraseArmed = 0;
            pick(LidarMenu.ERASE_BUTTON);
        } else {
            eraseArmed = ERASE_CONFIRM_TICKS;
        }
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
        ItemStack card = LidarModules.card(lidar);
        CardData data = MemoryCardItem.data(card);
        boolean author = data.author().isPresent() && minecraft != null && minecraft.player != null && data.author().get().equals(minecraft.player.getUUID());
        if (eraseArmed > 0) eraseArmed--;
        erase.visible = !card.isEmpty() && data.id().isPresent();
        erase.active = !data.noWrite();
        erase.selected = eraseArmed > 0;
        erase.setTooltip(Tooltip.create(Component.translatable(eraseArmed > 0 ? "seepers.lidar.screen.card.erase_confirm"
                : data.noWrite() ? "seepers.lidar.screen.card.erase_locked" : "seepers.lidar.screen.card.erase")));
        lockWrite.visible = author;
        lockCopy.visible = author;
        lockWrite.selected = data.noWrite();
        lockCopy.selected = data.noCopy();
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
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index <= LidarMenu.BATTERY_SLOT && menu.getCarried().isEmpty()) {
            String key = hoveredSlot.index == LidarMenu.BATTERY_SLOT ? "battery" : LidarModules.Kind.values()[hoveredSlot.index].id();
            graphics.renderTooltip(font, Component.translatable("seepers.lidar.slot." + key), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        int x1 = leftPos + imageWidth;
        int y1 = topPos + DEVICE_HEIGHT;
        // The body: bevelled edges, a raised panel, ribbed grips at the sides, screws in the corners.
        graphics.fill(x0, y0, x1, y1, BODY);
        graphics.fill(x0, y0, x1, y0 + 1, EDGE_LIGHT);
        graphics.fill(x0, y0, x0 + 1, y1, EDGE_LIGHT);
        graphics.fill(x0, y1 - 1, x1, y1, EDGE_DARK);
        graphics.fill(x1 - 1, y0, x1, y1, EDGE_DARK);
        graphics.fill(x0 + 4, y0 + 4, x1 - 4, y1 - 4, PANEL);
        for (int y = y0 + 20; y < y1 - 20; y += 4) {
            graphics.fill(x0 + 1, y, x0 + 4, y + 2, EDGE_DARK);
            graphics.fill(x1 - 4, y, x1 - 1, y + 2, EDGE_DARK);
        }
        for (int[] screw : new int[][] {{x0 + 6, y0 + 6}, {x1 - 10, y0 + 6}, {x0 + 6, y1 - 10}, {x1 - 10, y1 - 10}}) {
            graphics.fill(screw[0], screw[1], screw[0] + 4, screw[1] + 4, 0xFF4A5560);
            graphics.fill(screw[0] + 1, screw[1] + 2, screw[0] + 3, screw[1] + 3, EDGE_DARK);
        }
        display(graphics);
        for (Slot slot : menu.slots) {
            if (slot.index > LidarMenu.BATTERY_SLOT) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            if (slot.index == LidarMenu.BATTERY_SLOT) {
                battery(graphics, x, y);
            } else if (slot.index == LidarModules.Kind.MEMORY.ordinal()) {
                reader(graphics, x, y, slot.hasItem());
            } else {
                socket(graphics, x, y, KIND_COLORS[slot.index], slot.hasItem());
            }
        }
        // The pocket the inventory sits in.
        graphics.fill(x0 + LidarMenu.INVENTORY_X - 6, topPos + LidarMenu.INVENTORY_Y - 14, x0 + LidarMenu.INVENTORY_X + 168, y0 + imageHeight, 0xE00A1A26);
        for (Slot slot : menu.slots) {
            if (slot.index <= LidarMenu.BATTERY_SLOT) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0x40000000 | LidarHud.COLOR);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
        }
    }

    /** A module socket: recessed, rimmed in its module's colour, a diode above that lights with a module in. */
    private static void socket(GuiGraphics graphics, int x, int y, int color, boolean lit) {
        graphics.fill(x - 1, y - 1, x + 19, y + 19, EDGE_DARK);
        graphics.fill(x, y, x + 18, y + 18, 0x99000000 | color);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
        graphics.fill(x + 7, y - 4, x + 11, y - 2, lit ? 0xFF000000 | color : 0xFF2A3238);
    }

    /** The memory card's reader: a slot with gold contacts and its label. */
    private void reader(GuiGraphics graphics, int x, int y, boolean lit) {
        graphics.drawString(font, Component.translatable("seepers.lidar.screen.card"), x + 9 - font.width(Component.translatable("seepers.lidar.screen.card")) / 2,
                y - 12, 0xFF000000 | LABEL_COLOR, false);
        socket(graphics, x, y, KIND_COLORS[LidarModules.Kind.MEMORY.ordinal()], lit);
        for (int i = 0; i < 4; i++) graphics.fill(x + 3 + i * 4, y + 15, x + 5 + i * 4, y + 17, 0xFFB08A38);
    }

    /** The battery compartment: a wider recess with its contacts. */
    private static void battery(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 8, y - 2, x + 26, y + 20, EDGE_DARK);
        graphics.fill(x - 7, y - 1, x + 25, y + 19, 0xFF141A1F);
        graphics.fill(x - 5, y + 7, x - 2, y + 11, 0xFFB08A38);
        graphics.fill(x + 20, y + 7, x + 23, y + 11, 0xFFB08A38);
        graphics.fill(x, y, x + 18, y + 18, 0x66000000 | LidarHud.COLOR);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
    }

    /** The display: bezel, glass, everything the device knows, the scan area's value; scanlines over it. */
    private void display(GuiGraphics graphics) {
        int l = leftPos + DISPLAY_LEFT;
        int t = topPos + DISPLAY_TOP;
        int r = leftPos + DISPLAY_RIGHT;
        int b = topPos + DISPLAY_BOTTOM;
        graphics.fill(l - 3, t - 3, r + 3, b + 3, EDGE_DARK);
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x80000000 | LidarHud.COLOR);
        graphics.fill(l, t, r, b, 0xFF07141C);
        int text = 0xFF000000 | LidarHud.COLOR;
        stats(graphics, l + 6, r - 6, t + 5, text);
        for (int row = t; row < b; row += 2) graphics.fill(l, row, r, row + 1, 0x14000000);
        // The area's value between its buttons, in the device's own digits.
        ItemStack lidar = menu.lidar();
        LidarModules.Area area = LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).get(LidarModules.areaIndex(lidar));
        String value = area.field() == LidarModules.Field.CONE ? LidarHud.number(area.value()) + "°" : Math.round(area.value() * 100) + "%";
        int left = narrower.getX() + BUTTON;
        int width = wider.getX() - left;
        graphics.fill(left + 2, topPos + CONTROLS_Y, left + width - 2, topPos + CONTROLS_Y + BUTTON, 0xFF07141C);
        LidarGlyphs.draw(graphics, value, left + (width - LidarGlyphs.width(value) * 2) / 2, topPos + CONTROLS_Y + (BUTTON - LidarGlyphs.GLYPH_HEIGHT * 2) / 2,
                2, text);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF000000 | LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF000000 | LABEL_COLOR, false);
    }

    /** Everything the device knows about itself, from its modules, card and battery. */
    private void stats(GuiGraphics graphics, int left, int right, int y, int text) {
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
                {Component.translatable("seepers.lidar.screen.memory"), stats.card() == 0 ? Component.translatable("seepers.lidar.screen.lifetime.value",
                        LidarHud.number(ServerConfig.get(ServerConfig.LIDAR_UNSAVED_SECONDS)))
                        : Component.translatable("seepers.lidar.screen.memory.value", LidarHud.grouped(memory), LidarHud.grouped(stats.pointLimit()))},
                {Component.translatable("seepers.lidar.screen.card"), card(lidar)},
                {Component.translatable("seepers.lidar.screen.drain"),
                        Component.translatable("seepers.lidar.screen.drain.value", LidarHud.number(Math.round(stats.cost() * 100) / 100.0))},
                {Component.translatable("seepers.lidar.screen.battery"), battery(lidar, stats)},
                {Component.translatable("seepers.lidar.screen.session"),
                        Component.translatable("seepers.lidar.screen.session.value", LidarHud.grouped(LidarScan.sessionPoints()))},
        };
        for (Component[] row : rows) {
            graphics.drawString(font, row[0], left, y, 0xFF000000 | LABEL_COLOR, false);
            graphics.drawString(font, row[1], right - font.width(row[1]), y, text, false);
            y += ROW;
        }
    }

    /** The card in the reader: its level, its author, whether it is locked. */
    private static Component card(ItemStack lidar) {
        ItemStack card = LidarModules.card(lidar);
        if (card.isEmpty()) return Component.translatable("seepers.lidar.screen.card.none");
        CardData data = MemoryCardItem.data(card);
        int level = ((MemoryCardItem) card.getItem()).level();
        Component name = Component.translatable("seepers.lidar.screen.card.level", "I".repeat(level));
        if (data.noWrite()) return Component.translatable("seepers.lidar.screen.card.locked", name);
        if (data.signed()) return Component.translatable("seepers.lidar.screen.card.signed", name, data.authorName());
        return name;
    }

    private static Component battery(ItemStack lidar, LidarModules.Stats stats) {
        Integer charge = LidarItem.battery(lidar);
        if (charge == null) return Component.translatable("seepers.lidar.screen.battery.none");
        return Component.translatable("seepers.lidar.screen.battery.value", (int) Math.floor(100.0 * charge / BatteryItem.capacity()),
                LidarHud.minutes(charge, stats));
    }
}
