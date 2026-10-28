package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.client.touch.HandTouch;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.lidar.LidarModules;
import io.github.nullbrash.seepers.network.payload.LidarActionPayload;
import io.github.nullbrash.seepers.network.payload.LidarScanPayload;
import io.github.nullbrash.seepers.registry.SeepersSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the lidar (CLAUDE.md 5.3): while the use key is held, the beam traces its pattern (the rosette unless
 * the pattern module picks another, {@link LidarPatterns}) over its field, and points appear one after another along it,
 * so a held scan keeps landing in new spots until surfaces are painted solid. A short click still runs one petal.
 * Its own key opens the device screen while the lidar is in hand. A quick double press keeps the scan going on its own
 * until the next press. The beam needs charge: the server spends it while
 * told the beam runs ({@code lidar.LidarPower}); a flat or missing battery only beeps.
 * Without analysis every point has the base colour. Sounds play at the listener: placed in the world, they lagged
 * and panned as the player moved.
 */
public final class LidarScan {
    /** A click shorter than this still scans this long (about one petal and a half). */
    private static final int MIN_TICKS = 4;
    /** Tiny wobble of every ray so neighbouring passes do not snap to the same 1/32-block keys. */
    private static final double JITTER = 0.004;
    private static final float START_VOLUME = 0.13F;
    /** Horizontal speed below which the player counts as standing, blocks per tick. */
    private static final double STANDING_SPEED = 0.02;
    private static final float EMPTY_VOLUME = 0.3F;

