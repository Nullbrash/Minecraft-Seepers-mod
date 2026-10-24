package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointSource;
import io.github.nullbrash.seepers.config.ServerConfig;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Touch with feet and body (CLAUDE.md 5.1): points on what the player stands on, walks into, bumps the head
 * on, lands on, and feels for with a foot at an edge while crouching. Client side, from the player's own
 * collisions; works the same in light (points just fade there).
 */
public final class ClientTouch {
    private static final int REFRESH_TICKS = 10;
    private static final float LANDING_FALL = 2.0F;
    /** How far past the player's box a touched surface may be. */
    private static final double PROBE = 0.05;
    private static final double EPS = 1.0E-3;
    /** How far a crouching foot reaches over an edge. */
    private static final double FOOT_REACH = 0.3;

    private static final LongOpenHashSet footprint = new LongOpenHashSet();
    private static int ticks;
    private static float lastFallDistance;
    private static boolean wasOnGround = true;
    private static boolean wasPushing;
    private static boolean wasBumping;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        PointCloud cloud = PointCloud.get();
        if (player == null || level == null || cloud == null || player.isSpectator()) return;
        ticks++;
        boolean refresh = ticks % REFRESH_TICKS == 0;
        AABB box = player.getBoundingBox();
        boolean onGround = player.onGround();
        // In water the player sinks onto the bottom without landing: feel it as long as it is near.
        boolean wading = player.isInWater() && !onGround;

        if (onGround || wading) {
            feet(cloud, level, box, ticks % (REFRESH_TICKS * 2) == 0, wading ? 1.5 : 0.1, player.isCrouching());
        } else {
            footprint.clear();
        }
        if (onGround && !wasOnGround && lastFallDistance >= LANDING_FALL) landing(cloud, level, box);
        lastFallDistance = player.fallDistance;
        wasOnGround = onGround;

