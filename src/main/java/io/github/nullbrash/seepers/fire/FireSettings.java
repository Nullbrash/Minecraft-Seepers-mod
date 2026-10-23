package io.github.nullbrash.seepers.fire;

import io.github.nullbrash.seepers.config.ServerConfig;

/** Snapshot of the fires part of the server config. */
public record FireSettings(double blowOutStart, double blowOutFull, int checkTicks, int maxChecksPerTick) {
    private static volatile FireSettings current = read();

    public static FireSettings current() {
        return current;
    }

    public static void reload() {
        current = read();
    }

    /** Chance that one check puts the fire out: 0 below the start, rising linearly to 1 at the full density. */
    public double chance(double density) {
        if (density >= blowOutFull) return 1;
        if (density <= blowOutStart) return 0;
        return (density - blowOutStart) / (blowOutFull - blowOutStart);
    }

    private static FireSettings read() {
        double start = ServerConfig.get(ServerConfig.FIRE_BLOW_OUT_START);
        double full = Math.max(start, ServerConfig.get(ServerConfig.FIRE_BLOW_OUT_FULL));
        return new FireSettings(start, full, (int) Math.max(1, Math.round(ServerConfig.get(ServerConfig.FIRE_CHECK_SECONDS) * 20)),
                ServerConfig.get(ServerConfig.FIRE_MAX_CHECKS_PER_TICK));
    }
}
