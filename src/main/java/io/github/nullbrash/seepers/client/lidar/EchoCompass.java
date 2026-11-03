package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.StarterLidarItem;
import io.github.nullbrash.seepers.lidar.LidarModules;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Цель берётся из ванильной синхронизации смерти; звук остаётся личным сигналом прибора. */
public final class EchoCompass {
    public static final int HEIGHT = 40;
    private static int cooldown;

    public static boolean enabled(ItemStack lidar) {
        return LidarModules.level(lidar, LidarModules.Kind.ECHO) > 0
                && lidar.getOrDefault(SeepersComponents.ECHO_ENABLED.get(), true) && LidarItem.canScan(lidar);
    }

    private static GlobalPos target(ItemStack lidar) {
        var player = Minecraft.getInstance().player;
        if (player == null || !enabled(lidar)) return null;
        return player.getLastDeathLocation().filter(pos -> pos.dimension().equals(player.level().dimension())).orElse(null);
    }

    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { cooldown = 0; return; }
        if (mc.isPaused()) return;
        if (cooldown > 0) cooldown--;
        var hand = LidarScan.heldHand(mc.player);
        if (hand == null || mc.screen != null) return;
        GlobalPos target = target(mc.player.getItemInHand(hand));
        if (target == null) return;
        double radius = ServerConfig.get(ServerConfig.ECHO_SOUND_RADIUS);
        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(target.pos())) <= radius * radius && cooldown <= 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SeepersSounds.LIDAR_SCAN.get(), 0.8F, 0.18F));
            cooldown = ServerConfig.get(ServerConfig.ECHO_SOUND_INTERVAL);
        }
    }

    public static void render(GuiGraphics g, ItemStack lidar, int cx, int cy) {
        int color = 0xFF000000 | (StarterLidarItem.isStarter(lidar) ? 0xFFC36A : LidarHud.COLOR);
        LidarControls.disc(g, cx, cy, 17, 0xD806121A);
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2 * i / 64;
            LidarControls.ray(g, cx, cy, angle, i % 4 == 0 ? 14 : 16, 17, color);
        }
        GlobalPos target = target(lidar);
        var player = Minecraft.getInstance().player;
        if (target == null || player == null) {
            g.fill(cx - 3, cy, cx + 4, cy + 1, color);
            return;
        }
        double dx = target.pos().getX() + 0.5 - player.getX();
        double dz = target.pos().getZ() + 0.5 - player.getZ();
        double angle = Math.atan2(-dx, dz) - Math.toRadians(player.getYRot()) - Math.PI / 2;
        LidarControls.ray(g, cx, cy, angle + Math.PI, 1, 9, 0x887DA1B8);
        for (int i = -1; i <= 1; i++) LidarControls.ray(g, cx + i, cy, angle, 0, 12, color);
        double tx = cx + Math.cos(angle) * 12;
        double ty = cy + Math.sin(angle) * 12;
        LidarControls.ray(g, tx, ty, angle + 2.5, 0, 5, color);
        LidarControls.ray(g, tx, ty, angle - 2.5, 0, 5, color);
        LidarControls.disc(g, cx, cy, 1, color);
    }

    private EchoCompass() {}
}