        boolean pushing = player.horizontalCollision;
        if (pushing && (!wasPushing || refresh)) walls(cloud, level, player, box);
        wasPushing = pushing;
        boolean bumping = player.verticalCollision && !player.verticalCollisionBelow;
        if (bumping && (!wasBumping || refresh)) ceiling(cloud, level, box);
        wasBumping = bumping;
        if (onGround && player.isCrouching() && refresh) edges(cloud, level, player, box);
    }

    /**
     * The floor underfoot, and around the player while crouching (touch.feetRadius): top faces, denser right
     * underfoot, plus side faces and a line along the top edge where the floor drops away, so ledges read.
     * New blocks at once, all now and then.
     */
    private static void feet(PointCloud cloud, ClientLevel level, AABB box, boolean refreshAll, double below, boolean crouching) {
        int radius = crouching ? ServerConfig.get(ServerConfig.TOUCH_FEET_RADIUS) : 0;
        int centerX = Mth.floor((box.minX + box.maxX) / 2);
        int centerZ = Mth.floor((box.minZ + box.maxZ) / 2);
        LongOpenHashSet underfoot = new LongOpenHashSet();
        for (int x = Mth.floor(box.minX + EPS); x <= Mth.floor(box.maxX - EPS); x++) {
            for (int z = Mth.floor(box.minZ + EPS); z <= Mth.floor(box.maxZ - EPS); z++) {
                BlockPos support = surface(level, x, z, box.minY, below, 0.1);
                if (support != null) underfoot.add(support.asLong());
            }
        }
        LongOpenHashSet current = new LongOpenHashSet(underfoot);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius + 1) continue;
                // Steps a block up or down still count as floor around.
                BlockPos floor = surface(level, centerX + dx, centerZ + dz, box.minY, Math.max(below, 1.1), 1.1);
                if (floor != null) current.add(floor.asLong());
            }
        }
        var it = current.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if (!refreshAll && footprint.contains(key)) continue;
            BlockPos pos = BlockPos.of(key);
            touchFace(cloud, level, pos, Direction.UP, underfoot.contains(key) ? 3 : 2);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                if (level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                    touchFace(cloud, level, pos, side, 2);
                    touchEdge(cloud, level, pos, side);
                }
            }
        }
        footprint.clear();
        footprint.addAll(current);
    }

    /** After a fall the player catches themselves: the floor around, steps a block up or down included. */
    private static void landing(PointCloud cloud, ClientLevel level, AABB box) {
        for (int x = Mth.floor(box.minX + EPS) - 1; x <= Mth.floor(box.maxX - EPS) + 1; x++) {
            for (int z = Mth.floor(box.minZ + EPS) - 1; z <= Mth.floor(box.maxZ - EPS) + 1; z++) {
                BlockPos floor = surface(level, x, z, box.minY, 1.1, 1.1);
                if (floor != null) touchFace(cloud, level, floor, Direction.UP);
            }
        }
    }

    /** The faces the player is pressed against, over the player's height and width. */
    private static void walls(PointCloud cloud, ClientLevel level, LocalPlayer player, AABB box) {
        AABB body = box.deflate(0, 0.01, 0);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.noCollision(player, body.move(direction.getStepX() * PROBE, 0, direction.getStepZ() * PROBE))) continue;
            Direction.Axis axis = direction.getAxis();
            Direction.Axis lateral = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            double side = positive ? box.max(axis) : box.min(axis);
            int depth = Mth.floor(side + (positive ? PROBE : -PROBE));
            for (int l = Mth.floor(box.min(lateral) + EPS); l <= Mth.floor(box.max(lateral) - EPS); l++) {
                for (int y = Mth.floor(box.minY + EPS); y <= Mth.floor(box.maxY - EPS); y++) {
                    BlockPos pos = axis == Direction.Axis.X ? new BlockPos(depth, y, l) : new BlockPos(l, y, depth);
                    VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    if (shape.isEmpty()) continue;
                    double face = (axis == Direction.Axis.X ? pos.getX() : pos.getZ()) + (positive ? shape.min(axis) : shape.max(axis));
                    if (Math.abs(face - side) <= PROBE + 0.01) touchFace(cloud, level, pos, direction.getOpposite());
                }
            }
        }
    }

    /** Underside of the blocks the head hit. Touch never brings a ceiling down (CLAUDE.md 5.1). */
    private static void ceiling(PointCloud cloud, ClientLevel level, AABB box) {
        int y = Mth.floor(box.maxY + PROBE);
        for (int x = Mth.floor(box.minX + EPS); x <= Mth.floor(box.maxX - EPS); x++) {
            for (int z = Mth.floor(box.minZ + EPS); z <= Mth.floor(box.maxZ - EPS); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (!shape.isEmpty() && Math.abs(y + shape.min(Direction.Axis.Y) - box.maxY) <= PROBE + 0.01) {
                    touchFace(cloud, level, pos, Direction.DOWN);
                }
            }
        }
    }

    /**
     * Crouching toward a drop: the foot finds the edge (side face of the block stood on) and a step one block
     * lower if there is one. Only the edge, not how deep the drop is.
     */
    private static void edges(PointCloud cloud, ClientLevel level, LocalPlayer player, AABB box) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        double dx = left * Mth.cos(yaw) - forward * Mth.sin(yaw);
        double dz = forward * Mth.cos(yaw) + left * Mth.sin(yaw);
        double centerX = (box.minX + box.maxX) / 2;
        double centerZ = (box.minZ + box.maxZ) / 2;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (dx * direction.getStepX() + dz * direction.getStepZ() < 0.3) continue;
            Direction.Axis axis = direction.getAxis();
            boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            double side = positive ? box.max(axis) : box.min(axis);
            double beyond = side + (positive ? FOOT_REACH : -FOOT_REACH);
            double inside = side + (positive ? -PROBE : PROBE);
            int probeX = Mth.floor(axis == Direction.Axis.X ? beyond : centerX);
            int probeZ = Mth.floor(axis == Direction.Axis.Z ? beyond : centerZ);
            if (surface(level, probeX, probeZ, box.minY, 0.1, 0.1) != null) continue; // floor goes on: no edge
            BlockPos edge = surface(level, Mth.floor(axis == Direction.Axis.X ? inside : centerX),
                    Mth.floor(axis == Direction.Axis.Z ? inside : centerZ), box.minY, 0.1, 0.1);
            if (edge == null) continue;
            touchFace(cloud, level, edge, direction);
            BlockPos step = surface(level, probeX, probeZ, box.minY - 1, 0.6, 0.1);
            if (step != null) touchFace(cloud, level, step, Direction.UP);
        }
    }

    private static final int EDGE_POINTS = 5;

    /**
     * Where points on a block go: the union of its collision and visible shapes. Mud and soul sand collide
     * lower than they are drawn; points at the collision top would sit inside the drawn block, hidden.
     */
    static @Nullable AABB surfaceBounds(Level level, BlockPos pos, BlockState state) {
        VoxelShape collision = state.getCollisionShape(level, pos);
        if (collision.isEmpty()) return null;
        VoxelShape visible = state.getShape(level, pos);
        return visible.isEmpty() ? collision.bounds() : collision.bounds().minmax(visible.bounds());
    }

    /** A line of points along the top edge of a ledge, corners included: the drop reads as a line. */
    private static void touchEdge(PointCloud cloud, Level level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        AABB bounds = surfaceBounds(level, pos, state);
        if (bounds == null) return;
        int material = TouchMaterial.of(level, pos, state, side).ordinal();
        Direction.Axis along = side.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        double out = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.max(side.getAxis()) : bounds.min(side.getAxis());
        double top = pos.getY() + bounds.maxY;
        for (int i = 0; i < EDGE_POINTS; i++) {
            double t = Mth.lerp(i / (double) (EDGE_POINTS - 1), bounds.min(along), bounds.max(along));
            double x = pos.getX() + (side.getAxis() == Direction.Axis.X ? out : t);
            double z = pos.getZ() + (side.getAxis() == Direction.Axis.Z ? out : t);
            cloud.add(PointSource.TOUCH, x, top, z, side, material);
        }
    }

    /** The block in column (x, z) whose collision top lies within [y - below, y + above], searching down from above. */
    private static @Nullable BlockPos surface(Level level, int x, int z, double y, double below, double above) {
        for (int by = Mth.floor(y + above); by >= Mth.floor(y - below) - 1; by--) {
            BlockPos pos = new BlockPos(x, by, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = by + shape.max(Direction.Axis.Y);
            if (top >= y - below && top <= y + above) return pos;
        }
        return null;
    }

    /**
     * A 2×2 jittered grid of points over the block's face (its collision shape's bounds). The jitter is fixed per
     * spot, so touching the same face again refreshes these points instead of adding new ones.
     */
    static void touchFace(PointCloud cloud, Level level, BlockPos pos, Direction face) {
        touchFace(cloud, level, pos, face, 2);
    }

    /** A grid×grid jittered set of points over the face; see {@link #touchFace(PointCloud, Level, BlockPos, Direction)}. */
    static void touchFace(PointCloud cloud, Level level, BlockPos pos, Direction face, int grid) {
        BlockState state = level.getBlockState(pos);
        AABB bounds = surfaceBounds(level, pos, state);
        if (bounds == null) return;
        int material = TouchMaterial.of(level, pos, state, face).ordinal();
        Direction.Axis axis = face.getAxis();
        Direction.Axis uAxis = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis vAxis = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.max(axis) : bounds.min(axis);
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                long hash = HashCommon.mix(pos.asLong() * 31 + face.ordinal() * 7L + i * 8L + j + grid * 64L);
                double u = Mth.lerp((i + 0.5 + ((hash & 0xFF) / 255.0 - 0.5) * 0.6) / grid, bounds.min(uAxis), bounds.max(uAxis));
                double v = Mth.lerp((j + 0.5 + (((hash >>> 8) & 0xFF) / 255.0 - 0.5) * 0.6) / grid, bounds.min(vAxis), bounds.max(vAxis));
                double[] local = new double[3];
                local[axis.ordinal()] = plane;
                local[uAxis.ordinal()] = u;
                local[vAxis.ordinal()] = v;
                cloud.add(PointSource.TOUCH, pos.getX() + local[0], pos.getY() + local[1], pos.getZ() + local[2], face, material);
            }
        }
    }

    private ClientTouch() {}
}
