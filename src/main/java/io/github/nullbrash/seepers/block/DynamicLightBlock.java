package io.github.nullbrash.seepers.block;

import com.mojang.serialization.MapCodec;
import io.github.nullbrash.seepers.light.ClientLightSide;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The server half of dynamic light (CLAUDE.md 4.2): an invisible light block like vanilla's, but our own, so clients
 * can tell it from a map maker's light. For the world it shines (crops, other mechanics); clients that draw the
 * smooth light themselves give it no light, or the stepped block light would show under the smooth one.
 */
public final class DynamicLightBlock extends LightBlock {
    public static final MapCodec<LightBlock> CODEC = simpleCodec(DynamicLightBlock::new);

    public DynamicLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LightBlock> codec() {
        return CODEC;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return ClientLightSide.hidesServerLight(level) ? 0 : state.getValue(LEVEL);
    }
}
