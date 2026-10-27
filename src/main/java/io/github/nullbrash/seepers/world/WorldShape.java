package io.github.nullbrash.seepers.world;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.network.payload.DistortionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Zone sizes from data to the server and clients; the world spawn next to the distortion center. */
public final class WorldShape {
    /** Spawn chunks are searched in a square of this radius around chunk (0, 0). */
    private static final int SPAWN_SEARCH_CHUNKS = 4;

    public static void onServerStarting(ServerStartingEvent event) {
        DensityFunction function = event.getServer().registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION)
                .get(Seepers.id("distortion"));
        if (function instanceof DistortionFunction distortion) {
            Distortion.set(distortion.zones());
        } else {
            Seepers.LOGGER.warn("Density function seepers:distortion missing or replaced; default zones used");
            Distortion.set(Distortion.DEFAULT);
        }
        // Broken gravity patches are otherwise bound only when a new chunk generates: /locate saw none before that.
        for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getChunkSource().getGenerator().getBiomeSource() instanceof ZonedBiomeSource zoned) {
                zoned.bind(level.getChunkSource().randomState());
            }
        }
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, DistortionPayload.of(Distortion.zones()));
        }
    }

    /**
     * Vanilla looks for a spawn by climate and may end up hundreds of blocks away; distortion is centered on
     * (0, 0), so the spawn must be there. Same per-chunk check as vanilla, nearest chunks first — but only on
     * chunks whose center is land by the cheap noise height: each check generates a full chunk on the main thread.
     */
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BlockPos spawn = null;
        for (int ring = 0; ring <= SPAWN_SEARCH_CHUNKS && spawn == null; ring++) {
            for (int x = -ring; x <= ring && spawn == null; x++) {
                for (int z = -ring; z <= ring && spawn == null; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                    int floor = generator.getBaseHeight(x * 16 + 8, z * 16 + 8, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    if (floor <= generator.getSeaLevel()) continue;
                    spawn = PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(x, z));
                }
            }
        }
        if (spawn == null) {
            spawn = new BlockPos(8, level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8), 8);
        }
        event.getSettings().setSpawn(spawn, 0.0F);
        event.setCanceled(true);
    }

    private WorldShape() {}
}
