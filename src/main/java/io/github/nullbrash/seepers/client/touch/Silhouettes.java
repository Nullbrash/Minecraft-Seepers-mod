package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * A blurred silhouette of a mob, frozen where it stood when a blow landed (CLAUDE.md 5.2): points scattered over
 * its body's box, a little off it. Peaceful and neutral mobs are yellow, hostile ones red; visible in light too.
 */
public final class Silhouettes {
    /** Points per square block of the body's surface, and a cap for big mobs. */
    private static final double DENSITY = 40;
    private static final int MAX_POINTS = 160;
    /** How far points stray off the body: the outline is felt, not seen. */
    private static final double BLUR = 0.08;
    private static final RandomSource RANDOM = RandomSource.create();

    public static void of(Entity entity) {
        PointCloud cloud = PointCloud.get();
        if (cloud == null || entity == Minecraft.getInstance().player) return;
        int material = (entity instanceof Enemy ? TouchMaterial.HOSTILE : TouchMaterial.CREATURE).ordinal();
        AABB box = entity.getBoundingBox();
        double sx = box.getXsize();
        double sy = box.getYsize();
        double sz = box.getZsize();
        double[] areas = {sy * sz, sy * sz, sx * sz, sx * sz, sx * sy, sx * sy};
        Direction[] faces = {Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};
        double total = 0;
        for (double area : areas) total += area;
        int count = Mth.clamp((int) (total * DENSITY), 12, MAX_POINTS);
        for (int i = 0; i < count; i++) {
            // A face picked by its area, a random spot on it, nudged a little in or out.
            double pick = RANDOM.nextDouble() * total;
            int f = 0;
            while (f < 5 && pick > areas[f]) pick -= areas[f++];
            Direction face = faces[f];
            double x = Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX);
            double y = Mth.lerp(RANDOM.nextDouble(), box.minY, box.maxY);
            double z = Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ);
            double blur = (RANDOM.nextDouble() * 2 - 1) * BLUR;
            switch (face.getAxis()) {
                case X -> x = (face == Direction.EAST ? box.maxX : box.minX) + blur;
                case Y -> y = (face == Direction.UP ? box.maxY : box.minY) + blur;
                case Z -> z = (face == Direction.SOUTH ? box.maxZ : box.minZ) + blur;
            }
            cloud.add(PointSource.SILHOUETTE, x, y, z, face, material);
        }
    }

    private Silhouettes() {}
}
