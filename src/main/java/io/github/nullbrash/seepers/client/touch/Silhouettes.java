package io.github.nullbrash.seepers.client.touch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A blurred part of a mob's silhouette, frozen in the pose it had when a blow landed (CLAUDE.md 5.2): only what
 * the blow touched — the legs for a cane, the torso of a mob that struck the player, the spot the player struck
 * along the view. The entity's
 * renderer draws it into a vertex catcher instead of the screen, so the points follow its real model (head,
 * legs, tail); a renderer that draws nothing there falls back to the body's box. Peaceful and neutral mobs are
 * yellow, hostile ones red; visible in light too. The lidar's rays (analysis II and up) instead meet the model
 * itself, one point where each ray lands — at analysis III in the colour of the texture pixel there, as on blocks.
 */
public final class Silhouettes {
    /** Points per square block of model surface, and bounds per mob. */
    private static final double DENSITY = 39;
    private static final int MIN_POINTS = 15;
    private static final int MAX_POINTS = 259;
    /** How far points stray off the surface: the outline is felt, not seen. */
    private static final double BLUR = 0.05;
    private static final RandomSource RANDOM = RandomSource.create();
    /** Radius around the struck spot that is outlined, blocks. */
    private static final double STRUCK_RADIUS = 0.6;
    /** Models captured this tick for the lidar's rays, by entity id: one render per entity per tick. */
    private static final Int2ObjectOpenHashMap<Capture> CAPTURED = new Int2ObjectOpenHashMap<>();
    private static long capturedTick = Long.MIN_VALUE;

    /** Which part of the body a blow outlines. */
    public enum Part { LEGS, TORSO, STRUCK, WHOLE }

