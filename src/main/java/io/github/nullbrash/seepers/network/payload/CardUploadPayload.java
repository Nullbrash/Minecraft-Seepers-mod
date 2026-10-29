package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Player -> server: point records of one section of a card, and how many points the section now holds. Appended
 * records are new points; a rewrite (the whole section, compactly) comes in parts and replaces the file only with its
 * last part, so a rewrite cut short leaves the old file whole. Small: the server accepts about 32 KB a packet.
 */
public record CardUploadPayload(UUID card, long section, int live, int mode, byte[] records) implements CustomPacketPayload {
    public static final int MAX_BYTES = 30 * 1024;
    public static final int APPEND = 0;
    /** mode bits of a rewrite part: always REWRITE; FIRST starts it, LAST commits it. */
    public static final int REWRITE = 1;
    public static final int FIRST = 2;
    public static final int LAST = 4;
    public static final Type<CardUploadPayload> TYPE = new Type<>(Seepers.id("card_upload"));
    public static final StreamCodec<ByteBuf, CardUploadPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardUploadPayload::card,
            ByteBufCodecs.VAR_LONG, CardUploadPayload::section,
            ByteBufCodecs.VAR_INT, CardUploadPayload::live,
            ByteBufCodecs.VAR_INT, CardUploadPayload::mode,
            ByteBufCodecs.byteArray(MAX_BYTES), CardUploadPayload::records,
            CardUploadPayload::new);

    public boolean rewrite() {
        return (mode & REWRITE) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
