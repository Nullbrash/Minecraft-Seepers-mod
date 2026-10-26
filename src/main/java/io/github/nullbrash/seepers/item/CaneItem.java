package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A cane (CLAUDE.md 5.2): plain, bound with string, later reinforced with leather — the same tool with more
 * durability; any of them can get an iron tip on an anvil. Tapping and sweeping run on the client while the use key
 * is held with a cane in hand ({@code client.touch.CaneTouch}) — not through item use, which would slow the player
 * like eating does. The server spends durability, burns wooden tips in lava and plays the knock
 * ({@code touch.CaneTaps}).
 */
public class CaneItem extends Item {
    private final @Nullable Supplier<Item> repairItem;

    /** repairItem null: cannot be repaired (the plain cane — make a new one). */
    public CaneItem(Properties properties, @Nullable Supplier<Item> repairItem) {
        super(properties);
        this.repairItem = repairItem;
    }

    public static boolean hasIronTip(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(SeepersComponents.IRON_TIP.get()));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repairItem != null && repair.is(repairItem.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (hasIronTip(stack)) tooltip.add(Component.translatable("item.seepers.cane.iron_tip").withStyle(ChatFormatting.GRAY));
    }

    /** Cane + iron nugget on an anvil: the same cane (durability kept) with an iron tip, for one level. */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack cane = event.getLeft();
        if (!(cane.getItem() instanceof CaneItem) || hasIronTip(cane) || !event.getRight().is(Items.IRON_NUGGET)) return;
        ItemStack tipped = cane.copy();
        tipped.set(SeepersComponents.IRON_TIP.get(), true);
        event.setOutput(tipped);
        event.setCost(1);
        event.setMaterialCost(1);
    }
}
