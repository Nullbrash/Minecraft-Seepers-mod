package io.github.nullbrash.seepers.fire;

import io.github.nullbrash.seepers.data.chunk.FireSources;
import io.github.nullbrash.seepers.fog.FogDensity;
import io.github.nullbrash.seepers.fog.FogField;
import io.github.nullbrash.seepers.fog.FogNoise;
import io.github.nullbrash.seepers.fog.FogSettings;
import io.github.nullbrash.seepers.fog.StormSchedule;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import io.github.nullbrash.seepers.registry.SeepersTags;
import io.github.nullbrash.seepers.room.RoomRegistry;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Fog blows out fires (CLAUDE.md 4.6). Each chunk's {@link FireSources} follows its blocks: block changes,
 * plus a reconciliation on load that also registers fires placed by world generation. Fires in block-ticking
 * chunks are checked every checkSeconds against the density field (heat and rooms included) within a per-tick
 * budget; a chunk seen again after a gap catches up on the fog it missed.
 */
public final class FireTracker {
    /** Fog samples spread over a gap when catching up; storms inside the gap are sampled on top. */
    private static final int CATCH_UP_SAMPLES = 32;
    private static final int CATCH_UP_MAX_STORMS = 16;
    /** Room flood fills are the expensive part of a check. */
    private static final int ROOM_FILLS_PER_TICK = 2;
    private static final Map<ServerLevel, LevelFires> LEVELS = new WeakHashMap<>();

    private record Check(long pos, long catchUpFrom) {}

    private record Out(long pos, long due) {}

    private static final class LevelFires {
        final LongOpenHashSet chunks = new LongOpenHashSet();
        final ArrayDeque<Check> queue = new ArrayDeque<>();
        final LongOpenHashSet queued = new LongOpenHashSet();
        final List<Out> pending = new ArrayList<>();
        final LongOpenHashSet pendingPositions = new LongOpenHashSet();
    }

    private static final class Budget {
        int samples;
        int fills;
    }

    public record Inspection(FireSources.Kind kind, boolean burning, double density, double chance,
                             int chunkFires, long sinceCheck) {}

    public static @Nullable FireSources.Kind kindOf(BlockState state) {
        if (state.is(SeepersTags.Blocks.FIRE_BLOW_OUT)) return FireSources.Kind.TORCH;
        if (state.is(SeepersTags.Blocks.FIRE_BREAK)) return FireSources.Kind.CAMPFIRE;
        if (state.is(SeepersTags.Blocks.FIRE_EXTINGUISH) && state.hasProperty(BlockStateProperties.LIT)) {
            return FireSources.Kind.CANDLE;
        }
        return null;
    }

    /** Unlit campfires and candles and powered redstone torches stay registered but are not checked. */
    public static boolean isBurning(BlockState state) {
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        LevelChunk chunk = level.getChunkAt(pos);
        FireSources.Kind kind = kindOf(event.getState());
        if (kind != null) {
            FireSources sources = chunk.getData(SeepersAttachments.FIRE_SOURCES);
            if (sources.get(pos) == kind) return;
            // A chunk's first fire: an old check time would make it catch up on fog from before it existed.
            if (sources.size() == 0) sources.setLastCheckedTime(level.getGameTime());
            sources.put(pos, kind);
            of(level).chunks.add(chunk.getPos().toLong());
        } else {
            FireSources sources = chunk.getExistingDataOrNull(SeepersAttachments.FIRE_SOURCES.get());
            if (sources != null) sources.remove(pos);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        List<BlockPos> found = new ArrayList<>();
        chunk.findBlocks(state -> kindOf(state) != null, (pos, state) -> found.add(pos.immutable()));
        FireSources existing = chunk.getExistingDataOrNull(SeepersAttachments.FIRE_SOURCES.get());
        if (found.isEmpty() && existing == null) return;

        FireSources sources = chunk.getData(SeepersAttachments.FIRE_SOURCES);
        List<BlockPos> stale = new ArrayList<>();
        sources.forEach((pos, kind) -> {
            if (kindOf(chunk.getBlockState(pos)) != kind) stale.add(pos); // changed while we could not see it
        });
        stale.forEach(sources::remove);
        // New chunk (generated fires) or one without fires: nothing to catch up on.
        if (sources.size() == 0 && !found.isEmpty()) sources.setLastCheckedTime(level.getGameTime());
        for (BlockPos pos : found) {
            FireSources.Kind kind = kindOf(chunk.getBlockState(pos));
            if (sources.get(pos) != kind) sources.put(pos, kind);
        }
        if (sources.size() > 0) of(level).chunks.add(chunk.getPos().toLong());
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelFires fires = LEVELS.get(level);
            if (fires != null) fires.chunks.remove(event.getChunk().getPos().toLong());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            LevelFires fires = LEVELS.get(level);
            if (fires != null) tick(level, fires);
        }
    }

