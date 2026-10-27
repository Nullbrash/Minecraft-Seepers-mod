package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.item.ChiselItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items. Registry names are saved in worlds — do not rename. */
public final class SeepersItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Seepers.MOD_ID);

    public static final DeferredItem<CaneItem> CANE = ITEMS.register("cane",
            () -> new CaneItem(new Item.Properties().durability(666), null));
    public static final DeferredItem<CaneItem> BOUND_CANE = ITEMS.register("bound_cane",
            () -> new CaneItem(new Item.Properties().durability(2664), () -> Items.STRING));

    /** Chisel (CLAUDE.md 5.4): 512 uses. */
    public static final DeferredItem<ChiselItem> CHISEL = ITEMS.register("chisel", () -> new ChiselItem(new Item.Properties().durability(512)));

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CANE.get());
            event.accept(BOUND_CANE.get());
            event.accept(CHISEL.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) event.accept(SeepersBlocks.ANOMALY_ROCK_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(SeepersBlocks.ANOMALY_SHARD.get());
    }

    private SeepersItems() {}
}
