package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> player: sections a full card let go (the least recently written); their points leave the cloud. */
public record CardDropPayload(UUID card, long[] sections) implements CustomPacketPayload {
    public static final Type<CardDropPayload> TYPE = new Type<>(Seepers.id("card_drop"));
    public static final StreamCodec<ByteBuf, CardDropPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardDropPayload::card,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(4096)).map(list -> list.stream().mapToLong(Long::longValue).toArray(),
                    array -> java.util.Arrays.stream(array).boxed().toList()), CardDropPayload::sections,
            CardDropPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
