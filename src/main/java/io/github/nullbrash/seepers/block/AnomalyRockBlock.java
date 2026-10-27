package io.github.nullbrash.seepers.block;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.world.AnomalyRegrowth;
import io.github.nullbrash.seepers.world.Distortion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Anomalous rock (CLAUDE.md 4.8): no tool breaks it, only explosions; harder to blast the deeper into distortion.
 * Pistons do not move it (push reaction) and endermen do not take it (not holdable). Shards: loot table by kind.
 */
public final class AnomalyRockBlock extends Block {
    /**
     * Where the rock comes from: geodes give shards more often and regrow faster than walls (and cliff faces);
     * hewn rock (chisel) never feeds regrowth.
     */
    public enum Kind implements StringRepresentable {
        WALL("wall"),
        GEODE("geode"),
        HEWN("hewn");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    public AnomalyRockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    /** Blasted rock is remembered for regrowth (hewn rock regrows as wall rock). */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        super.onBlockExploded(state, level, pos, explosion);
        if (level instanceof ServerLevel server) AnomalyRegrowth.blasted(server, pos, state.getValue(KIND));
    }

    /** Distortion level 1 -> near value (TNT and creepers take it), level 3 and beyond -> far value (only the blast core). */
    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        double t = Mth.clamp((Distortion.level(pos.getX(), pos.getZ()) - 1) / 2, 0, 1);
        return (float) Mth.lerp(t, ServerConfig.get(ServerConfig.ANOMALY_RESISTANCE_NEAR), ServerConfig.get(ServerConfig.ANOMALY_RESISTANCE_FAR));
    }
}
