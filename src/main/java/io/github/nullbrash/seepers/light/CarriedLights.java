package io.github.nullbrash.seepers.light;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.fire.FireSettings;
import io.github.nullbrash.seepers.fog.FogField;
import io.github.nullbrash.seepers.registry.SeepersTags;
import io.github.nullbrash.seepers.room.RoomRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Light in hand and on the ground (CLAUDE.md 4.2): an item shines as its block would where it is (light categories,
 * dimension); of two hands, the brighter. Open fire (the {@code seepers:fire/*} tags: torches, campfires, candles)
 * goes out under water and in fog by the thresholds of placed fires (4.6), checked as often; the item stays and the
 * flame comes back by itself once the fog has thinned or the head is out of the water. Lanterns never go out.
 */
public final class CarriedLights {
    /** Carriers whose open fire the fog has put out, until it thins below the safe threshold. */
    private static final Set<UUID> DOUSED = new HashSet<>();
    /** Dropped items that may shine, gathered as they enter a level. */
    private static final Map<UUID, ItemEntity> ITEMS = new HashMap<>();

    public static void register() {
        DynamicLights.addProvider(CarriedLights::brightness);
    }

    static Iterable<ItemEntity> droppedItems() {
        return ITEMS.values();
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && source(item.getItem()) != null) {
            ITEMS.put(item.getUUID(), item);
        }
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item) {
            ITEMS.remove(item.getUUID());
            DOUSED.remove(item.getUUID());
        }
    }

    private static int brightness(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return 0;
        if (entity instanceof ItemEntity item) return light(level, entity, item.getItem());
        if (entity instanceof LivingEntity living) {
            return Math.max(light(level, entity, living.getMainHandItem()), light(level, entity, living.getOffhandItem()));
        }
        return 0;
    }

    private static int light(ServerLevel level, Entity carrier, ItemStack stack) {
        BlockState state = source(stack);
        if (state == null) return 0;
        BlockPos pos = BlockPos.containing(carrier.getEyePosition());
        int light = state.getLightEmission(level, pos);
        if (light <= 0) return 0;
        boolean underwater = carrier instanceof ItemEntity ? carrier.isInWater() : carrier.isEyeInFluid(FluidTags.WATER);
        if (isOpenFire(state)) {
            if (underwater) return 0;
            if (doused(level, carrier)) return 0;
            return light;
        }
        if (underwater && !state.is(SeepersTags.Blocks.LIGHT_NO_WATER_DIMMING)) {
            return (int) Math.round(light * ServerConfig.get(ServerConfig.LIGHT_WATERLOGGED_FACTOR));
        }
        return light;
    }

    /** The block an item shines as, or null. */
    private static @Nullable BlockState source(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(Items.LAVA_BUCKET)) return Blocks.LAVA.defaultBlockState();
        if (stack.getItem() instanceof BlockItem item) {
            BlockState state = item.getBlock().defaultBlockState();
            return state.getLightEmission() > 0 || state.is(SeepersTags.Blocks.LIGHT_VANILLA) ? state : null;
        }
        return null;
    }

    private static boolean isOpenFire(BlockState state) {
        return state.is(SeepersTags.Blocks.FIRE_BLOW_OUT) || state.is(SeepersTags.Blocks.FIRE_BREAK) || state.is(SeepersTags.Blocks.FIRE_EXTINGUISH);
    }

    /**
     * Fog check for open fire, every fire check interval per carrier (staggered by its id): a burning flame goes out
     * by the chance of placed fires; a doused one lights again once the fog is below the safe threshold.
     */
    private static boolean doused(ServerLevel level, Entity carrier) {
        FireSettings settings = FireSettings.current();
        boolean doused = DOUSED.contains(carrier.getUUID());
        long phase = Math.floorMod(carrier.getUUID().getLeastSignificantBits(), settings.checkTicks());
        if (Math.floorMod(level.getGameTime(), settings.checkTicks()) != phase) return doused;
        Vec3 eye = carrier.getEyePosition();
        double density = FogField.density(level, eye.x, eye.y, eye.z);
        if (settings.chance(density) > 0) {
            // Rooms are only found when it matters, as for placed fires (a fill can cost a few milliseconds).
            BlockPos pos = BlockPos.containing(eye);
            RoomRegistry rooms = RoomRegistry.of(level);
            if (rooms.roomAt(pos) == null && rooms.discover(level, pos) != null) density = FogField.density(level, eye.x, eye.y, eye.z);
        }
        if (!doused && level.random.nextDouble() < settings.chance(density)) {
            DOUSED.add(carrier.getUUID());
            level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
            level.sendParticles(ParticleTypes.SMOKE, eye.x, eye.y - 0.3, eye.z, 6, 0.15, 0.1, 0.15, 0.01);
            return true;
        }
        if (doused && density < settings.blowOutStart()) {
            DOUSED.remove(carrier.getUUID());
            return false;
        }
        return doused;
    }

    private CarriedLights() {}
}
