package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.network.payload.CaneTapPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the cane (CLAUDE.md 5.2), driven by the use key held with a cane in hand — not by item use,
 * which would slow the player down. A press taps where the player looks; holding on sweeps: each sweep scans the
 * low part of the surroundings (floor to knee) in a narrow fan straight ahead.
 */
public final class CaneTouch {
    /** Held longer than this, a press becomes a sweep. */
    private static final int SWEEP_START_TICKS = 8;
    /** The patch around an aimed tap: rays within this angle of the tap's direction. */
    private static final double PATCH_HALF_ANGLE = Math.toRadians(4);
    /** Highest point a sweep feels, blocks above the feet (about knee height). */
    private static final double KNEE = 0.6;

    private static final RandomSource RANDOM = RandomSource.create();
    private static int heldTicks;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        PointCloud cloud = PointCloud.get();
        InteractionHand hand = player == null ? null : caneHand(player);
        if (player == null || minecraft.level == null || cloud == null || hand == null || minecraft.screen != null
                || !minecraft.options.keyUse.isDown() || player.isSpectator()) {
            heldTicks = 0;
            return;
        }
        int held = heldTicks++;
        if (held == 0) {
            tap(player, minecraft.level, cloud, hand, player.getEyePosition(), player.getViewVector(1.0F),
                    ServerConfig.get(ServerConfig.CANE_REACH), ServerConfig.get(ServerConfig.CANE_POINTS), true);
        } else if (held >= SWEEP_START_TICKS && (held - SWEEP_START_TICKS) % ServerConfig.get(ServerConfig.CANE_SWEEP_TICKS) == 0) {
            sweep(player, minecraft.level, cloud, hand);
        }
    }

    private static @Nullable InteractionHand caneHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof CaneItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof CaneItem) return InteractionHand.OFF_HAND;
        return null;
    }

    /** Rays from the eyes to random spots of a fan straight ahead, from the floor up to the knees. */
    private static void sweep(LocalPlayer player, ClientLevel level, PointCloud cloud, InteractionHand hand) {
        double halfAngle = Math.toRadians(ServerConfig.get(ServerConfig.CANE_SWEEP_DEGREES) / 2);
        double min = ServerConfig.get(ServerConfig.CANE_SWEEP_MIN);
        double max = ServerConfig.get(ServerConfig.CANE_SWEEP_MAX);
        int rays = ServerConfig.get(ServerConfig.CANE_SWEEP_RAYS);
        Vec3 eye = player.getEyePosition();
        Vec3 feet = player.position();
        HandTouch.Hit first = null;
        for (int i = 0; i < rays; i++) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD + (float) ((RANDOM.nextDouble() * 2 - 1) * halfAngle);
            double distance = Mth.lerp(RANDOM.nextDouble(), min, max);
            Vec3 target = feet.add(-Mth.sin(yaw) * distance, RANDOM.nextDouble() * KNEE - 0.2, Mth.cos(yaw) * distance);
            Vec3 direction = target.subtract(eye);
            HandTouch.Hit hit = HandTouch.cast(player, level, eye, direction.normalize(), direction.length() + 0.3);
            if (hit == null) continue;
            if (first == null) first = hit;
            add(cloud, hit);
        }
        send(hand, first, eye);
    }

    private static void tap(LocalPlayer player, ClientLevel level, PointCloud cloud, InteractionHand hand, Vec3 eye,
                            Vec3 direction, double reach, int points, boolean aimed) {
        HandTouch.Hit center = HandTouch.cast(player, level, eye, direction, reach);
        if (center != null) {
            for (int i = 0; i < points; i++) {
                HandTouch.Hit hit = i == 0 ? center : HandTouch.cast(player, level, eye, HandTouch.inCone(direction, PATCH_HALF_ANGLE), reach + 0.5);
                if (hit != null) add(cloud, hit);
            }
            if (aimed) {
                TouchCaption.show(center.fluid() ? null : center.state().getBlock().getName(),
                        Component.translatable("seepers.touch.material." + center.material().name().toLowerCase(Locale.ROOT)),
                        center.material());
            }
            if (!center.fluid()) TouchSounds.playFunctional(level, player, center.result().getBlockPos(), center.state(), center.location());
        }
        send(hand, center, eye);
    }

    private static void add(PointCloud cloud, HandTouch.Hit hit) {
        cloud.add(PointSource.CANE, hit.location().x, hit.location().y, hit.location().z, hit.result().getDirection(),
                hit.material().ordinal());
    }

    /** The server spends durability and plays the knock (or a swing through air) for everyone near. */
    private static void send(InteractionHand hand, @Nullable HandTouch.Hit hit, Vec3 eye) {
        Vec3 at = hit == null ? eye : hit.location();
        BlockPos pos = hit == null ? BlockPos.containing(eye) : hit.result().getBlockPos();
        PacketDistributor.sendToServer(new CaneTapPayload(hand == InteractionHand.OFF_HAND, hit != null, pos,
                (float) at.x, (float) at.y, (float) at.z));
    }

    private CaneTouch() {}
}
