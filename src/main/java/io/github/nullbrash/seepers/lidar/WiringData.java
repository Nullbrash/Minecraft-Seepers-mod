package io.github.nullbrash.seepers.lidar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A lidar's tuned wiring (component {@code seepers:wiring}): the modules it was wired for (Wiring.signature), the battery
 * drain it cuts, the crank power it adds. Another set of modules and the bonus is gone until wired again.
 */
public record WiringData(int signature, float drainCut, float chargeBoost, int length, int minimal) {
    public static final Codec<WiringData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("signature").forGetter(WiringData::signature),
            Codec.FLOAT.fieldOf("drain_cut").forGetter(WiringData::drainCut),
            Codec.FLOAT.fieldOf("charge_boost").forGetter(WiringData::chargeBoost),
            Codec.INT.optionalFieldOf("length", 0).forGetter(WiringData::length),
            Codec.INT.optionalFieldOf("minimal", 0).forGetter(WiringData::minimal)
    ).apply(instance, WiringData::new));

    public static final StreamCodec<ByteBuf, WiringData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, WiringData::signature,
            ByteBufCodecs.FLOAT, WiringData::drainCut,
            ByteBufCodecs.FLOAT, WiringData::chargeBoost,
            ByteBufCodecs.VAR_INT, WiringData::length,
            ByteBufCodecs.VAR_INT, WiringData::minimal,
            WiringData::new);
}
