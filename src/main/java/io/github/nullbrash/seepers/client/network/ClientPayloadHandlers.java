package io.github.nullbrash.seepers.client.network;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers for client-bound payloads. Client only — referenced from common code through lambdas. */
public final class ClientPayloadHandlers {
    public static void handlePing(PingPayload payload, IPayloadContext context) {
        if (ClientConfig.LOG_NETWORK.get()) {
            Seepers.LOGGER.info("Received ping {}", payload.id());
        }
        context.reply(new PongPayload(payload.id()));
    }

    private ClientPayloadHandlers() {}
}
