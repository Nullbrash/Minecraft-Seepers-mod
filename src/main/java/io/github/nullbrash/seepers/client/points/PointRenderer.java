package io.github.nullbrash.seepers.client.points;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import io.github.nullbrash.seepers.config.ClientConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

/**
 * Draws the point cloud (CLAUDE.md 5, 5.3): one static buffer per section, rebuilt only when its points or their
 * light change. A point is a single 16-byte vertex drawn as a point sprite that our shader turns into a camera-facing
 * glowing disc: darkness and fog do not touch it, depth does (no points through walls), fading with age happens on
 * the GPU. Vanilla's vertex modes have no points, so the buffers are plain GL objects bound around the shader.
 */
public final class PointRenderer {
    /**
     * Attribute names in location order for the shader ({@link ShaderInstance} binds them by this list); the actual
     * layout is {@link #STRIDE} bytes set up in {@link Mesh}: position (3 shorts, 1/1024 block, section-relative,
     * already lifted off the face), lifetime (unsigned short seconds, 0 = never fades), colour (4 bytes, alpha = how
     * well light shows the surface), born (float seconds).
     */
    static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("Born", VertexFormatElement.UV0)
            .add("Life", VertexFormatElement.UV2)
            .build();
    static final int STRIDE = 16;
    private static final long REBUILD_BUDGET_NANOS = 2_000_000;
    /**
     * A big section under a held beam turns dirty every tick and costs its whole size to rebuild: such sections are
     * rebuilt at most this often (new points show a moment later), small ones at once.
     */
    private static final long BIG_REBUILD_INTERVAL_NANOS = 150_000_000;
    private static final int BIG_SECTION_POINTS = 4096;
    /** Longer lifetimes are drawn as never fading (lidar points). */
    private static final int MAX_LIFETIME_SECONDS = 65534;
    /** Texture pixels (analysis III) are drawn a little darker than the texture: the scan's look, not a photo. */
    private static final float TEXEL_BRIGHTNESS = 0.95F;
    /** Lifetime value marking broken data (anomalous rock): the shader makes it tremble and flicker, never fade. */
    private static final int BROKEN_DATA = 65535;

    private static @Nullable ShaderInstance shader;
    private static @Nullable ByteBuffer bytes;

    // Stats for /seepers points stats.
    private static int rebuiltThisSecond;
    private static long rebuildNanosThisSecond;
    private static int rebuiltLastSecond;
    private static long rebuildNanosLastSecond;
    private static long secondStarted;
    private static double drawNanosAverage;

    /** One section's points on the GPU. Render thread only. */
    static final class Mesh {
        private final int vao = GlStateManager._glGenVertexArrays();
        private final int vbo = GlStateManager._glGenBuffers();
        private int count;

        Mesh() {
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GlStateManager._enableVertexAttribArray(0);
            GlStateManager._vertexAttribPointer(0, 3, GL11.GL_SHORT, false, STRIDE, 0);
            GlStateManager._enableVertexAttribArray(1);
            GlStateManager._vertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 8);
            GlStateManager._enableVertexAttribArray(2);
            GlStateManager._vertexAttribPointer(2, 1, GL11.GL_FLOAT, false, STRIDE, 12);
            GlStateManager._enableVertexAttribArray(3);
            GlStateManager._vertexAttribPointer(3, 1, GL11.GL_UNSIGNED_SHORT, false, STRIDE, 6);
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
        }

        void upload(ByteBuffer data, int points) {
            count = points;
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            BufferUploader.invalidate();
        }

