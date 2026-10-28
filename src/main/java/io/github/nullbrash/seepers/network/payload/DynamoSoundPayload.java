package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> clients near: a player started or stopped cranking a lidar dynamo; clients loop its sound on that player. */
public record DynamoSoundPayload(int entityId, boolean cranking) implements CustomPacketPayload {
    public static final Type<DynamoSoundPayload> TYPE = new Type<>(Seepers.id("dynamo_sound"));
    public static final StreamCodec<ByteBuf, DynamoSoundPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DynamoSoundPayload::entityId,
            ByteBufCodecs.BOOL, DynamoSoundPayload::cranking,
            DynamoSoundPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
