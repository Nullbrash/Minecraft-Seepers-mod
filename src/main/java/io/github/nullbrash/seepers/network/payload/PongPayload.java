package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server answer to {@link PingPayload}. */
public record PongPayload(int id) implements CustomPacketPayload {
    public static final Type<PongPayload> TYPE = new Type<>(Seepers.id("pong"));
    public static final StreamCodec<ByteBuf, PongPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PongPayload::new, PongPayload::id);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
