package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Несъёмные узлы старой модели обслуживаются без уничтожения прибора при поломке. */
public final class StarterLidarItem extends LidarItem {
    public StarterLidarItem(Properties properties) {
        super(properties);
    }

    public static boolean isStarter(ItemStack stack) {
        return stack.getItem() instanceof StarterLidarItem;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return ServerConfig.get(ServerConfig.STARTER_DURABILITY);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.COPPER_INGOT) || repair.is(SeepersItems.CIRCUIT.get());
    }

    public static void wear(ItemStack stack, int amount) {
        if (isStarter(stack) && amount > 0) stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + amount));
    }
}
