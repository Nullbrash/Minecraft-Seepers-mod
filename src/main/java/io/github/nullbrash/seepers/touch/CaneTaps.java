package io.github.nullbrash.seepers.touch;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.network.payload.CaneTapPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of a cane tap (CLAUDE.md 5.2): the cane wears down and the knock is heard by everyone near — the
 * cane is the loud sense, the hands are the quiet one.
 */
public final class CaneTaps {
    /** Faster taps than a sweep can make are ignored (a modified client cannot drum for free). */
    private static final int MIN_TICKS_BETWEEN_TAPS = 3;
    private static final float KNOCK_VOLUME = 0.8F;
    private static final float HOLLOW_VOLUME = 1.0F;
    private static final float HOLLOW_PITCH = 0.55F;
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
        boolean tipped = CaneItem.hasIronTip(cane);
        double reach = ServerConfig.get(ServerConfig.CANE_REACH) + (tipped ? 2.0 : 1.0);
        if (tap.hit() && player.getEyePosition().distanceToSqr(at) <= reach * reach && level.isLoaded(tap.pos())) {
            BlockState state = level.getBlockState(tap.pos());
            if (level.getFluidState(tap.pos()).is(FluidTags.LAVA)) {
                // Lava: the hiss below says it all.
            } else if (state.getShape(level, tap.pos()).isEmpty()) {
                // Nothing solid there: the cane met a mob.
                level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, KNOCK_VOLUME, 0.8F);
            } else {
                SoundType sound = state.getSoundType(level, tap.pos(), player);
                Direction face = tap.face();
                // A wall with air right behind it sounds hollow (checked here, not taken from the client).
                boolean hollow = face != null && face.getAxis().isHorizontal()
                        && level.getBlockState(tap.pos().relative(face.getOpposite())).getCollisionShape(level, tap.pos().relative(face.getOpposite())).isEmpty();
                level.playSound(null, at.x, at.y, at.z, sound.getHitSound(), SoundSource.PLAYERS,
                        hollow ? HOLLOW_VOLUME : KNOCK_VOLUME, sound.getPitch() * (hollow ? HOLLOW_PITCH : 1.0F));
                if (face == Direction.DOWN) NeoForge.EVENT_BUS.post(new CaneCeilingTapEvent(player, tap.pos()));
            }
        } else {
            Vec3 eye = player.getEyePosition();
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.4F, 1.3F);
        }
        EquipmentSlot slot = hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        // A wooden end in lava chars: a share of the full durability goes, with a hiss and a puff of smoke.
        if (tap.hit() && tap.face() == null && !tipped && level.isLoaded(tap.pos())
                && level.getFluidState(tap.pos()).is(FluidTags.LAVA) && player.getEyePosition().distanceToSqr(at) <= reach * reach) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 6, 0.1, 0.1, 0.1, 0.01);
            cane.hurtAndBreak(Math.max(1, (int) Math.ceil(cane.getMaxDamage() * ServerConfig.get(ServerConfig.CANE_LAVA_BURN))), player, slot);
            return;
        }
        cane.hurtAndBreak(1, player, slot);
    }

    private CaneTaps() {}
}
