package io.github.nullbrash.seepers.fog;

import com.google.common.hash.Hashing;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * The fog density field: base density plus modifiers ("writers": heat, rooms, later dry ice and wind
 * charges). Server side reads it directly at a point; the client caches it in a grid for rendering.
 * For many samples build one {@link FogDensity.Context} and reuse it.
 */
public final class FogField {
    /** Adds (usually negative) density at a point. Must be deterministic from world state. */
    public interface Modifier {
        double densityAt(Level level, double x, double y, double z, double baseDensity);
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();
    private static final long SEED_SALT = 0x5EE9E25F06L;
    private static volatile long serverSeed;

    public static void addModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static void onServerStarting(ServerStartingEvent event) {
        serverSeed = fogSeed(event.getServer());
    }

    public static long serverSeed() {
        return serverSeed;
    }

    public static FogDensity.Context context(Level level, long fogSeed) {
        return FogDensity.Context.of(level, FogNoise.of(fogSeed, level.dimension()));
    }

    /** The field at another day time; modifiers (heat, rooms) still see the current state. */
    public static FogDensity.Context context(Level level, long fogSeed, double dayTime) {
        return FogDensity.Context.at(level, FogNoise.of(fogSeed, level.dimension()), dayTime);
    }

    /** Total density at a point, never negative. */
    public static double density(FogDensity.Context context, double x, double y, double z) {
        double base = FogDensity.at(context, x, y, z);
        double total = base;
        for (Modifier modifier : MODIFIERS) {
            total += modifier.densityAt(context.level(), x, y, z, base);
        }
        return Math.max(0, total);
    }

    /** Single query; prefer a shared context for many points. */
    public static double density(Level level, long fogSeed, double x, double y, double z) {
        return density(context(level, fogSeed), x, y, z);
    }

    /** Server side: density with the running server's fog seed. */
    public static double density(Level level, double x, double y, double z) {
        return density(level, serverSeed, x, y, z);
    }

    /**
     * Fog seed sent to clients: a one-way hash of the world seed, so clients can compute the same field
     * without learning the world seed.
     */
    public static long fogSeed(MinecraftServer server) {
        return Hashing.sha256().hashLong(server.getWorldData().worldGenOptions().seed() ^ SEED_SALT).asLong();
    }

    private FogField() {}
}
