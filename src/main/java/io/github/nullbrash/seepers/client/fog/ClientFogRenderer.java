package io.github.nullbrash.seepers.client.fog;

import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import com.mojang.blaze3d.shaders.FogShape;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.fog.FogSettings;
import io.github.nullbrash.seepers.heat.HeatField;
import io.github.nullbrash.seepers.room.RoomField;
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
    private static double smoothedEdge;
    /** Fog end limit from the loaded distance, eased so a change of the automatic distance never steps. */
    private static double smoothedLimit = Double.NaN;
    private static long limitMillis;
    private static long lastUpdateNanos;

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        FogType type = event.getType();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if ((type != FogType.NONE && type != FogType.WATER) || level == null) return;

        FogSettings settings = FogSettings.current();
        Vec3 camera = event.getCamera().getPosition();
        double target;
        double edge = 0;
        if (type == FogType.WATER) {
            target = settings.underwaterVisibility();
            if (event.getCamera().getEntity() instanceof LocalPlayer player) {
                target *= 1 + 0.5 * player.getWaterVision();
                if (player.hasEffect(MobEffects.CONDUIT_POWER)) target *= 1.5;
            }
        } else {
            // Exact value at the camera: grid cells (4 blocks) straddle the walls of small rooms.
            double density = ClientFog.densityDirect(level, camera.x, camera.y, camera.z);
            if (Double.isNaN(density)) return; // no fog seed yet: leave vanilla fog
            target = settings.visibility(density);
            // Inside a heated area the fog starts at its edge and the outside looks fogged (CLAUDE.md 4.4).
            edge = Math.max(HeatField.edgeDistance(level, camera.x, camera.y, camera.z),
                    RoomField.reach(level, camera.x, camera.y, camera.z));
            if (edge > 0) {
                double outside = settings.visibility(ClientFog.baseDensity(level, camera.x, camera.y, camera.z));
                target = Math.max(target, edge + outside);
            }
        }

        double alpha = smoothingStep(type);
        smoothedVisibility += (target - smoothedVisibility) * alpha;
        smoothedEdge += (edge - smoothedEdge) * alpha;
        double renderLimit = easedLimit(AutoRenderDistance.loaded(minecraft.options.getEffectiveRenderDistance()) * 16.0
                - settings.renderDistanceMargin());
        float far = (float) Math.max(1.0, Math.min(Math.min(smoothedVisibility, renderLimit), event.getFarPlaneDistance()));
        float near = (float) Math.min(far, Math.max(far * settings.nearFraction(), smoothedEdge));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
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

    /**
     * Fraction to move toward the targets this call. Time-based, so the two fog events per frame
     * (sky and terrain) do not step twice; 1 (snap) when entering or leaving water.
     */
    /** About 3 s to settle (time constant 1 s), whatever the frame rate. */
    private static double easedLimit(double target) {
        long now = net.minecraft.Util.getMillis();
        double dt = Math.min(1.0, (now - limitMillis) / 1000.0);
        limitMillis = now;
        if (Double.isNaN(smoothedLimit) || dt >= 1.0) smoothedLimit = target;
        else smoothedLimit += (target - smoothedLimit) * (1 - Math.exp(-dt));
        return smoothedLimit;
    }

    private static double smoothingStep(FogType type) {
        long now = System.nanoTime();
        double alpha;
        if (type != smoothedType) {
            smoothedType = type;
            alpha = 1;
        } else {
            double dt = (now - lastUpdateNanos) / 1.0e9;
            alpha = 1 - Math.exp(-Math.max(0, dt) / SMOOTHING_SECONDS);
        }
        lastUpdateNanos = now;
        return alpha;
    }

    private ClientFogRenderer() {}
}
