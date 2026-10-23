package io.github.nullbrash.seepers.light;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** Which dimension's light values apply: 0 = overworld (and any other dimension), 1 = nether, 2 = end. */
public final class LightDimension {
    public static final int OVERWORLD = 0;
    public static final int NETHER = 1;
    public static final int END = 2;

    // For getters without a level (client chunk-mesh regions): the client's current dimension.
    // The light engine always passes the Level itself, so lighting never depends on this.
    private static volatile int fallback = OVERWORLD;

    public static int of(BlockGetter getter) {
        if (getter instanceof Level level) return index(level.dimension());
        if (getter instanceof LevelChunk chunk) return index(chunk.getLevel().dimension());
        return fallback;
    }

    public static int index(ResourceKey<Level> dimension) {
        // ResourceKeys are interned, identity comparison is enough.
        if (dimension == Level.NETHER) return NETHER;
        if (dimension == Level.END) return END;
        return OVERWORLD;
    }

    public static void setFallback(ResourceKey<Level> dimension) {
        fallback = index(dimension);
    }

    private LightDimension() {}
}
