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

    /** A battery's charge, in ticks of the basic lidar scan (CLAUDE.md 5.3); absent = empty. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE =
            COMPONENTS.registerComponentType("charge", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The battery inside a lidar: its charge; absent = no battery. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY =
            COMPONENTS.registerComponentType("battery", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A lidar's modules, slot i = kind i (lidar.LidarModules). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> MODULES =
            COMPONENTS.registerComponentType("modules", builder -> builder.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC).cacheEncoding());
    /** The scan pattern a lidar with the pattern module draws (index of LidarModules.Pattern). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PATTERN =
            COMPONENTS.registerComponentType("pattern", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The scan area chosen on a lidar's screen (index of LidarModules.areas); absent = the widest. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AREA =
            COMPONENTS.registerComponentType("area", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A memory card's identity (lidar.CardData); its points live in the world, not here. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<io.github.nullbrash.seepers.lidar.CardData>> CARD =
            COMPONENTS.registerComponentType("card", builder -> builder.persistent(io.github.nullbrash.seepers.lidar.CardData.CODEC)
                    .networkSynchronized(io.github.nullbrash.seepers.lidar.CardData.STREAM_CODEC));

    private SeepersComponents() {}
}
