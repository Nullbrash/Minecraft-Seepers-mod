package io.github.nullbrash.seepers.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.item.StarterLidarItem;
import io.github.nullbrash.seepers.lidar.LidarMenu;
import io.github.nullbrash.seepers.lidar.LidarModules;
import io.github.nullbrash.seepers.lidar.StartingEquipment;
import io.github.nullbrash.seepers.lidar.StarterDismantleRecipe;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import java.util.List;
import java.util.UUID;

/** Серверные проверки используют отдельного несохраняемого игрока и не трогают инвентарь владельца. */
public final class StarterDebugCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("startertest")
                .requires(source -> source.getServer().getWorldData().getLevelName().equals("phase7-test"))
                .executes(context -> run(context.getSource()));
    }

    private static int run(CommandSourceStack source) {
        int checks = 0;
        FakePlayer player = new FakePlayer(source.getLevel(), new GameProfile(UUID.randomUUID(), "SeepersTest"));
        try {
            StartingEquipment.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            ItemStack lidar = player.getInventory().items.stream().filter(StarterLidarItem::isStarter).findFirst().orElseThrow();
            check(LidarItem.canScan(lidar), "Первый вход: работающий лидар"); checks++;
            int charge = LidarItem.battery(lidar);
            int min = Math.min(ServerConfig.get(ServerConfig.STARTER_CHARGE_MIN), ServerConfig.get(ServerConfig.STARTER_CHARGE_MAX));
            int max = Math.max(ServerConfig.get(ServerConfig.STARTER_CHARGE_MIN), ServerConfig.get(ServerConfig.STARTER_CHARGE_MAX));
            check(charge >= (long) BatteryItem.capacity() * min / 100 && charge <= (long) BatteryItem.capacity() * max / 100,
                    "Начальный заряд внутри диапазона"); checks++;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                check(player.getItemBySlot(slot).getItem() instanceof io.github.nullbrash.seepers.item.HazmatItem, "Выдача хазмата: " + slot); checks++;
            }
            int count = starterCount(player);
            lidar.set(SeepersComponents.BATTERY.get(), 123);
            StartingEquipment.onLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            check(starterCount(player) == count && LidarItem.battery(lidar) == 123, "Повторный вход не выдаёт набор и не меняет заряд"); checks++;
            check(LidarModules.stats(lidar).pointLimit() == ServerConfig.get(ServerConfig.STARTER_POINTS), "Ёмкость встроенной памяти"); checks++;
            check(LidarModules.level(lidar, LidarModules.Kind.VIEW) == 1 && LidarModules.stats(lidar).dynamo(), "Встроенный обзор и динамо"); checks++;
            player.setItemInHand(InteractionHand.MAIN_HAND, lidar);
            LidarMenu menu = new LidarMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
            int analysis = LidarModules.Kind.ANALYSIS.ordinal();
            check(menu.getSlot(analysis).mayPlace(new ItemStack(SeepersItems.MODULES.get(LidarModules.Kind.ANALYSIS).getFirst().get())), "Анализ I допускается"); checks++;
            check(!menu.getSlot(analysis).mayPlace(new ItemStack(SeepersItems.MODULES.get(LidarModules.Kind.ANALYSIS).get(1).get())), "Анализ II блокируется"); checks++;
            for (var kind : new LidarModules.Kind[] {LidarModules.Kind.MEMORY, LidarModules.Kind.VIEW, LidarModules.Kind.DENSITY,
                    LidarModules.Kind.DYNAMO, LidarModules.Kind.BEAMS, LidarModules.Kind.ECHO}) {
                check(!menu.getSlot(kind.ordinal()).mayPickup(player), "Закрытый узел: " + kind); checks++;
            }
            menu.batteryOpen = true;
            check(!menu.getSlot(LidarMenu.BATTERY_SLOT).mayPickup(player)
                    && !menu.getSlot(LidarMenu.BATTERY_SLOT).mayPlace(BatteryItem.withCharge(1)), "Несъёмная батарея"); checks++;
            StarterLidarItem.wear(lidar, lidar.getMaxDamage());
            check(!lidar.isEmpty() && !LidarItem.canScan(lidar), "Поломка сохраняет предмет и блокирует скан"); checks++;
            check(lidar.getItem().isValidRepairItem(lidar, new ItemStack(Items.COPPER_INGOT)), "Медь ремонтирует LIDA-RA"); checks++;
            lidar.setDamageValue(0);
            check(LidarItem.canScan(lidar), "Восстановление прочности возвращает скан"); checks++;
            var recipe = new StarterDismantleRecipe(CraftingBookCategory.MISC);
            var input = CraftingInput.of(1, 1, List.of(lidar));
            check(recipe.matches(input, source.getLevel()) && recipe.assemble(input, source.registryAccess()).is(SeepersItems.CIRCUIT.get()), "Разборка на плату"); checks++;
            var modules = LidarModules.contents(lidar);
            modules.set(analysis, new ItemStack(SeepersItems.MODULES.get(LidarModules.Kind.ANALYSIS).getFirst().get()));
            LidarModules.store(lidar, modules);
            check(!recipe.matches(input, source.getLevel()), "Разборка не уничтожает установленный модуль"); checks++;
            ItemStack rb = new ItemStack(SeepersItems.LIDAR.get());
            var rbModules = LidarModules.contents(rb);
            rbModules.set(LidarModules.Kind.ECHO.ordinal(), new ItemStack(SeepersItems.MODULES.get(LidarModules.Kind.ECHO).getFirst().get()));
            LidarModules.store(rb, rbModules);
            player.setItemInHand(InteractionHand.MAIN_HAND, rb);
            LidarMenu rbMenu = new LidarMenu(2, player.getInventory(), InteractionHand.MAIN_HAND);
            check(rbMenu.clickMenuButton(player, LidarMenu.ECHO_BUTTON) && !rb.getOrDefault(SeepersComponents.ECHO_ENABLED.get(), true), "Эхо выключается"); checks++;
            check(rbMenu.clickMenuButton(player, LidarMenu.ECHO_BUTTON) && rb.getOrDefault(SeepersComponents.ECHO_ENABLED.get(), false), "Эхо включается"); checks++;
            // Сценарии питания из прежних smoke-скриптов выполняются без ввода в игровое окно.
            var context = (net.neoforged.neoforge.network.handling.IPayloadContext) java.lang.reflect.Proxy.newProxyInstance(
                    StarterDebugCommand.class.getClassLoader(), new Class<?>[] {net.neoforged.neoforge.network.handling.IPayloadContext.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("player")) return player;
                        throw new UnsupportedOperationException(method.getName());
                    });
            var tick = new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player);
            var start = new io.github.nullbrash.seepers.network.payload.LidarScanPayload(true, false, false);
            rb.set(SeepersComponents.BATTERY.get(), 100);
            io.github.nullbrash.seepers.lidar.LidarPower.handle(start, context);
            for (int i = 0; i < 20; i++) io.github.nullbrash.seepers.lidar.LidarPower.onPlayerTick(tick);
            check(LidarItem.battery(rb) == 80, "Базовый расход LIDA-RB: один тик заряда за тик скана"); checks++;
            io.github.nullbrash.seepers.lidar.LidarPower.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
            rb.set(SeepersComponents.BATTERY.get(), 10);
            io.github.nullbrash.seepers.lidar.LidarPower.handle(start, context);
            for (int i = 0; i < 40; i++) io.github.nullbrash.seepers.lidar.LidarPower.onPlayerTick(tick);
            check(LidarItem.battery(rb) == 0 && !LidarItem.canScan(rb), "Разряд останавливается на нуле"); checks++;
            io.github.nullbrash.seepers.lidar.LidarPower.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
            rb.set(SeepersComponents.BATTERY.get(), 100);
            io.github.nullbrash.seepers.lidar.LidarPower.handle(start, context);
            for (int i = 0; i < 5; i++) io.github.nullbrash.seepers.lidar.LidarPower.onPlayerTick(tick);
            ItemStack other = rb.copy();
            other.set(SeepersComponents.BATTERY.get(), 500);
            player.setItemInHand(InteractionHand.MAIN_HAND, other);
            io.github.nullbrash.seepers.lidar.LidarPower.onPlayerTick(tick);
            check(LidarItem.battery(rb) == 95 && LidarItem.battery(other) == 500, "Смена прибора сохраняет расход на исходном экземпляре"); checks++;
            player.setItemInHand(InteractionHand.MAIN_HAND, lidar);
            lidar.setDamageValue(0);
            lidar.set(SeepersComponents.BATTERY.get(), BatteryItem.capacity());
            io.github.nullbrash.seepers.lidar.LidarPower.handle(start, context);
            for (int i = 0; i < ServerConfig.get(ServerConfig.STARTER_SCAN_WEAR_TICKS) + 20; i++)
                io.github.nullbrash.seepers.lidar.LidarPower.onPlayerTick(tick);
            check(lidar.getDamageValue() > 0, "Сканирование изнашивает стартовый прибор"); checks++;
            io.github.nullbrash.seepers.lidar.LidarPower.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
            var encoded = ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), lidar).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), encoded).getOrThrow();
            check(ItemStack.isSameItemSameComponents(lidar, decoded), "Сохранение восстанавливает заряд, модули, память и износ"); checks++;
            var transition = new DimensionTransition(source.getLevel(), Vec3.ZERO, Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING);
            StartingEquipment.onRespawnPosition(new PlayerRespawnPositionEvent(player, transition, false));
            var copy = new FakePlayer(source.getLevel(), new GameProfile(UUID.randomUUID(), "SeepersClone"));
            StartingEquipment.onClone(new PlayerEvent.Clone(copy, player, true));
            StartingEquipment.onRespawn(new PlayerEvent.PlayerRespawnEvent(copy, false));
            check(starterCount(copy) == 1, "Возрождение без кровати выдаёт набор"); checks++;
            StartingEquipment.onRespawn(new PlayerEvent.PlayerRespawnEvent(copy, false));
            check(starterCount(copy) == 1, "Повторное событие не дублирует набор"); checks++;
            StartingEquipment.onLogin(new PlayerEvent.PlayerLoggedInEvent(copy));
            check(starterCount(copy) == 1, "Маркер первого входа сохраняется после смерти"); checks++;
            var bed = new net.minecraft.core.BlockPos(0, source.getLevel().getMaxBuildHeight() - 4, 0);
            var previous = source.getLevel().getBlockState(bed);
            try {
                source.getLevel().setBlock(bed, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), 2);
                copy.setRespawnPosition(source.getLevel().dimension(), bed, 0, false, false);
                StartingEquipment.onRespawnPosition(new PlayerRespawnPositionEvent(copy, transition, false));
                FakePlayer atBed = new FakePlayer(source.getLevel(), new GameProfile(UUID.randomUUID(), "SeepersBed"));
                StartingEquipment.onClone(new PlayerEvent.Clone(atBed, copy, true));
                StartingEquipment.onRespawn(new PlayerEvent.PlayerRespawnEvent(atBed, false));
                check(starterCount(atBed) == 0, "Возрождение у кровати не выдаёт набор"); checks++;
                StartingEquipment.onLogin(new PlayerEvent.PlayerLoggedInEvent(atBed));
                check(starterCount(atBed) == 0, "После возрождения у кровати перезаход не выдаёт набор"); checks++;
            } finally {
                source.getLevel().setBlock(bed, previous, 2);
            }
            StartingEquipment.onRespawnPosition(new PlayerRespawnPositionEvent(copy, transition, true));
            StartingEquipment.onRespawn(new PlayerEvent.PlayerRespawnEvent(copy, true));
            check(starterCount(copy) == 1, "Возвращение из Энда не выдаёт набор"); checks++;
            int passed = checks;
            source.sendSuccess(() -> Component.literal("STARTER_TEST_OK: " + passed + " проверок"), false);
            return checks;
        } catch (Exception error) {
            source.sendFailure(Component.literal("STARTER_TEST_FAILED после " + checks + ": " + error.getMessage()));
            io.github.nullbrash.seepers.Seepers.LOGGER.error("Проверка стартового снаряжения не пройдена", error);
            return 0;
        } finally {
            io.github.nullbrash.seepers.lidar.LidarPower.onLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    private static int starterCount(FakePlayer player) {
        return (int) player.getInventory().items.stream().filter(StarterLidarItem::isStarter).count();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private StarterDebugCommand() {}
}
