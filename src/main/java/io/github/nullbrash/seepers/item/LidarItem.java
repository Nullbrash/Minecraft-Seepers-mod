package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * The lidar (CLAUDE.md 5.3). Scans run on the client while the use key is held with it in hand
 * ({@code client.lidar.LidarScan}) — not through item use, which would slow the player like eating does. The battery
 * sits inside (component {@code seepers:battery}: its charge; absent = none), swapped in the inventory like a bundle:
 * battery on the cursor, right-click the lidar; an empty cursor takes it out; redstone dust charges it in place. The
 * server spends the charge while the client scans ({@code lidar.LidarPower}).
 */
public class LidarItem extends Item {
    public LidarItem(Properties properties) {
        super(properties);
    }

    /** The inserted battery's charge, or null without one. */
    public static @Nullable Integer battery(ItemStack lidar) {
        Integer charge = lidar.get(SeepersComponents.BATTERY.get());
        return charge == null ? null : Math.min(Math.max(charge, 0), BatteryItem.capacity());
    }

    public static boolean canScan(ItemStack lidar) {
        Integer charge = battery(lidar);
        return charge != null && charge > 0;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;
        Integer inside = battery(stack);
        if (other.getItem() instanceof BatteryItem) {
            stack.set(SeepersComponents.BATTERY.get(), BatteryItem.charge(other));
            access.set(inside == null ? ItemStack.EMPTY : BatteryItem.withCharge(inside));
            player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.6F);
            return true;
        }
        if (inside == null) return false;
        if (other.isEmpty()) {
            access.set(BatteryItem.withCharge(inside));
            stack.remove(SeepersComponents.BATTERY.get());
            player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.6F, 1.4F);
            return true;
        }
        if (other.is(Items.REDSTONE)) {
            int charged = BatteryItem.withDust(inside);
            if (charged < 0) return true;
            stack.set(SeepersComponents.BATTERY.get(), charged);
            other.shrink(1);
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.6F, 1.4F);
            return true;
        }
        return false;
    }

    /** The charge drains every second while scanning: that must not replay the raise-into-hand animation. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return battery(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Integer charge = battery(stack);
        return charge == null ? 0 : Math.round(13.0F * charge / BatteryItem.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BatteryItem.BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Integer charge = battery(stack);
        tooltip.add((charge == null ? Component.translatable("item.seepers.lidar.no_battery")
                : Component.translatable("item.seepers.lidar.battery", BatteryItem.describe(charge))).withStyle(ChatFormatting.GRAY));
    }
}
