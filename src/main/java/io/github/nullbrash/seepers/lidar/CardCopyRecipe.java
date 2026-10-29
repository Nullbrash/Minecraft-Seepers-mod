package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.item.MemoryCardItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Copying a memory card (CLAUDE.md 5.3): a written card and a blank one of the same or a higher level give a blank
 * copy of the blank's level that fills from the written card when it first goes into a lidar; the written card stays.
 * A card its author locked against copying does not copy.
 */
public class CardCopyRecipe extends CustomRecipe {
    public CardCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Pair(ItemStack source, int sourceSlot, ItemStack blank) {}

    private static Pair find(CraftingInput input) {
        ItemStack source = ItemStack.EMPTY;
        ItemStack blank = ItemStack.EMPTY;
        int sourceSlot = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof MemoryCardItem)) return null;
            CardData data = MemoryCardItem.data(stack);
            if (data.id().isPresent()) {
                if (!source.isEmpty()) return null;
                source = stack;
                sourceSlot = i;
            } else if (data.copyOf().isEmpty()) {
                if (!blank.isEmpty()) return null;
                blank = stack;
            } else {
                return null;
            }
        }
        if (source.isEmpty() || blank.isEmpty()) return null;
        if (MemoryCardItem.data(source).noCopy()) return null;
        if (((MemoryCardItem) blank.getItem()).level() < ((MemoryCardItem) source.getItem()).level()) return null;
        return new Pair(source, sourceSlot, blank);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Pair pair = find(input);
        if (pair == null) return ItemStack.EMPTY;
        ItemStack copy = pair.blank().copyWithCount(1);
        copy.set(SeepersComponents.CARD.get(), MemoryCardItem.data(pair.source()).copy());
        return copy;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Pair pair = find(input);
        if (pair != null) remaining.set(pair.sourceSlot(), pair.source().copyWithCount(1));
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SeepersRecipes.CARD_COPY.get();
    }
}