    public static void of(Entity entity, Part part) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || entity == Minecraft.getInstance().player) return;
        int material = (entity instanceof Enemy ? TouchMaterial.HOSTILE : TouchMaterial.CREATURE).ordinal();
        java.util.function.Predicate<Vec3> keep = filter(entity, part);
        Capture quads = capture(entity);
        if (quads.pos.size() >= 12) {
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
            case WHOLE -> p -> true;
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

    /**
     * Model quads relative to the entity's position: per vertex xyz, uv, colour and the index of its texture in
     * {@code textures} (-1: none).
     */
    private record Capture(FloatArrayList pos, FloatArrayList uv, IntArrayList color, IntArrayList texture,
                           List<ResourceLocation> textures) {}

    private static Capture capture(Entity entity) {
        Capture out = new Capture(new FloatArrayList(), new FloatArrayList(), new IntArrayList(), new IntArrayList(), new ArrayList<>());
        int[] current = {-1};
        VertexConsumer catcher = new VertexConsumer() {
            @Override public VertexConsumer addVertex(float x, float y, float z) {
                out.pos.add(x); out.pos.add(y); out.pos.add(z);
                out.uv.add(0); out.uv.add(0);
                out.color.add(-1);
                out.texture.add(current[0]);
                return this;
            }
            @Override public VertexConsumer setColor(int r, int g, int b, int a) {
                if (!out.color.isEmpty()) out.color.set(out.color.size() - 1, a << 24 | r << 16 | g << 8 | b);
                return this;
            }
            @Override public VertexConsumer setUv(float u, float v) {
                int n = out.uv.size();
                if (n >= 2) { out.uv.set(n - 2, u); out.uv.set(n - 1, v); }
                return this;
            }
            @Override public VertexConsumer setUv1(int u, int v) { return this; }
            @Override public VertexConsumer setUv2(int u, int v) { return this; }
            @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        try {
            Minecraft minecraft = Minecraft.getInstance();
            float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
            // The renderer itself, not the dispatcher: no shadow or fire overlay in the outline.
            minecraft.getEntityRenderDispatcher().getRenderer(entity).render(entity, entity.getYRot(), partial, new PoseStack(),
                    renderType -> {
                        ResourceLocation texture = TexturePixels.textureOf(renderType);
                        current[0] = texture == null ? -1 : indexOf(out.textures, texture);
                        return catcher;
                    }, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException e) {
            Seepers.LOGGER.debug("Silhouette capture failed for {}", entity, e);
            out.pos.clear();
        }
        int vertices = out.pos.size() / 12 * 4; // whole quads only
        out.pos.size(vertices * 3);
        out.uv.size(vertices * 2);
        out.color.size(vertices);
        out.texture.size(vertices);
        return out;
    }

    private static int indexOf(List<ResourceLocation> list, ResourceLocation texture) {
        int i = list.indexOf(texture);
        if (i >= 0) return i;
        list.add(texture);
        return list.size() - 1;
    }

    /** Where a lidar ray lands on an entity's model: the point's material and own colour (0 = the material's). */
    public record ModelHit(double distance, Vec3 at, Direction face, int material, int rgb) {}

    /**
     * The nearest spot of the entity's model a ray meets within {@code reach}, passing through clear texture pixels
     * (a zombie's hat layer); null if it misses. With {@code texels} the spot is the centre of its model pixel
     * (1/16 block) in that pixel's colour, otherwise a mark in the entity's silhouette colour.
     */
    public static @Nullable ModelHit cast(Entity entity, Vec3 from, Vec3 direction, double reach, boolean texels) {
        long tick = entity.level().getGameTime();
        if (tick != capturedTick) {
            CAPTURED.clear();
            capturedTick = tick;
        }
        Capture capture = CAPTURED.computeIfAbsent(entity.getId(), id -> capture(entity));
        FloatArrayList q = capture.pos;
        Vec3 dir = direction.normalize();
        Vec3 origin = entity.position();
        int mark = (entity instanceof Enemy ? TouchMaterial.HOSTILE : TouchMaterial.CREATURE).ordinal();
        ModelHit best = null;
        double bestT = reach;
        for (int i = 0, quads = q.size() / 12; i < quads; i++) {
            int o = i * 12;
            Vec3 a = corner(q, o, 0).add(origin);
            Vec3 e1 = corner(q, o, 1).add(origin).subtract(a);
            Vec3 e2 = corner(q, o, 3).add(origin).subtract(a);
            Vec3 n = e1.cross(e2);
            double denom = dir.dot(n);
            if (Math.abs(denom) < 1.0E-9) continue;
            double t = a.subtract(from).dot(n) / denom;
            if (t <= 0 || t >= bestT) continue;
            Vec3 w = from.add(dir.scale(t)).subtract(a);
            double l1 = e1.lengthSqr();
            double l2 = e2.lengthSqr();
            if (l1 < 1.0E-12 || l2 < 1.0E-12) continue;
            double u = w.dot(e1) / l1;
            double v = w.dot(e2) / l2;
            if (u < 0 || u > 1 || v < 0 || v > 1) continue;
            int rgb = pixel(capture, i * 4, u, v);
            if (rgb == 0) continue; // a clear pixel is no surface
            Vec3 at;
            if (texels) {
                int nu = Math.max(1, (int) Math.round(Math.sqrt(l1) * 16));
                int nv = Math.max(1, (int) Math.round(Math.sqrt(l2) * 16));
                u = (Math.min(Math.floor(u * nu), nu - 1) + 0.5) / nu;
                v = (Math.min(Math.floor(v * nv), nv - 1) + 0.5) / nv;
                at = a.add(e1.scale(u)).add(e2.scale(v));
            } else {
                at = a.add(w);
            }
            Vec3 normal = denom > 0 ? n.reverse() : n; // the side the ray comes from
            Direction face = Direction.getNearest(normal.x, normal.y, normal.z);
            best = texels ? new ModelHit(t, at, face, TouchMaterial.TEXEL_MARK.ordinal(), rgb == -1 ? colorOf(mark) : rgb)
                    : new ModelHit(t, at, face, mark, 0);
            bestT = t;
        }
        return best;
    }

    private static void fromQuads(PointCloud cloud, Vec3 origin, Capture capture, int material,
                                  java.util.function.Predicate<Vec3> keep) {
        FloatArrayList q = capture.pos;
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
            Direction face = Direction.getNearest(normal.x, normal.y, normal.z);
            p = p.add(normal.scale((RANDOM.nextDouble() * 2 - 1) * BLUR)).add(origin);
            if (!keep.test(p)) continue;
            cloud.add(PointSource.SILHOUETTE, p.x, p.y, p.z, face, material);
        }
    }

    /**
     * RGB of the texture pixel at (u, v) across the quad starting at vertex {@code first}, tinted by the vertex
     * colour; 0 for a clear pixel, -1 if the texture cannot be read.
     */
    private static int pixel(Capture capture, int first, double u, double v) {
        int texture = capture.texture.getInt(first);
        if (texture < 0) return -1;
        double[] weight = {(1 - u) * (1 - v), u * (1 - v), u * v, (1 - u) * v};
        float tu = 0;
        float tv = 0;
        for (int k = 0; k < 4; k++) {
            tu += (float) (weight[k] * capture.uv.getFloat((first + k) * 2));
            tv += (float) (weight[k] * capture.uv.getFloat((first + k) * 2 + 1));
        }
        int argb = TexturePixels.argb(capture.textures.get(texture), tu, tv);
        if (argb == 0) return TexturePixels.readable(capture.textures.get(texture)) ? 0 : -1;
        int tint = capture.color.getInt(first);
        if ((argb >>> 24) < 16 || (tint >>> 24) < 16) return 0;
        int r = (argb >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        int g = (argb >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        int rgb = r << 16 | g << 8 | b;
        return rgb == 0 ? 0x010101 : rgb; // 0 means "a clear pixel"
    }

    private static int colorOf(int material) {
        TouchMaterial m = TouchMaterial.byId(material);
        return m.red << 16 | m.green << 8 | m.blue;
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
