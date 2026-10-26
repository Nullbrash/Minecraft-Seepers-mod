package io.github.nullbrash.seepers.mob;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.registry.SeepersTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * No natural hostile spawns (CLAUDE.md 7.3) — in true darkness vanilla would fill every spot.
 * Spawners, structures, commands and eggs are not affected.
 */
public final class HostileSpawnControl {
    /** Natural spawning: cancelled before the entity is created. */
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (isBlocked(event.getEntityType(), event.getSpawnType()) || overExemptCap(event)) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * Exempt hostiles (drowned) are the only monsters left to fill vanilla's whole monster cap; keep them to a
     * few near each spot, and refill slowly.
     */
    private static boolean overExemptCap(MobSpawnEvent.SpawnPlacementCheck event) {
        EntityType<?> type = event.getEntityType();
        boolean natural = event.getSpawnType() == MobSpawnType.NATURAL || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION;
        if (!natural || type.getCategory() != MobCategory.MONSTER || !type.is(SeepersTags.EntityTypes.NATURAL_SPAWN_EXEMPT)) {
            return false;
        }
        if (event.getRandom().nextDouble() >= ServerConfig.get(ServerConfig.EXEMPT_SPAWN_CHANCE)) return true;
        double radius = ServerConfig.get(ServerConfig.EXEMPT_RADIUS);
        int nearby = event.getLevel().getEntitiesOfClass(Mob.class, new AABB(event.getPos()).inflate(radius),
                mob -> mob.getType().is(SeepersTags.EntityTypes.NATURAL_SPAWN_EXEMPT)).size();
        return nearby >= ServerConfig.get(ServerConfig.EXEMPT_MAX_NEARBY);
    }

    /** Patrols and phantoms skip spawn placement checks, so they are caught here. */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (isBlocked(event.getEntity().getType(), event.getSpawnType())) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean isBlocked(EntityType<?> type, MobSpawnType spawnType) {
        boolean natural = spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.PATROL;
        return natural
                && type.getCategory() == MobCategory.MONSTER
                && !type.is(SeepersTags.EntityTypes.NATURAL_SPAWN_EXEMPT)
                && ServerConfig.DISABLE_NATURAL_HOSTILE_SPAWNS.get();
    }

    private HostileSpawnControl() {}
}
