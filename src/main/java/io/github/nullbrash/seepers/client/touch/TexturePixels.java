package io.github.nullbrash.seepers.client.touch;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.mixin.client.CompositeStateAccessor;
import io.github.nullbrash.seepers.mixin.client.RenderTypeStateAccessor;
import io.github.nullbrash.seepers.mixin.client.TextureStateShardInvoker;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Pixels of the textures entities are drawn with, for the lidar's analysis III (CLAUDE.md 5.3). Entity textures
 * (skins downloaded at run time, atlases for held items and armour trims) have no copy in memory, so each is read
 * back from the GPU once and kept; render thread only.
 */
public final class TexturePixels {
    /** Larger textures (the block atlas at high resolution) are not copied. */
    private static final int MAX_SIDE = 4096;

    private record Copy(int glId, NativeImage image) {}

    private static final Map<ResourceLocation, Copy> COPIES = new HashMap<>();

    /** The texture a render type binds, if it binds one. */
    public static @Nullable ResourceLocation textureOf(RenderType type) {
        if (!(type instanceof RenderTypeStateAccessor composite)) return null;
        var state = (CompositeStateAccessor) (Object) composite.seepers$state();
        return ((TextureStateShardInvoker) state.seepers$textureState()).seepers$texture().orElse(null);
    }

    /** Whether the texture's pixels could be copied. */
    public static boolean readable(ResourceLocation texture) {
        return image(texture) != null;
    }

    /** ARGB of the pixel at texture coordinates (u, v) in [0, 1]; 0 if clear or the texture cannot be read. */
    public static int argb(ResourceLocation texture, float u, float v) {
        NativeImage image = image(texture);
        if (image == null) return 0;
        int x = Mth.clamp((int) (u * image.getWidth()), 0, image.getWidth() - 1);
        int y = Mth.clamp((int) (v * image.getHeight()), 0, image.getHeight() - 1);
        int abgr = image.getPixelRGBA(x, y);
        // NativeImage pixels are ABGR.
        return abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | abgr >> 16 & 0xFF;
    }

    private static @Nullable NativeImage image(ResourceLocation texture) {
        if (!RenderSystem.isOnRenderThread()) return null;
        AbstractTexture gpu = Minecraft.getInstance().getTextureManager().getTexture(texture);
        int id = gpu.getId();
        Copy copy = COPIES.get(texture);
        // A resource reload uploads textures anew under new ids.
        if (copy != null && copy.glId() == id) return copy.image();
        if (copy != null && copy.image() != null) copy.image().close();
        NativeImage image = null;
        try {
            gpu.bind();
            int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE) {
                image = new NativeImage(width, height, false);
                image.downloadTexture(0, false);
            }
        } catch (RuntimeException e) {
            Seepers.LOGGER.debug("Could not read texture {}", texture, e);
            if (image != null) image.close();
            image = null;
        }
        COPIES.put(texture, new Copy(id, image));
        return image;
    }

    private TexturePixels() {}
}
