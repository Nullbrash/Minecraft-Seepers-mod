package io.github.nullbrash.seepers.network;

import io.github.nullbrash.seepers.fog.FogField;
import io.github.nullbrash.seepers.network.payload.FogProbePayload;
import io.github.nullbrash.seepers.network.payload.FogProbeReplyPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Debug: asks a client for its fog density at a point and prints it next to the server's value. Main thread only. */
public final class FogProbeTracker {
    private record Pending(UUID player, CommandSourceStack requester, BlockPos pos, double serverDensity) {}

    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static int nextId;

    public static void send(CommandSourceStack requester, ServerPlayer target, BlockPos pos) {
        if (PENDING.size() > 64) PENDING.clear(); // answers that never came
        int id = nextId++;
        double server = FogField.density(target.level(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        PENDING.put(id, new Pending(target.getUUID(), requester, pos, server));
        PacketDistributor.sendToPlayer(target, new FogProbePayload(id, pos));
    }

    static void handleReply(FogProbeReplyPayload payload, IPayloadContext context) {
        Pending pending = PENDING.remove(payload.id());
        if (pending == null || !(context.player() instanceof ServerPlayer player) || !player.getUUID().equals(pending.player())) {
            return;
        }
        String server = String.format(Locale.ROOT, "%.3f", pending.serverDensity());
        String direct = payload.hasSeed() ? String.format(Locale.ROOT, "%.3f", payload.direct()) : "-";
        String grid = Float.isNaN(payload.grid()) ? "-" : String.format(Locale.ROOT, "%.3f", payload.grid());
        pending.requester().sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.client",
                pending.pos().toShortString(), server, direct, grid), false);
    }

    private FogProbeTracker() {}
}
