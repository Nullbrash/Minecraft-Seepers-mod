package io.github.nullbrash.seepers.client.fog;

import com.mojang.blaze3d.shaders.FogShape;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.fog.FogSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Fog level 0 (CLAUDE.md 4.4): vanilla fog driven by the density field. Air and water only;
 * lava and powder snow keep vanilla fog. Vanilla's own distance (render distance, blindness,
 * darkness effect) stays an upper bound.
 */
public final class ClientFogRenderer {
    private static final float[] AIR_HALO = {0.55F, 0.50F, 0.42F};
    private static final float[] WATER_BASE = {0.0F, 0.012F, 0.03F};
    private static final float[] WATER_HALO = {0.10F, 0.28F, 0.30F};
    /** Seconds for the fog distance to follow a change (crossing a layer edge should not pop). */
    private static final double SMOOTHING_SECONDS = 0.75;

    private static FogType smoothedType;
    private static double smoothedVisibility;
    private static long lastUpdateNanos;

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        FogType type = event.getType();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if ((type != FogType.NONE && type != FogType.WATER) || level == null) return;

        FogSettings settings = FogSettings.current();
        Vec3 camera = event.getCamera().getPosition();
        double target;
        if (type == FogType.WATER) {
            target = settings.underwaterVisibility();
            if (event.getCamera().getEntity() instanceof LocalPlayer player) {
                target *= 1 + 0.5 * player.getWaterVision();
                if (player.hasEffect(MobEffects.CONDUIT_POWER)) target *= 1.5;
            }
        } else {
            double density = ClientFog.density(level, camera.x, camera.y, camera.z);
            if (Double.isNaN(density)) return; // no fog seed yet: leave vanilla fog
            target = settings.visibility(density);
        }

        double visibility = smooth(type, target);
        double renderLimit = minecraft.options.getEffectiveRenderDistance() * 16.0 - settings.renderDistanceMargin();
        float far = (float) Math.max(1.0, Math.min(Math.min(visibility, renderLimit), event.getFarPlaneDistance()));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance((float) (far * settings.nearFraction()));
        event.setFogShape(FogShape.SPHERE); // layers are horizontal: distance must count vertically too
        event.setCanceled(true); // required for the plane changes to apply
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        FogType type = event.getCamera().getFluidInCamera();
        ClientLevel level = Minecraft.getInstance().level;
        if ((type != FogType.NONE && type != FogType.WATER) || level == null) return;

        double halo = halo(level, event.getCamera().getPosition());
        float[] base = type == FogType.WATER ? WATER_BASE : new float[3];
        float[] tint = type == FogType.WATER ? WATER_HALO : AIR_HALO;
        event.setRed((float) (base[0] + tint[0] * halo));
        event.setGreen((float) (base[1] + tint[1] * halo));
        event.setBlue((float) (base[2] + tint[2] * halo));
    }

    /** 0 in darkness, up to haloStrength when the camera stands in full block light. */
    private static double halo(ClientLevel level, Vec3 camera) {
        int black = ServerConfig.get(ServerConfig.BLACK_LIGHT_LEVEL);
        int light = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(camera));
        double t = Mth.clamp((light - black) / (double) Math.max(1, 15 - black), 0.0, 1.0);
        return FogSettings.current().haloStrength() * t * t;
    }

    /** Time-based, so the two fog events per frame (sky and terrain) do not step twice. */
    private static double smooth(FogType type, double target) {
        long now = System.nanoTime();
        if (type != smoothedType) {
            smoothedType = type;
            smoothedVisibility = target; // entering or leaving water: snap
        } else {
            double dt = (now - lastUpdateNanos) / 1.0e9;
            double alpha = 1 - Math.exp(-Math.max(0, dt) / SMOOTHING_SECONDS);
            smoothedVisibility += (target - smoothedVisibility) * alpha;
        }
        lastUpdateNanos = now;
        return smoothedVisibility;
    }

    private ClientFogRenderer() {}
}
