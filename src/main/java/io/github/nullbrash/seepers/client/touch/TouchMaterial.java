package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.heat.HeatField;
import io.github.nullbrash.seepers.registry.SeepersTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What a surface feels like (CLAUDE.md 5.1), shown as a muted tint of its points. Ids are the point material
 * byte; the first match in order warm, wet, loose, soft wins, anything else is solid.
 */
public enum TouchMaterial {
    SOLID(199, 230, 255),
    LOOSE(232, 212, 160),
    SOFT(172, 200, 168),
    WET(105, 145, 255),
    WARM(255, 160, 90);

    public final int red;
    public final int green;
    public final int blue;

    TouchMaterial(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /** Feet and body only tell water and warmth apart; the full palette is for the hands (CLAUDE.md 5.1). */
    public TouchMaterial forBody() {
        return this == WET || this == WARM ? this : SOLID;
    }

    public static TouchMaterial byId(int id) {
        TouchMaterial[] values = values();
        return id >= 0 && id < values.length ? values[id] : SOLID;
    }

    /** The material of a block's face, judged from the open side the player touches it from. */
    public static TouchMaterial of(Level level, BlockPos pos, BlockState state, Direction face) {
        BlockPos open = pos.relative(face);
        double heat = -HeatField.densityAt(level, open.getX() + 0.5, open.getY() + 0.5, open.getZ() + 0.5, 0);
        if (state.is(SeepersTags.Blocks.TOUCH_WARM) || heat >= ServerConfig.get(ServerConfig.TOUCH_WARM_HEAT)) return WARM;
        boolean waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
        if (state.is(SeepersTags.Blocks.TOUCH_WET) || waterlogged || level.getFluidState(open).is(FluidTags.WATER)) return WET;
        if (state.is(SeepersTags.Blocks.TOUCH_LOOSE)) return LOOSE;
        if (state.is(SeepersTags.Blocks.TOUCH_SOFT)) return SOFT;
        return SOLID;
    }
}
