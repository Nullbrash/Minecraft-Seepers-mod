package io.github.nullbrash.seepers.room;

import io.github.nullbrash.seepers.config.ServerConfig;

/** Snapshot of the room part of the server config (synced to clients). Times in ticks. */
public record RoomSettings(int maxVolume, double fillTicks, double clearTicks, double referenceVolume, int recheckTicks) {
    private static volatile RoomSettings current = read();

    public static RoomSettings current() {
        return current;
    }

    public static void reload() {
        current = read();
    }

    /** Time to fill with outside fog: fillTicks for one leak in a reference-size room; more leaks per volume — faster. */
    public double fillTicks(int leaks, int volume) {
        double ratio = Math.sqrt(Math.max(1, volume) / referenceVolume) / Math.max(1, leaks);
        return Math.clamp(fillTicks * ratio, 100.0, 12000.0);
    }

    private static RoomSettings read() {
        return new RoomSettings(ServerConfig.get(ServerConfig.ROOM_MAX_VOLUME),
                ServerConfig.get(ServerConfig.ROOM_FILL_SECONDS) * 20.0,
                ServerConfig.get(ServerConfig.ROOM_CLEAR_SECONDS) * 20.0,
                ServerConfig.get(ServerConfig.ROOM_REFERENCE_VOLUME),
                ServerConfig.get(ServerConfig.ROOM_RECHECK_TICKS));
    }
}
