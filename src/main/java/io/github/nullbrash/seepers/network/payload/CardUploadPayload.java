package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Player -> server: new point records of one section of the active card, and how many points the section now holds
 * (replace: the records are the whole section, rewritten compactly). Small: the server accepts about 32 KB a packet.
 */
public record CardUploadPayload(UUID card, long section, int live, boolean replace, byte[] records) implements CustomPacketPayload {
    public static final int MAX_BYTES = 30 * 1024;
    public static final Type<CardUploadPayload> TYPE = new Type<>(Seepers.id("card_upload"));
    public static final StreamCodec<ByteBuf, CardUploadPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardUploadPayload::card,
            ByteBufCodecs.VAR_LONG, CardUploadPayload::section,
            ByteBufCodecs.VAR_INT, CardUploadPayload::live,
            ByteBufCodecs.BOOL, CardUploadPayload::replace,
            ByteBufCodecs.byteArray(MAX_BYTES), CardUploadPayload::records,
            CardUploadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
