package io.github.nullbrash.seepers.touch;

import io.github.nullbrash.seepers.network.payload.SilhouettePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every blow between a player and another creature leaves a silhouette of the other one for that player
 * (CLAUDE.md 5.2): the player hitting a mob sees the mob, a mob (or its arrow) hitting the player shows the mob.
 */
public final class BlowSilhouettes {
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == target || target.level().isClientSide) return;
        if (target instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SilhouettePayload(attacker.getId(), false));
        }
        if (attacker instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SilhouettePayload(target.getId(), true));
        }
    }

    private BlowSilhouettes() {}
}
