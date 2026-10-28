package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.client.touch.HandTouch;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.registry.SeepersSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the lidar (CLAUDE.md 5.3): while the use key is held, the beam traces a rosette — two counter-rotating
 * circles, as in prism-scanning lidars — and points appear one after another along it, never repeating the same path,
 * so a held scan keeps landing in new spots until surfaces are painted solid. A short click still runs one petal.
 * Its own key opens the device screen while the lidar is in hand.
 * Without analysis every point has the base colour. Sounds play at the listener: placed in the world, they lagged
 * and panned as the player moved.
 */
public final class LidarScan {
    /** The two circles' turns per second; their sum is about the petals drawn per second. Irrational ratio: no repeats. */
    private static final double FAST_TURNS = 7.3;
    private static final double SLOW_TURNS = -4.9 * Math.sqrt(1.02);
    /** A click shorter than this still scans this long (about one petal and a half). */
    private static final int MIN_TICKS = 4;
    /** Tiny wobble of every ray so neighbouring passes do not snap to the same 1/32-block keys. */
    private static final double JITTER = 0.004;
    private static final float START_VOLUME = 0.19F;

    private static final RandomSource RANDOM = RandomSource.create();
    /** Rosette time in seconds; runs on across scans so each one continues the pattern. */
    private static double patternTime;
    private static double carry;
    private static int scanTicks;
    private static int debugTicks;
    private static Hum hum;
    /** Points this lidar laid since the world was joined (the device screen's counter). */
    private static long sessionPoints;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean ready = player != null && minecraft.level != null && !player.isSpectator() && holdsLidar(player);
        while (LidarScreen.KEY.consumeClick()) {
            if (player != null && heldHand(player) != null && minecraft.screen == null) minecraft.setScreen(new LidarScreen());
        }
        boolean pressed = ready && minecraft.screen == null && minecraft.options.keyUse.isDown();
        if (debugTicks > 0) {
            debugTicks--;
            pressed = ready;
        }
        if (!ready || !pressed && (scanTicks == 0 || scanTicks >= MIN_TICKS)) {
            scanTicks = 0;
            return;
        }
        if (scanTicks++ == 0) start(minecraft);
        scan(player, minecraft.level);
    }

    /** {@code /seepers points lidar [ticks]}: scans as if the key were held that long (debugging from the console). */
    public static boolean debugScan(int ticks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !holdsLidar(player)) return false;
        debugTicks = ticks;
        return true;
    }

    public static boolean scanning() {
        return scanTicks > 0;
    }

    public static long sessionPoints() {
        return sessionPoints;
    }

    /** As the cane: in the off hand it answers the use key only when the main hand is empty. */
    static boolean holdsLidar(LocalPlayer player) {
        return lidarHand(player) != null;
    }

    /** Either hand, for the hologram and the screen key; scanning keeps the cane's off-hand rule. */
    static @Nullable InteractionHand heldHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof LidarItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof LidarItem) return InteractionHand.OFF_HAND;
        return null;
    }

    static @Nullable InteractionHand lidarHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof LidarItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof LidarItem && player.getMainHandItem().isEmpty()) return InteractionHand.OFF_HAND;
        return null;
    }

    /** This tick's share of the points per second, each at its own moment of the rosette. */
    private static void scan(LocalPlayer player, ClientLevel level) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null) return;
        double range = ServerConfig.get(ServerConfig.LIDAR_RANGE);
        double halfAngle = Math.toRadians(ServerConfig.get(ServerConfig.LIDAR_CONE_DEGREES) / 2);
        carry += ServerConfig.get(ServerConfig.LIDAR_POINTS_PER_SECOND) / 20.0;
        int rays = (int) carry;
        carry -= rays;
        // From the player's eyes along their view, also in third person (CLAUDE.md 5).
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look);
        for (int i = 0; i < rays; i++) {
            patternTime += 0.05 / rays;
            double a = patternTime * FAST_TURNS * Math.PI * 2;
            double b = patternTime * SLOW_TURNS * Math.PI * 2;
            // Two half-radius circles: their sum sweeps petals through the centre, out to the beam's edge.
            double u = (Math.cos(a) + Math.cos(b)) * 0.5 + RANDOM.nextGaussian() * JITTER;
            double v = (Math.sin(a) + Math.sin(b)) * 0.5 + RANDOM.nextGaussian() * JITTER;
            double r = Math.sqrt(u * u + v * v);
            double theta = Math.min(r, 1) * halfAngle;
            double phi = Math.atan2(v, u);
            Vec3 direction = look.scale(Math.cos(theta))
                    .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                    .add(up.scale(Math.sin(theta) * Math.sin(phi)));
            HandTouch.Hit hit = HandTouch.cast(player, level, eye, direction, range);
            if (hit == null) continue;
            cloud.add(PointSource.LIDAR, hit.location().x, hit.location().y, hit.location().z, hit.result().getDirection(),
                    TouchMaterial.SOLID.ordinal());
            sessionPoints++;
        }
    }

    private static void start(Minecraft minecraft) {
        if (volume() > 0) minecraft.getSoundManager().play(atListener(SeepersSounds.LIDAR_SCAN.get(), START_VOLUME * volume(), 1.0F));
        if (hum == null || hum.isStopped()) {
            hum = new Hum(SeepersSounds.LIDAR_HUM.get());
            minecraft.getSoundManager().play(hum);
        }
    }

    private static float volume() {
        return ClientConfig.LIDAR_VOLUME.get().floatValue();
    }

    private static SoundInstance atListener(SoundEvent sound, float volume, float pitch) {
        return new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }

    public static void onLoggingOut() {
        scanTicks = 0;
        debugTicks = 0;
        carry = 0;
        sessionPoints = 0;
    }

    /** The beam's hum: looped at the listener while scanning, swells in and dies away on release. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private static final float FULL = 0.209F;

        Hum(SoundEvent sound) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.01F;
        }

        @Override
        public void tick() {
            float full = FULL * volume();
            float step = Math.max(full, 0.01F) / 3;
            volume = scanning() ? Math.min(full, volume + step) : volume - step;
            if (volume <= 0 || full <= 0 && !scanning()) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private LidarScan() {}
}
