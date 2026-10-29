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
 * in), the memory card's reader with its erase button under a flip cover and its author's latching lock buttons, a knob for the
 * scan area and a rotary switch for the pattern, the battery under a screwed lid — and the inventory below. The lid
 * closes with the screen.
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
    /** The knob and the rotary switch under the display: their centre line. */
    private static final int CONTROLS_Y = 143;
    private static final int KNOB = 22;
    /** The pattern switch, its name display to the left and its icon display to the right. */
    private static final int PATTERN_SWITCH_X = 226;
    private static final int PATTERN_NAME_WIDTH = 58;
    private static final int PATTERN_ICON_WIDTH = 18;
    /** The battery bay's lid, bottom left, held by the body's corner screw. */
    private static final int LID_LEFT = 5;
    private static final int LID_TOP = 98;
    private static final int LID_RIGHT = 54;
    private static final int LID_BOTTOM = 153;

    private static final int LABEL_COLOR = 0x8FB4CC;
    private static final int BODY = 0xFF1C2329;
    private static final int PANEL = 0xFF232C33;
    private static final int EDGE_LIGHT = 0xFF3A4650;
    private static final int EDGE_DARK = 0xFF0C1013;
    private static final int RECESS = 0xFF06121A;
    /** Socket colours by module kind, matching the stone on each module: view, density, memory card, analysis, filter,
     * water, pattern, echo, dynamo, refresh, beams. */
    private static final int[] KIND_COLORS = {0xA06EDC, 0xE1DCCD, 0xD6AA46, 0x4FC46A, 0xB8B8B8, 0x3E7FE0, 0xDC3C32, 0x2AA89A, 0xBE6E46, 0xE8C040, 0x5CC8DC};

    private LidarControls.Knob area;
    private LidarControls.RotarySwitch pattern;
    private LidarControls.GuardedButton erase;
    private LidarControls.LatchButton lockWrite;
    private LidarControls.LatchButton lockCopy;

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
        int y = topPos + CONTROLS_Y - KNOB / 2;
        area = addRenderableWidget(new LidarControls.Knob(leftPos + DISPLAY_LEFT + 4, y, KNOB, Component.translatable("seepers.lidar.screen.area.knob"),
                () -> LidarModules.areaIndex(menu.lidar()),
                () -> LidarModules.areas(LidarModules.level(menu.lidar(), LidarModules.Kind.VIEW)).size(),
                index -> pick(LidarMenu.AREA_BUTTON + index)));
        pattern = addRenderableWidget(new LidarControls.RotarySwitch(leftPos + PATTERN_SWITCH_X, y, KNOB,
                Component.translatable("seepers.lidar.screen.pattern.switch"), LidarModules.Pattern.values().length,
                () -> LidarModules.pattern(menu.lidar()).ordinal(), this::pick));
        int[] reader = LidarMenu.SOCKETS[LidarModules.Kind.MEMORY.ordinal()];
        erase = addRenderableWidget(new LidarControls.GuardedButton(leftPos + reader[0] - 24, topPos + reader[1] - 2,
                Component.translatable("seepers.lidar.screen.card.erase"), Component.translatable("seepers.lidar.screen.card.erase_confirm"),
                () -> pick(LidarMenu.ERASE_BUTTON)));
        lockWrite = addRenderableWidget(new LidarControls.LatchButton(leftPos + reader[0] - 22, topPos + reader[1] + 24,
                Component.translatable("seepers.lidar.screen.card.lock_write"), () -> MemoryCardItem.data(LidarModules.card(menu.lidar())).noWrite(),
                () -> pick(LidarMenu.LOCK_WRITE_BUTTON)));
        lockCopy = addRenderableWidget(new LidarControls.LatchButton(leftPos + reader[0] + 2, topPos + reader[1] + 24,
                Component.translatable("seepers.lidar.screen.card.lock_copy"), () -> MemoryCardItem.data(LidarModules.card(menu.lidar())).noCopy(),
                () -> pick(LidarMenu.LOCK_COPY_BUTTON)));
        menu.batteryOpen = false;
        // The body's bottom left corner screw holds the lid; taken out, it lies above the bay.
        addRenderableWidget(new LidarControls.Screw(leftPos + 4, topPos + DEVICE_HEIGHT - 12,
                leftPos + 40, topPos + 76,
                Component.translatable("seepers.lidar.screen.battery.screw"), Component.translatable("seepers.lidar.screen.battery.screw_out"),
                () -> menu.batteryOpen, () -> menu.batteryOpen = !menu.batteryOpen));
    }

    private void pick(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack lidar = menu.lidar();
        pattern.visible = LidarModules.level(lidar, LidarModules.Kind.PATTERN) > 0;
        ItemStack card = LidarModules.card(lidar);
        CardData data = MemoryCardItem.data(card);
        boolean author = data.author().isPresent() && minecraft != null && minecraft.player != null && data.author().get().equals(minecraft.player.getUUID());
        erase.visible = !card.isEmpty() && data.id().isPresent() && !data.noWrite();
        lockWrite.visible = author;
        lockCopy.visible = author;
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
        // The fourth corner's screw is the battery lid's own (a control).
        for (int[] screw : new int[][] {{x0 + 8, y0 + 8}, {x1 - 8, y0 + 8}, {x1 - 8, y1 - 8}}) {
            LidarControls.screwHead(graphics, screw[0], screw[1], false);
        }
        display(graphics);
        for (Slot slot : menu.slots) {
            if (slot.index > LidarMenu.BATTERY_SLOT) continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            if (slot.index == LidarMenu.BATTERY_SLOT) {
                battery(graphics, x, y, menu.batteryOpen);
            } else if (slot.index == LidarModules.Kind.MEMORY.ordinal()) {
                reader(graphics, x, y, slot.hasItem());
            } else {
                socket(graphics, x, y, KIND_COLORS[slot.index], slot.hasItem());
            }
        }
        // The pocket the inventory sits in, apart from the device, with its own edges.
        int px0 = x0 + LidarMenu.INVENTORY_X - 7;
        int py0 = topPos + LidarMenu.INVENTORY_Y - 15;
        int px1 = x0 + LidarMenu.INVENTORY_X + 169;
        int py1 = y0 + imageHeight;
        graphics.fill(px0, py0, px1, py1, 0xF00A1A26);
        graphics.fill(px0, py0, px1, py0 + 1, EDGE_LIGHT);
        graphics.fill(px0, py0, px0 + 1, py1, EDGE_LIGHT);
        graphics.fill(px0, py1 - 1, px1, py1, EDGE_DARK);
        graphics.fill(px1 - 1, py0, px1, py1, EDGE_DARK);
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

    /**
     * The battery bay: under its lid (the panel's own colour, seams, a screw), or open — a recess where the battery
     * stands upright between a spring contact on its top nub and a plate under it.
     */
    private void battery(GuiGraphics graphics, int x, int y, boolean open) {
        int l = leftPos + LID_LEFT;
        int t = topPos + LID_TOP;
        int r = leftPos + LID_RIGHT;
        int b = topPos + LID_BOTTOM;
        if (!open) {
            graphics.fill(l - 1, t - 1, r + 1, b + 1, EDGE_DARK);
            graphics.fill(l, t, r, b, PANEL);
            graphics.fill(l, t, r, t + 1, EDGE_LIGHT);
            graphics.fill(l, t, l + 1, b, EDGE_LIGHT);
            for (int i = 0; i < 3; i++) graphics.fill(l + 8, t + 12 + i * 5, r - 8, t + 13 + i * 5, EDGE_DARK);
            graphics.drawString(font, Component.translatable("seepers.lidar.screen.battery.lid"), l + (r - l - font.width(Component.translatable("seepers.lidar.screen.battery.lid"))) / 2,
                    t + 3, 0xFF000000 | LABEL_COLOR, false);
            return;
        }
        graphics.fill(l - 1, t - 1, r + 1, b + 1, EDGE_DARK);
        graphics.fill(l, t, r, b, 0xFF11171C);
        // Spring contact on the battery's top nub, plate under its base.
        int cx = x + 9;
        graphics.fill(cx - 3, y - 6, cx + 3, y - 5, 0xFFB08A38);
        graphics.fill(cx - 1, y - 5, cx + 1, y + 1, 0xFFD6AA46);
        graphics.fill(cx - 2, y - 3, cx + 2, y - 2, 0xFFB08A38);
        graphics.fill(x + 3, y + 18, x + 15, y + 20, 0xFFD6AA46);
        graphics.fill(x + 2, y + 20, x + 16, y + 21, 0xFFB08A38);
        graphics.fill(x, y, x + 18, y + 18, 0x55000000 | LidarHud.COLOR);
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
        // The area knob's own small display beside it, in the device's digits.
        ItemStack lidar = menu.lidar();
        LidarModules.Area current = LidarModules.areas(LidarModules.level(lidar, LidarModules.Kind.VIEW)).get(LidarModules.areaIndex(lidar));
        String value = current.field() == LidarModules.Field.CONE ? LidarHud.number(current.value()) + "°" : Math.round(current.value() * 100) + "%";
        int dl = area.getX() + KNOB + 6;
        int dt = topPos + CONTROLS_Y - 9;
        glass(graphics, dl, dt, 50);
        LidarGlyphs.draw(graphics, value, dl + (50 - LidarGlyphs.width(value) * 2) / 2, dt + (18 - LidarGlyphs.GLYPH_HEIGHT * 2) / 2, 2, text);
        controlLabel(graphics, Component.translatable("seepers.lidar.screen.beam"), area.getX(), dt);
        // The pattern switch: its name on a display to the left, its icon on a small one to the right.
        if (LidarModules.level(lidar, LidarModules.Kind.PATTERN) > 0) {
            LidarModules.Pattern chosen = LidarModules.pattern(lidar);
            int nl = pattern.getX() - PATTERN_NAME_WIDTH - 6;
            glass(graphics, nl, dt, PATTERN_NAME_WIDTH);
            Component name = Component.translatable("seepers.lidar.pattern." + chosen.id());
            graphics.drawString(font, name, nl + (PATTERN_NAME_WIDTH - font.width(name)) / 2, dt + 5, text, false);
            int il = pattern.getX() + KNOB + 6;
            glass(graphics, il, dt, PATTERN_ICON_WIDTH);
            LidarGlyphs.pattern(graphics, chosen.ordinal(), il + (PATTERN_ICON_WIDTH - LidarGlyphs.PATTERN_SIZE) / 2,
                    dt + (18 - LidarGlyphs.PATTERN_SIZE) / 2, text);
            controlLabel(graphics, Component.translatable("seepers.lidar.screen.pattern"), nl, dt);
        }
    }

    /** A small display of the controls row: bezel, rim, glass; 18 high. */
    private static void glass(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x - 2, y - 2, x + width + 2, y + 20, EDGE_DARK);
        graphics.fill(x - 1, y - 1, x + width + 1, y + 19, 0x80000000 | LidarHud.COLOR);
        graphics.fill(x, y, x + width, y + 18, 0xFF07141C);
    }

    /** A control's name engraved on the panel just above it. */
    private void controlLabel(GuiGraphics graphics, Component label, int x, int displayTop) {
        graphics.drawString(font, label, x, displayTop - 13, 0xFF000000 | LABEL_COLOR, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The model's code, stamped on the body (LIDA-RA is the starting model, this the second).
        graphics.drawString(font, Component.translatable("seepers.lidar.model.rb"), titleLabelX, titleLabelY, 0xFF000000 | LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF000000 | LABEL_COLOR, false);
    }

    /** Everything the device knows about itself, from its modules, card and battery. */
    private void stats(GuiGraphics graphics, int left, int right, int y, int text) {
        ItemStack lidar = menu.lidar();
        LidarModules.Stats stats = LidarModules.stats(lidar);
        PointCloud cloud = PointCloud.get();
        int memory = cloud == null ? 0 : cloud.count(PointSource.LIDAR);
        Component[][] rows = {
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
