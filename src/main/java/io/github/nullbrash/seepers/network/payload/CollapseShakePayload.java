package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: shake the camera (a collapse starting nearby, later tremors). Strength in degrees. */
public record CollapseShakePayload(float strength, int ticks) implements CustomPacketPayload {
    public static final Type<CollapseShakePayload> TYPE = new Type<>(Seepers.id("collapse_shake"));
    public static final StreamCodec<ByteBuf, CollapseShakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CollapseShakePayload::strength,
            ByteBufCodecs.VAR_INT, CollapseShakePayload::ticks,
            CollapseShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
