package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.BatteryItem;
import io.github.nullbrash.seepers.item.LidarItem;
import io.github.nullbrash.seepers.network.payload.DynamoSoundPayload;
import io.github.nullbrash.seepers.network.payload.LidarActionPayload;
import io.github.nullbrash.seepers.network.payload.LidarScanPayload;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The lidar's charge, kept by the server (CLAUDE.md 5.3). The client says when its beam starts and stops; while it
 * runs, the battery in that hand loses a tick of charge per tick times the modules' drain (wider and denser scans
 * cost more). The hand dynamo works the other way: while cranked it charges and is loud for everyone near. Charge is
 * written back once a second, so the item is not resent every tick.
 */
public final class LidarPower {
    private static final int WRITE_TICKS = 20;
    /** How often a turning crank sends a vibration: often enough to keep a sculk sensor going. */
    private static final int CRANK_VIBRATION_TICKS = 10;

    private static final Map<UUID, Session> SCANNING = new HashMap<>();
    private static final Map<UUID, Session> CRANKING = new HashMap<>();

    private static final class Session {
        final InteractionHand hand;
        boolean round;
        double unspent;
        int ticks;

        Session(InteractionHand hand) {
            this.hand = hand;
        }
    }

    public static void handle(LidarScanPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (!payload.scanning()) {
            Session session = SCANNING.remove(id);
            if (session != null) write(player, session, -1);
            return;
        }
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!LidarItem.canScan(player.getItemInHand(hand)) || CRANKING.containsKey(id)) return;
        SCANNING.computeIfAbsent(id, key -> new Session(hand)).round = payload.round();
    }

    public static void handleAction(LidarActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack lidar = player.getItemInHand(hand);
        switch (payload.action()) {
            case LidarActionPayload.OPEN_SCREEN -> LidarMenu.open(player, hand);
            case LidarActionPayload.DYNAMO_START -> {
                if (lidar.getItem() instanceof LidarItem && LidarModules.level(lidar, LidarModules.Kind.DYNAMO) > 0
                        && LidarItem.battery(lidar) != null) {
                    Session scan = SCANNING.remove(player.getUUID());
                    if (scan != null) write(player, scan, -1);
                    if (CRANKING.putIfAbsent(player.getUUID(), new Session(hand)) == null) crankSound(player, true);
                }
            }
            case LidarActionPayload.DYNAMO_STOP -> {
                Session session = CRANKING.remove(player.getUUID());
                if (session != null) {
                    write(player, session, 1);
                    crankSound(player, false);
                }
            }
            default -> {}
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        Session scan = SCANNING.get(id);
        if (scan != null) {
            ItemStack lidar = player.getItemInHand(scan.hand);
            if (!(lidar.getItem() instanceof LidarItem) || !LidarItem.canScan(lidar)) {
                SCANNING.remove(id);
            } else {
                scan.unspent += LidarModules.cost(lidar, scan.round);
                if (++scan.ticks % WRITE_TICKS == 0 && !write(player, scan, -1)) SCANNING.remove(id);
            }
        }
        Session crank = CRANKING.get(id);
        if (crank != null) {
            ItemStack lidar = player.getItemInHand(crank.hand);
            if (!(lidar.getItem() instanceof LidarItem) || LidarItem.battery(lidar) == null) {
                CRANKING.remove(id);
                crankSound(player, false);
                return;
            }
            crank.unspent += ServerConfig.get(ServerConfig.LIDAR_DYNAMO_RATE);
            if (++crank.ticks % WRITE_TICKS == 0) write(player, crank, 1);
            // The crank is loud (CLAUDE.md 5.3): sculk sensors and wardens hear it all the time it turns, sneaking or not.
            if (crank.ticks % CRANK_VIBRATION_TICKS == 1) player.level().gameEvent(player, GameEvent.ENTITY_ACTION, player.position());
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SCANNING.remove(event.getEntity().getUUID());
        if (CRANKING.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer player) crankSound(player, false);
    }

    /** Someone coming near a cranking player hears the crank too. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer cranker && CRANKING.containsKey(cranker.getUUID())
                && event.getEntity() instanceof ServerPlayer listener) {
            PacketDistributor.sendToPlayer(listener, new DynamoSoundPayload(cranker.getId(), true));
        }
    }

    /** Clients near (and the cranker) loop the crank on this player; a clip per half second would gap. */
    private static void crankSound(ServerPlayer player, boolean cranking) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DynamoSoundPayload(player.getId(), cranking));
    }

    /**
     * Moves the session's whole ticks of charge in or out of the battery in its hand (sign: +1 charges, -1 spends);
     * false once a spending battery is flat.
     */
    private static boolean write(ServerPlayer player, Session session, int sign) {
        ItemStack lidar = player.getItemInHand(session.hand);
        Integer charge = LidarItem.battery(lidar);
        int whole = (int) session.unspent;
        session.unspent -= whole;
        if (!(lidar.getItem() instanceof LidarItem) || charge == null) return false;
        int next = Math.max(0, Math.min(BatteryItem.capacity(), charge + sign * whole));
        lidar.set(SeepersComponents.BATTERY.get(), next);
        return next > 0;
    }

    private LidarPower() {}
}
