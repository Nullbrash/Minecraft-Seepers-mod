package io.github.nullbrash.seepers.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/**
 * Distortion, the main axis of the world (CLAUDE.md 4.8): grows with the distance from (0, 0) — world
 * generation cannot know the spawn, so the spawn is placed near (0, 0) instead. Level k is reached where zone k
 * starts; between zone starts it grows linearly. Pure function, the same on server and client.
 */
public final class Distortion {
    /** Distance from (0, 0) where zones 1, 2 and 3 start. Defined in data: seepers:distortion density function. */
    public record Zones(int zone1, int zone2, int zone3) {
        public static final Codec<Zones> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 30_000_000).fieldOf("zone1").forGetter(Zones::zone1),
                Codec.intRange(1, 30_000_000).fieldOf("zone2").forGetter(Zones::zone2),
                Codec.intRange(1, 30_000_000).fieldOf("zone3").forGetter(Zones::zone3))
                .apply(instance, Zones::new));
    }

    public static final Zones DEFAULT = new Zones(800, 2000, 4000);
    /** Past zone 3 the level keeps growing at the zone 2 -> 3 rate up to this. */
    public static final double MAX_LEVEL = 4.0;

    private static volatile Zones current = DEFAULT;

    /** Zones of the world being played (server: from data at start; client: sent on login). */
    public static Zones zones() {
        return current;
    }

    public static void set(Zones zones) {
        current = zones;
    }

    public static double level(double x, double z) {
        return level(current, x, z);
    }

    public static double level(Zones zones, double x, double z) {
        double r = Math.sqrt(x * x + z * z);
        if (r < zones.zone1()) return r / zones.zone1();
        if (r < zones.zone2()) return 1 + (r - zones.zone1()) / Math.max(1, zones.zone2() - zones.zone1());
        double step = Math.max(1, zones.zone3() - zones.zone2());
        if (r < zones.zone3()) return 2 + (r - zones.zone2()) / step;
        return Math.min(MAX_LEVEL, 3 + (r - zones.zone3()) / step);
    }

    /** Zone 0..3 for a level. */
    public static int zone(double level) {
        return Mth.clamp(Mth.floor(level), 0, 3);
    }

    private Distortion() {}
}
