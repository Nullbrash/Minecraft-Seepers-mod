package io.github.nullbrash.seepers.client.points;

import io.github.nullbrash.seepers.Seepers;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

/**
 * Lidar memory III keeps its points between visits (CLAUDE.md 5.3): when the cloud is dropped (leaving the world or
 * the dimension) and the last lidar in hand had it, its points go to a file on this computer, one per world (or
 * server) and dimension; the next cloud of that place loads and deletes it. Points only ever live on the client, so
 * they are not sent anywhere. Files are named by a hash, not by the server's address.
 */
public final class LidarMemory {
    private static final int VERSION = 1;
    private static boolean keeps;

    /** Set from the last lidar in hand: whether its memory keeps points between visits. */
    public static void setKeeps(boolean value) {
        keeps = value;
    }

    static void save(PointCloud cloud, ClientLevel level) {
        if (!keeps) return;
        Path file = file(level);
        if (file == null || cloud.count(PointSource.LIDAR) == 0) return;
        PointCloud.Ring ring = cloud.ringFor(PointSource.LIDAR);
        if (ring == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
                out.writeInt(VERSION);
                out.writeInt(ring.limit);
                out.writeInt(ring.live);
                int written = 0;
                for (PointCloud.Section section : cloud.sections.values()) {
                    for (Int2IntMap.Entry entry : section.points.int2IntEntrySet()) {
                        int packed = entry.getIntValue();
                        if (packed >>> 24 != PointSource.LIDAR.ordinal()) continue;
                        int slot = packed & 0xFFFFFF;
                        out.writeLong(section.key);
                        out.writeInt(ring.local[slot]);
                        out.writeShort(ring.x[slot]);
                        out.writeShort(ring.y[slot]);
                        out.writeShort(ring.z[slot]);
                        out.writeByte(ring.material[slot]);
                        written++;
                    }
                }
                if (written != ring.live) Seepers.LOGGER.warn("Lidar memory: wrote {} of {} points", written, ring.live);
            }
        } catch (IOException e) {
            Seepers.LOGGER.warn("Lidar memory could not be saved to {}", file, e);
        }
    }

    static void load(PointCloud cloud, ClientLevel level) {
        Path file = file(level);
        if (file == null || !Files.isRegularFile(file)) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != VERSION) return;
            // The saved device's limit until a lidar in hand sets its own: the points must not be cut on arrival.
            PointSource.setLidarLimit(in.readInt());
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long sectionKey = in.readLong();
                int local = in.readInt();
                double x = SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)) + in.readShort() / PointCloud.POSITION_SCALE;
                double y = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)) + in.readShort() / PointCloud.POSITION_SCALE;
                double z = SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)) + in.readShort() / PointCloud.POSITION_SCALE;
                int material = in.readByte();
                cloud.add(PointSource.LIDAR, x, y, z, Direction.values()[local >>> 27], material);
            }
        } catch (IOException e) {
            Seepers.LOGGER.warn("Lidar memory could not be read from {}", file, e);
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A stale file is only loaded once more.
        }
    }

    /** The file for this world (or server) and dimension, or null if the place is unknown. */
    private static @Nullable Path file(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        String place;
        if (minecraft.getSingleplayerServer() != null) {
            place = "local:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData server = minecraft.getCurrentServer();
            if (server == null) return null;
            place = "server:" + server.ip;
        }
        place += "|" + level.dimension().location();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(place.getBytes(StandardCharsets.UTF_8));
            return minecraft.gameDirectory.toPath().resolve("seepers").resolve("lidar-memory")
                    .resolve(HexFormat.of().formatHex(hash, 0, 12) + ".bin");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private LidarMemory() {}
}
