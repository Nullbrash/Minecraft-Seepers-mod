package io.github.nullbrash.seepers.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiWorldInteractionRecipe;
import dev.emi.emi.api.stack.EmiStack;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import io.github.nullbrash.seepers.item.ChiselItem;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * Optional EMI integration: shows what plain recipes do not — the cane's iron tip and repairs on the anvil, and the
 * chisel's block conversions. Loaded only by EMI (annotation scan), so the mod never needs it.
 */
@EmiEntrypoint
public final class SeepersEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        for (Item cane : new Item[] {SeepersItems.CANE.get(), SeepersItems.BOUND_CANE.get()}) {
            ItemStack tipped = new ItemStack(cane);
            tipped.set(SeepersComponents.IRON_TIP.get(), true);
            registry.addRecipe(new AnvilEmiRecipe(id("anvil/iron_tip/" + name(cane)), EmiStack.of(cane), EmiStack.of(Items.IRON_NUGGET), EmiStack.of(tipped)));
        }
        repair(registry, SeepersItems.BOUND_CANE.get(), Items.STRING);
        repair(registry, SeepersItems.CHISEL.get(), Items.IRON_INGOT);

        ItemStack hewn = new ItemStack(SeepersBlocks.ANOMALY_ROCK_ITEM.get());
        hewn.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(AnomalyRockBlock.KIND, AnomalyRockBlock.Kind.HEWN));
        chisel(registry, "anomaly_rock", EmiStack.of(SeepersBlocks.ANOMALY_ROCK_ITEM.get()), EmiStack.of(hewn),
                Component.translatable("emi.seepers.hewn"));
        // The data map is synced to clients, so a server's own conversions show too.
        BuiltInRegistries.BLOCK.getDataMap(ChiselItem.RESULTS).forEach((key, result) -> {
            Block from = BuiltInRegistries.BLOCK.get(key);
            if (from.asItem() == Items.AIR || result.result().asItem() == Items.AIR) return;
            chisel(registry, key.location().getNamespace() + "/" + key.location().getPath(), EmiStack.of(from), EmiStack.of(result.result()), null);
        });
    }

    private static void repair(EmiRegistry registry, Item item, Item material) {
        ItemStack worn = new ItemStack(item);
        worn.setDamageValue(worn.getMaxDamage() * 3 / 4);
        registry.addRecipe(new AnvilEmiRecipe(id("anvil/repair/" + name(item)), EmiStack.of(worn), EmiStack.of(material), EmiStack.of(item)));
    }

    private static void chisel(EmiRegistry registry, String path, EmiStack from, EmiStack to, Component note) {
        registry.addRecipe(EmiWorldInteractionRecipe.builder()
                .id(id("chisel/" + path))
                .leftInput(from)
                .rightInput(EmiStack.of(SeepersItems.CHISEL.get()), true,
                        slot -> slot.appendTooltip(Component.translatable("emi.seepers.chisel_hold", Component.keybind("key.use"))))
                .output(to, slot -> note == null ? slot : slot.appendTooltip(note))
                .build());
    }

    private static String name(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /** Synthetic ids start with '/', as EMI asks for recipes that are not in the recipe manager. */
    private static ResourceLocation id(String path) {
        return Seepers.id("/" + path);
    }
}
