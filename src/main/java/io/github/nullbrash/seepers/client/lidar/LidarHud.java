package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ClientConfig;
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
        int limit = PointSource.LIDAR.capacity();

        Font font = minecraft.font;
        // The beam in fixed columns sized for their widest values, so nothing jumps as they change; memory under it,
        // its bar along the panel's whole bottom edge.
        Component mode = Component.translatable("seepers.lidar.hud.mode", number(ServerConfig.get(ServerConfig.LIDAR_CONE_DEGREES)));
        Component rate = Component.translatable("seepers.lidar.hud.rate", ServerConfig.get(ServerConfig.LIDAR_POINTS_PER_SECOND));
        Component idle = Component.translatable("seepers.lidar.hud.idle");
        Component range = range(minecraft, player);
        Component memoryText = Component.translatable("seepers.lidar.hud.memory", grouped((long) shownMemory), grouped(limit));
        int separator = font.width(SEPARATOR);
        int modeWidth = font.width(mode);
        int scanWidth = Math.max(font.width(rate), font.width(idle));
        int rangeWidth = Math.max(font.width(Component.translatable("seepers.lidar.hud.range",
                number(ServerConfig.get(ServerConfig.LIDAR_RANGE)) + ".0")), font.width(Component.translatable("seepers.lidar.hud.no_range")));
        int beamWidth = modeWidth + separator + scanWidth + separator + rangeWidth;
        int memoryWidth = font.width(Component.translatable("seepers.lidar.hud.memory", grouped(limit), grouped(limit)));
        int width = Math.max(Math.max(beamWidth, memoryWidth), MIN_WIDTH) + PAD * 2;
        int height = 2 * (font.lineHeight + 2) + PAD * 2 - 2 + BAR;

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
        float strength = (LidarScan.scanning() ? 1.0F : 0.7F) * flicker;
        int text = alpha(strength) << 24 | COLOR;

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0);
        graphics.fill(0, 0, width, height, Math.round(0x48 * strength) << 24 | 0x0A1A26);
        graphics.fill(0, 0, width, 1, alpha(strength * 0.6F) << 24 | COLOR);
        int y = PAD;
        int x = PAD;
        graphics.drawString(font, range, x, y, text, false);
        x += rangeWidth;
        graphics.drawString(font, SEPARATOR, x, y, text, false);
        x += separator;
        graphics.drawString(font, mode, x, y, text, false);
        x += modeWidth;
        graphics.drawString(font, SEPARATOR, x, y, text, false);
        x += separator;
        graphics.drawString(font, LidarScan.scanning() ? rate : idle, x, y, text, false);
        y += font.lineHeight + 2;
        graphics.drawString(font, memoryText, PAD, y, text, false);
        int filled = limit <= 0 ? 0 : (int) Math.round(width * Math.min(1.0, shownMemory / limit));
        graphics.fill(0, height - BAR, width, height, alpha(strength * 0.25F) << 24 | COLOR);
        graphics.fill(0, height - BAR, Math.max(filled, shownMemory > 0 ? 1 : 0), height, text);
        // Scanlines: every other row a touch darker, drifting slowly.
        int offset = (int) (now / 120 % 2);
        for (int row = offset; row < height; row += 2) graphics.fill(0, row, width, row + 1, 0x18000000);
        graphics.pose().popPose();
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
    static String grouped(long value) {
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
