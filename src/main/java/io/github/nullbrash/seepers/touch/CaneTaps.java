package io.github.nullbrash.seepers.touch;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.network.payload.CaneTapPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of a cane tap (CLAUDE.md 5.2): the cane wears down and the knock is heard by everyone near — the
 * cane is the loud sense, the hands are the quiet one.
 */
public final class CaneTaps {
    /** Faster taps than a sweep can make are ignored (a modified client cannot drum for free). */
    private static final int MIN_TICKS_BETWEEN_TAPS = 3;
    private static final float KNOCK_VOLUME = 0.8F;
    private static final Map<ServerPlayer, Long> LAST_TAP = new WeakHashMap<>();

    public static void handle(CaneTapPayload tap, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        InteractionHand hand = tap.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack cane = player.getItemInHand(hand);
        if (!(cane.getItem() instanceof CaneItem)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST_TAP.get(player);
        if (last != null && now - last < MIN_TICKS_BETWEEN_TAPS) return;
        LAST_TAP.put(player, now);

        Vec3 at = new Vec3(tap.x(), tap.y(), tap.z());
        double reach = ServerConfig.get(ServerConfig.CANE_REACH) + 1.0;
        if (tap.hit() && player.getEyePosition().distanceToSqr(at) <= reach * reach && level.isLoaded(tap.pos())) {
            BlockState state = level.getBlockState(tap.pos());
            SoundType sound = state.getSoundType(level, tap.pos(), player);
            level.playSound(null, at.x, at.y, at.z, sound.getHitSound(), SoundSource.PLAYERS, KNOCK_VOLUME, sound.getPitch());
        } else {
            Vec3 eye = player.getEyePosition();
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.4F, 1.3F);
        }
        cane.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
    }

    private CaneTaps() {}
}
