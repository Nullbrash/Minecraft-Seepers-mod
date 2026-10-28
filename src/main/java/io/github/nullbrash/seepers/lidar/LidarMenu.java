package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.ModuleItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The lidar's screen as a menu (CLAUDE.md 5.3): one slot per module kind, the player's inventory below. The modules
 * live in the held lidar (component {@code seepers:modules}); every change is written straight back to it. The held
 * lidar's own slot is locked while the screen is open. Buttons pick the scan pattern (with the pattern module) and
 * the scan area.
 */
public class LidarMenu extends AbstractContainerMenu {
    public static final int MODULE_X = 12;
    public static final int MODULE_Y = 22;
    public static final int INVENTORY_X = 69;
    public static final int INVENTORY_Y = 162;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer modules = new SimpleContainer(LidarModules.SLOTS);
    private boolean loading;

    public static void open(ServerPlayer player, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof LidarItem)) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LidarMenu(id, inventory, hand),
                Component.translatable("seepers.lidar.screen.title")), buf -> buf.writeBoolean(hand == InteractionHand.OFF_HAND));
    }

    public static LidarMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new LidarMenu(id, inventory, buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    public LidarMenu(int id, Inventory inventory, InteractionHand hand) {
        super(SeepersMenus.LIDAR.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        loading = true;
        NonNullList<ItemStack> stored = LidarModules.contents(lidar());
        for (int i = 0; i < LidarModules.SLOTS; i++) modules.setItem(i, stored.get(i));
        loading = false;
        modules.addListener(container -> save());
        for (int i = 0; i < LidarModules.SLOTS; i++) {
            LidarModules.Kind kind = LidarModules.Kind.values()[i];
            addSlot(new Slot(modules, i, MODULE_X + (i % 3) * 20, MODULE_Y + (i / 3) * 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ModuleItem module && module.kind() == kind;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        int locked = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            boolean lock = column == locked;
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58) {
                @Override
                public boolean mayPickup(Player player) {
                    return !lock;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !lock;
                }
            });
        }
    }

    public ItemStack lidar() {
        return player.getItemInHand(hand);
    }

    private void save() {
        if (loading || player.level().isClientSide) return;
        NonNullList<ItemStack> slots = NonNullList.withSize(LidarModules.SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < LidarModules.SLOTS; i++) slots.set(i, modules.getItem(i).copy());
        ItemStack lidar = lidar();
        if (lidar.getItem() instanceof LidarItem) LidarModules.store(lidar, slots);
    }

    /** Buttons: 0-3 the scan pattern (with the pattern module), AREA_BUTTON + i the scan area. */
    public static final int AREA_BUTTON = 10;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= AREA_BUTTON) {
            int count = LidarModules.areas(LidarModules.level(lidar(), LidarModules.Kind.VIEW)).size();
            if (id - AREA_BUTTON >= count) return false;
            lidar().set(SeepersComponents.AREA.get(), id - AREA_BUTTON);
            return true;
        }
        if (id < 0 || id >= LidarModules.Pattern.values().length || LidarModules.level(lidar(), LidarModules.Kind.PATTERN) == 0) return false;
        lidar().set(SeepersComponents.PATTERN.get(), id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < LidarModules.SLOTS) {
            if (!moveItemStackTo(stack, LidarModules.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof ModuleItem module) {
            int target = module.kind().ordinal();
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return lidar().getItem() instanceof LidarItem;
    }
}