        void draw() {
            GlStateManager._glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_POINTS, 0, count);
        }

        int count() {
            return count;
        }

        void close() {
            GlStateManager._glDeleteBuffers(vbo);
            GlStateManager._glDeleteVertexArrays(vao);
        }
    }

    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Seepers.id("point_cloud"), FORMAT),
                    loaded -> shader = loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("Seepers point cloud shader failed to load", e);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Dispatched from renderLevel itself (survives renderers that replace chunk drawing) and before the
        // translucent pass: water surfaces then tint points under them instead of hiding them by depth.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || shader == null) return;
        PointCloud cloud = PointCloud.get();
        if (cloud == null || cloud.sections.isEmpty()) return;

        rebuildDirty(cloud);
        long started = System.nanoTime();
        Vec3 camera = event.getCamera().getPosition();
        Minecraft minecraft = Minecraft.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // points glow
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        GL11.glEnable(GL32.GL_PROGRAM_POINT_SIZE); // the vertex shader sizes each sprite

        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, event.getModelViewMatrix(), event.getProjectionMatrix(), minecraft.getWindow());
        shader.safeGetUniform("PointTime").set(cloud.now(event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        shader.safeGetUniform("PointSize").set(ClientConfig.POINT_SIZE.get().floatValue());
        shader.safeGetUniform("MinPixels").set(ClientConfig.POINT_MIN_PIXELS.get().floatValue());
        shader.apply();
        Uniform offset = shader.CHUNK_OFFSET;
        for (PointCloud.Section section : cloud.sections.values()) {
            if (section.buffer == null) continue;
            int ox = SectionPos.sectionToBlockCoord(SectionPos.x(section.key));
            int oy = SectionPos.sectionToBlockCoord(SectionPos.y(section.key));
            int oz = SectionPos.sectionToBlockCoord(SectionPos.z(section.key));
            if (!event.getFrustum().isVisible(new AABB(ox, oy, oz, ox + 16, oy + 16, oz + 16))) continue;
            if (offset != null) {
                offset.set((float) (ox - camera.x), (float) (oy - camera.y), (float) (oz - camera.z));
                offset.upload();
            }
            section.buffer.draw();
            if (section.freshBuffer != null && section.freshBuffer.count() > 0) section.freshBuffer.draw();
        }
        shader.clear();
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();

        GL11.glDisable(GL32.GL_PROGRAM_POINT_SIZE);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        drawNanosAverage += (System.nanoTime() - started - drawNanosAverage) * 0.05;
    }

    public static String[] stats() {
        return new String[] {String.valueOf(rebuiltLastSecond), String.format(java.util.Locale.ROOT, "%.2f", rebuildNanosLastSecond / 1e6),
                String.format(java.util.Locale.ROOT, "%.3f", drawNanosAverage / 1e6)};
    }

    /** Rebuilds dirty sections within a time budget per frame; the rest wait for the next frame. */
    private static void rebuildDirty(PointCloud cloud) {
        long started = System.nanoTime();
        if (started - secondStarted > 1_000_000_000L) {
            rebuiltLastSecond = rebuiltThisSecond;
            rebuildNanosLastSecond = rebuildNanosThisSecond;
            rebuiltThisSecond = 0;
            rebuildNanosThisSecond = 0;
            secondStarted = started;
        }
        List<PointCloud.Section> empty = new ArrayList<>();
        for (PointCloud.Section section : cloud.sections.values()) {
            if (!section.dirty) continue;
            if (System.nanoTime() - started > REBUILD_BUDGET_NANOS) break;
            if (section.points.size() > BIG_SECTION_POINTS && started - section.rebuiltAt < BIG_REBUILD_INTERVAL_NANOS) continue;
            if (section.points.isEmpty()) {
                empty.add(section);
                continue;
            }
            rebuild(cloud, section);
            rebuiltThisSecond++;
        }
        for (PointCloud.Section section : empty) {
            if (section.buffer != null) section.buffer.close();
            if (section.freshBuffer != null) section.freshBuffer.close();
            cloud.sections.remove(section.key);
        }
        // Sections still waiting for their rebuild show their fresh points from the small buffer.
        Style style = null;
        for (PointCloud.Section section : cloud.sections.values()) {
            if (!section.freshDirty || !section.dirty || section.buffer == null) continue;
            if (style == null) style = new Style();
            section.freshDirty = false;
            var cells = PointCloud.lightCells();
            ByteBuffer data = buffer(section.fresh.size() * STRIDE);
            int count = 0;
            for (int i = 0; i < section.fresh.size(); i++) {
                int packed = section.fresh.getInt(i);
                PointCloud.Ring ring = cloud.ringOf(packed);
                int slot = packed & 0xFFFFFF;
                // Replaced or refreshed since: its key now holds another slot (or none).
                if (ring == null || section.points.get(ring.local[slot]) != packed) continue;
                write(data, cloud, section, packed, cells, style);
                count++;
            }
            data.flip();
            if (section.freshBuffer == null) section.freshBuffer = new Mesh();
            section.freshBuffer.upload(data, count);
        }
        rebuildNanosThisSecond += System.nanoTime() - started;
    }

    /** Per-pass settings for writing points. */
    private static final class Style {
        final float[] lifetimes = new float[PointSource.VALUES.length];
        final int fadeStart = ClientConfig.POINT_LIGHT_FADE_START.get();
        final int fadeEnd = Math.max(fadeStart + 1, ClientConfig.POINT_LIGHT_FADE_END.get());
        // Lift the disc off the face so it does not sink into the block it lies on.
        final float lift = ClientConfig.POINT_SIZE.get().floatValue() * 0.5F + 0.005F;

        Style() {
            for (PointSource source : PointSource.VALUES) lifetimes[source.ordinal()] = source.lifetimeSeconds();
        }
    }

    private static void rebuild(PointCloud cloud, PointCloud.Section section) {
        section.dirty = false;
        section.rebuiltAt = System.nanoTime();
        section.fresh.clear();
        section.freshDirty = false;
        if (section.freshBuffer != null) section.freshBuffer.upload(buffer(0), 0);
        var cells = PointCloud.lightCells();
        Style style = new Style();
        int points = section.points.size();
        ByteBuffer data = buffer(points * STRIDE);
        int hash = 1;
        for (int packed : section.points.values()) hash = hash * 31 + write(data, cloud, section, packed, cells, style);
        data.flip();
        section.lightHash = hash;
        if (section.buffer == null) section.buffer = new Mesh();
        section.buffer.upload(data, points);
    }

    /** Writes one point's vertex; returns the light over it (for the section's light hash). */
    private static int write(ByteBuffer data, PointCloud cloud, PointCloud.Section section, int packed,
                             it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap cells, Style style) {
        int fadeStart = style.fadeStart;
        int fadeEnd = style.fadeEnd;
        float lift = style.lift;
        float[] lifetimes = style.lifetimes;
        {
            PointCloud.Ring ring = cloud.ringOf(packed);
            PointSource source = PointSource.VALUES[packed >>> 24];
            int slot = packed & 0xFFFFFF;
            int light = cloud.lightAt(section.key, ring, slot, cells);
            float t = Mth.clamp((light - fadeStart) / (float) (fadeEnd - fadeStart), 0, 1);
            TouchMaterial material = TouchMaterial.byId(ring.material[slot]);
            // Lit surfaces still get points: fog may hide them, the shader decides. Marks never fade.
            int alpha = material.isMark() ? 0 : Math.round(t * t * (3 - 2 * t) * 255);
            // The lowest alpha bit flags a texel for the shader (a hard square); 1/255 of light changes nothing.
            alpha = material.isTexel() ? alpha | 1 : alpha & ~1;
            Direction face = ring.face(slot);
            float lifetime = lifetimes[source.ordinal()];
            // The point's own colour (lidar analysis) over its material's.
            int own = ring.rgb[slot];
            int red = own == 0 ? material.red : own >> 16 & 0xFF;
            int green = own == 0 ? material.green : own >> 8 & 0xFF;
            int blue = own == 0 ? material.blue : own & 0xFF;
            if (material.isTexel()) {
                red = Math.round(red * TEXEL_BRIGHTNESS);
                green = Math.round(green * TEXEL_BRIGHTNESS);
                blue = Math.round(blue * TEXEL_BRIGHTNESS);
            }
            data.putShort(position(ring.x(slot) + face.getStepX() * lift))
                    .putShort(position(ring.y(slot) + face.getStepY() * lift))
                    .putShort(position(ring.z(slot) + face.getStepZ() * lift))
                    .putShort((short) (material == TouchMaterial.ANOMALY ? BROKEN_DATA
                            : lifetime > MAX_LIFETIME_SECONDS ? 0 : Math.max(1, Math.round(lifetime))))
                    // Material tint (touch); solid is the cold blue-white shared by all sources (CLAUDE.md 5).
                    .put((byte) red).put((byte) green).put((byte) blue).put((byte) alpha)
                    .putFloat(ring.born[slot]);
            return light;
        }
    }

    private static short position(float local) {
        return (short) Math.round(local * PointCloud.POSITION_SCALE);
    }

    /** A reused native buffer of at least the given size, cleared for writing. */
    private static ByteBuffer buffer(int size) {
        if (bytes == null || bytes.capacity() < size) {
            if (bytes != null) MemoryUtil.memFree(bytes);
            bytes = MemoryUtil.memAlloc(Math.max(size, 64 * 1024));
        }
        bytes.clear();
        return bytes;
    }

    private PointRenderer() {}
}
