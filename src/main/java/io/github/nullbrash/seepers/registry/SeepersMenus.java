package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.lidar.LidarMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menus. */
public final class SeepersMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Seepers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<LidarMenu>> LIDAR = MENUS.register("lidar",
            () -> IMenuTypeExtension.create(LidarMenu::fromNetwork));

    private SeepersMenus() {}
}
