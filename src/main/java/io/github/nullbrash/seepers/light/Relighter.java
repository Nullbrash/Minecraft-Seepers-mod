package io.github.nullbrash.seepers.light;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Re-checks block light in existing chunks after light values changed ({@code /seepers debug relight}).
 * Processed in batches on the server tick; the light engine itself runs off-thread and syncs clients.
 */
public final class Relighter {
    /** Chunks queued to the light engine per server tick: a debug tool, kept small to avoid lag spikes. */
    private static final int CHUNKS_PER_TICK = 2;
    public static final int MAX_RADIUS = 16;

    // Vanilla emitters (not our value: a block set to 0 must still drop its old light) and water,
    // whose light absorption may have changed.
    private static final Predicate<BlockState> NEEDS_CHECK = state ->
            state.getLightEmission() > 0 || state.hasDynamicLightEmission() || state.getFluidState().is(FluidTags.WATER);

    private static final class Job {
        final ServerLevel level;
        final CommandSourceStack requester;
        final Deque<ChunkPos> pending = new ArrayDeque<>();
        final int chunks;
        int blocks;

        Job(ServerLevel level, CommandSourceStack requester, ChunkPos center, int radius) {
            this.level = level;
            this.requester = requester;
            for (int x = center.x - radius; x <= center.x + radius; x++) {
                for (int z = center.z - radius; z <= center.z + radius; z++) {
                    pending.add(new ChunkPos(x, z));
                }
            }
            this.chunks = pending.size();
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    /** @return number of chunks queued */
    public static int start(CommandSourceStack requester, ServerLevel level, ChunkPos center, int radius) {
        Job job = new Job(level, requester, center, Math.min(radius, MAX_RADIUS));
        JOBS.add(job);
        return job.chunks;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        int budget = CHUNKS_PER_TICK;
        Iterator<Job> jobs = JOBS.iterator();
        while (jobs.hasNext() && budget > 0) {
            Job job = jobs.next();
            LevelLightEngine engine = job.level.getChunkSource().getLightEngine();
            while (budget > 0 && !job.pending.isEmpty()) {
                ChunkPos pos = job.pending.poll();
                LevelChunk chunk = job.level.getChunk(pos.x, pos.z);
                chunk.findBlocks(NEEDS_CHECK, (blockPos, state) -> {
                    engine.checkBlock(blockPos);
                    job.blocks++;
                });
                budget--;
            }
            if (job.pending.isEmpty()) {
                job.requester.sendSuccess(() -> Component.translatable("commands.seepers.debug.relight.done",
                        job.chunks, job.blocks), true);
                jobs.remove();
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    private Relighter() {}
}
