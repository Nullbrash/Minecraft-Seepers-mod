package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.payload.TouchLavaPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Touch with the hands (CLAUDE.md 5.1): each press of the attack key casts a few rays in a narrow cone along the
 * view, from the eyes; points appear where they hit. On the ground or in water, moving or not. Holding the key
 * still mines as usual: the click event is never cancelled.
 */
public final class HandTouch {
    private static final float SWING_SOUND_VOLUME = 0.3F;
    /** Darkness long enough before the hint shows (CLAUDE.md 12). */
    private static final int HINT_DELAY_TICKS = 200;
    private static final int HINT_REPEAT_TICKS = 40;
    private static final int DARK_LIGHT = 3;

    private static final RandomSource RANDOM = RandomSource.create();
    private static boolean attackWasDown;
    /** Far in the past but safe to subtract from (Long.MIN_VALUE would overflow the cooldown check). */
    private static final long NEVER = -1_000_000L;
    private static long lastTouchTick = NEVER;
    private static boolean usedThisSession;
    private static int darkTicks;

    /** Fires on the press and, while mining, every tick the key is held: only a fresh press touches. */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) keepFeltBlock(event);
        if (!event.isAttack() || attackWasDown) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        PointCloud cloud = PointCloud.get();
        if (player == null || level == null || cloud == null || !canTouch(player)) return;
        long now = level.getGameTime();
        double perSecond = ServerConfig.get(ServerConfig.TOUCH_HAND_CLICKS_PER_SECOND);
        if (now - lastTouchTick < 20.0 / perSecond) return;
        lastTouchTick = now;
        usedThisSession = true;
        touch(player, level, cloud);
    }

    /** {@code /seepers points touch}: the same touch as a click, without the key (debugging from the console). */
    public static boolean debugTouch() {
        Minecraft minecraft = Minecraft.getInstance();
        PointCloud cloud = PointCloud.get();
        if (minecraft.player == null || minecraft.level == null || cloud == null || !canTouch(minecraft.player)) return false;
        usedThisSession = true;
        touch(minecraft.player, minecraft.level, cloud);
        return true;
    }

    /**
     * An empty hand only feels blocks that break in one hit (grass, flowers, torches), so feeling around in the
     * dark does not tear them off; with anything in hand they break as usual. Not in creative, where every block
     * breaks in one hit.
     */
    private static void keepFeltBlock(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.getMainHandItem().isEmpty() || player.getAbilities().instabuild) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (minecraft.level.getBlockState(pos).getDestroyProgress(player, minecraft.level, pos) >= 1.0F) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        attackWasDown = minecraft.options.keyAttack.isDown();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || usedThisSession || player.isSpectator()) return;
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        int light = Math.max(minecraft.level.getBrightness(LightLayer.BLOCK, eye), io.github.nullbrash.seepers.client.light.ClientDynamicLights.level(eye));
        darkTicks = light <= DARK_LIGHT ? darkTicks + 1 : 0;
        if (darkTicks >= HINT_DELAY_TICKS && (darkTicks - HINT_DELAY_TICKS) % HINT_REPEAT_TICKS == 0) {
            minecraft.gui.setOverlayMessage(hint(minecraft.options.keyAttack), false);
        }
    }

    public static void onLoggingOut() {
        lastTouchTick = NEVER; // the next world has its own game time
        usedThisSession = false;
        darkTicks = 0;
    }

    /** The current binding of the attack key, or that it is not bound (CLAUDE.md 12). */
    private static Component hint(KeyMapping attack) {
        return attack.isUnbound() ? Component.translatable("seepers.touch.hint.unbound")
                : Component.translatable("seepers.touch.hint", attack.getTranslatedKeyMessage());
    }

    /** On the ground (walking and running included) or anywhere in water; not mid-jump, on a ladder or riding. */
    private static boolean canTouch(LocalPlayer player) {
        if (player.isSpectator() || player.onClimbable() || player.isPassenger()) return false;
        return player.isInWater() || player.onGround();
    }

    private static void touch(LocalPlayer player, ClientLevel level, PointCloud cloud) {
        double reach = ServerConfig.get(ServerConfig.TOUCH_HAND_REACH);
        double halfAngle = Math.toRadians(ServerConfig.get(ServerConfig.TOUCH_HAND_CONE_DEGREES) / 2);
        int rays = ServerConfig.get(ServerConfig.TOUCH_HAND_POINTS);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);

        Hit center = null;
        BlockPos lava = null;
        for (int i = 0; i < rays; i++) {
            Vec3 direction = i == 0 ? look : inCone(look, halfAngle);
            Hit hit = cast(player, level, eye, direction, reach);
            if (hit == null) continue;
            if (center == null) center = hit;
            cloud.add(PointSource.TOUCH, hit.location.x, hit.location.y, hit.location.z, hit.result.getDirection(),
                    hit.material.ordinal());
            if (hit.lava && lava == null) lava = hit.result.getBlockPos();
        }

        if (lava != null) {
            PacketDistributor.sendToServer(new TouchLavaPayload(lava));
            level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F, false);
            TouchCaption.show(null, Component.translatable("seepers.touch.hot"), TouchMaterial.WARM);
        } else if (center != null) {
            TouchCaption.show(center.fluid ? null : center.state.getBlock().getName(),
                    Component.translatable("seepers.touch.material." + center.material.name().toLowerCase(java.util.Locale.ROOT)),
                    center.material);
        }
        if (center == null) {
            // Nothing within reach: the hand swings through air (a passage, or a drop).
            level.playLocalSound(eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, SWING_SOUND_VOLUME, 1.2F, false);
        } else if (!center.fluid) {
            TouchSounds.play(level, player, center.result.getBlockPos(), center.state, center.location);
        }
    }

    public record Hit(BlockHitResult result, Vec3 location, BlockState state, TouchMaterial material, boolean fluid, boolean lava) {}

    /**
     * First block along the ray. The hand goes through water (blocks under it are felt, wet) but stops at lava,
     * which burns.
     */
    public static @Nullable Hit cast(LocalPlayer player, ClientLevel level, Vec3 eye, Vec3 direction, double reach) {
        return cast(player, level, eye, direction, reach, true);
    }

    /** feel: judge the touch material (false for the lidar, which never shows it: the hit is SOLID). */
    public static @Nullable Hit cast(LocalPlayer player, ClientLevel level, Vec3 eye, Vec3 direction, double reach, boolean feel) {
        Vec3 end = eye.add(direction.scale(reach));
        // Outline, not collision: the hand also finds what one walks through (pressure plates, cobweb, grass).
        // With fluids first: lava in front stops the ray; with no fluid on the way it is the same hit as without.
        // Only water in front needs the second clip, without fluids, to find what lies under it.
        BlockHitResult result = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (result.getType() != HitResult.Type.MISS) {
            FluidState met = level.getFluidState(result.getBlockPos());
            if (!met.isEmpty() && !met.is(FluidTags.LAVA)) {
                result = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            }
        }
        if (result.getType() == HitResult.Type.MISS) return null;
        BlockPos pos = result.getBlockPos();
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        boolean fluid = !fluidState.isEmpty() && state.getCollisionShape(level, pos).isEmpty();
        boolean lava = fluid && fluidState.is(FluidTags.LAVA);
        TouchMaterial material = lava ? TouchMaterial.WARM
                : fluid ? TouchMaterial.WET
                : feel ? TouchMaterial.of(level, pos, state, result.getDirection()) : TouchMaterial.SOLID;
        Vec3 location = result.getLocation();
        if (!fluid) {
            // The ray stops at the collision shape; move the point out to the drawn surface if that sticks out
            // further (mud, soul sand), or it would be hidden inside the block.
            AABB bounds = ClientTouch.surfaceBounds(level, pos, state);
            Direction face = result.getDirection();
            if (bounds != null) {
                Direction.Axis axis = face.getAxis();
                double plane = (axis == Direction.Axis.X ? pos.getX() : axis == Direction.Axis.Y ? pos.getY() : pos.getZ())
                        + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.max(axis) : bounds.min(axis));
                location = location.with(axis, face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? Math.max(location.get(axis), plane) : Math.min(location.get(axis), plane));
            }
        }
        return new Hit(result, location, state, material, fluid, lava);
    }

    /** A random direction within halfAngle of the axis, uniform over the cone's spherical cap. */
    public static Vec3 inCone(Vec3 axis, double halfAngle) {
        double cosTheta = 1 - RANDOM.nextDouble() * (1 - Math.cos(halfAngle));
        double sinTheta = Math.sqrt(1 - cosTheta * cosTheta);
        double phi = RANDOM.nextDouble() * Math.PI * 2;
        Vec3 helper = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = axis.cross(helper).normalize();
        Vec3 v = axis.cross(u);
        return axis.scale(cosTheta).add(u.scale(sinTheta * Mth.cos((float) phi))).add(v.scale(sinTheta * Mth.sin((float) phi)));
    }

    private HandTouch() {}
}
