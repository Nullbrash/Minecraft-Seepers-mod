package io.github.nullbrash.seepers.registry;

import com.mojang.serialization.Codec;
import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. Names are saved with items — do not rename. */
public final class SeepersComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Seepers.MOD_ID);

    /** A cane with an iron tip (CLAUDE.md 5.2): does not burn in lava, reaches one block further. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IRON_TIP =
            COMPONENTS.registerComponentType("iron_tip", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private SeepersComponents() {}
}
