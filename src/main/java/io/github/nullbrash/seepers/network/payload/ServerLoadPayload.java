package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client, once a second: how well the server keeps up, as TPS scaled to 20 (20 = no lag, whatever the
 * tick rate). The client lowers its render distance on server lag (CLAUDE.md 4.7).
 */
public record ServerLoadPayload(float tps) implements CustomPacketPayload {
    public static final Type<ServerLoadPayload> TYPE = new Type<>(Seepers.id("server_load"));
    public static final StreamCodec<ByteBuf, ServerLoadPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(ServerLoadPayload::new, ServerLoadPayload::tps);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
