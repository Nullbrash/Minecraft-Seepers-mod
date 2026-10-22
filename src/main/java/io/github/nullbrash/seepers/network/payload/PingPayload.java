package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client diagnostic ping; the client answers with {@link PongPayload} carrying the same id. */
public record PingPayload(int id) implements CustomPacketPayload {
    public static final Type<PingPayload> TYPE = new Type<>(Seepers.id("ping"));
    public static final StreamCodec<ByteBuf, PingPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PingPayload::new, PingPayload::id);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
