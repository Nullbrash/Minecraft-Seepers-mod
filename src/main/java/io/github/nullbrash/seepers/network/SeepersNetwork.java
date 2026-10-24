package io.github.nullbrash.seepers.network;

import io.github.nullbrash.seepers.client.network.ClientPayloadHandlers;
import io.github.nullbrash.seepers.network.payload.CaneTapPayload;
import io.github.nullbrash.seepers.network.payload.ClientCommandPayload;
import io.github.nullbrash.seepers.network.payload.FogProbePayload;
import io.github.nullbrash.seepers.network.payload.FogProbeReplyPayload;
import io.github.nullbrash.seepers.network.payload.FogSeedPayload;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import io.github.nullbrash.seepers.network.payload.TouchLavaPayload;
import io.github.nullbrash.seepers.touch.CaneTaps;
import io.github.nullbrash.seepers.touch.LavaTouch;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SeepersNetwork {
    /** Bump on any incompatible payload change: NeoForge refuses connections with a different version. */
    public static final String PROTOCOL_VERSION = "4";

    public static void register(RegisterPayloadHandlersEvent event) {
        // Handlers run on the main thread (registrar default).
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // Client-bound handlers must be lambdas, not method references: the client class is then
        // resolved only when a packet is handled, so a dedicated server never loads it.
        registrar.playToClient(PingPayload.TYPE, PingPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handlePing(payload, context));

        registrar.playToServer(PongPayload.TYPE, PongPayload.STREAM_CODEC, PingTracker::handlePong);

        registrar.playToClient(FogSeedPayload.TYPE, FogSeedPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleFogSeed(payload, context));
        registrar.playToClient(FogProbePayload.TYPE, FogProbePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleFogProbe(payload, context));
        registrar.playToClient(ClientCommandPayload.TYPE, ClientCommandPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.handleClientCommand(payload, context));
        registrar.playToServer(TouchLavaPayload.TYPE, TouchLavaPayload.STREAM_CODEC, LavaTouch::handle);
        registrar.playToServer(CaneTapPayload.TYPE, CaneTapPayload.STREAM_CODEC, CaneTaps::handle);
        registrar.playToServer(FogProbeReplyPayload.TYPE, FogProbeReplyPayload.STREAM_CODEC, FogProbeTracker::handleReply);
    }

    private SeepersNetwork() {}
}