    /** For the debug command: the fire at pos as the next check would see it, or null if none is registered. */
    public static @Nullable Inspection inspect(ServerLevel level, BlockPos pos) {
        FireSources sources = level.getChunkAt(pos).getExistingDataOrNull(SeepersAttachments.FIRE_SOURCES.get());
        FireSources.Kind kind = sources == null ? null : sources.get(pos);
        if (kind == null) return null;
        RoomRegistry.of(level).discover(level, pos);
        double density = FogField.density(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        long last = sources.lastCheckedTime();
        return new Inspection(kind, isBurning(level.getBlockState(pos)), density, FireSettings.current().chance(density),
                sources.size(), last < 0 ? -1 : level.getGameTime() - last);
    }

    private static LevelFires of(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new LevelFires());
    }

    private static void tick(ServerLevel level, LevelFires fires) {
        FireSettings settings = FireSettings.current();
        long now = level.getGameTime();
        int interval = settings.checkTicks();
        enqueueDueChunks(level, fires, now, interval);

        Budget budget = new Budget();
        budget.samples = settings.maxChecksPerTick();
        budget.fills = ROOM_FILLS_PER_TICK;
        FogDensity.Context context = null;
        while (budget.samples > 0 && !fires.queue.isEmpty()) {
            if (context == null) context = FogField.context(level, FogField.serverSeed());
            Check check = fires.queue.peek();
            if (!check(level, fires, check, context, settings, budget, now)) break; // out of room fills
            fires.queue.poll();
            fires.queued.remove(check.pos());
        }

        if (!fires.pending.isEmpty()) {
            fires.pending.removeIf(out -> {
                if (out.due() > now) return false;
                fires.pendingPositions.remove(out.pos());
                putOut(level, BlockPos.of(out.pos()));
                return true;
            });
        }
    }

    /** Every chunk is due once per interval, each at its own tick, so checks spread evenly. */
    private static void enqueueDueChunks(ServerLevel level, LevelFires fires, long now, int interval) {
        int slot = (int) Math.floorMod(now, interval);
        var it = fires.chunks.iterator();
        while (it.hasNext()) {
            long chunkKey = it.nextLong();
            if (Math.floorMod(HashCommon.mix(chunkKey), interval) != slot || !level.shouldTickBlocksAt(chunkKey)) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            FireSources sources = chunk == null ? null : chunk.getExistingDataOrNull(SeepersAttachments.FIRE_SOURCES.get());
            if (sources == null || sources.size() == 0) {
                it.remove();
                continue;
            }
            long last = sources.lastCheckedTime();
            long catchUpFrom = last >= 0 && now - last > 2L * interval ? last : -1;
            sources.setLastCheckedTime(now);
            sources.forEach((pos, kind) -> {
                long key = pos.asLong();
                if (fires.queued.add(key)) fires.queue.add(new Check(key, catchUpFrom));
            });
        }
    }

