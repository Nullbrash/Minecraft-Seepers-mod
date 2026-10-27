package io.github.nullbrash.seepers.world;

import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.data.SeepersData;
import io.github.nullbrash.seepers.data.chunk.AnomalyBlocks;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Regrowth of blasted anomalous rock (CLAUDE.md 4.8): layer by layer, only from intact or regrown neighbours (hewn
 * rock is no source), geodes faster than walls. A blasted spot filled by any other block (cladding) or occupied by
 * an entity waits. Time away is caught up from the game time on the next pass (14).
 */
public final class AnomalyRegrowth {
    private static final int PASS_TICKS = 20;
    /** Chunks of a level that have blasted rock waiting. */
    private static final Map<ServerLevel, LongOpenHashSet> CHUNKS = new WeakHashMap<>();
    /** Chunks loaded since the last pass: their saved data is not readable yet at the load event, checked next pass. */
    private static final Map<ServerLevel, LongOpenHashSet> LOADED = new WeakHashMap<>();

    public static void blasted(ServerLevel level, BlockPos pos, AnomalyRockBlock.Kind kind) {
        LevelChunk chunk = level.getChunkAt(pos);
        SeepersData.anomalyBlocks(chunk).markBlasted(pos, level.getGameTime(), kind == AnomalyRockBlock.Kind.GEODE);
        CHUNKS.computeIfAbsent(level, l -> new LongOpenHashSet()).add(chunk.getPos().toLong());
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.computeIfAbsent(level, l -> new LongOpenHashSet()).add(chunk.getPos().toLong());
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            long pos = event.getChunk().getPos().toLong();
            LongOpenHashSet chunks = CHUNKS.get(level);
            if (chunks != null) chunks.remove(pos);
            LongOpenHashSet loaded = LOADED.get(level);
            if (loaded != null) loaded.remove(pos);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % PASS_TICKS != 0 || !ServerConfig.get(ServerConfig.REGROWTH_ENABLED)) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LongOpenHashSet loaded = LOADED.get(level);
            if (loaded != null && !loaded.isEmpty()) {
                for (long packed : loaded.toLongArray()) {
                    ChunkPos pos = new ChunkPos(packed);
                    if (!level.hasChunk(pos.x, pos.z)) continue;
                    loaded.remove(packed);
                    AnomalyBlocks data = level.getChunk(pos.x, pos.z).getExistingDataOrNull(SeepersAttachments.ANOMALY_BLOCKS.get());
                    if (data != null && data.blastedCount() > 0) CHUNKS.computeIfAbsent(level, l -> new LongOpenHashSet()).add(packed);
                }
            }
            LongOpenHashSet chunks = CHUNKS.get(level);
            if (chunks == null || chunks.isEmpty()) continue;
            for (long packed : chunks.toLongArray()) {
                ChunkPos pos = new ChunkPos(packed);
                if (!level.hasChunk(pos.x, pos.z)) {
                    chunks.remove(packed);
                    continue;
                }
                AnomalyBlocks data = SeepersData.anomalyBlocks(level.getChunk(pos.x, pos.z));
                grow(level, data, false);
                grow(level, data, true);
                if (data.blastedCount() == 0) chunks.remove(packed);
            }
        }
    }

    /** Runs every layer due since the last one (several after time away, capped). */
    private static void grow(ServerLevel level, AnomalyBlocks data, boolean geode) {
        long oldest = data.oldestBlast(geode);
        if (oldest == Long.MAX_VALUE) return;
        long interval = Math.max(1, Math.round(20 * (geode ? ServerConfig.get(ServerConfig.REGROWTH_GEODE_SECONDS) : ServerConfig.get(ServerConfig.REGROWTH_WALL_SECONDS))));
        long now = level.getGameTime();
        long last = Math.max(data.layerTime(geode), oldest);
        long due = (now - last) / interval;
        if (due <= 0) return;
        int layers = (int) Math.min(due, ServerConfig.get(ServerConfig.REGROWTH_MAX_CATCH_UP));
        for (int layer = 0; layer < layers; layer++) {
            long layerTime = last + (layer + 1) * interval;
            List<BlockPos> ready = new ArrayList<>();
            data.forEachBlasted(geode, (pos, blastTime) -> {
                if (layerTime - blastTime >= interval && canGrow(level, pos)) ready.add(pos);
            });
            if (ready.isEmpty()) break;
            BlockState rock = AnomalyRock.rock(geode ? AnomalyRockBlock.Kind.GEODE : AnomalyRockBlock.Kind.WALL);
            // Decided for the whole layer first, then placed: a layer grows one block deep, not through the hole.
            for (BlockPos pos : ready) {
                level.setBlockAndUpdate(pos, rock);
                data.clearBlasted(pos);
            }
        }
        data.setLayerTime(geode, last + due * interval);
    }

    private static boolean canGrow(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        // Air or loose liquid only: any placed block (cladding) holds the spot.
        if (!here.isAir() && here.getFluidState().isEmpty()) return false;
        // Players and creatures hold the spot; dropped items (the shards themselves) do not.
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), Entity::isAlive).isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            BlockState next = level.getBlockState(pos.relative(direction));
            if (next.is(SeepersBlocks.ANOMALY_ROCK.get()) && next.getValue(AnomalyRockBlock.KIND) != AnomalyRockBlock.Kind.HEWN) return true;
        }
        return false;
    }

    /** For the debug command: chunks with blasted rock loaded now and the blocks waiting in them. */
    public static String summary(ServerLevel level) {
        LongOpenHashSet chunks = CHUNKS.get(level);
        int count = chunks == null ? 0 : chunks.size();
        int blocks = 0;
        if (chunks != null) {
            for (long packed : chunks) {
                ChunkPos pos = new ChunkPos(packed);
                if (level.hasChunk(pos.x, pos.z)) blocks += SeepersData.anomalyBlocks(level.getChunk(pos.x, pos.z)).blastedCount();
            }
        }
        return "regrowth: " + count + " chunks, " + blocks + " blasted blocks waiting";
    }

    private AnomalyRegrowth() {}
}
