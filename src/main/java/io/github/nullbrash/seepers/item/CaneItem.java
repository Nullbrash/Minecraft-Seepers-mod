package io.github.nullbrash.seepers.item;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A cane (CLAUDE.md 5.2): plain, bound with string, later reinforced with leather — the same tool with more
 * durability. Tapping and sweeping run on the client while the use key is held with a cane in hand
 * ({@code client.touch.CaneTouch}) — not through item use, which would slow the player like eating does. The
 * server spends durability and plays the knock ({@code touch.CaneTaps}).
 */
public class CaneItem extends Item {
    private final @Nullable Supplier<Item> repairItem;

    /** repairItem null: cannot be repaired (the plain cane — make a new one). */
    public CaneItem(Properties properties, @Nullable Supplier<Item> repairItem) {
        super(properties);
        this.repairItem = repairItem;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repairItem != null && repair.is(repairItem.get());
    }
}
