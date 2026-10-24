package io.github.nullbrash.seepers.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The cane (CLAUDE.md 5.2): held right click taps where the player looks; holding it sweeps the floor ahead.
 * Taps are computed on the client (points); the server spends durability and plays the knock for everyone.
 */
public class CaneItem extends Item {
    /** Client side of using the cane; set by the client entry point so this common class never loads client code. */
    public interface ClientUse {
        void tick(LivingEntity user, InteractionHand hand, int usedTicks);
    }

    private static @Nullable ClientUse clientUse;

    public CaneItem(Properties properties) {
        super(properties);
    }

    public static void setClientUse(ClientUse use) {
        clientUse = use;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide && clientUse != null) {
            clientUse.tick(user, user.getUsedItemHand(), getUseDuration(stack, user) - remainingUseDuration);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.STRING);
    }
}
