package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client on login: seed of the fog field (a hash, not the world seed). */
public record FogSeedPayload(long seed) implements CustomPacketPayload {
    public static final Type<FogSeedPayload> TYPE = new Type<>(Seepers.id("fog_seed"));
    public static final StreamCodec<ByteBuf, FogSeedPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(FogSeedPayload::new, FogSeedPayload::seed);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
