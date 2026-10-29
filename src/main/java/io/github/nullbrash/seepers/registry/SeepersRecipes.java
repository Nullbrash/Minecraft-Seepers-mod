package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.lidar.CardCopyRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipes made in code. Names are saved in recipe files — do not rename. */
public final class SeepersRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Seepers.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CardCopyRecipe>> CARD_COPY =
            SERIALIZERS.register("memory_card_copy", () -> new SimpleCraftingRecipeSerializer<>(CardCopyRecipe::new));

    private SeepersRecipes() {}
}
