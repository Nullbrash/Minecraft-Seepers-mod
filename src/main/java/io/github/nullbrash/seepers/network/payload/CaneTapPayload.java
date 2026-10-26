package io.github.nullbrash.seepers.network.payload;

import io.github.nullbrash.seepers.Seepers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Client -> server: the cane tapped. flags: bit 0 off hand, bit 1 hit something, bits 2-4 the struck face + 1
 * (0 = none: a mob or air). x, y, z: where it landed. The server spends durability and plays the knock.
 */
public record CaneTapPayload(int flags, BlockPos pos, float x, float y, float z) implements CustomPacketPayload {
    public static final Type<CaneTapPayload> TYPE = new Type<>(Seepers.id("cane_tap"));
    public static final StreamCodec<ByteBuf, CaneTapPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CaneTapPayload::flags,
            BlockPos.STREAM_CODEC, CaneTapPayload::pos,
            ByteBufCodecs.FLOAT, CaneTapPayload::x,
            ByteBufCodecs.FLOAT, CaneTapPayload::y,
            ByteBufCodecs.FLOAT, CaneTapPayload::z,
            CaneTapPayload::new);

    public static CaneTapPayload of(boolean offhand, boolean hit, @Nullable Direction face, BlockPos pos, double x, double y, double z) {
        int flags = (offhand ? 1 : 0) | (hit ? 2 : 0) | (face == null ? 0 : face.ordinal() + 1) << 2;
        return new CaneTapPayload(flags, pos, (float) x, (float) y, (float) z);
    }

    public boolean offhand() {
        return (flags & 1) != 0;
    }

    public boolean hit() {
        return (flags & 2) != 0;
    }

    public @Nullable Direction face() {
        int face = (flags >> 2) & 7;
        return face == 0 || face > 6 ? null : Direction.values()[face - 1];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
