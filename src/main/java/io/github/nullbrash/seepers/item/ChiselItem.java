package io.github.nullbrash.seepers.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.data.SeepersData;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Chisel (CLAUDE.md 5.4): hold it on a block. Anomalous rock becomes hewn — it never regrows and feeds no regrowth,
 * gives no shards; ordinary blocks turn into their polished or chiseled form (data map {@code seepers:chisel}).
 */
public final class ChiselItem extends Item {
    public record Result(Block result) {
        static final Codec<Result> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("result").forGetter(Result::result)).apply(instance, Result::new));
    }

    public static final DataMapType<Block, Result> RESULTS = DataMapType.builder(Seepers.id("chisel"), Registries.BLOCK, Result.CODEC)
            .synced(Result.CODEC, false).build();

    private static final int MAX_USE = 72_000;
    /** Server side: what each player is hewing now and since when (looking at another block restarts). */
    private static final Map<UUID, BlockPos> TARGETS = new HashMap<>();

    public ChiselItem(Properties properties) {
        super(properties);
    }

    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(RESULTS);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || target(context.getLevel().getBlockState(context.getClickedPos())) == null) return InteractionResult.PASS;
        player.startUsingItem(context.getHand());
        if (!context.getLevel().isClientSide) TARGETS.put(player.getUUID(), context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        BlockPos pos = lookedAt(level, player);
        BlockPos started = TARGETS.get(player.getUUID());
        if (pos == null || !pos.equals(started)) {
            // Moved to another block: start over there (or stop when looking at nothing usable).
            if (pos == null || target(level.getBlockState(pos)) == null) {
                player.stopUsingItem();
                TARGETS.remove(player.getUUID());
                return;
            }
            TARGETS.put(player.getUUID(), pos);
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState result = target(state);
        if (result == null) return;
        int used = MAX_USE - remaining;
        boolean rock = state.is(SeepersBlocks.ANOMALY_ROCK.get());
        double seconds = rock ? ServerConfig.get(ServerConfig.CHISEL_ROCK_SECONDS) : ServerConfig.get(ServerConfig.CHISEL_BLOCK_SECONDS);
        if (used % 5 == 0) level.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.5F, rock ? 0.6F : 1.0F);
        if (used < Math.round(seconds * 20)) return;
        level.setBlockAndUpdate(pos, result);
        if (rock) SeepersData.anomalyBlocks(level.getChunkAt(pos)).markChiseled(pos);
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, rock ? 0.7F : 1.2F);
        stack.hurtAndBreak(1, player, player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.stopUsingItem();
        TARGETS.remove(player.getUUID());
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        TARGETS.remove(entity.getUUID());
    }

    /** What the chisel makes of this block, or null. */
    private static @Nullable BlockState target(BlockState state) {
        if (state.is(SeepersBlocks.ANOMALY_ROCK.get())) {
            return state.getValue(AnomalyRockBlock.KIND) == AnomalyRockBlock.Kind.HEWN ? null
                    : state.setValue(AnomalyRockBlock.KIND, AnomalyRockBlock.Kind.HEWN);
        }
        Result result = state.getBlockHolder().getData(RESULTS);
        return result == null ? null : result.result().withPropertiesOf(state);
    }

    private static @Nullable BlockPos lookedAt(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.IRON_INGOT);
    }
}
