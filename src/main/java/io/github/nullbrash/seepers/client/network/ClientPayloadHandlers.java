package io.github.nullbrash.seepers.client.network;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.fog.ClientFog;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.network.payload.ClientCommandPayload;
import io.github.nullbrash.seepers.network.payload.FogProbePayload;
import io.github.nullbrash.seepers.network.payload.FogProbeReplyPayload;
import io.github.nullbrash.seepers.network.payload.FogSeedPayload;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers for client-bound payloads. Client only — referenced from common code through lambdas. */
public final class ClientPayloadHandlers {
    public static void handlePing(PingPayload payload, IPayloadContext context) {
        if (ClientConfig.LOG_NETWORK.get()) {
            Seepers.LOGGER.info("Received ping {}", payload.id());
        }
        context.reply(new PongPayload(payload.id()));
    }

    public static void handleClientCommand(ClientCommandPayload payload, IPayloadContext context) {
        // Only our own client commands: the server must not drive anything else on the client.
        if (payload.command().startsWith("seepers ")) {
            ClientCommandHandler.runCommand(payload.command());
        }
    }

    public static void handleFogSeed(FogSeedPayload payload, IPayloadContext context) {
        if (ClientConfig.LOG_NETWORK.get()) {
            Seepers.LOGGER.info("Received fog seed");
        }
        ClientFog.setSeed(payload.seed());
    }

    public static void handleFogProbe(FogProbePayload payload, IPayloadContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockPos pos = payload.pos();
        float direct = Float.NaN;
        float grid = Float.NaN;
        if (level != null && ClientFog.hasSeed()) {
            direct = (float) ClientFog.densityDirect(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            grid = (float) ClientFog.gridOnly(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        context.reply(new FogProbeReplyPayload(payload.id(), direct, grid, ClientFog.hasSeed()));
    }

    private ClientPayloadHandlers() {}
}
