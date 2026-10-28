package io.github.nullbrash.seepers.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A predecessor's schematic (CLAUDE.md 6.6): a template in module recipes of its level — it is not used up, it comes
 * back out of the crafting grid.
 */
public class SchematicItem extends Item {
    private final int level;

    public SchematicItem(Properties properties, int level) {
        super(properties);
        this.level = level;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.seepers.schematic.hint", level == 2 ? "II" : "III").withStyle(ChatFormatting.GRAY));
    }
}
