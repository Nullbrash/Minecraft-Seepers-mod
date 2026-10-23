package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client (debug): run one of our client commands as if typed, so client-side debugging can be driven
 * from the server console without input into the game window. The client accepts only {@code seepers ...}.
 */
public record ClientCommandPayload(String command) implements CustomPacketPayload {
    public static final Type<ClientCommandPayload> TYPE = new Type<>(Seepers.id("client_command"));
    public static final StreamCodec<ByteBuf, ClientCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), ClientCommandPayload::command,
            ClientCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
