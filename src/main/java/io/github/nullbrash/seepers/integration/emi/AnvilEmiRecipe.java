package io.github.nullbrash.seepers.integration.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** An anvil operation done in code (AnvilUpdateEvent, isValidRepairItem): EMI only derives repairs for tiered tools and armor. */
final class AnvilEmiRecipe extends BasicEmiRecipe {
    AnvilEmiRecipe(ResourceLocation id, EmiIngredient left, EmiIngredient right, EmiStack output) {
        super(VanillaEmiRecipeCategories.ANVIL_REPAIRING, id, 125, 18);
        inputs = List.of(left, right);
        outputs = List.of(output);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 0);
        widgets.addTexture(EmiTexture.PLUS, 27, 3);
        widgets.addSlot(inputs.get(1), 49, 0);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 76, 1);
        widgets.addSlot(outputs.get(0), 107, 0).recipeContext(this);
    }
}
