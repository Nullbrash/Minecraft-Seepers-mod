package io.github.nullbrash.seepers.client.points;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.touch.TouchMaterial;
import io.github.nullbrash.seepers.config.ClientConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
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

/**
 * Draws the point cloud (CLAUDE.md 5, 5.3): one static vertex buffer per section, rebuilt only when its points or
 * their light change. Points are camera-facing glowing discs drawn by our own shader: darkness and fog do not
 * touch them, depth does (no points through walls), fading with age happens on the GPU.
 */
public final class PointRenderer {
    /** Position (section-relative), color (alpha = visibility in light), UV0 = (born, lifetime) s, UV1 = corner. */
    static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .build();
    private static final long REBUILD_BUDGET_NANOS = 2_000_000;
    private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private static @Nullable ShaderInstance shader;
    private static @Nullable ByteBufferBuilder bytes;

    // Stats for /seepers points stats.
    private static int rebuiltThisSecond;
    private static long rebuildNanosThisSecond;
    private static int rebuiltLastSecond;
    private static long rebuildNanosLastSecond;
    private static long secondStarted;
    private static double drawNanosAverage;

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
            section.buffer.bind();
            section.buffer.draw();
        }
        shader.clear();
        VertexBuffer.unbind();

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
            if (section.points.isEmpty()) {
                empty.add(section);
                continue;
            }
            rebuild(cloud, section);
            rebuiltThisSecond++;
        }
        for (PointCloud.Section section : empty) {
            if (section.buffer != null) section.buffer.close();
            cloud.sections.remove(section.key);
        }
        rebuildNanosThisSecond += System.nanoTime() - started;
    }

    private static void rebuild(PointCloud cloud, PointCloud.Section section) {
        section.dirty = false;
        int fadeStart = ClientConfig.POINT_LIGHT_FADE_START.get();
        int fadeEnd = Math.max(fadeStart + 1, ClientConfig.POINT_LIGHT_FADE_END.get());
        // Lift the disc off the face so it does not sink into the block it lies on.
        float lift = ClientConfig.POINT_SIZE.get().floatValue() * 0.5F + 0.005F;
        if (bytes == null) bytes = new ByteBufferBuilder(FORMAT.getVertexSize() * 4 * 256);
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, FORMAT);
        int hash = 1;
        for (int packed : section.points.values()) {
            PointCloud.Ring ring = cloud.ringOf(packed);
            int slot = packed & 0xFFFFFF;
            int light = cloud.lightAt(section.key, ring, slot);
            hash = hash * 31 + light;
            float t = Mth.clamp((light - fadeStart) / (float) (fadeEnd - fadeStart), 0, 1);
            int alpha = Math.round((1 - t * t * (3 - 2 * t)) * 255);
            if (alpha <= 0) continue; // surface is lit and visible: no point needed
            Direction face = Direction.values()[ring.face[slot]];
            float x = ring.x[slot] + face.getStepX() * lift;
            float y = ring.y[slot] + face.getStepY() * lift;
            float z = ring.z[slot] + face.getStepZ() * lift;
            // Material tint (touch); solid is the cold blue-white shared by all sources (CLAUDE.md 5).
            TouchMaterial material = TouchMaterial.byId(ring.material[slot]);
            for (int[] corner : CORNERS) {
                builder.addVertex(x, y, z).setColor(material.red, material.green, material.blue, alpha).setUv(ring.born[slot], ring.lifetime[slot])
                        .setUv1(corner[0], corner[1]);
            }
        }
        section.lightHash = hash;
        MeshData mesh = builder.build();
        if (mesh == null) {
            if (section.buffer != null) {
                section.buffer.close();
                section.buffer = null;
            }
            return;
        }
        if (section.buffer == null) section.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        section.buffer.bind();
        section.buffer.upload(mesh);
        VertexBuffer.unbind();
    }

    private PointRenderer() {}
}
