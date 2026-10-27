package io.github.nullbrash.seepers.light;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.data.chunk.DynamicLightMarks;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import io.github.nullbrash.seepers.network.payload.DynamicLightPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Dynamic light (CLAUDE.md 4.2): a light carried by an entity is a real, invisible light block the server keeps
 * on it — at the head, else at the feet, else in the nearest free block — so every mechanic that reads block light,
 * other players and any renderer see it. Placed without neighbour updates (walking under hanging gravel must not wake
 * it); marked in the chunk, so a light left behind by a crash is taken out when the chunk loads again.
 * <p>A step that swapped the light in one tick blinked: clients rebuild the meshes around before their light engine has
 * spread the new light, so for a frame or two it was dark. Now the new light goes in first and the old one stays a few
 * ticks longer (a brief overlap is only brighter), and the light stays put until its carrier is more than a block and a
 * half away — half as many steps, and no back-and-forth on a block border.
 */
public final class DynamicLights {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Brightness an entity carries this tick, from every source (held items, helmet lamp, flares...). */
    private static final List<ToIntFunction<Entity>> PROVIDERS = new ArrayList<>();
    private static final Map<UUID, Integer> DEBUG = new HashMap<>();
    private static final Map<UUID, Placed> PLACED = new HashMap<>();
    /** Brightness last told to clients, by carrier. */
    private static final Map<UUID, Integer> TOLD = new HashMap<>();
    /** Old lights left in a little after a step, by carrier; taken out when due. */
    private static final List<Leaving> LEAVING = new ArrayList<>();
    /** Chunks loaded since the last pass: their marks are not readable at the load event, so they are checked next tick. */
    private static final Map<ServerLevel, LongOpenHashSet> LOADED = new WeakHashMap<>();

    private record Placed(ResourceKey<Level> dimension, BlockPos pos, int level, long movedAt) {}

    private record Leaving(UUID owner, ResourceKey<Level> dimension, BlockPos pos, long due) {}

    public static void addProvider(ToIntFunction<Entity> provider) {
        PROVIDERS.add(provider);
    }

    /** Debug: a light of this level on the entity (0 removes it). */
    public static void setDebug(Entity entity, int level) {
        if (level <= 0) DEBUG.remove(entity.getUUID());
        else DEBUG.put(entity.getUUID(), Math.min(15, level));
    }

