package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The lidar's battery (CLAUDE.md 5.3). Its charge is ticks of the basic scan (component {@code seepers:charge}); a full
 * one lasts the configured minutes. Recharged with redstone dust in the inventory: dust on the cursor, right-click the
 * battery (or a lidar holding it).
 */
public class BatteryItem extends Item {
    public static final int BAR_COLOR = 0x6FC3FF;

    public BatteryItem(Properties properties) {
        super(properties);
    }

    /** Full charge, in ticks of the basic scan. */
    public static int capacity() {
        return ServerConfig.get(ServerConfig.LIDAR_BATTERY_MINUTES) * 60 * 20;
    }

    public static int charge(ItemStack battery) {
        return Mth.clamp(battery.getOrDefault(SeepersComponents.CHARGE.get(), 0), 0, capacity());
    }

    public static ItemStack withCharge(int charge) {
        ItemStack battery = new ItemStack(SeepersItems.BATTERY.get());
        if (charge > 0) battery.set(SeepersComponents.CHARGE.get(), Math.min(charge, capacity()));
        return battery;
    }

    /** One dust's worth added to a charge, or -1 when already full. */
    public static int withDust(int charge) {
        int capacity = capacity();
        if (charge >= capacity) return -1;
        return Math.min(capacity, charge + (int) Math.round(capacity * ServerConfig.get(ServerConfig.LIDAR_REDSTONE_CHARGE)));
    }

    public static Component describe(int charge) {
        int percent = (int) Math.floor(100.0 * charge / capacity());
        int minutes = (int) Math.ceil(charge / 1200.0);
        return Component.translatable("item.seepers.battery.charge", percent, minutes);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !other.is(Items.REDSTONE)) return false;
        int charged = withDust(charge(stack));
        if (charged < 0) return true; // full: the dust stays on the cursor
        stack.set(SeepersComponents.CHARGE.get(), charged);
        other.shrink(1);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.6F, 1.4F);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * charge(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(describe(charge(stack)).copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.seepers.battery.hint", Component.keybind("key.use")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
