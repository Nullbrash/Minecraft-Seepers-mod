package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the cane in the given hand tapped (hit = touched the block at pos, at x, y, z; otherwise it
 * swung through air). The server spends durability and plays the knock for everyone near.
 */
public record CaneTapPayload(boolean offhand, boolean hit, BlockPos pos, float x, float y, float z) implements CustomPacketPayload {
    public static final Type<CaneTapPayload> TYPE = new Type<>(Seepers.id("cane_tap"));
    public static final StreamCodec<ByteBuf, CaneTapPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CaneTapPayload::offhand,
            ByteBufCodecs.BOOL, CaneTapPayload::hit,
            BlockPos.STREAM_CODEC, CaneTapPayload::pos,
            ByteBufCodecs.FLOAT, CaneTapPayload::x,
            ByteBufCodecs.FLOAT, CaneTapPayload::y,
            ByteBufCodecs.FLOAT, CaneTapPayload::z,
            CaneTapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
