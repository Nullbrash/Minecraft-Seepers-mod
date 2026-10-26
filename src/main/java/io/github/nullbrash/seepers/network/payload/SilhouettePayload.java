package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a blow landed between the player and this entity; leave the part of its silhouette the blow
 * touched. playerStruck: the player hit it (outline around where the player looked); otherwise it hit the player
 * (its torso).
 */
public record SilhouettePayload(int entityId, boolean playerStruck) implements CustomPacketPayload {
    public static final Type<SilhouettePayload> TYPE = new Type<>(Seepers.id("silhouette"));
    public static final StreamCodec<ByteBuf, SilhouettePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SilhouettePayload::entityId,
            ByteBufCodecs.BOOL, SilhouettePayload::playerStruck,
            SilhouettePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
