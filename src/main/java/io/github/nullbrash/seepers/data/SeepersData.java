package io.github.nullbrash.seepers.data;

import io.github.nullbrash.seepers.data.chunk.AnomalyBlocks;
import io.github.nullbrash.seepers.data.chunk.FireSources;
import io.github.nullbrash.seepers.data.chunk.PlayerPlacedBlocks;
import io.github.nullbrash.seepers.data.world.BaseCopyData;
import io.github.nullbrash.seepers.data.world.ChunkActivityData;
import io.github.nullbrash.seepers.data.world.LidarPointData;
import io.github.nullbrash.seepers.data.world.LifeData;
import io.github.nullbrash.seepers.data.world.RemainsData;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Single access point to the data stores of CLAUDE.md section 13. Server side only.
 * Global stores live in the overworld's data folder: dimensions are layers of one world, not separate worlds.
 */
public final class SeepersData {
    // --- per chunk (data attachments) ---

    public static PlayerPlacedBlocks playerPlacedBlocks(LevelChunk chunk) {
        return chunk.getData(SeepersAttachments.PLAYER_PLACED_BLOCKS);
    }

    public static FireSources fireSources(LevelChunk chunk) {
        return chunk.getData(SeepersAttachments.FIRE_SOURCES);
    }

    public static AnomalyBlocks anomalyBlocks(LevelChunk chunk) {
        return chunk.getData(SeepersAttachments.ANOMALY_BLOCKS);
    }

    // --- per dimension ---

    public static ChunkActivityData chunkActivity(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ChunkActivityData.FACTORY, ChunkActivityData.FILE_NAME);
    }

    public static LidarPointData lidarPoints(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(LidarPointData.FACTORY, LidarPointData.FILE_NAME);
    }

    // --- global ---

    public static BaseCopyData baseCopies(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BaseCopyData.FACTORY, BaseCopyData.FILE_NAME);
    }

    public static RemainsData remains(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RemainsData.FACTORY, RemainsData.FILE_NAME);
    }

    public static LifeData lives(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LifeData.FACTORY, LifeData.FILE_NAME);
    }

    private SeepersData() {}
}
