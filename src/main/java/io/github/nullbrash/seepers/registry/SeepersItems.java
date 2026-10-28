package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.item.ChiselItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.ModuleItem;
import io.github.nullbrash.seepers.item.SchematicItem;
import io.github.nullbrash.seepers.lidar.LidarModules;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** Lidar (CLAUDE.md 5.3). */
    public static final DeferredItem<LidarItem> LIDAR = ITEMS.register("lidar", () -> new LidarItem(new Item.Properties().stacksTo(1)));

    /** The lidar's battery (CLAUDE.md 5.3), recharged with redstone dust. */
    public static final DeferredItem<BatteryItem> BATTERY = ITEMS.register("battery", () -> new BatteryItem(new Item.Properties().stacksTo(1)));

    /** The board every lidar module starts from. */
    public static final DeferredItem<Item> CIRCUIT = ITEMS.register("lidar_circuit", () -> new Item(new Item.Properties()));
    /** Predecessors' schematics for module levels II and III (CLAUDE.md 6.6). */
    public static final DeferredItem<SchematicItem> SCHEMATIC_2 = ITEMS.register("schematic_2", () -> new SchematicItem(new Item.Properties().stacksTo(1), 2));
    public static final DeferredItem<SchematicItem> SCHEMATIC_3 = ITEMS.register("schematic_3", () -> new SchematicItem(new Item.Properties().stacksTo(1), 3));
    /** Lidar modules that exist so far, by kind, level I first (analysis, filter, water and echo come in later phases). */
    public static final Map<LidarModules.Kind, List<DeferredItem<ModuleItem>>> MODULES = modules(
            LidarModules.Kind.VIEW, LidarModules.Kind.DENSITY, LidarModules.Kind.MEMORY, LidarModules.Kind.PATTERN, LidarModules.Kind.DYNAMO,
            LidarModules.Kind.REFRESH, LidarModules.Kind.ANALYSIS, LidarModules.Kind.BEAMS);

    private static Map<LidarModules.Kind, List<DeferredItem<ModuleItem>>> modules(LidarModules.Kind... kinds) {
        Map<LidarModules.Kind, List<DeferredItem<ModuleItem>>> map = new EnumMap<>(LidarModules.Kind.class);
        for (LidarModules.Kind kind : kinds) {
            DeferredItem<ModuleItem>[] levels = new DeferredItem[kind.maxLevel];
            for (int level = 1; level <= kind.maxLevel; level++) {
                int l = level;
                String name = "lidar_module_" + kind.id() + (kind.maxLevel > 1 ? "_" + level : "");
                levels[level - 1] = ITEMS.register(name, () -> new ModuleItem(new Item.Properties().stacksTo(1), kind, l));
            }
            map.put(kind, List.of(levels));
        }
        return map;
    }

    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CANE.get());
            event.accept(BOUND_CANE.get());
            event.accept(CHISEL.get());
            event.accept(LIDAR.get());
            event.accept(BATTERY.get());
            event.accept(BatteryItem.withCharge(BatteryItem.capacity()));
            event.accept(CIRCUIT.get());
            event.accept(SCHEMATIC_2.get());
            event.accept(SCHEMATIC_3.get());
            MODULES.values().forEach(levels -> levels.forEach(module -> event.accept(module.get())));
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) event.accept(SeepersBlocks.ANOMALY_ROCK_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(SeepersBlocks.ANOMALY_SHARD.get());
    }

    private SeepersItems() {}
}
