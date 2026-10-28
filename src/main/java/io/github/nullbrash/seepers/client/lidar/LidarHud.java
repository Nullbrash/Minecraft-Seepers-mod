package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.lidar.LidarModules;
import io.github.nullbrash.seepers.config.ServerConfig;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.TranslatableEnum;
import org.jetbrains.annotations.Nullable;

/**
 * The lidar's hologram (CLAUDE.md 5.3), shown while the lidar is in either hand: a wide translucent panel, placed by a
 * client-config preset (top corner by default), in the colour of the points, with scanlines and a
 * faint flicker. In-game data of the device, not debugging: range to the surface under the crosshair, mode and scan
 * speed, then memory with its bar along the bottom edge. Counters run while scanning.
 */
public final class LidarHud {
    public enum Position implements TranslatableEnum {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER, BESIDE_HOTBAR, ABOVE_HOTBAR;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("seepers.configuration.lidarHudPosition." + name().toLowerCase(Locale.ROOT));
        }
    }

    static final int COLOR = 0xC7E6FF;
    private static final int PAD = 4;
    private static final int HOTBAR_HALF = 91;
    private static final int MIN_WIDTH = 150;
    private static final int MARGIN = 4;
    private static final int BESIDE_GAP = 31;
    private static final int BAR = 4;
    private static final int BACKING_ALPHA = 0xD8;
    /** An icon and its gap before the text. */
    private static final int ICON = 10;
    private static final ResourceLocation ICONS = Seepers.id("textures/gui/lidar_icons.png");
    private static final int ICONS_WIDTH = 40;
    /** Below this share of a full battery its charge blinks. */
    private static final double LOW_SHARE = 0.1;
    private static final String SEPARATOR = "  ·  ";

    private static final int NOT_RAISED = Integer.MIN_VALUE;
    private static final int LIFT_GAP = 2;
    /** The overlay message's line with its backdrop. */
    private static final int OVERLAY_HEIGHT = 12;

    /** Memory shown, easing toward the real count so it visibly runs while scanning. */
    private static double shownMemory;
    /** Top of the hologram drawn above the hotbar this frame, or NOT_RAISED. */
    private static int raisedTop = NOT_RAISED;
    private static @Nullable ResourceLocation lifted;

    public static void register(RegisterGuiLayersEvent event) {
        // After the status rows: their real height (armour, absorption, air) is known, and the item name and the
        // overlay message, drawn later, can be lifted above the hologram.
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, Seepers.id("lidar_hud"), LidarHud::render);
    }

    /**
     * Above the hotbar the hologram takes the place of the selected item's name and the overlay message: both are
     * lifted to sit right above it, this frame only.
     */
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        if (raisedTop == NOT_RAISED || event.isCanceled()) return;
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        int status = Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight);
        int h = graphics.guiHeight();
        int nameTop = raisedTop - LIFT_GAP - minecraft.font.lineHeight;
        int shift;
        if (event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            int vanilla = h - Math.max(status, 59) + (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer() ? 14 : 0);
            shift = nameTop - vanilla;
        } else if (event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)) {
            int vanilla = h - Math.max(status + 9, 68) - 4;
            shift = nameTop - LIFT_GAP - OVERLAY_HEIGHT - vanilla;
        } else {
            return;
        }
        if (shift >= 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, shift, 0);
        lifted = event.getName();
    }

    public static void onRenderLayerPost(RenderGuiLayerEvent.Post event) {
        if (lifted != null && lifted.equals(event.getName())) {
            event.getGuiGraphics().pose().popPose();
            lifted = null;
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        raisedTop = NOT_RAISED;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        PointCloud cloud = PointCloud.get();
        if (player == null || cloud == null || minecraft.options.hideGui || minecraft.screen != null) return;
        int memory = cloud.count(PointSource.LIDAR);
        InteractionHand hand = LidarScan.heldHand(player);
        if (hand == null) {
            shownMemory = memory;
            return;
        }
        shownMemory += (memory - shownMemory) * 0.25;
        if (Math.abs(memory - shownMemory) < 1) shownMemory = memory;
        LidarModules.Stats stats = LidarModules.stats(player.getItemInHand(hand));
        int limit = stats.pointLimit();

        Font font = minecraft.font;
        Integer charge = LidarItem.battery(player.getItemInHand(hand));
        // Icons instead of words, each value in a fixed column sized for its widest value, so nothing jumps as values
        // change. Row 1: range, mode, scan speed. Row 2: memory, battery. Charge bar along the top edge, memory along
        // the bottom.
        Component mode = field(stats);
        Component rate = Component.translatable(LidarScan.latched() ? "seepers.lidar.hud.rate_auto" : "seepers.lidar.hud.rate", stats.pointsPerSecond());
        Component idle = Component.translatable("seepers.lidar.hud.idle");
        Component range = range(minecraft, player);
        Component memoryText = Component.translatable("seepers.lidar.hud.memory", grouped((long) shownMemory), grouped(limit));
        Component chargeText = charge == null ? Component.translatable("seepers.lidar.hud.no_battery") : chargeText(charge, stats);
        int separator = font.width(SEPARATOR);
        int rangeWidth = Math.max(font.width(Component.translatable("seepers.lidar.hud.range",
                number(ServerConfig.get(ServerConfig.LIDAR_RANGE)) + ".0")), font.width(Component.translatable("seepers.lidar.hud.no_range")));
        // The sphere's label changes as one stops and walks: sized for both.
        int modeWidth = Math.max(font.width(Component.translatable("seepers.lidar.hud.mode.screen_share", 100)),
                font.width(Component.translatable("seepers.lidar.hud.mode.round_moving")));
        int scanWidth = Math.max(Math.max(font.width(rate), font.width(idle)),
                font.width(Component.translatable("seepers.lidar.hud.rate_auto", stats.pointsPerSecond())));
        int beamWidth = ICON * 3 + rangeWidth + modeWidth + scanWidth + separator * 2;
        int memoryWidth = ICON + font.width(Component.translatable("seepers.lidar.hud.memory", grouped(limit), grouped(limit)));
        int chargeWidth = ICON + Math.max(font.width(chargeText(BatteryItem.capacity(), stats)), font.width(Component.translatable("seepers.lidar.hud.no_battery")));
        int width = Math.max(Math.max(beamWidth, memoryWidth + separator + chargeWidth), MIN_WIDTH) + PAD * 2;
        int height = BAR + 2 * (font.lineHeight + 2) + PAD * 2 - 2 + BAR;

        // Presets (client config); beside the hotbar goes to the side of the hand that holds the lidar.
        boolean right = (hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite()) == HumanoidArm.RIGHT;
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int cx = w / 2;
        int left;
        int top;
        switch (ClientConfig.LIDAR_HUD_POSITION.get()) {
            case TOP_RIGHT -> { left = w - MARGIN - width; top = MARGIN; }
            case TOP_CENTER -> { left = cx - width / 2; top = MARGIN; }
            // Past the off-hand slot, which vanilla draws just outside the hotbar on the other side.
            case BESIDE_HOTBAR -> { left = right ? cx + HOTBAR_HALF + BESIDE_GAP : cx - HOTBAR_HALF - BESIDE_GAP - width; top = h - height; }
            case ABOVE_HOTBAR -> {
                left = cx - width / 2;
                top = h - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) - 1 - height;
                raisedTop = top;
            }
            default -> { left = MARGIN; top = MARGIN; }
        }

        // A faint flicker, brighter while the beam runs.
        long now = System.currentTimeMillis();
        float flicker = 0.92F + 0.08F * Mth.sin(now / 37.0F) * Mth.sin(now / 91.0F);
        float strength = (LidarScan.scanning() ? 1.0F : 0.85F) * flicker;
        int text = alpha(strength) << 24 | COLOR;

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        // A nearly opaque backing: points drawn behind it must not show through the text.
        graphics.fill(0, 0, width, height, BACKING_ALPHA << 24 | 0x06121A);
        int capacity = BatteryItem.capacity();
        bar(graphics, 0, width, charge == null ? 0 : charge / (double) capacity, strength, text);
        int y = BAR + PAD;
        int x = PAD;
        x = icon(graphics, Icon.RANGE, x, y, strength);
        graphics.drawString(font, range, x, y, text, false);
        x += rangeWidth;
        graphics.drawString(font, SEPARATOR, x, y, text, false);
        x += separator;
        x = icon(graphics, Icon.ROSETTE, x, y, strength);
        graphics.drawString(font, mode, x, y, text, false);
        x += modeWidth;
        graphics.drawString(font, SEPARATOR, x, y, text, false);
        x += separator;
        x = icon(graphics, Icon.SCAN, x, y, strength);
        graphics.drawString(font, LidarScan.scanning() ? rate : idle, x, y, text, false);
        y += font.lineHeight + 2;
        x = icon(graphics, Icon.MEMORY, PAD, y, strength);
        graphics.drawString(font, memoryText, x, y, text, false);
        // A low or missing battery blinks.
        boolean low = charge == null || charge < capacity * LOW_SHARE;
        float chargeStrength = low && (now / 400) % 2 == 0 ? strength * 0.35F : strength;
        x = icon(graphics, Icon.BATTERY, width - PAD - chargeWidth, y, chargeStrength);
        graphics.drawString(font, chargeText, x, y, alpha(chargeStrength) << 24 | COLOR, false);
        bar(graphics, height - BAR, width, limit <= 0 ? 0 : shownMemory / limit, strength, text);
        // Scanlines: every other row a touch darker, drifting slowly.
        int offset = (int) (now / 120 % 2);
        for (int row = offset; row < height; row += 2) graphics.fill(0, row, width, row + 1, 0x18000000);
        graphics.pose().popPose();
    }

    private static Component chargeText(int charge, LidarModules.Stats stats) {
        int capacity = BatteryItem.capacity();
        return Component.translatable("seepers.lidar.hud.charge", (int) Math.floor(100.0 * charge / capacity), minutes(charge, stats));
    }

    /** Minutes of scanning left at this lidar's drain. */
    static int minutes(int charge, LidarModules.Stats stats) {
        return (int) Math.ceil(charge / (1200.0 * Math.max(stats.cost(), 0.001)));
    }

    /** The beam's field: the chosen area, or the special scan while its key is held (180, 360, repair). */
    static Component field(LidarModules.Stats stats) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && LidarScan.roundKeyHeld(player)) {
            boolean still = LidarScan.standing();
            if (stats.refresh() && still) return Component.translatable("seepers.lidar.hud.mode.repair");
            if (stats.view() == 2) return Component.translatable("seepers.lidar.hud.mode.wide");
            if (stats.view() >= 3) {
                return Component.translatable(still ? "seepers.lidar.hud.mode.round" : "seepers.lidar.hud.mode.round_moving");
            }
            return Component.translatable("seepers.lidar.hud.mode.repair_moving");
        }
        return area(stats.field(), stats.field() == LidarModules.Field.CONE ? stats.coneHalf() * 2 : stats.screenShare());
    }

    static Component area(LidarModules.Field field, double value) {
        return field == LidarModules.Field.CONE ? Component.translatable("seepers.lidar.hud.mode", number(value))
                : Component.translatable("seepers.lidar.hud.mode.screen_share", Math.round(value * 100));
    }

    /** A full-width bar: faint track, lit share. */
    private static void bar(GuiGraphics graphics, int y, int width, double share, float strength, int lit) {
        graphics.fill(0, y, width, y + BAR, alpha(strength * 0.25F) << 24 | COLOR);
        int filled = (int) Math.round(width * Math.min(1.0, Math.max(0.0, share)));
        if (share > 0) graphics.fill(0, y, Math.max(filled, 1), y + BAR, lit);
    }

    /** Icons of the hologram, 8x8 each in one sheet, tinted with the panel's colour. */
    enum Icon { RANGE, ROSETTE, SCAN, MEMORY, BATTERY }

    /** Draws the icon at x and returns where its text starts. */
    private static int icon(GuiGraphics graphics, Icon icon, int x, int y, float strength) {
        graphics.setColor(((COLOR >> 16) & 0xFF) / 255.0F, ((COLOR >> 8) & 0xFF) / 255.0F, (COLOR & 0xFF) / 255.0F, Mth.clamp(strength, 0, 1));
        RenderSystem.enableBlend();
        graphics.blit(ICONS, x, y, icon.ordinal() * 8, 0, 8, 8, ICONS_WIDTH, 8);
        graphics.setColor(1, 1, 1, 1);
        return x + ICON;
    }

    private static Component range(Minecraft minecraft, LocalPlayer player) {
        double reach = ServerConfig.get(ServerConfig.LIDAR_RANGE);
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return Component.translatable("seepers.lidar.hud.no_range");
        return Component.translatable("seepers.lidar.hud.range", String.format(Locale.ROOT, "%.1f", hit.getLocation().distanceTo(eye)));
    }

    /** 1048576 → "1 048 576": long counts read at a glance. */
    public static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Text alpha below 4 is drawn opaque by vanilla. */
    static int alpha(float strength) {
        return Math.max(4, Math.round(Mth.clamp(strength, 0, 1) * 255));
    }

    private LidarHud() {}
}
