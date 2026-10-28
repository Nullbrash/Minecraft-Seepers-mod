package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.config.ServerConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The data refresh module's repair (CLAUDE.md 5.3): never on its own and never during ordinary scans — only the special
 * scan made standing still with the module starts it. Then the lidar's memory all around, within its range, is checked
 * against the world a few thousand points a tick, and points whose surface is gone are dropped. Later the same repair
 * also undoes what the world, special blocks and hostile entities do to the data.
 */
public final class LidarRefresh {
    private static final int POINTS_PER_TICK = 4096;
    /** A section's centre may lie this far past the range while its points are within it. */
    private static final double NEAR = 14;

    private static final LongArrayList QUEUE = new LongArrayList();
    private static int next;

    public static void onClientTick(ClientTickEvent.Post event) {
        PointCloud cloud = PointCloud.current();
        if (cloud == null || next >= QUEUE.size()) return;
        int budget = POINTS_PER_TICK;
        while (budget > 0 && next < QUEUE.size()) budget -= Math.max(1, cloud.refreshSection(QUEUE.getLong(next++)));
    }

    /** A repair pass: queues every section with points within the lidar's range around the player, nearest first. */
    public static void repairAround() {
        LocalPlayer player = Minecraft.getInstance().player;
        PointCloud cloud = PointCloud.current();
        if (player == null || cloud == null) return;
        QUEUE.clear();
        next = 0;
        Vec3 eye = player.getEyePosition();
        double range = ServerConfig.get(ServerConfig.LIDAR_RANGE) + NEAR;
        for (long key : cloud.sectionKeys()) {
            if (centre(key).distanceToSqr(eye) <= range * range) QUEUE.add(key);
        }
        QUEUE.sort((x, y) -> Double.compare(centre(x).distanceToSqr(eye), centre(y).distanceToSqr(eye)));
    }

    private static Vec3 centre(long key) {
        return new Vec3(SectionPos.sectionToBlockCoord(SectionPos.x(key)) + 8, SectionPos.sectionToBlockCoord(SectionPos.y(key)) + 8,
                SectionPos.sectionToBlockCoord(SectionPos.z(key)) + 8);
    }

    public static void onLoggingOut() {
        QUEUE.clear();
        next = 0;
    }

    private LidarRefresh() {}
}
