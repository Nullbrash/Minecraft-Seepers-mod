package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> player: a piece of a card's section, its point records in the order written (lidar.CardRecords). */
public record CardSectionPayload(UUID card, int epoch, long section, byte[] records, boolean last) implements CustomPacketPayload {
    public static final int MAX_BYTES = 256 * 1024;
    public static final Type<CardSectionPayload> TYPE = new Type<>(Seepers.id("card_section"));
    public static final StreamCodec<ByteBuf, CardSectionPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardSectionPayload::card,
            ByteBufCodecs.VAR_INT, CardSectionPayload::epoch,
            ByteBufCodecs.VAR_LONG, CardSectionPayload::section,
            ByteBufCodecs.byteArray(MAX_BYTES), CardSectionPayload::records,
            ByteBufCodecs.BOOL, CardSectionPayload::last,
            CardSectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
