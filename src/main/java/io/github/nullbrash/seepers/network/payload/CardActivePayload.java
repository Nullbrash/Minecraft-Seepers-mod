package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> player: the memory card whose points the player's cloud shows (CLAUDE.md 5.3); none = the lidar forgets.
 * A new card or epoch clears the cloud's card points (another card, or this one erased); its sections follow.
 */
public record CardActivePayload(Optional<UUID> card, int capacity, boolean writable, int epoch) implements CustomPacketPayload {
    public static final Type<CardActivePayload> TYPE = new Type<>(Seepers.id("card_active"));
    public static final StreamCodec<ByteBuf, CardActivePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional), CardActivePayload::card,
            ByteBufCodecs.VAR_INT, CardActivePayload::capacity,
            ByteBufCodecs.BOOL, CardActivePayload::writable,
            ByteBufCodecs.VAR_INT, CardActivePayload::epoch,
            CardActivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
