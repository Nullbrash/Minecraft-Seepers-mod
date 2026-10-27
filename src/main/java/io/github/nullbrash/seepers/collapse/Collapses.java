package io.github.nullbrash.seepers.collapse;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.payload.CollapseShakePayload;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Collapses (CLAUDE.md 4.10): a hanging block that is due to fall first asks the props, then warns — dust, a crack,
 * a light shake nearby — for 1–2 s, and only then falls. Blocks just placed and blocks above one that already fell
 * (the same column) fall at once, as in vanilla. Falling sand and gravel hurt; past a per-chunk limit of falling
 * blocks the rest is set straight where it would land.
 */
public final class Collapses {
    /** Blocks may skip the warning this long after being placed or losing the block under them to a collapse. */
    private static final int FRESH_TICKS = 4;
    private static final int DUST_EVERY = 5;
    private static final double SHAKE_RADIUS = 8;

    private static final Map<ServerLevel, Long2LongOpenHashMap> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Long2LongOpenHashMap> FRESH = new WeakHashMap<>();
    private static final Map<ServerLevel, Long2LongOpenHashMap> CRACKS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> SHAKEN = new WeakHashMap<>();

    /**
     * From the falling block's scheduled tick. Returns true when the tick is handled here (held, warning or set
     * in place); false lets vanilla drop the block now.
     */
    public static boolean onFallTick(BlockState state, ServerLevel level, BlockPos pos) {
        if (!ServerConfig.get(ServerConfig.COLLAPSE_ENABLED)) return false;
        if (!FallingBlock.isFree(level.getBlockState(pos.below())) || pos.getY() < level.getMinBuildHeight()) return false;
        long key = pos.asLong();
        Long2LongOpenHashMap pending = PENDING.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        if (Supports.holds(level, pos)) {
            pending.remove(key);
            return true;
        }
        long now = level.getGameTime();
        if (!isFresh(level, key, now)) {
            long due = pending.getOrDefault(key, -1L);
            if (due < 0) {
                int min = ServerConfig.get(ServerConfig.COLLAPSE_WARN_MIN_TICKS);
                int max = Math.max(min, ServerConfig.get(ServerConfig.COLLAPSE_WARN_MAX_TICKS));
                due = now + min + level.random.nextInt(max - min + 1);
                pending.put(key, due);
                warn(level, pos, state);
            }
            if (now < due) {
                // Neighbour updates may tick it early; ticks of one block do not stack, so ask again for the due one.
                level.scheduleTick(pos, state.getBlock(), (int) (due - now));
                return true;
            }
            pending.remove(key);
        }
        markColumnAbove(level, pos, now);
        if (fallingInChunk(level, pos) >= ServerConfig.get(ServerConfig.COLLAPSE_MAX_FALLING_PER_CHUNK)) {
            setWhereItLands(level, pos, state);
            return true;
        }
        return false;
    }

    /** Placed blocks fall without a warning, as in vanilla. */
    public static void onPlace(ServerLevel level, BlockPos pos) {
        FRESH.computeIfAbsent(level, l -> new Long2LongOpenHashMap()).put(pos.asLong(), level.getGameTime());
    }

    /** Falling sand, gravel and the like hurt what they land on (anvils and dripstone keep their own damage). */
    public static void onFalling(FallingBlockEntity entity) {
        entity.setHurtsEntities(ServerConfig.get(ServerConfig.COLLAPSE_DAMAGE_PER_BLOCK).floatValue(),
                ServerConfig.get(ServerConfig.COLLAPSE_DAMAGE_MAX));
    }

    /** Starts a collapse at this spot through the normal path (debug, tremors, the cane). */
    public static boolean trigger(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FallingBlock) || !FallingBlock.isFree(level.getBlockState(pos.below()))) return false;
        level.scheduleTick(pos, state.getBlock(), 1);
        return true;
    }

    public static int pendingCount(ServerLevel level) {
        Long2LongOpenHashMap pending = PENDING.get(level);
        return pending == null ? 0 : pending.size();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % DUST_EVERY != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            Long2LongOpenHashMap pending = PENDING.get(level);
            if (pending != null && !pending.isEmpty()) {
                var it = pending.long2LongEntrySet().fastIterator();
                while (it.hasNext()) {
                    Long2LongMap.Entry entry = it.next();
                    BlockPos pos = BlockPos.of(entry.getLongKey());
                    // Mined, held or long overdue (chunk unloaded): forget it; a fresh tick starts a new warning.
                    if (now > entry.getLongValue() + 100 || !level.isLoaded(pos)) {
                        it.remove();
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof FallingBlock) dust(level, pos, state, 2);
                }
            }
            Long2LongOpenHashMap fresh = FRESH.get(level);
            if (fresh != null) fresh.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > FRESH_TICKS);
            Long2LongOpenHashMap cracks = CRACKS.get(level);
            if (cracks != null) cracks.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > 40);
        }
    }

    private static boolean isFresh(ServerLevel level, long key, long now) {
        Long2LongOpenHashMap fresh = FRESH.get(level);
        if (fresh == null) return false;
        long at = fresh.getOrDefault(key, Long.MIN_VALUE);
        return at != Long.MIN_VALUE && now - at <= FRESH_TICKS;
    }

    /** The column above a block that falls now comes down with it, not layer by layer after fresh warnings. */
    private static void markColumnAbove(ServerLevel level, BlockPos pos, long now) {
        Long2LongOpenHashMap fresh = FRESH.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        BlockPos.MutableBlockPos above = pos.mutable();
        for (int i = 0; i < 64; i++) {
            above.move(0, 1, 0);
            if (!(level.getBlockState(above).getBlock() instanceof FallingBlock)) break;
            fresh.put(above.asLong(), now);
        }
    }

    private static void warn(ServerLevel level, BlockPos pos, BlockState state) {
        dust(level, pos, state, 6);
        long now = level.getGameTime();
        // One crack per section and second: a whole ceiling going at once should not stack hundreds of sounds.
        long section = BlockPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        Long2LongOpenHashMap cracks = CRACKS.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        if (now - cracks.getOrDefault(section, Long.MIN_VALUE / 2) >= 20) {
            cracks.put(section, now);
            level.playSound(null, pos, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.7F, 0.5F + level.random.nextFloat() * 0.2F);
        }
        level.playSound(null, pos, state.getSoundType(level, pos, null).getHitSound(), SoundSource.BLOCKS, 0.4F, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getCenter()) > SHAKE_RADIUS * SHAKE_RADIUS) continue;
            Long last = SHAKEN.get(player);
            if (last != null && now - last < 10) continue;
            SHAKEN.put(player, now);
            PacketDistributor.sendToPlayer(player, new CollapseShakePayload(0.35F, 20));
        }
    }

    private static void dust(ServerLevel level, BlockPos pos, BlockState state, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5, count, 0.3, 0.0, 0.3, 0.0);
    }

    private static int fallingInChunk(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        return level.getEntitiesOfClass(FallingBlockEntity.class, box).size();
    }

    /** Over the limit: no entity, the block is moved straight down to where it would come to rest. */
    private static void setWhereItLands(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos land = pos.mutable();
        while (land.getY() > level.getMinBuildHeight() && FallingBlock.isFree(level.getBlockState(land.below()))) land.move(0, -1, 0);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // Into the void it is simply gone, as a falling entity would be.
        if (!FallingBlock.isFree(level.getBlockState(land.below()))) level.setBlock(land, state, 3);
    }

    private Collapses() {}
}
