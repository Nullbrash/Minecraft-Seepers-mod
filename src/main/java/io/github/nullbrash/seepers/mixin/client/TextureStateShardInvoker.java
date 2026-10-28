package io.github.nullbrash.seepers.mixin.client;

import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** See {@link RenderTypeStateAccessor}: the texture a state binds (protected in vanilla). */
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface TextureStateShardInvoker {
    @Invoker("cutoutTexture")
    Optional<ResourceLocation> seepers$texture();
}
