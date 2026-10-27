package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the entity carries a dynamic light of this level (0 = none); the client draws it smoothly. */
public record DynamicLightPayload(int entityId, int level) implements CustomPacketPayload {
    public static final Type<DynamicLightPayload> TYPE = new Type<>(Seepers.id("dynamic_light"));
    public static final StreamCodec<ByteBuf, DynamicLightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DynamicLightPayload::entityId,
            ByteBufCodecs.VAR_INT, DynamicLightPayload::level,
            DynamicLightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
