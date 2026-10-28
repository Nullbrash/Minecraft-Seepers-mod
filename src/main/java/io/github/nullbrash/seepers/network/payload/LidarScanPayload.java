package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: the lidar's beam started, stopped or switched, in which hand, and whether it is the all-around
 * scan (view III, standing). The server spends the charge meanwhile.
 */
public record LidarScanPayload(boolean scanning, boolean offhand, boolean round) implements CustomPacketPayload {
    public static final Type<LidarScanPayload> TYPE = new Type<>(Seepers.id("lidar_scan"));
    public static final StreamCodec<ByteBuf, LidarScanPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, LidarScanPayload::scanning,
            ByteBufCodecs.BOOL, LidarScanPayload::offhand,
            ByteBufCodecs.BOOL, LidarScanPayload::round,
            LidarScanPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
