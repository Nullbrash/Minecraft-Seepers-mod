package io.github.nullbrash.seepers.client.lidar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nullbrash.seepers.config.ClientConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The lidar's beams made barely visible (CLAUDE.md 5.3): a faint line from the lidar in hand to where a ray landed,
 * gone in a moment — the laser at work, not a light that shows the world. A sample of the rays, not all of them; the
 * opacity is in the client config (0 = off).
 */
public final class LidarRays {
    private static final int CAPACITY = 256;
    /** How long a beam line shows, seconds. */
    private static final float LIFE_SECONDS = 0.12F;
    /** At most this many lines per frame are recorded: the beam reads as a flicker, not a solid fan. */
    private static final int PER_FRAME = 12;
    private static final float RED = 0.78F;
    private static final float GREEN = 0.9F;
    private static final float BLUE = 1.0F;
    /** The emitter of the lidar held in first person, on screen (-1..1 from the centre; measured on the item model). */
    private static final double EMITTER_SCREEN_X = 0.66;
    private static final double EMITTER_SCREEN_Y = -0.2;
    /** How far ahead of the camera the start sits: close, so the line leaves the item and not the air before it. */
    private static final double EMITTER_DEPTH = 0.4;

    private static final double[] END = new double[CAPACITY * 3];
    private static final long[] BORN = new long[CAPACITY];
    private static int next;
    private static int recordedThisFrame;
    private static long frame;

    /** A ray of this frame landed at (or ran out at) the end point. */
    static void record(Vec3 end, long frameNanos) {
        if (frameNanos != frame) {
            frame = frameNanos;
            recordedThisFrame = 0;
        }
        if (recordedThisFrame >= PER_FRAME || ClientConfig.LIDAR_RAYS_OPACITY.get() <= 0) return;
        recordedThisFrame++;
        int i = next;
        next = (next + 1) % CAPACITY;
        END[i * 3] = end.x;
        END[i * 3 + 1] = end.y;
        END[i * 3 + 2] = end.z;
        BORN[i] = System.nanoTime();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        float opacity = ClientConfig.LIDAR_RAYS_OPACITY.get().floatValue();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (opacity <= 0 || player == null) return;
        long now = System.nanoTime();
        Vec3 camera = event.getCamera().getPosition();
        Vec3 from = origin(minecraft, player, event.getPartialTick().getGameTimeDeltaPartialTick(false)).subtract(camera);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = null;
        PoseStack.Pose pose = event.getPoseStack().last();
        for (int i = 0; i < CAPACITY; i++) {
            float age = (now - BORN[i]) / 1.0E9F;
            if (BORN[i] == 0 || age > LIFE_SECONDS) continue;
            if (lines == null) lines = buffers.getBuffer(LidarRayType.RAYS);
            float alpha = opacity * (1 - age / LIFE_SECONDS);
            float x = (float) (END[i * 3] - camera.x);
            float y = (float) (END[i * 3 + 1] - camera.y);
            float z = (float) (END[i * 3 + 2] - camera.z);
            float dx = x - (float) from.x;
            float dy = y - (float) from.y;
            float dz = z - (float) from.z;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-4F) continue;
            dx /= length;
            dy /= length;
            dz /= length;
            // Brighter at the lidar, fainter at the far end.
            lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(RED, GREEN, BLUE, alpha).setNormal(pose, dx, dy, dz);
            lines.addVertex(pose, x, y, z).setColor(RED, GREEN, BLUE, alpha * 0.4F).setNormal(pose, dx, dy, dz);
        }
        if (lines != null) buffers.endBatch(LidarRayType.RAYS);
    }

    /**
     * Where the beams start: the emitter at the top of the lidar in hand. In first person the held item is drawn at a
     * fixed spot on the screen, so the start is the world point a little ahead of the camera that shows there (any
     * field of view); in third person, by the holding hand.
     */
    private static Vec3 origin(Minecraft minecraft, Player player, float partial) {
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Vec3 right = LidarScan.right(player, partial);
        Vec3 up = right.cross(look);
        InteractionHand hand = LidarScan.lidarHand(minecraft.player);
        HumanoidArm arm = hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
        double side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            double tanY = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2);
            double tanX = tanY * minecraft.getWindow().getWidth() / Math.max(1.0, minecraft.getWindow().getHeight());
            return eye.add(look.add(right.scale(side * EMITTER_SCREEN_X * tanX)).add(up.scale(EMITTER_SCREEN_Y * tanY)).scale(EMITTER_DEPTH));
        }
        return eye.add(look.scale(0.55)).add(right.scale(side * 0.38)).add(up.scale(-0.45));
    }

    public static void clear() {
        java.util.Arrays.fill(BORN, 0);
    }

    private LidarRays() {}
}
