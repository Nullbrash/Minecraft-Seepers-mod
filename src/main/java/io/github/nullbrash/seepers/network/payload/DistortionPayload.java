package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.world.Distortion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client on login: zone sizes of this world (fog grows with distortion on the client too). */
public record DistortionPayload(int zone1, int zone2, int zone3) implements CustomPacketPayload {
    public static final Type<DistortionPayload> TYPE = new Type<>(Seepers.id("distortion"));
    public static final StreamCodec<ByteBuf, DistortionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DistortionPayload::zone1,
            ByteBufCodecs.VAR_INT, DistortionPayload::zone2,
            ByteBufCodecs.VAR_INT, DistortionPayload::zone3,
            DistortionPayload::new);

    public static DistortionPayload of(Distortion.Zones zones) {
        return new DistortionPayload(zones.zone1(), zones.zone2(), zones.zone3());
    }

    public Distortion.Zones zones() {
        return new Distortion.Zones(zone1, zone2, zone3);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
