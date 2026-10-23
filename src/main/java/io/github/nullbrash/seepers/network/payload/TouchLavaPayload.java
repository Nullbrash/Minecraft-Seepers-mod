package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player's hand touched lava at pos; the server checks it and burns the hand. */
public record TouchLavaPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<TouchLavaPayload> TYPE = new Type<>(Seepers.id("touch_lava"));
    public static final StreamCodec<ByteBuf, TouchLavaPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TouchLavaPayload::pos,
            TouchLavaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
