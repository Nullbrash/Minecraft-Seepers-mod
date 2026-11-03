package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.HazmatItem;
import io.github.nullbrash.seepers.item.StarterLidarItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Выдача отмечается в данных игрока, поэтому повторный вход и переход из Энда не создают новый набор. */
public final class StartingEquipment {
    private static final String JOINED = "seepers:starter_received";
    private static final String GIVE = "seepers:starter_on_respawn";

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getPersistentData().getBoolean(JOINED)) return;
        player.getPersistentData().putBoolean(JOINED, true);
        give(player);
    }

    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isFromEndFight()) return;
        var pos = player.getRespawnPosition();
        var level = player.server.getLevel(player.getRespawnDimension());
        boolean bed = pos != null && level != null && !event.getOriginalDimensionTransition().missingRespawnBlock()
                && level.getBlockState(pos).getBlock() instanceof BedBlock;
        player.getPersistentData().putBoolean(GIVE, !bed);
    }

    public static void onClone(PlayerEvent.Clone event) {
        var original = event.getOriginal().getPersistentData();
        var data = event.getEntity().getPersistentData();
        data.putBoolean(JOINED, original.getBoolean(JOINED));
        data.putBoolean(GIVE, event.isWasDeath() && original.getBoolean(GIVE));
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean give = player.getPersistentData().getBoolean(GIVE);
        player.getPersistentData().remove(GIVE);
        if (!event.isEndConquered() && give) give(player);
    }

    private static void give(ServerPlayer player) {
        ItemStack lidar = new ItemStack(SeepersItems.STARTER_LIDAR.get());
        int a = ServerConfig.get(ServerConfig.STARTER_CHARGE_MIN);
        int b = ServerConfig.get(ServerConfig.STARTER_CHARGE_MAX);
        int percent = Math.min(a, b) + player.getRandom().nextInt(Math.abs(b - a) + 1);
        lidar.set(SeepersComponents.BATTERY.get(), (int) ((long) BatteryItem.capacity() * percent / 100));
        LidarModules.initializeStarter(lidar);
        giveItem(player, lidar);
        int color = DyeColor.values()[player.getRandom().nextInt(DyeColor.values().length)].getTextureDiffuseColor();
        for (var item : SeepersItems.HAZMAT) {
            ItemStack armor = new ItemStack(item.get());
            armor.set(DataComponents.DYED_COLOR, new DyedItemColor(color, true));
            EquipmentSlot slot = ((HazmatItem) armor.getItem()).getEquipmentSlot();
            if (player.getItemBySlot(slot).isEmpty()) player.setItemSlot(slot, armor);
            else giveItem(player, armor);
        }
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || player.getAbilities().instabuild) return;
        int interval = ServerConfig.get(ServerConfig.HAZMAT_WEAR_TICKS);
        if (interval <= 0 || player.tickCount % interval != 0) return;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof HazmatItem) stack.hurtAndBreak(1, player, slot);
        }
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || player.getAbilities().instabuild) return;
        int wear = (int) Math.ceil(event.getNewDamage() * ServerConfig.get(ServerConfig.STARTER_DAMAGE_WEAR));
        for (ItemStack stack : player.getInventory().items) StarterLidarItem.wear(stack, wear);
        for (ItemStack stack : player.getInventory().offhand) StarterLidarItem.wear(stack, wear);
    }

    private StartingEquipment() {}
}