    private static final RandomSource RANDOM = RandomSource.create();
    /** Rosette time in seconds; runs on across scans so each one continues the pattern. */
    private static double patternTime;
    private static double carry;
    private static int scanTicks;
    private static int debugTicks;
    private static Hum hum;
    /** Points this lidar laid since the world was joined (the device screen's counter). */
    private static long sessionPoints;
    /** The hand the server was told is scanning, or null. */
    private static @Nullable InteractionHand sentHand;
    /** This press already beeped for a flat battery. */
    private static boolean refused;
    /** Two presses within this many ticks latch the scan on. */
    private static final int DOUBLE_PRESS_TICKS = 8;
    private static final long NEVER = -1_000_000L;
    private static boolean useWasDown;
    private static boolean specialWasDown;
    private static boolean useLatched;
    private static boolean specialLatched;
    private static long lastUsePress = NEVER;
    private static long lastSpecialPress = NEVER;
    /** This tick's scan is a special pass (the special key: 180, 360 or a repair pass). */
    private static boolean round;
    /** The running pass covers the sphere; it repairs the data (refresh module, standing). */
    private static boolean passSphere;
    private static boolean passRepair;
    /** The running scan began on the special key. */
    private static boolean startedSpecial;
    /** Ticks into the all-around scan's pass and pause cycle; the pass's turn and height shift. */
    private static int roundTick;
    private static double roundTurn;
    private static double roundShift;
    /** Game time the last pass started; far in the past at first. */
    private static long lastPassStart = -1_000_000L;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    /** Whether the server was told the running scan is the all-around one. */
    private static boolean sentRound;
    /** The hand whose dynamo the server was told is cranking, or null. */
    private static @Nullable InteractionHand crankHand;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean ready = player != null && minecraft.level != null && !player.isSpectator() && holdsLidar(player);
        InteractionHand held = player == null ? null : heldHand(player);
        while (LidarScreen.KEY.consumeClick()) {
            if (held != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new LidarActionPayload(LidarActionPayload.OPEN_SCREEN, held == InteractionHand.OFF_HAND));
            }
        }
        // The cloud keeps as many lidar points as the last lidar in hand remembers.
        if (held != null) {
            LidarModules.Stats heldStats = LidarModules.stats(player.getItemInHand(held));
            PointSource.setLidarLimit(heldStats.pointLimit());
            io.github.nullbrash.seepers.client.points.LidarMemory.setKeeps(heldStats.keepsMemory());
        }
        boolean cranking = crank(minecraft, held);
        // The view key's passes: view II's 180 over the whole screen (walking too), view III's 360 (only standing still;
        // walking with it held scans nothing).
        int view = ready ? LidarModules.level(player.getItemInHand(held), LidarModules.Kind.VIEW) : 0;
        boolean free = ready && minecraft.screen == null;
        boolean useDown = free && minecraft.options.keyUse.isDown();
        boolean specialDown = free && LidarScreen.ROUND_KEY.isDown();
        // A quick double press keeps scanning on its own until the key is pressed again: the use key for the ordinary
        // scan, the special key for view II's 180 only (not the 360).
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        if (useDown && !useWasDown) useLatched = press(useLatched, now, true);
        if (specialDown && !specialWasDown) specialLatched = press(specialLatched, now, false) && view == 2;
        useWasDown = useDown;
        specialWasDown = specialDown;
        if (!ready || cranking) {
            useLatched = false;
            specialLatched = false;
        }
        if (view != 2) specialLatched = false;
        boolean repair = ready && LidarModules.level(player.getItemInHand(held), LidarModules.Kind.REFRESH) > 0;
        boolean roundKey = ready && minecraft.screen == null && (view >= 2 || repair) && (specialDown || specialLatched);
        // Standing with the refresh module: a sphere pass that repairs the data; else view II's 180 (walking too) or
        // view III's 360 (standing). A pass once begun runs to its end even if the key was only tapped.
        boolean still = standing();
        round = roundKey && (view == 2 || still) || passRunning();
        if (roundKey && !passRunning()) {
            passSphere = still && (repair || view >= 3);
            passRepair = still && repair;
        }
        boolean pressed = ready && minecraft.screen == null && !cranking
                && ((useDown || useLatched) && !roundKey || round);
        if (debugTicks > 0) {
            debugTicks--;
            pressed = ready;
        }
        InteractionHand hand = ready ? lidarHand(player) : null;
        boolean charged = hand != null && LidarItem.canScan(player.getItemInHand(hand));
        if (pressed && !charged) {
            useLatched = false;
            specialLatched = false;
            // No charge: one soft beep per press, no beam.
            if (!refused) beep(minecraft);
            refused = true;
            stop(hand);
            return;
        }
        if (!pressed) refused = false;
        // The shortest scan applies to the use key only: a released special key ends at once (its pass still finishes),
        // or a quick tap would run an ordinary scan for the rest of it.
        if (!ready || !pressed && (scanTicks == 0 || scanTicks >= MIN_TICKS || startedSpecial)) {
            stop(hand);
            return;
        }
        if (scanTicks == 0) startedSpecial = round;
        if (scanTicks++ == 0 || sentRound != round) {
            if (scanTicks == 1) start(minecraft);
            sentHand = hand;
            sentRound = round;
            PacketDistributor.sendToServer(new LidarScanPayload(true, hand == InteractionHand.OFF_HAND, round));
        }
        scan(player, minecraft.level, LidarModules.stats(player.getItemInHand(hand)));
    }

    /** The hand dynamo: held key with the dynamo module; while cranking there is no beam. */
    private static boolean crank(Minecraft minecraft, @Nullable InteractionHand held) {
        LocalPlayer player = minecraft.player;
        boolean want = held != null && minecraft.screen == null && LidarScreen.DYNAMO_KEY.isDown()
                && LidarModules.level(player.getItemInHand(held), LidarModules.Kind.DYNAMO) > 0;
        if (want && crankHand == null) {
            crankHand = held;
            PacketDistributor.sendToServer(new LidarActionPayload(LidarActionPayload.DYNAMO_START, held == InteractionHand.OFF_HAND));
        } else if (!want && crankHand != null) {
            PacketDistributor.sendToServer(new LidarActionPayload(LidarActionPayload.DYNAMO_STOP, crankHand == InteractionHand.OFF_HAND));
            crankHand = null;
        }
        return crankHand != null;
    }

    /**
     * A fresh press of a scan key: while latched it unlatches; a second press soon after the first latches. Returns
     * the new latch state.
     */
    private static boolean press(boolean latched, long now, boolean use) {
        if (latched) {
            if (use) lastUsePress = NEVER;
            else lastSpecialPress = NEVER;
            return false;
        }
        long last = use ? lastUsePress : lastSpecialPress;
        if (use) lastUsePress = now;
        else lastSpecialPress = now;
        return now - last <= DOUBLE_PRESS_TICKS;
    }

    /** The ordinary scan or view II's 180 runs on its own after a double press (the hologram may show it). */
    public static boolean latched() {
        return useLatched || specialLatched;
    }

    /** An all-around pass has begun and not yet ended. */
    private static boolean passRunning() {
        int cycle = ServerConfig.get(ServerConfig.LIDAR_ROUND_PASS_TICKS) + ServerConfig.get(ServerConfig.LIDAR_ROUND_PAUSE_TICKS);
        int tick = roundTick % cycle;
        return roundTick > 0 && tick > 0 && tick < ServerConfig.get(ServerConfig.LIDAR_ROUND_PASS_TICKS);
    }

    /** Ends a running scan and tells the server, which stops spending the charge. */
    private static void stop(@Nullable InteractionHand hand) {
        scanTicks = 0;
        roundTick = 0;
        if (sentHand != null) {
            PacketDistributor.sendToServer(new LidarScanPayload(false, sentHand == InteractionHand.OFF_HAND, false));
            sentHand = null;
        }
    }

    /** {@code /seepers points lidar [ticks]}: scans as if the key were held that long (debugging from the console). */
    public static boolean debugScan(int ticks) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !holdsLidar(player)) return false;
        debugTicks = ticks;
        return true;
    }

    /** The special key is held with a lidar that has a special scan (view II or III, or the refresh module). */
    static boolean roundKeyHeld(LocalPlayer player) {
        InteractionHand held = heldHand(player);
        if (held == null || !LidarScreen.ROUND_KEY.isDown()) return false;
        ItemStack lidar = player.getItemInHand(held);
        return LidarModules.level(lidar, LidarModules.Kind.VIEW) >= 2 || LidarModules.level(lidar, LidarModules.Kind.REFRESH) > 0;
    }

    /** Standing still: the all-around scan works only then. */
    static boolean standing() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getDeltaMovement().horizontalDistanceSqr() < STANDING_SPEED * STANDING_SPEED && player.onGround();
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

    /** This tick's share of the points per second, each at its own moment of the pattern, laid over the field. */
    private static void scan(LocalPlayer player, ClientLevel level, LidarModules.Stats stats) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null) return;
        double range = ServerConfig.get(ServerConfig.LIDAR_RANGE);
        if (round) {
            roundPass(player, level, cloud, range, passSphere, stats.pointsPerSecond());
            return;
        }
        roundTick = 0;
        carry += stats.pointsPerSecond() / 20.0;
        int rays = (int) carry;
        carry -= rays;
        // From the player's eyes along their view, also in third person (CLAUDE.md 5).
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look);
        double[] uv = new double[2];
        // The chosen area; the special scans go by passes (roundPass).
        LidarModules.Field field = stats.field();
        // Half-widths of the screen's image plane at distance 1, from the camera's vertical field of view and the window.
        Minecraft minecraft = Minecraft.getInstance();
        double screenY = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2) * stats.screenShare();
        double screenX = screenY * minecraft.getWindow().getWidth() / Math.max(1.0, minecraft.getWindow().getHeight());
        for (int i = 0; i < rays; i++) {
            patternTime += 0.05 / rays;
            if (!LidarPatterns.sample(stats.pattern(), patternTime, field == LidarModules.Field.CONE, RANDOM, uv)) continue;
            double u = uv[0] + RANDOM.nextGaussian() * JITTER;
            double v = uv[1] + RANDOM.nextGaussian() * JITTER;
            Vec3 direction = switch (field) {
                case CONE -> {
                    double r = Math.sqrt(u * u + v * v);
                    double theta = Math.min(r, 1) * Math.toRadians(stats.coneHalf());
                    double phi = Math.atan2(v, u);
                    yield look.scale(Math.cos(theta))
                            .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                            .add(up.scale(Math.sin(theta) * Math.sin(phi)));
                }
                // The screen: the pattern's square laid on the camera's image plane, so it stays a rectangle at any pitch.
                case SCREEN -> look.add(right.scale(u * screenX)).add(up.scale(v * screenY)).normalize();
            };
            HandTouch.Hit hit = HandTouch.cast(player, level, eye, direction, range);
            if (hit == null) continue;
            cloud.add(PointSource.LIDAR, hit.location().x, hit.location().y, hit.location().z, hit.result().getDirection(),
                    TouchMaterial.SOLID.ordinal());
            sessionPoints++;
        }
    }

    /**
     * The view key's passes: a whole pass right after the press, spread over a few ticks; held, a new pass follows after
     * a short pause. They lay points faster than the ordinary scan by a bonus (config). View III (sphere): rays on a Fibonacci sphere, turned anew each pass. View II: the whole screen in
     * a grid with a random spot in each cell. Either way each pass lands in new spots.
     */
    private static void roundPass(LocalPlayer player, ClientLevel level, PointCloud cloud, double range, boolean sphere, int pointsPerSecond) {
        int passTicks = ServerConfig.get(ServerConfig.LIDAR_ROUND_PASS_TICKS);
        int cycle = passTicks + ServerConfig.get(ServerConfig.LIDAR_ROUND_PAUSE_TICKS);
        // Faster than the ordinary scan by the bonus: a pass carries a whole cycle's points.
        int rays = Math.max(16, (int) Math.round(pointsPerSecond * (1 + ServerConfig.get(ServerConfig.LIDAR_PASS_SPEED_BONUS)) * cycle / 20.0));
        int tick = roundTick % cycle;
        if (tick == 0) {
            // Pressing again does not hurry it: a new pass waits a whole cycle from the last one's start.
            long now = level.getGameTime();
            if (now - lastPassStart < cycle) return;
            lastPassStart = now;
            if (passRepair) LidarRefresh.repairAround();
            roundTurn = RANDOM.nextDouble() * Math.PI * 2;
            roundShift = RANDOM.nextDouble();
        }
        roundTick++;
        if (tick >= passTicks) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look);
        Minecraft minecraft = Minecraft.getInstance();
        double screenY = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2);
        double aspect = minecraft.getWindow().getWidth() / Math.max(1.0, minecraft.getWindow().getHeight());
        int columns = Math.max(1, (int) Math.round(Math.sqrt(rays * aspect)));
        int rows = Math.max(1, (rays + columns - 1) / columns);
        int from = rays * tick / passTicks;
        int to = rays * (tick + 1) / passTicks;
        for (int i = from; i < to; i++) {
            Vec3 direction;
            if (sphere) {
                double y = 1 - 2 * (i + roundShift) / rays;
                double flat = Math.sqrt(Math.max(0, 1 - y * y));
                double phi = i * GOLDEN_ANGLE + roundTurn;
                direction = new Vec3(Math.cos(phi) * flat, y, Math.sin(phi) * flat);
            } else {
                double u = ((i % columns) + RANDOM.nextDouble()) / columns * 2 - 1;
                // Rows go out top to bottom as the pass runs.
                double v = 1 - ((i / columns) + RANDOM.nextDouble()) / rows * 2;
                direction = look.add(right.scale(u * screenY * aspect)).add(up.scale(v * screenY)).normalize();
            }
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
            hum = new Hum(ClientConfig.LIDAR_BEAM_SOUND.get());
            minecraft.getSoundManager().play(hum);
        }
    }

    private static float volume() {
        return ClientConfig.LIDAR_VOLUME.get().floatValue();
    }

    private static void beep(Minecraft minecraft) {
        if (volume() > 0) minecraft.getSoundManager().play(atListener(SeepersSounds.LIDAR_EMPTY.get(), EMPTY_VOLUME * volume(), 1.0F));
    }

    private static SoundInstance atListener(SoundEvent sound, float volume, float pitch) {
        return new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }

    public static void onLoggingOut() {
        scanTicks = 0;
        debugTicks = 0;
        carry = 0;
        lastPassStart = -1_000_000L; // the next world has its own game time
        useLatched = false;
        specialLatched = false;
        lastUsePress = NEVER;
        lastSpecialPress = NEVER;
        sentHand = null; // the server forgets the sessions on logout
        crankHand = null;
        refused = false;
        sessionPoints = 0;
    }

    /** The beam's loop: at the listener while scanning, swells in and dies away on release. */
    /** The beam's loop, chosen in the client config; each at the volume it was tuned at. */
    public enum BeamSound implements TranslatableEnum {
        CLICKS(0.131F, true), DRY_CLICKS(0.131F, true), HUM(0.146F, false);

        final float volume;
        /** Click loops follow the scan speed; the hum would only change its tone. */
        final boolean followsSpeed;

        BeamSound(float volume, boolean followsSpeed) {
            this.volume = volume;
            this.followsSpeed = followsSpeed;
        }

        SoundEvent sound() {
            return switch (this) {
                case CLICKS -> SeepersSounds.LIDAR_CLICKS.get();
                case DRY_CLICKS -> SeepersSounds.LIDAR_CLICKS_DRY.get();
                case HUM -> SeepersSounds.LIDAR_HUM.get();
            };
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("seepers.configuration.lidarBeamSound." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        /** The click loops match this scan speed; faster scans play them faster (within the sound engine's 0.5-2). */
        private static final float BASE_POINTS_PER_SECOND = 320;
        private final BeamSound kind;

        Hum(BeamSound kind) {
            super(kind.sound(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.kind = kind;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.01F;
        }

        @Override
        public void tick() {
            if (kind.followsSpeed) pitch = Mth.clamp(ServerConfig.get(ServerConfig.LIDAR_POINTS_PER_SECOND) / BASE_POINTS_PER_SECOND, 0.5F, 2.0F);
            float full = kind.volume * volume();
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
