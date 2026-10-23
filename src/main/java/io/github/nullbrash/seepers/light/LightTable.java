package io.github.nullbrash.seepers.light;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersTags;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Computes the light overrides of every block state from the light tags and the server config.
 * Must be rebuilt whenever tags or the config change; already lit areas keep their old light until relit.
 * Client and server compute the same values (tags and server config are synced to clients).
 */
public final class LightTable {
    private static final int PRESENT = 1 << 12;

    public static synchronized void rebuild() {
        long startedAt = System.nanoTime();
        int cap = ServerConfig.get(ServerConfig.LIGHT_UNCATEGORIZED_MAX);
        double waterloggedFactor = ServerConfig.get(ServerConfig.LIGHT_WATERLOGGED_FACTOR);
        int waterAbsorption = ServerConfig.get(ServerConfig.LIGHT_WATER_ABSORPTION);
        Map<LightCategory, int[]> levels = new EnumMap<>(LightCategory.class);
        for (LightCategory category : LightCategory.values()) {
            levels.put(category, configuredLevels(category));
        }

        int overridden = 0;
        Set<String> uncategorized = new TreeSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                LightOverrides overrides = (LightOverrides) state;
                int packed = computeLight(state, levels, cap, waterloggedFactor, uncategorized);
                overrides.seepers$setPackedLight(packed);
                if (packed != 0) overridden++;

                overrides.seepers$setRoomWalls(0); // tags changed: room walls are recomputed lazily
                overrides.seepers$setLightBlockOverride(0);
                if (state.getFluidState().is(FluidTags.WATER)) {
                    int vanillaOpacity = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    overrides.seepers$setLightBlockOverride(Math.max(vanillaOpacity, waterAbsorption) + 1);
                }
            }
        }
        Seepers.LOGGER.info("Light table rebuilt in {} ms: {} states overridden; emitters without a light category: {}",
                (System.nanoTime() - startedAt) / 1_000_000, overridden, uncategorized.isEmpty() ? "none" : uncategorized);
    }

    private static int computeLight(BlockState state, Map<LightCategory, int[]> levels, int cap,
                                    double waterloggedFactor, Set<String> uncategorized) {
        // The vanilla value keeps state logic for free: unlit furnaces, dead sea pickles etc. stay dark.
        int vanilla = state.getLightEmission();
        if (vanilla <= 0 || state.hasDynamicLightEmission() || state.is(SeepersTags.Blocks.LIGHT_VANILLA)) {
            return 0;
        }

        int overworld;
        int nether;
        int end;
        LightCategory category = categoryOf(state);
        if (category == null) {
            uncategorized.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            if (vanilla <= cap) return 0;
            overworld = nether = end = cap;
        } else if (category.byCandles) {
            int[] byCount = levels.get(category);
            int count = state.hasProperty(CandleBlock.CANDLES) ? state.getValue(CandleBlock.CANDLES) : 1;
            overworld = nether = end = byCount[Math.min(count, byCount.length) - 1];
        } else {
            int[] byDimension = levels.get(category);
            overworld = byDimension[LightDimension.OVERWORLD];
            nether = byDimension[LightDimension.NETHER];
            end = byDimension[LightDimension.END];
        }

        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
                && !state.is(SeepersTags.Blocks.LIGHT_NO_WATER_DIMMING)) {
            overworld = scale(overworld, waterloggedFactor);
            nether = scale(nether, waterloggedFactor);
            end = scale(end, waterloggedFactor);
        }
        return pack(overworld, nether, end);
    }

    public static LightCategory categoryOf(BlockState state) {
        for (LightCategory category : LightCategory.values()) {
            if (state.is(category.tag)) return category;
        }
        return null;
    }

    private static int[] configuredLevels(LightCategory category) {
        List<? extends Integer> configured = ServerConfig.get(ServerConfig.LIGHT_LEVELS.get(category));
        List<? extends Integer> values = configured.size() == category.defaults.size() ? configured : category.defaults;
        if (values != configured) {
            Seepers.LOGGER.warn("Config light.categories.{} needs {} values, got {}; using defaults {}",
                    category.id, category.defaults.size(), configured, category.defaults);
        }
        return values.stream().mapToInt(v -> Math.clamp(v, 0, 15)).toArray();
    }

    private static int scale(int level, double factor) {
        return (int) Math.clamp(Math.round(level * factor), 0, 15);
    }

    /** Packs per-dimension levels as 4-bit fields indexed by {@link LightDimension}; never 0. */
    static int pack(int overworld, int nether, int end) {
        return PRESENT | end << 8 | nether << 4 | overworld;
    }

    private LightTable() {}
}
