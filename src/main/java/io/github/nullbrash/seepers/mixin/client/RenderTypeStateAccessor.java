package io.github.nullbrash.seepers.mixin.client;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The lidar's analysis III reads which texture an entity's render type draws with; vanilla keeps it private. */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface RenderTypeStateAccessor {
    @Invoker("state")
    RenderType.CompositeState seepers$state();
}
