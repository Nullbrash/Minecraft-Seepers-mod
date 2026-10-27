package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a tremor starts around this player. The client plays the rumble itself, at the volume of its own
 * config (a sound the server plays cannot be scaled per client).
 */
public record TremorRumblePayload() implements CustomPacketPayload {
    public static final Type<TremorRumblePayload> TYPE = new Type<>(Seepers.id("tremor_rumble"));
    public static final StreamCodec<ByteBuf, TremorRumblePayload> STREAM_CODEC = StreamCodec.unit(new TremorRumblePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
