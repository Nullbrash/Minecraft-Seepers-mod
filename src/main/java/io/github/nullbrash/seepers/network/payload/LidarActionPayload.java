package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server, about the lidar in a hand: open its screen, or start / stop cranking its dynamo. The server checks
 * the item itself.
 */
public record LidarActionPayload(int action, boolean offhand) implements CustomPacketPayload {
    public static final int OPEN_SCREEN = 0;
    public static final int DYNAMO_START = 1;
    public static final int DYNAMO_STOP = 2;

    public static final Type<LidarActionPayload> TYPE = new Type<>(Seepers.id("lidar_action"));
    public static final StreamCodec<ByteBuf, LidarActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LidarActionPayload::action,
            ByteBufCodecs.BOOL, LidarActionPayload::offhand,
            LidarActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
