package io.github.nullbrash.seepers.collapse;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.payload.CollapseShakePayload;
import io.github.nullbrash.seepers.network.payload.TremorRumblePayload;
import io.github.nullbrash.seepers.world.Distortion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import io.github.nullbrash.seepers.registry.SeepersTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tremors (CLAUDE.md 4.10): around each player now and then — rarely near spawn, often deep in distortion. One deep
 * rumble swells for a few seconds — that is the warning — and at its loudest the shake starts and a share of the
 * hanging blocks around comes down at once (props hold; neighbours of the fallen follow by chance, with the usual
 * warning). The volume is scanned while the rumble swells, skipping chunk sections with nothing that falls.
 */
public final class Tremors {
    /** The rumble (sounds/tremor/rumble.ogg, 9 s) peaks at 4.2 s: blocks drop right then. */
    private static final int PEAK_TICKS = 84;
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Tremor> ACTIVE = new HashMap<>();

    /** One tremor: while the rumble swells the scan picks blocks slab by slab; at {@code shakeAt} they all drop. */
    private static final class Tremor {
        final ServerLevel level;
        final BlockPos center;
        final int radius;
        final double share;
        final float strength;
        final long shakeAt;
        final int shakeTicks;
        final List<BlockPos> picked = new ArrayList<>();
        int nextY;

        Tremor(ServerLevel level, BlockPos center, int radius, double share, float strength, long shakeAt, int shakeTicks) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.share = share;
            this.strength = strength;
            this.shakeAt = shakeAt;
            this.shakeTicks = shakeTicks;
            this.nextY = center.getY() - radius;
        }
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) schedule(player);
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT.remove(event.getEntity().getUUID());
        ACTIVE.remove(event.getEntity().getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        boolean enabled = ServerConfig.get(ServerConfig.COLLAPSE_ENABLED) && ServerConfig.get(ServerConfig.TREMOR_ENABLED);
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                Long next = NEXT.get(player.getUUID());
                if (next == null) {
                    schedule(player);
                } else if (enabled && level.getGameTime() >= next && !ACTIVE.containsKey(player.getUUID())) {
                    start(player);
                }
            }
        }
        Iterator<Map.Entry<UUID, Tremor>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Tremor tremor = it.next().getValue();
            if (step(tremor)) it.remove();
        }
    }

    /** Starts a tremor around the player now (debug, or when it is due). */
    public static void start(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double t = strength(player);
        int radius = (int) Math.round(Mth.lerp(t, ServerConfig.get(ServerConfig.TREMOR_RADIUS_NEAR), ServerConfig.get(ServerConfig.TREMOR_RADIUS_FAR)));
        double share = Mth.lerp(t, ServerConfig.get(ServerConfig.TREMOR_SHARE_NEAR), ServerConfig.get(ServerConfig.TREMOR_SHARE_FAR));
        int rumble = PEAK_TICKS;
        int shake = between(level, ServerConfig.get(ServerConfig.TREMOR_SHAKE_MIN_TICKS), ServerConfig.get(ServerConfig.TREMOR_SHAKE_MAX_TICKS));
        float strength = (float) Mth.lerp(t, 0.6, 1.5);
        Tremor tremor = new Tremor(level, player.blockPosition(), radius, share, strength, level.getGameTime() + rumble, shake);
        ACTIVE.put(player.getUUID(), tremor);
        // One rumble for the whole tremor, felt by everyone within the radius; each client plays it at its own volume.
        for (ServerPlayer other : level.players()) {
            if (other.blockPosition().closerThan(tremor.center, radius)) {
                PacketDistributor.sendToPlayer(other, new TremorRumblePayload());
                PacketDistributor.sendToPlayer(other, new CollapseShakePayload(0.15F, rumble));
            }
        }
        schedule(player);
    }

    /** Ticks until the next tremor for this player, or -1. */
    public static long ticksUntil(ServerPlayer player) {
        Long next = NEXT.get(player.getUUID());
        return next == null ? -1 : next - player.serverLevel().getGameTime();
    }

    private static void schedule(ServerPlayer player) {
        double t = strength(player);
        double min = Mth.lerp(t, ServerConfig.get(ServerConfig.TREMOR_INTERVAL_NEAR_MIN), ServerConfig.get(ServerConfig.TREMOR_INTERVAL_FAR_MIN));
        double max = Mth.lerp(t, ServerConfig.get(ServerConfig.TREMOR_INTERVAL_NEAR_MAX), ServerConfig.get(ServerConfig.TREMOR_INTERVAL_FAR_MAX));
        double minutes = min + player.getRandom().nextDouble() * Math.max(0, max - min);
        NEXT.put(player.getUUID(), player.serverLevel().getGameTime() + Math.round(minutes * 60 * 20));
    }

    /** 0 near spawn (zone 0) to 1 from zone 3 on, linear in distortion. */
    private static double strength(ServerPlayer player) {
        return Mth.clamp(Distortion.level(player.getX(), player.getZ()) / 3.0, 0, 1);
    }

    private static int between(ServerLevel level, int min, int max) {
        return min + level.random.nextInt(Math.max(1, max - min + 1));
    }

    /** Advances a tremor by one tick; true when it is over. */
    private static boolean step(Tremor tremor) {
        ServerLevel level = tremor.level;
        int top = tremor.center.getY() + tremor.radius;
        // Spread the scan over the swell so it is done by the peak.
        int perTick = Math.max(1, Mth.ceil((2.0 * tremor.radius + 1) / PEAK_TICKS));
        for (int i = 0; i < perTick && tremor.nextY <= top; i++, tremor.nextY++) sweepSlab(tremor, tremor.nextY);
        if (level.getGameTime() < tremor.shakeAt) return false;
        while (tremor.nextY <= top) sweepSlab(tremor, tremor.nextY++);
        for (ServerPlayer other : level.players()) {
            if (other.blockPosition().closerThan(tremor.center, tremor.radius)) {
                PacketDistributor.sendToPlayer(other, new CollapseShakePayload(tremor.strength, tremor.shakeTicks));
            }
        }
        for (BlockPos pos : tremor.picked) Collapses.drop(level, pos);
        return true;
    }

    private static void sweepSlab(Tremor tremor, int y) {
        ServerLevel level = tremor.level;
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return;
        int r = tremor.radius;
        int cx = tremor.center.getX();
        int cz = tremor.center.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = (cx - r) >> 4; chunkX <= (cx + r) >> 4; chunkX++) {
            for (int chunkZ = (cz - r) >> 4; chunkZ <= (cz + r) >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                if (section.hasOnlyAir() || !section.maybeHas(Tremors::mayFall)) continue;
                int x0 = Math.max(cx - r, chunkX << 4);
                int x1 = Math.min(cx + r, (chunkX << 4) + 15);
                int z0 = Math.max(cz - r, chunkZ << 4);
                int z1 = Math.min(cz + r, (chunkZ << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        if (!mayFall(state)) continue;
                        pos.set(x, y, z);
                        if (!Collapses.isFallable(level, pos, state) || !FallingBlock.isFree(level.getBlockState(pos.below()))) continue;
                        if (level.random.nextDouble() < tremor.share) tremor.picked.add(pos.immutable());
                    }
                }
            }
        }
    }

    /** Cheap test before the biome one: could this state fall at all. */
    private static boolean mayFall(BlockState state) {
        return state.getBlock() instanceof FallingBlock || state.is(SeepersTags.Blocks.BROKEN_GRAVITY_UNSTABLE);
    }

    private Tremors() {}
}