    /** False if the check needs a room fill and none is left this tick (it stays queued). */
    private static boolean check(ServerLevel level, LevelFires fires, Check check, FogDensity.Context context,
                                 FireSettings settings, Budget budget, long now) {
        BlockPos pos = BlockPos.of(check.pos());
        if (!level.isLoaded(pos)) return true;
        BlockState state = level.getBlockState(pos);
        FireSources.Kind kind = kindOf(state);
        if (kind == null) {
            FireSources sources = level.getChunkAt(pos).getExistingDataOrNull(SeepersAttachments.FIRE_SOURCES.get());
            if (sources != null) sources.remove(pos);
            return true;
        }
        if (!isBurning(state) || fires.pendingPositions.contains(check.pos())) return true;

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        boolean catchUp = check.catchUpFrom() >= 0;
        double density = FogField.density(context, x, y, z);
        budget.samples--;
        RoomRegistry rooms = RoomRegistry.of(level);
        if (rooms.roomAt(pos) == null && (catchUp || settings.chance(density) > 0)) {
            if (rooms.needsFill(level, pos)) {
                if (budget.fills <= 0) return false;
                budget.fills--;
            }
            if (rooms.discover(level, pos) != null) {
                density = FogField.density(context, x, y, z);
                budget.samples--;
            }
        }

        boolean out;
        if (catchUp) {
            out = level.getRandom().nextDouble() >= survival(level, x, y, z, check.catchUpFrom(), now, settings, budget);
        } else {
            double chance = settings.chance(density);
            out = chance > 0 && level.getRandom().nextDouble() < chance;
        }
        if (out && fires.pendingPositions.add(check.pos())) {
            // A random delay within the interval: fires near each other go out one by one, not all at once.
            fires.pending.add(new Out(check.pos(), now + level.getRandom().nextInt(settings.checkTicks())));
        }
        return true;
    }

    /**
     * Chance that a fire survived the fog between two game times with a check every interval. Heat and rooms are
     * taken as they are now; the base fog (layers, waves, storms) as it was then. Weather is the current one.
     */
    private static double survival(ServerLevel level, double x, double y, double z, long fromGameTime, long now,
                                   FireSettings settings, Budget budget) {
        long fogSeed = FogField.serverSeed();
        double elapsed = now - fromGameTime;
        double dayNow = level.getDayTime();
        // Fog moves with day time: with the daylight cycle off it stood still the whole time.
        double dayFrom = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? dayNow - elapsed : dayNow;
        double checks = elapsed / settings.checkTicks();
        int samples = (int) Math.min(CATCH_UP_SAMPLES, Math.max(1, Math.ceil(checks)));
        double logSurvival = 0;
        for (int i = 0; i < samples; i++) {
            double t = dayFrom + (i + 0.5) * (dayNow - dayFrom) / samples;
            budget.samples--;
            double chance = settings.chance(FogField.density(FogField.context(level, fogSeed, t), x, y, z));
            if (chance >= 1) return 0;
            logSurvival += checks / samples * Math.log1p(-chance);
        }
        // Storms last minutes: sparse samples over a long gap could step over them.
        FogNoise noise = FogNoise.of(fogSeed, level.dimension());
        FogSettings fog = FogSettings.current();
        StormSchedule.Storm storm = StormSchedule.next(noise, fog, dayFrom);
        for (int n = 0; n < CATCH_UP_MAX_STORMS && storm.start() < dayNow; n++) {
            double start = Math.max(storm.start(), dayFrom);
            double end = Math.min(storm.end(), dayNow);
            if (end > start) {
                budget.samples--;
                double chance = settings.chance(FogField.density(FogField.context(level, fogSeed, (start + end) / 2), x, y, z));
                if (chance >= 1) return 0;
                logSurvival += (end - start) / settings.checkTicks() * Math.log1p(-chance);
            }
            storm = StormSchedule.next(noise, fog, storm.end() + 1);
        }
        return Math.exp(logSurvival);
    }

    private static void putOut(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        FireSources.Kind kind = kindOf(state);
        if (kind == null || !isBurning(state)) return;
        switch (kind) {
            case TORCH, CAMPFIRE -> level.destroyBlock(pos, true);
            case CANDLE -> {
                if (state.getBlock() instanceof AbstractCandleBlock) {
                    AbstractCandleBlock.extinguish(null, state, level, pos);
                } else {
                    level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 11);
                }
            }
        }
    }

    private FireTracker() {}
}
