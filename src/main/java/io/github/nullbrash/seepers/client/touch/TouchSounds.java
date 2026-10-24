package io.github.nullbrash.seepers.client.touch;

import io.github.nullbrash.seepers.registry.SeepersTags;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the hand hears (CLAUDE.md 5.1): the touched block's step sound, quietly; functional blocks (tag
 * #seepers:touch/functional) their own use sound, but "felt" rather than used: quiet and fading out fast (a chest is
 * lifted a crack, not opened). Heard only by the player.
 */
public final class TouchSounds {
    private static final float STEP_VOLUME = 0.25F;
    private static final float FUNCTIONAL_VOLUME = 0.35F;
    /** Ticks over which a functional block's sound fades to silence. */
    private static final int FADE_TICKS = 8;
    private static final Map<Block, SoundEvent> USE_SOUNDS = new IdentityHashMap<>();

    /** A sound whose volume falls to zero over FADE_TICKS, then stops. */
    private static final class Fading extends AbstractTickableSoundInstance {
        private final float startVolume;
        private int age;

        Fading(SoundEvent event, float volume, double x, double y, double z) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.startVolume = volume;
            this.volume = volume;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void tick() {
            age++;
            volume = startVolume * Math.max(0, 1 - age / (float) FADE_TICKS);
            if (age >= FADE_TICKS) stop();
        }
    }

    static {
        USE_SOUNDS.put(Blocks.CHEST, SoundEvents.CHEST_OPEN);
        USE_SOUNDS.put(Blocks.TRAPPED_CHEST, SoundEvents.CHEST_OPEN);
        USE_SOUNDS.put(Blocks.ENDER_CHEST, SoundEvents.ENDER_CHEST_OPEN);
        USE_SOUNDS.put(Blocks.BARREL, SoundEvents.BARREL_OPEN);
        USE_SOUNDS.put(Blocks.ANVIL, SoundEvents.ANVIL_LAND);
        USE_SOUNDS.put(Blocks.CHIPPED_ANVIL, SoundEvents.ANVIL_LAND);
        USE_SOUNDS.put(Blocks.DAMAGED_ANVIL, SoundEvents.ANVIL_LAND);
        USE_SOUNDS.put(Blocks.BELL, SoundEvents.BELL_BLOCK);
        USE_SOUNDS.put(Blocks.LECTERN, SoundEvents.BOOK_PAGE_TURN);
        USE_SOUNDS.put(Blocks.ENCHANTING_TABLE, SoundEvents.ENCHANTMENT_TABLE_USE);
        USE_SOUNDS.put(Blocks.COMPOSTER, SoundEvents.COMPOSTER_FILL);
        USE_SOUNDS.put(Blocks.BEACON, SoundEvents.BEACON_AMBIENT);
        USE_SOUNDS.put(Blocks.RESPAWN_ANCHOR, SoundEvents.RESPAWN_ANCHOR_CHARGE);
        USE_SOUNDS.put(Blocks.BREWING_STAND, SoundEvents.BREWING_STAND_BREW);
        USE_SOUNDS.put(Blocks.GRINDSTONE, SoundEvents.GRINDSTONE_USE);
        USE_SOUNDS.put(Blocks.STONECUTTER, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
        USE_SOUNDS.put(Blocks.LOOM, SoundEvents.UI_LOOM_SELECT_PATTERN);
        USE_SOUNDS.put(Blocks.CARTOGRAPHY_TABLE, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
        USE_SOUNDS.put(Blocks.SMITHING_TABLE, SoundEvents.SMITHING_TABLE_USE);
    }

    /** The felt sound of a functional block only (the cane's knock itself comes from the server). */
    public static void playFunctional(Level level, Player player, BlockPos pos, BlockState state, Vec3 at) {
        if (!state.is(SeepersTags.Blocks.TOUCH_FUNCTIONAL)) return;
        SoundType type = state.getSoundType(level, pos, player);
        SoundEvent event = USE_SOUNDS.getOrDefault(state.getBlock(), type.getHitSound());
        Minecraft.getInstance().getSoundManager().play(new Fading(event, FUNCTIONAL_VOLUME, at.x, at.y, at.z));
    }

    public static void play(Level level, Player player, BlockPos pos, BlockState state, Vec3 at) {
        SoundType type = state.getSoundType(level, pos, player);
        if (!state.is(SeepersTags.Blocks.TOUCH_FUNCTIONAL)) {
            level.playLocalSound(at.x, at.y, at.z, type.getStepSound(), SoundSource.PLAYERS, type.getVolume() * STEP_VOLUME,
                    type.getPitch(), false);
            return;
        }
        SoundEvent event = USE_SOUNDS.getOrDefault(state.getBlock(), type.getHitSound());
        Minecraft.getInstance().getSoundManager().play(new Fading(event, FUNCTIONAL_VOLUME, at.x, at.y, at.z));
    }

    private TouchSounds() {}
}
