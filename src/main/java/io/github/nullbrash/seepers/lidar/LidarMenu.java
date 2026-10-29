package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.MemoryCardItem;
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
 * The lidar's screen as a menu (CLAUDE.md 5.3): the device in hand — a socket per module kind, the memory card's
 * reader in the memory slot, the battery compartment — and the player's inventory below. Modules, card and battery
 * live in the held lidar (components {@code seepers:modules}, {@code seepers:battery}); every change is written straight
 * back to it. The held lidar's own slot is locked while the screen is open. Buttons pick the scan pattern and area,
 * erase the card and set its author's locks.
 */
public class LidarMenu extends AbstractContainerMenu {
    /**
     * Socket positions on the device, by module kind (slot i = kind i); the memory slot is the card reader. Left:
     * quality and output (view, density, beams, dynamo); right: accuracy, filters, display (analysis, filter, water,
     * pattern, refresh, echo).
     */
    public static final int[][] SOCKETS = {
            {14, 20}, {36, 20}, {312, 100}, {290, 20}, {312, 20}, {290, 42},
            {312, 42}, {312, 64}, {36, 42}, {290, 64}, {14, 42}};
    public static final int BATTERY_X = 21;
    public static final int BATTERY_Y = 116;
    /** The battery compartment's slot index, after the modules. */
    public static final int BATTERY_SLOT = LidarModules.SLOTS;
    public static final int INVENTORY_X = 89;
    public static final int INVENTORY_Y = 180;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer modules = new SimpleContainer(LidarModules.SLOTS);
    private final SimpleContainer battery = new SimpleContainer(1);
    private boolean loading;
    /** The battery compartment's lid is off (client only: the screen's screw); the slot hides under the lid. */
    public boolean batteryOpen;

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
        Integer charge = LidarItem.battery(lidar());
        battery.setItem(0, charge == null ? ItemStack.EMPTY : BatteryItem.withCharge(charge));
        loading = false;
        modules.addListener(container -> save());
        battery.addListener(container -> saveBattery());
        for (int i = 0; i < LidarModules.SLOTS; i++) {
            LidarModules.Kind kind = LidarModules.Kind.values()[i];
            addSlot(new Slot(modules, i, SOCKETS[i][0], SOCKETS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (kind == LidarModules.Kind.MEMORY) return stack.getItem() instanceof MemoryCardItem;
                    return stack.getItem() instanceof ModuleItem module && module.kind() == kind;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Slot(battery, 0, BATTERY_X, BATTERY_Y) {
            @Override
            public boolean isActive() {
                return !player.level().isClientSide || batteryOpen;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof BatteryItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
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

    private void saveBattery() {
        if (loading || player.level().isClientSide) return;
        ItemStack lidar = lidar();
        if (!(lidar.getItem() instanceof LidarItem)) return;
        ItemStack inside = battery.getItem(0);
        if (inside.isEmpty()) lidar.remove(SeepersComponents.BATTERY.get());
        else lidar.set(SeepersComponents.BATTERY.get(), BatteryItem.charge(inside));
    }

    /** Buttons: 0-3 the scan pattern (with the pattern module), AREA_BUTTON + i the scan area. */
    public static final int AREA_BUTTON = 10;
    /** Buttons beyond the pattern and area ones: the memory card's erase and its author's locks. */
    public static final int ERASE_BUTTON = 40;
    public static final int LOCK_WRITE_BUTTON = 41;
    public static final int LOCK_COPY_BUTTON = 42;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player instanceof ServerPlayer server && id >= ERASE_BUTTON) {
            save();
            if (id == ERASE_BUTTON) CardService.erase(server, lidar());
            else if (id == LOCK_WRITE_BUTTON || id == LOCK_COPY_BUTTON) CardService.lock(server, lidar(), id == LOCK_WRITE_BUTTON);
            loading = true;
            modules.setItem(LidarModules.Kind.MEMORY.ordinal(), LidarModules.card(lidar()).copy());
            loading = false;
            return true;
        }
        if (id >= ERASE_BUTTON) return true;
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
        int device = BATTERY_SLOT + 1;
        if (index < device) {
            if (!moveItemStackTo(stack, device, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = stack.getItem() instanceof ModuleItem module ? module.kind().ordinal()
                    : stack.getItem() instanceof MemoryCardItem ? LidarModules.Kind.MEMORY.ordinal()
                    : stack.getItem() instanceof BatteryItem ? BATTERY_SLOT : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    /** The card slot follows the server's changes to the card (its id, its point count) while the screen is open. */
    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide && lidar().getItem() instanceof LidarItem) {
            ItemStack card = LidarModules.card(lidar());
            int slot = LidarModules.Kind.MEMORY.ordinal();
            if (!ItemStack.isSameItemSameComponents(card, modules.getItem(slot)) && card.getItem() == modules.getItem(slot).getItem()) {
                loading = true;
                modules.setItem(slot, card.copy());
                loading = false;
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return lidar().getItem() instanceof LidarItem;
    }
}
