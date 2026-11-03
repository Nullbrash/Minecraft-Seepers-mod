package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.item.StarterLidarItem;
import io.github.nullbrash.seepers.registry.SeepersItems;
import io.github.nullbrash.seepers.registry.SeepersRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Перед разборкой сменные модули снимаются, чтобы крафт не уничтожил их вместе с прибором. */
public final class StarterDismantleRecipe extends CustomRecipe {
    public StarterDismantleRecipe(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int count = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (!StarterLidarItem.isStarter(stack)) return false;
            var modules = LidarModules.contents(stack);
            if (!modules.get(LidarModules.Kind.PATTERN.ordinal()).isEmpty()
                    || !modules.get(LidarModules.Kind.ANALYSIS.ordinal()).isEmpty()) return false;
            count++;
        }
        return input.size() >= 1 && count == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(SeepersItems.CIRCUIT.get(), io.github.nullbrash.seepers.config.ServerConfig.get(
                io.github.nullbrash.seepers.config.ServerConfig.STARTER_RECOVERED_CIRCUITS));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return width * height >= 1; }

    @Override
    public RecipeSerializer<?> getSerializer() { return SeepersRecipes.STARTER_DISMANTLE.get(); }
}
