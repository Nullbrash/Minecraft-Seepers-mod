package io.github.nullbrash.seepers.client.touch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A blurred part of a mob's silhouette, frozen in the pose it had when a blow landed (CLAUDE.md 5.2): only what
 * the blow touched — the legs for a cane, the torso of a mob that struck the player, the spot the player struck
 * along the view. The entity's
 * renderer draws it into a vertex catcher instead of the screen, so the points follow its real model (head,
 * legs, tail); a renderer that draws nothing there falls back to the body's box. Peaceful and neutral mobs are
 * yellow, hostile ones red; visible in light too.
 */
public final class Silhouettes {
    /** Points per square block of model surface, and bounds per mob. */
    private static final double DENSITY = 48;
    private static final int MIN_POINTS = 19;
    private static final int MAX_POINTS = 320;
    /** How far points stray off the surface: the outline is felt, not seen. */
    private static final double BLUR = 0.05;
    private static final RandomSource RANDOM = RandomSource.create();
    /** Radius around the struck spot that is outlined, blocks. */
    private static final double STRUCK_RADIUS = 0.6;

    /** Which part of the body a blow outlines. */
    public enum Part { LEGS, TORSO, STRUCK }

    public static void of(Entity entity, Part part) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || entity == Minecraft.getInstance().player) return;
        int material = (entity instanceof Enemy ? TouchMaterial.HOSTILE : TouchMaterial.CREATURE).ordinal();
        java.util.function.Predicate<Vec3> keep = filter(entity, part);
        FloatArrayList quads = capture(entity);
        if (quads.size() >= 12) {
            fromQuads(cloud, entity.position(), quads, material, keep);
        } else {
            fromBox(cloud, entity.getBoundingBox(), material, keep);
        }
    }

    /** World-space test for the part: height bands for legs and torso, a ball around the struck spot. */
    private static java.util.function.Predicate<Vec3> filter(Entity entity, Part part) {
        AABB box = entity.getBoundingBox();
        double h = box.getYsize();
        return switch (part) {
            case LEGS -> p -> p.y <= box.minY + h * 0.4;
            case TORSO -> p -> p.y >= box.minY + h * 0.35 && p.y <= box.minY + h * 0.8;
            case STRUCK -> {
                Vec3 spot = struckSpot(entity);
                yield p -> p.distanceToSqr(spot) <= STRUCK_RADIUS * STRUCK_RADIUS;
            }
        };
    }

    /** Where the player's view meets the mob's box; its centre if the player looks elsewhere (a ranged blow). */
    private static Vec3 struckSpot(Entity entity) {
        var player = Minecraft.getInstance().player;
        AABB box = entity.getBoundingBox().inflate(0.1);
        if (player != null) {
            Vec3 eye = player.getEyePosition();
            var hit = box.clip(eye, eye.add(player.getViewVector(1.0F).scale(64)));
            if (hit.isPresent()) return hit.get();
        }
        return box.getCenter();
    }

    /** Model quads (4 corners × xyz each), relative to the entity's position. */
    private static FloatArrayList capture(Entity entity) {
        FloatArrayList out = new FloatArrayList();
        VertexConsumer catcher = new VertexConsumer() {
            @Override public VertexConsumer addVertex(float x, float y, float z) { out.add(x); out.add(y); out.add(z); return this; }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer setUv(float u, float v) { return this; }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        try {
            Minecraft minecraft = Minecraft.getInstance();
            float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            // The renderer itself, not the dispatcher: no shadow or fire overlay in the outline.
            minecraft.getEntityRenderDispatcher().getRenderer(entity).render(entity, entity.getYRot(), partial, new PoseStack(),
                    renderType -> catcher, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            Seepers.LOGGER.debug("Silhouette capture failed for {}", entity, e);
            out.clear();
        }
        out.size(out.size() - out.size() % 12); // whole quads only
        return out;
    }

    private static void fromQuads(PointCloud cloud, Vec3 origin, FloatArrayList q, int material,
                                  java.util.function.Predicate<Vec3> keep) {
        int quads = q.size() / 12;
        double[] area = new double[quads];
        double total = 0;
        for (int i = 0; i < quads; i++) {
            int o = i * 12;
            Vec3 a = corner(q, o, 0);
            Vec3 b = corner(q, o, 1);
            Vec3 d = corner(q, o, 3);
            area[i] = b.subtract(a).cross(d.subtract(a)).length();
            total += area[i];
        }
        if (total <= 0) return;
        int count = Mth.clamp((int) (total * DENSITY), MIN_POINTS, MAX_POINTS);
        for (int n = 0; n < count; n++) {
            double pick = RANDOM.nextDouble() * total;
            int i = 0;
            while (i < quads - 1 && pick > area[i]) pick -= area[i++];
            int o = i * 12;
            Vec3 a = corner(q, o, 0);
            Vec3 b = corner(q, o, 1);
            Vec3 c = corner(q, o, 2);
            Vec3 d = corner(q, o, 3);
            double u = RANDOM.nextDouble();
            double v = RANDOM.nextDouble();
            Vec3 p = a.scale((1 - u) * (1 - v)).add(b.scale(u * (1 - v))).add(c.scale(u * v)).add(d.scale((1 - u) * v));
            Vec3 normal = b.subtract(a).cross(d.subtract(a)).normalize();
            p = p.add(normal.scale((RANDOM.nextDouble() * 2 - 1) * BLUR)).add(origin);
            if (!keep.test(p)) continue;
            Direction face = Direction.getNearest(normal.x, normal.y, normal.z);
            cloud.add(PointSource.SILHOUETTE, p.x, p.y, p.z, face, material);
        }
    }

    private static Vec3 corner(FloatArrayList q, int offset, int index) {
        int i = offset + index * 3;
        return new Vec3(q.getFloat(i), q.getFloat(i + 1), q.getFloat(i + 2));
    }

    /** Fallback: points over the body's box. */
    private static void fromBox(PointCloud cloud, AABB box, int material, java.util.function.Predicate<Vec3> keep) {
        int count = Mth.clamp((int) ((box.getXsize() * box.getYsize() + box.getYsize() * box.getZsize()) * 2 * DENSITY), MIN_POINTS, MAX_POINTS);
        for (int i = 0; i < count; i++) {
            Direction face = Direction.getRandom(RANDOM);
            double x = Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX);
            double y = Mth.lerp(RANDOM.nextDouble(), box.minY, box.maxY);
            double z = Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ);
            switch (face) {
                case EAST -> x = box.maxX;
                case WEST -> x = box.minX;
                case UP -> y = box.maxY;
                case DOWN -> y = box.minY;
                case SOUTH -> z = box.maxZ;
                case NORTH -> z = box.minZ;
            }
            if (keep.test(new Vec3(x, y, z))) cloud.add(PointSource.SILHOUETTE, x, y, z, face, material);
        }
    }

    private Silhouettes() {}
}
