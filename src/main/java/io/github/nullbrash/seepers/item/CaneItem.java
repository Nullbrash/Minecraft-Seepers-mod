package io.github.nullbrash.seepers.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The cane (CLAUDE.md 5.2). Tapping and sweeping run on the client while the use key is held with a cane in hand
 * ({@code client.touch.CaneTouch}) — not through item use, which would slow the player like eating does. The
 * server spends durability and plays the knock ({@code touch.CaneTaps}).
 */
public class CaneItem extends Item {
    public CaneItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.STRING);
    }
}
