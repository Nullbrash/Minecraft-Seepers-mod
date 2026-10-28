package io.github.nullbrash.seepers.integration.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A click in the inventory done in code (overrideOtherStackedOnMe): the first item on the cursor, used on the second. */
final class InventoryEmiRecipe extends BasicEmiRecipe {
    InventoryEmiRecipe(EmiRecipeCategory category, ResourceLocation id, EmiIngredient cursor, EmiIngredient target, EmiStack output) {
        super(category, id, 125, 18);
        inputs = List.of(cursor, target);
        outputs = List.of(output);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 0, 0)
                .appendTooltip(Component.translatable("emi.seepers.inventory_click", Component.keybind("key.use")));
        widgets.addTexture(EmiTexture.PLUS, 27, 3);
        widgets.addSlot(inputs.get(1), 49, 0);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 76, 1);
        widgets.addSlot(outputs.get(0), 107, 0).recipeContext(this);
    }
}
