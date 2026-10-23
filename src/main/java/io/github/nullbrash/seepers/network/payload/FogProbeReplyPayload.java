package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server (debug): fog density computed directly and read from the client grid. */
public record FogProbeReplyPayload(int id, float direct, float grid, boolean hasSeed) implements CustomPacketPayload {
    public static final Type<FogProbeReplyPayload> TYPE = new Type<>(Seepers.id("fog_probe_reply"));
    public static final StreamCodec<ByteBuf, FogProbeReplyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FogProbeReplyPayload::id,
            ByteBufCodecs.FLOAT, FogProbeReplyPayload::direct,
            ByteBufCodecs.FLOAT, FogProbeReplyPayload::grid,
            ByteBufCodecs.BOOL, FogProbeReplyPayload::hasSeed,
            FogProbeReplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
