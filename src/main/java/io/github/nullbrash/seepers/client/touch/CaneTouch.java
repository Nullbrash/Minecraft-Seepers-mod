package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.payload.CaneTapPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the cane (CLAUDE.md 5.2). The first tick of use is an aimed tap along the view; holding on
 * sweeps: taps alternate left and right on the floor ahead. Each tap leaves a small patch of points.
 */
public final class CaneTouch {
    /** Held longer than this, a click becomes a sweep. */
    private static final int SWEEP_START_TICKS = 8;
    private static final double SWEEP_HALF_ANGLE = Math.toRadians(35);
    /** The patch around a tap: rays within this angle of the tap's direction. */
    private static final double PATCH_HALF_ANGLE = Math.toRadians(4);

    private static int sweepIndex;

    public static void tick(LivingEntity user, InteractionHand hand, int usedTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (user != minecraft.player || minecraft.level == null) return;
        PointCloud cloud = PointCloud.get();
        if (cloud == null) return;
        LocalPlayer player = minecraft.player;
        if (usedTicks == 0) {
            Vec3 eye = player.getEyePosition();
            tap(player, minecraft.level, cloud, hand, eye, player.getViewVector(1.0F), ServerConfig.get(ServerConfig.CANE_REACH), true);
            return;
        }
        int sweepTicks = ServerConfig.get(ServerConfig.CANE_SWEEP_TICKS);
        if (usedTicks < SWEEP_START_TICKS || (usedTicks - SWEEP_START_TICKS) % sweepTicks != 0) return;
        // Left, right, left...: each sweep tap a bit further, from near to far and back.
        double side = sweepIndex % 2 == 0 ? -1 : 1;
        double min = ServerConfig.get(ServerConfig.CANE_SWEEP_MIN);
        double max = ServerConfig.get(ServerConfig.CANE_SWEEP_MAX);
        double distance = Mth.lerp((sweepIndex / 2 % 3) / 2.0, min, max);
        sweepIndex++;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD + (float) (side * SWEEP_HALF_ANGLE);
        Vec3 feet = player.position();
        Vec3 target = feet.add(-Mth.sin(yaw) * distance, -0.3, Mth.cos(yaw) * distance);
        Vec3 eye = player.getEyePosition();
        Vec3 direction = target.subtract(eye);
        tap(player, minecraft.level, cloud, hand, eye, direction.normalize(), direction.length() + 0.5, false);
    }

    private static void tap(LocalPlayer player, ClientLevel level, PointCloud cloud, InteractionHand hand, Vec3 eye,
                            Vec3 direction, double reach, boolean aimed) {
        HandTouch.Hit center = HandTouch.cast(player, level, eye, direction, reach);
        if (center != null) {
            int points = ServerConfig.get(ServerConfig.CANE_POINTS);
            for (int i = 0; i < points; i++) {
                HandTouch.Hit hit = i == 0 ? center : HandTouch.cast(player, level, eye, HandTouch.inCone(direction, PATCH_HALF_ANGLE), reach + 0.5);
                if (hit == null) continue;
                cloud.add(PointSource.CANE, hit.location().x, hit.location().y, hit.location().z, hit.result().getDirection(),
                        hit.material().ordinal());
            }
            if (aimed) {
                TouchCaption.show(center.fluid() ? null : center.state().getBlock().getName(),
                        Component.translatable("seepers.touch.material." + center.material().name().toLowerCase(Locale.ROOT)),
                        center.material());
            }
            if (!center.fluid()) TouchSounds.playFunctional(level, player, center.result().getBlockPos(), center.state(), center.location());
        }
        Vec3 at = center == null ? eye : center.location();
        BlockPos pos = center == null ? BlockPos.containing(eye) : center.result().getBlockPos();
        PacketDistributor.sendToServer(new CaneTapPayload(hand == InteractionHand.OFF_HAND, center != null, pos,
                (float) at.x, (float) at.y, (float) at.z));
    }

    private CaneTouch() {}
}
