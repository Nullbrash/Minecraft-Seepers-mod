package io.github.nullbrash.seepers.light;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Whether a light lookup comes from a client that draws dynamic light itself. The client's light engine asks with
 * its own level or chunks; renderers ask with region views (vanilla, Sodium) that only exist on the client. The
 * server (dedicated or integrated) always asks with a server level or its chunks.
 */
public final class ClientLightSide {
    /** Set by the client from its config: off falls back to the stepped server light. */
    private static volatile boolean smooth = true;

    public static void setSmooth(boolean value) {
        smooth = value;
    }

    public static boolean smooth() {
        return smooth;
    }

    public static boolean hidesServerLight(BlockGetter level) {
        if (!smooth) return false;
        if (level instanceof Level l) return l.isClientSide();
        if (level instanceof LevelChunk chunk) return chunk.getLevel().isClientSide();
        // Proto chunks are world generation (server); anything else is a client render region.
        return !(level instanceof ChunkAccess);
    }

    private ClientLightSide() {}
}
