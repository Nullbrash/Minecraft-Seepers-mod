package io.github.nullbrash.seepers.mob;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * A mob spawner keeps at most a few of its own mobs alive at once (CLAUDE.md 7.3), wherever they wandered: vanilla
 * only counts mobs in a small box around the spawner, so the ones that walked off let it fill the place. Each spawned
 * mob carries its spawner's position ({@code seepers:spawner}); the count follows mobs entering and leaving the
 * loaded world. Trial spawners are not spawners here: they are finite on their own.
 */
public final class SpawnerCap {
    /** Living mobs by spawner, per dimension: spawner position -> mob ids. */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Set<UUID>>> ALIVE = new HashMap<>();

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.SPAWNER || event.getSpawner() == null) return;
        if (!(event.getSpawner().left().orElse(null) instanceof SpawnerBlockEntity spawner)) return;
        long pos = spawner.getBlockPos().asLong();
        event.getEntity().setData(SeepersAttachments.SPAWNER.get(), pos);
        // Over the cap the spawn is cancelled and the spawner waits its usual delay before trying again. Also while
        // the mobs around it are still loading (a chunk ticks before its entities are in): its own would go uncounted.
        if (!entitiesLoaded(event.getLevel(), spawner.getBlockPos()) || full(event.getEntity().level().dimension(), pos)) {
            event.setSpawnCancelled(true);
        }
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || event.isCanceled() || !entity.hasData(SeepersAttachments.SPAWNER.get())) return;
        // A cancelled spawn never really joins (NeoForge turns it away, maybe after this listener): not counted.
        if (entity instanceof net.minecraft.world.entity.Mob mob && mob.isSpawnCancelled()) return;
        long pos = entity.getData(SeepersAttachments.SPAWNER.get());
        // A new mob past the cap stays out, however it got here (one cancelled spawn slipped in while entities loaded).
        if (!event.loadedFromDisk() && full(event.getLevel().dimension(), pos)) {
            event.setCanceled(true);
            return;
        }
        alive(event.getLevel().dimension()).computeIfAbsent(pos, k -> new HashSet<>()).add(entity.getUUID());
    }

    private static boolean full(ResourceKey<Level> dimension, long pos) {
        Set<UUID> alive = alive(dimension).get(pos);
        return alive != null && alive.size() >= ServerConfig.get(ServerConfig.SPAWNER_MAX_ALIVE);
    }

    /** The entities of the chunks around the spawner are loaded (its mobs nearby are counted). */
    private static boolean entitiesLoaded(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.core.BlockPos pos) {
        net.minecraft.server.level.ServerLevel server = level.getLevel();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!server.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz))) return false;
            }
        }
        return true;
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !entity.hasData(SeepersAttachments.SPAWNER.get())) return;
        Long2ObjectOpenHashMap<Set<UUID>> spawners = alive(event.getLevel().dimension());
        long pos = entity.getData(SeepersAttachments.SPAWNER.get());
        Set<UUID> alive = spawners.get(pos);
        if (alive == null) return;
        alive.remove(entity.getUUID());
        if (alive.isEmpty()) spawners.remove(pos);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ALIVE.clear();
    }

    private static Long2ObjectOpenHashMap<Set<UUID>> alive(ResourceKey<Level> dimension) {
        return ALIVE.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap<>());
    }

    private SpawnerCap() {}
}
