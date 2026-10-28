package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.client.touch.HandTouch;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * What the lidar's analysis module makes of a hit (CLAUDE.md 5.3): level I picks out ores, II also entities (in the
 * scan), III gives every point the real colour of the texture pixel the ray landed on (tinted like grass and leaves).
 * Anomalous rock is broken data at any level: no colour, the points tremble.
 */
final class LidarAnalysis {
    /** Ores at analysis I and II: the yellow-orange kept for them (CLAUDE.md 5.2). */
    static final int ORE_COLOR = 0xF0B040;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Direction[] DIRECTIONS = Direction.values();

    /** A point's material and own colour (0 = the material's). */
    record Paint(int material, int rgb) {}

    /** Analysis III: a texture pixel of a block's model a ray met — its centre, the side the ray came from, colour. */
    record Texel(Vec3 at, Direction face, int rgb, double distance) {}

    /** castModel's answer for a block whose model has no quads (drawn by a block entity renderer). */
    static final Texel NO_MODEL = new Texel(Vec3.ZERO, Direction.UP, 0, 0);

    static Paint paint(ClientLevel level, HandTouch.Hit hit, int analysis) {
        BlockState state = hit.state();
        if (state.is(SeepersBlocks.ANOMALY_ROCK.get())) return new Paint(TouchMaterial.ANOMALY.ordinal(), 0);
        if (analysis <= 0) return new Paint(TouchMaterial.SOLID.ordinal(), 0);
        if (analysis < 3) {
            boolean ore = !hit.fluid() && state.is(Tags.Blocks.ORES);
            return new Paint(TouchMaterial.SOLID.ordinal(), ore ? ORE_COLOR : 0);
        }
        if (hit.fluid()) return new Paint(TouchMaterial.SOLID.ordinal(), texel(level, hit, hit.location()));
        return new Paint(TouchMaterial.TEXEL.ordinal(), texel(level, hit, snap(hit)));
    }

    /**
     * Analysis III: the hit moved to the centre of its texture pixel on the face (a 16x16 grid), so a point is one
     * pixel of the block and a ray landing on the same pixel again only refreshes it.
     */
    static Vec3 snap(HandTouch.Hit hit) {
        BlockPos pos = hit.result().getBlockPos();
        Vec3 at = hit.location();
        Direction.Axis[] axes = inPlane(hit.result().getDirection());
        double[] c = {at.x, at.y, at.z};
        for (Direction.Axis axis : axes) {
            int i = axis.ordinal();
            double base = i == 0 ? pos.getX() : i == 1 ? pos.getY() : pos.getZ();
            double local = Mth.clamp(c[i] - base, 0, 0.9999);
            c[i] = base + (Math.floor(local * 16) + 0.5) / 16;
        }
        return new Vec3(c[0], c[1], c[2]);
    }

