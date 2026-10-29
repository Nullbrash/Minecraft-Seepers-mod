package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Player -> server: a solved wiring of the lidar in hand, one path of cells per pair; the server checks it. */
public record WiringPayload(boolean offhand, List<int[]> paths) implements CustomPacketPayload {
    public static final Type<WiringPayload> TYPE = new Type<>(Seepers.id("wiring"));
    private static final StreamCodec<ByteBuf, int[]> PATH = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512))
            .map(list -> list.stream().mapToInt(Integer::intValue).toArray(), array -> java.util.Arrays.stream(array).boxed().toList());
    public static final StreamCodec<ByteBuf, WiringPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WiringPayload::offhand,
            PATH.apply(ByteBufCodecs.list(32)), WiringPayload::paths,
            WiringPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
