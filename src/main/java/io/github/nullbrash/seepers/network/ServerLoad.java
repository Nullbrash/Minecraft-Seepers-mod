package io.github.nullbrash.seepers.network;

import io.github.nullbrash.seepers.network.payload.ServerLoadPayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells clients once a second how well the server keeps up (for the automatic render distance). */
public final class ServerLoad {
    private static final int PERIOD_TICKS = 20;

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD_TICKS != 0 || server.getPlayerCount() == 0) return;
        PacketDistributor.sendToAllPlayers(new ServerLoadPayload(tps(server)));
    }

    /** Vanilla averages tick work over the last 100 ticks (5 s); a tick longer than its budget slows the game. */
    static float tps(MinecraftServer server) {
        long average = Math.max(1, server.getAverageTickTimeNanos());
        long budget = server.tickRateManager().nanosecondsPerTick();
        return 20.0F * (float) Math.min(1.0, (double) budget / average);
    }

    private ServerLoad() {}
}