    /**
     * Analysis III: the nearest opaque texture pixel of the block's model along the ray (from {@code from}, unit
     * {@code dir}), in model space — leaves, bushes and flowers show what they are drawn as, and a ray through a
     * clear pixel finds nothing here (null) and goes on. NO_MODEL if the model has no quads.
     */
    static @Nullable Texel castModel(ClientLevel level, BlockPos pos, BlockState state, Vec3 from, Vec3 dir) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        ModelData data = level.getModelData(pos);
        long seed = state.getSeed(pos);
        Vec3 local = from.subtract(pos.getX(), pos.getY(), pos.getZ());
        Texel best = null;
        double bestT = Double.MAX_VALUE;
        boolean any = false;
        for (int side = 0; side <= 6; side++) {
            // The variant the renderer draws here: the same seed per position.
            RANDOM.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, side < 6 ? DIRECTIONS[side] : null, RANDOM, data, null);
            for (BakedQuad quad : quads) {
                any = true;
                int[] v = quad.getVertices();
                int stride = v.length / 4;
                Vec3 a = vertex(v, 0);
                Vec3 e1 = vertex(v, stride).subtract(a);
                Vec3 e2 = vertex(v, 3 * stride).subtract(a);
                Vec3 n = e1.cross(e2);
                double denom = dir.dot(n);
                if (Math.abs(denom) < 1.0E-9) continue;
                double t = a.subtract(local).dot(n) / denom;
                if (t < -1.0E-4 || t >= bestT) continue;
                Vec3 w = local.add(dir.scale(t)).subtract(a);
                double l1 = e1.lengthSqr();
                double l2 = e2.lengthSqr();
                if (l1 < 1.0E-12 || l2 < 1.0E-12) continue;
                double s = w.dot(e1) / l1;
                double r = w.dot(e2) / l2;
                if (s < -1.0E-4 || s > 1.0001 || r < -1.0E-4 || r > 1.0001) continue;
                TextureAtlasSprite sprite = quad.getSprite();
                float u0 = Float.intBitsToFloat(v[4]);
                float v0 = Float.intBitsToFloat(v[5]);
                float du1 = Float.intBitsToFloat(v[stride + 4]) - u0;
                float dv1 = Float.intBitsToFloat(v[stride + 5]) - v0;
                float du2 = Float.intBitsToFloat(v[3 * stride + 4]) - u0;
                float dv2 = Float.intBitsToFloat(v[3 * stride + 5]) - v0;
                int width = sprite.contents().width();
                int height = sprite.contents().height();
                double spanU = Math.max(sprite.getU1() - sprite.getU0(), 1.0E-6);
                double spanV = Math.max(sprite.getV1() - sprite.getV0(), 1.0E-6);
                // Texture pixels along each edge: the point sits at the centre of the pixel it fell in.
                int n1 = Math.max(1, (int) Math.round(Math.hypot(du1 / spanU * width, dv1 / spanV * height)));
                int n2 = Math.max(1, (int) Math.round(Math.hypot(du2 / spanU * width, dv2 / spanV * height)));
                s = (Math.min(Math.floor(Mth.clamp(s, 0, 1) * n1), n1 - 1) + 0.5) / n1;
                r = (Math.min(Math.floor(Mth.clamp(r, 0, 1) * n2), n2 - 1) + 0.5) / n2;
                double tu = u0 + du1 * s + du2 * r;
                double tv = v0 + dv1 * s + dv2 * r;
                int px = Mth.clamp((int) ((tu - sprite.getU0()) / spanU * width), 0, width - 1);
                int py = Mth.clamp((int) ((tv - sprite.getV0()) / spanV * height), 0, height - 1);
                int abgr = sprite.getPixelRGBA(0, px, py);
                if ((abgr >>> 24) < 16) continue; // a clear pixel: the ray goes on
                // NativeImage pixels are ABGR.
                int color = (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                if (quad.isTinted()) color = multiply(color, minecraft.getBlockColors().getColor(state, level, pos, quad.getTintIndex()));
                Vec3 at = a.add(e1.scale(s)).add(e2.scale(r)).add(pos.getX(), pos.getY(), pos.getZ());
                Vec3 normal = denom > 0 ? n.reverse() : n; // the side the ray comes from
                best = new Texel(at, Direction.getNearest(normal.x, normal.y, normal.z), color == 0 ? 0x010101 : color, t);
                bestT = t;
            }
        }
        return any ? best : NO_MODEL;
    }

    private static Vec3 vertex(int[] v, int offset) {
        return new Vec3(Float.intBitsToFloat(v[offset]), Float.intBitsToFloat(v[offset + 1]), Float.intBitsToFloat(v[offset + 2]));
    }

    /** The colour of the texture pixel under the hit, tinted as the block draws; the map colour if none is found. */
    private static int texel(ClientLevel level, HandTouch.Hit hit, Vec3 at) {
        BlockPos pos = hit.result().getBlockPos();
        BlockState state = hit.state();
        if (hit.fluid()) {
            return level.getFluidState(pos).is(FluidTags.LAVA) ? 0xFF7A28 : BiomeColors.getAverageWaterColor(level, pos);
        }
        Direction face = hit.result().getDirection();
        Vec3 local = at.subtract(pos.getX(), pos.getY(), pos.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        BakedQuad quad = quadAt(model, state, face, local);
        if (quad != null) {
            int color = sample(quad, face, local);
            if (color != 0) {
                if (quad.isTinted()) color = multiply(color, minecraft.getBlockColors().getColor(state, level, pos, quad.getTintIndex()));
                return color == 0 ? 0x010101 : color; // 0 means "the material's colour"
            }
        }
        int map = state.getMapColor(level, pos).col;
        return map == 0 ? 0x808080 : map;
    }

    /** The model quad on this face whose rectangle holds the point; the first on the face otherwise. */
    private static BakedQuad quadAt(BakedModel model, BlockState state, Direction face, Vec3 local) {
        BakedQuad first = null;
        for (int pass = 0; pass < 2; pass++) {
            List<BakedQuad> quads = model.getQuads(state, pass == 0 ? face : null, RANDOM, ModelData.EMPTY, null);
            for (BakedQuad quad : quads) {
                if (quad.getDirection() != face) continue;
                if (first == null) first = quad;
                if (contains(quad, face, local)) return quad;
            }
        }
        return first;
    }

    private static boolean contains(BakedQuad quad, Direction face, Vec3 local) {
        int[] v = quad.getVertices();
        int stride = v.length / 4;
        double plane = Float.intBitsToFloat(v[face.getAxis().ordinal()]);
        if (Math.abs(local.get(face.getAxis()) - plane) > 0.02) return false;
        Direction.Axis[] axes = inPlane(face);
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 2; k++) {
                double c = Float.intBitsToFloat(v[i * stride + axes[k].ordinal()]);
                min[k] = Math.min(min[k], c);
                max[k] = Math.max(max[k], c);
            }
        }
        for (int k = 0; k < 2; k++) {
            double c = local.get(axes[k]);
            if (c < min[k] - 0.01 || c > max[k] + 0.01) return false;
        }
        return true;
    }

    /** Bilinear texture coordinates at the point from the quad's corners, then the sprite's pixel there (0 if clear). */
    private static int sample(BakedQuad quad, Direction face, Vec3 local) {
        int[] v = quad.getVertices();
        int stride = v.length / 4;
        Direction.Axis[] axes = inPlane(face);
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 2; k++) {
                double c = Float.intBitsToFloat(v[i * stride + axes[k].ordinal()]);
                min[k] = Math.min(min[k], c);
                max[k] = Math.max(max[k], c);
            }
        }
        double s = (local.get(axes[0]) - min[0]) / Math.max(max[0] - min[0], 1.0E-4);
        double t = (local.get(axes[1]) - min[1]) / Math.max(max[1] - min[1], 1.0E-4);
        s = Mth.clamp(s, 0, 1);
        t = Mth.clamp(t, 0, 1);
        double u = 0;
        double w = 0;
        for (int i = 0; i < 4; i++) {
            boolean highA = Float.intBitsToFloat(v[i * stride + axes[0].ordinal()]) > (min[0] + max[0]) / 2;
            boolean highB = Float.intBitsToFloat(v[i * stride + axes[1].ordinal()]) > (min[1] + max[1]) / 2;
            double weight = (highA ? s : 1 - s) * (highB ? t : 1 - t);
            u += weight * Float.intBitsToFloat(v[i * stride + 4]);
            w += weight * Float.intBitsToFloat(v[i * stride + 5]);
        }
        TextureAtlasSprite sprite = quad.getSprite();
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int px = Mth.clamp((int) ((u - sprite.getU0()) / Math.max(sprite.getU1() - sprite.getU0(), 1.0E-6) * width), 0, width - 1);
        int py = Mth.clamp((int) ((w - sprite.getV0()) / Math.max(sprite.getV1() - sprite.getV0(), 1.0E-6) * height), 0, height - 1);
        int abgr = sprite.getPixelRGBA(0, px, py);
        if ((abgr >>> 24) < 16) return 0;
        // NativeImage pixels are ABGR.
        return (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
    }

    /** The two block axes lying in a face. */
    private static Direction.Axis[] inPlane(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction.Axis[] {Direction.Axis.Z, Direction.Axis.Y};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static int multiply(int color, int tint) {
        if (tint == -1) return color;
        int r = (color >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        int g = (color >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        int b = (color & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    private LidarAnalysis() {}
}
