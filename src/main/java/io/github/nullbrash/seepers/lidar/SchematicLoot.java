package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersItems;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Until predecessors' structures exist (milestone 14), module schematics lie in vanilla chests (CLAUDE.md 6.6): level
 * II in mineshafts and dungeons, level III deeper — ancient cities and strongholds. Chances are in the server config,
 * read when loot tables load.
 */
public final class SchematicLoot {
    private static final Set<String> LEVEL_2 = Set.of("chests/abandoned_mineshaft", "chests/simple_dungeon");
    private static final Set<String> LEVEL_3 = Set.of("chests/ancient_city", "chests/stronghold_corridor",
            "chests/stronghold_crossing", "chests/stronghold_library");

    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();
        if (!name.getNamespace().equals("minecraft")) return;
        if (LEVEL_2.contains(name.getPath())) add(event, SeepersItems.SCHEMATIC_2.get(), ServerConfig.get(ServerConfig.LIDAR_SCHEMATIC_2_CHANCE));
        if (LEVEL_3.contains(name.getPath())) add(event, SeepersItems.SCHEMATIC_3.get(), ServerConfig.get(ServerConfig.LIDAR_SCHEMATIC_3_CHANCE));
    }

    private static void add(LootTableLoadEvent event, Item schematic, double chance) {
        if (chance <= 0) return;
        event.getTable().addPool(LootPool.lootPool()
                .name("seepers_schematic")
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(schematic))
                .when(LootItemRandomChanceCondition.randomChance((float) chance))
                .build());
    }

    private SchematicLoot() {}
}