    public static int brightness(Entity entity) {
        int level = DEBUG.getOrDefault(entity.getUUID(), 0);
        for (ToIntFunction<Entity> provider : PROVIDERS) level = Math.max(level, provider.applyAsInt(entity));
        return Math.min(15, level);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        cleanLoaded(server);
        takeOutLeaving(server, false);
        boolean enabled = ServerConfig.get(ServerConfig.DYNAMIC_LIGHT_ENABLED);
        Map<UUID, Entity> carriers = new HashMap<>();
        if (enabled) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSpectator() && player.isAlive()) carriers.put(player.getUUID(), player);
            }
        }
        // Gone, dead, spectating, in another dimension or switched off: take the light out first.
        Iterator<Map.Entry<UUID, Placed>> it = PLACED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Placed> entry = it.next();
            Entity carrier = carriers.get(entry.getKey());
            if (carrier == null || carrier.level().dimension() != entry.getValue().dimension()) {
                remove(server, entry.getValue());
                it.remove();
            }
        }
        int budget = ServerConfig.get(ServerConfig.DYNAMIC_LIGHT_MAX_SOURCES);
        for (Entity carrier : carriers.values()) {
            int level = budget > 0 ? brightness(carrier) : 0;
            tell(carrier, level);
            if (budget <= 0) continue;
            if (update((ServerLevel) carrier.level(), carrier)) budget--;
        }
        TOLD.keySet().retainAll(carriers.keySet());
    }

    /** Clients draw a carrier's light from this: sent when it changes, to everyone tracking the carrier. */
    private static void tell(Entity carrier, int level) {
        Integer told = TOLD.get(carrier.getUUID());
        if (told != null && told == level) return;
        if (told == null && level == 0) return;
        TOLD.put(carrier.getUUID(), level);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(carrier, new DynamicLightPayload(carrier.getId(), level));
    }

    /** A player who starts seeing a carrier learns its light. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Integer level = TOLD.get(event.getTarget().getUUID());
        if (level != null && level > 0 && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new DynamicLightPayload(event.getTarget().getId(), level));
        }
    }

    /** Returns whether the entity carries a light now. */
    private static boolean update(ServerLevel level, Entity entity) {
        UUID id = entity.getUUID();
        Placed placed = PLACED.get(id);
        int want = brightness(entity);
        if (want <= 0) {
            if (placed != null) {
                remove(level.getServer(), placed);
                PLACED.remove(id);
            }
            return false;
        }
        long now = level.getGameTime();
        BlockPos target = choose(level, entity, placed);
        if (target == null) {
            if (placed != null) {
                remove(level.getServer(), placed);
                PLACED.remove(id);
            }
            return false;
        }
        if (placed != null && placed.level() == want && (placed.pos().equals(target) || stays(level, entity, placed))) return true;
        if (placed != null && now - placed.movedAt() < ServerConfig.get(ServerConfig.DYNAMIC_LIGHT_MIN_MOVE_TICKS)) return true;
        LEAVING.removeIf(leaving -> leaving.owner().equals(id) && leaving.pos().equals(target) && leaving.dimension() == level.dimension());
        place(level, target, want);
        if (placed != null && !placed.pos().equals(target)) {
            LEAVING.add(new Leaving(id, placed.dimension(), placed.pos(), now + ServerConfig.get(ServerConfig.DYNAMIC_LIGHT_OVERLAP_TICKS)));
        }
        PLACED.put(id, new Placed(level.dimension(), target, want, now));
        return true;
    }

    /** The light may stay where it is: still there and the carrier's eyes within the keep distance of it. */
    private static boolean stays(ServerLevel level, Entity entity, Placed placed) {
        if (!isOurs(level.getBlockState(placed.pos()))) return false;
        double keep = ServerConfig.get(ServerConfig.DYNAMIC_LIGHT_KEEP_DISTANCE);
        return placed.pos().getCenter().distanceToSqr(entity.getEyePosition()) <= keep * keep;
    }

    private static boolean isOurs(BlockState state) {
        return state.is(SeepersBlocks.DYNAMIC_LIGHT.get());
    }

    private static void takeOutLeaving(MinecraftServer server, boolean all) {
        Iterator<Leaving> it = LEAVING.iterator();
        while (it.hasNext()) {
            Leaving leaving = it.next();
            ServerLevel level = server.getLevel(leaving.dimension());
            if (!all && level != null && level.getGameTime() < leaving.due()) continue;
            it.remove();
            if (level != null && level.isLoaded(leaving.pos()) && !carried(level, leaving.pos())) takeOut(level, leaving.pos());
        }
    }

    /** At the head, else at the feet, else the nearest free block around the head. */
    private static @Nullable BlockPos choose(ServerLevel level, Entity entity, @Nullable Placed placed) {
        BlockPos head = BlockPos.containing(entity.getEyePosition());
        if (free(level, head, placed)) return head;
        BlockPos feet = entity.blockPosition();
        if (free(level, feet, placed)) return feet;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(head.offset(-1, -1, -1), head.offset(1, 1, 1))) {
            if (!free(level, pos, placed)) continue;
            double distance = pos.getCenter().distanceToSqr(entity.getEyePosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /**
     * Air or still water, or a light this carrier has there (its current one, or an old one still on its way out);
     * never another carrier's or a map maker's light.
     */
    private static boolean free(ServerLevel level, BlockPos pos, @Nullable Placed placed) {
        if (!level.isInWorldBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (isOurs(state)) return placed != null && (placed.pos().equals(pos) || leavingOf(placed, pos));
        if (state.is(Blocks.LIGHT)) return false;
        if (state.isAir()) return true;
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private static void place(ServerLevel level, BlockPos pos, int brightness) {
        BlockState here = level.getBlockState(pos);
        boolean water = here.is(Blocks.WATER) || (isOurs(here) && here.getValue(BlockStateProperties.WATERLOGGED));
        level.setBlock(pos, SeepersBlocks.DYNAMIC_LIGHT.get().defaultBlockState().setValue(LightBlock.LEVEL, brightness)
                .setValue(BlockStateProperties.WATERLOGGED, water), UPDATE_FLAGS);
        level.getChunkAt(pos).getData(SeepersAttachments.DYNAMIC_LIGHT_MARKS).add(pos);
    }

    private static void remove(MinecraftServer server, Placed placed) {
        ServerLevel level = server.getLevel(placed.dimension());
        if (level == null || !level.isLoaded(placed.pos())) return; // unloaded: its mark takes it out on the next load
        takeOut(level, placed.pos());
    }

    /** Takes out a light this mechanic put there (only while it is still a light block). */
    private static void takeOut(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Vanilla light blocks only from marks of an older build (before our own block existed).
        if (isOurs(state) || state.is(Blocks.LIGHT)) {
            boolean water = state.getValue(BlockStateProperties.WATERLOGGED);
            level.setBlock(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }
        DynamicLightMarks marks = level.getChunkAt(pos).getExistingDataOrNull(SeepersAttachments.DYNAMIC_LIGHT_MARKS.get());
        if (marks != null) marks.remove(pos);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.computeIfAbsent(level, l -> new LongOpenHashSet()).add(chunk.getPos().toLong());
        }
    }

    /** Lights marked in a chunk that just loaded were left behind (no carrier is holding them): take them out. */
    private static void cleanLoaded(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LongOpenHashSet loaded = LOADED.get(level);
            if (loaded == null || loaded.isEmpty()) continue;
            for (long packed : loaded.toLongArray()) {
                ChunkPos pos = new ChunkPos(packed);
                loaded.remove(packed);
                if (!level.hasChunk(pos.x, pos.z)) continue;
                DynamicLightMarks marks = level.getChunk(pos.x, pos.z).getExistingDataOrNull(SeepersAttachments.DYNAMIC_LIGHT_MARKS.get());
                if (marks == null || marks.isEmpty()) continue;
                for (BlockPos mark : marks.all()) {
                    if (!held(level, mark)) takeOut(level, mark);
                }
            }
        }
    }

    private static boolean leavingOf(Placed placed, BlockPos pos) {
        for (Leaving leaving : LEAVING) {
            if (leaving.pos().equals(pos) && leaving.dimension() == placed.dimension() && ownerOf(placed) != null
                    && leaving.owner().equals(ownerOf(placed))) return true;
        }
        return false;
    }

    private static @Nullable UUID ownerOf(Placed placed) {
        for (Map.Entry<UUID, Placed> entry : PLACED.entrySet()) if (entry.getValue() == placed) return entry.getKey();
        return null;
    }

    /** A carrier's current light is there (an old light being taken out must not take a new one with it). */
    private static boolean carried(ServerLevel level, BlockPos pos) {
        for (Placed placed : PLACED.values()) {
            if (placed.dimension() == level.dimension() && placed.pos().equals(pos)) return true;
        }
        return false;
    }

    private static boolean held(ServerLevel level, BlockPos pos) {
        for (Placed placed : PLACED.values()) {
            if (placed.dimension() == level.dimension() && placed.pos().equals(pos)) return true;
        }
        for (Leaving leaving : LEAVING) {
            if (leaving.dimension() == level.dimension() && leaving.pos().equals(pos)) return true;
        }
        return false;
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        takeOutLeaving(event.getServer(), true);
        for (Placed placed : PLACED.values()) remove(event.getServer(), placed);
        PLACED.clear();
        DEBUG.clear();
    }

    /** For the debug command. */
    public static List<String> describe() {
        List<String> lines = new ArrayList<>();
        PLACED.forEach((id, placed) -> lines.add(id.toString().substring(0, 8) + " light " + placed.level() + " at "
                + placed.pos().toShortString() + " in " + placed.dimension().location()));
        return lines;
    }

    private DynamicLights() {}
}
