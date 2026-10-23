package io.github.nullbrash.seepers.network;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.config.CommonConfig;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-side round trip check for the network channel ({@code /seepers debug ping}). Main thread only. */
public final class PingTracker {
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);

    private record Pending(UUID player, CommandSourceStack requester, long sentAt) {}

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextId;

    public static void send(CommandSourceStack requester, ServerPlayer target) {
        long now = System.nanoTime();
        PENDING.values().removeIf(p -> now - p.sentAt() > TIMEOUT_NANOS);
        int id = nextId++;
        PENDING.put(id, new Pending(target.getUUID(), requester, now));
        PacketDistributor.sendToPlayer(target, new PingPayload(id));
    }

    static void handlePong(PongPayload payload, IPayloadContext context) {
        Pending pending = PENDING.remove(payload.id());
        // Ignore unknown/expired ids and answers coming from a different player.
        if (pending == null || !(context.player() instanceof ServerPlayer player) || !player.getUUID().equals(pending.player())) {
            return;
        }
        String ms = String.format(Locale.ROOT, "%.1f", (System.nanoTime() - pending.sentAt()) / 1_000_000.0);
        String name = player.getGameProfile().getName();
        if (CommonConfig.VERBOSE_LOGGING.get()) {
            Seepers.LOGGER.info("Ping {} from {}: {} ms", payload.id(), name, ms);
        }
        pending.requester().sendSuccess(() -> Component.translatable("commands.seepers.debug.ping.result", name, ms), false);
    }

    private PingTracker() {}
}
