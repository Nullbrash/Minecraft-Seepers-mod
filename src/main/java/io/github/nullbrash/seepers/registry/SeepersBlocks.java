package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks. Registry names are saved in worlds — do not rename. */
public final class SeepersBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Seepers.MOD_ID);

    /** Hardness -1: unbreakable by tools, like bedrock; explosion resistance comes from the block (by distortion). */
    public static final DeferredBlock<AnomalyRockBlock> ANOMALY_ROCK = BLOCKS.register("anomaly_rock",
            () -> new AnomalyRockBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE).pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> ANOMALY_ROCK_ITEM = SeepersItems.ITEMS.registerSimpleBlockItem(ANOMALY_ROCK);
    public static final DeferredItem<Item> ANOMALY_SHARD = SeepersItems.ITEMS.registerSimpleItem("anomaly_shard");

    private SeepersBlocks() {}
}
