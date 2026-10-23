package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client (debug): compute fog density at a position and answer with {@link FogProbeReplyPayload}. */
public record FogProbePayload(int id, BlockPos pos) implements CustomPacketPayload {
    public static final Type<FogProbePayload> TYPE = new Type<>(Seepers.id("fog_probe"));
    public static final StreamCodec<ByteBuf, FogProbePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FogProbePayload::id,
            BlockPos.STREAM_CODEC, FogProbePayload::pos,
            FogProbePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
