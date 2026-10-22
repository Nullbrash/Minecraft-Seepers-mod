package io.github.nullbrash.seepers.network;

import io.github.nullbrash.seepers.client.network.ClientPayloadHandlers;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SeepersNetwork {
    /** Bump on any incompatible payload change: NeoForge refuses connections with a different version. */
    public static final String PROTOCOL_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        // Handlers run on the main thread (registrar default).
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Client-bound handlers must be lambdas, not method references: the client class is then
        // resolved only when a packet is handled, so a dedicated server never loads it.
        registrar.playToClient(PingPayload.TYPE, PingPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handlePing(payload, context));

        registrar.playToServer(PongPayload.TYPE, PongPayload.STREAM_CODEC, PingTracker::handlePong);
    }

    private SeepersNetwork() {}
}
