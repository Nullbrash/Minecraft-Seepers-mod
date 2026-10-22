package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.data.SeepersData;
import io.github.nullbrash.seepers.data.chunk.AnomalyBlocks;
import io.github.nullbrash.seepers.data.chunk.FireSources;
import io.github.nullbrash.seepers.data.chunk.PlayerPlacedBlocks;
import io.github.nullbrash.seepers.data.world.BaseCopyData;
import io.github.nullbrash.seepers.data.world.ChunkActivityData;
import io.github.nullbrash.seepers.data.world.LidarPointData;
import io.github.nullbrash.seepers.data.world.LifeData;
import io.github.nullbrash.seepers.data.world.RemainsData;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * {@code /seepers debug storage [test]}: shows what the section-13 stores hold around the command source
 * and writes one test record into each — to verify save/load across restarts.
 */
final class StorageDebugCommand {
    /** Stands in for a player when the command runs from the server console. */
    private static final UUID CONSOLE_ID = UUID.nameUUIDFromBytes("seepers:console".getBytes(StandardCharsets.UTF_8));

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("storage")
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("test").executes(context -> writeTest(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();
        BlockPos pos = BlockPos.containing(source.getPosition());
        LevelChunk chunk = level.getChunkAt(pos);
        UUID owner = ownerOf(source);

        FireSources fire = SeepersData.fireSources(chunk);
        AnomalyBlocks anomaly = SeepersData.anomalyBlocks(chunk);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.storage.chunk",
                chunk.getPos().toString(), SeepersData.playerPlacedBlocks(chunk).size(), fire.size(),
                fire.lastCheckedTime(), anomaly.blastedCount(), anomaly.chiseledCount()), false);

        ChunkActivityData activity = SeepersData.chunkActivity(level);
        LidarPointData lidar = SeepersData.lidarPoints(level);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.storage.dimension",
                level.dimension().location().toString(), activity.size(), activity.score(chunk.getPos()),
                lidar.size(), lidar.sourceCount()), false);

        LifeData lives = SeepersData.lives(server);
        LifeData.Life current = lives.current(owner);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.storage.global",
                SeepersData.baseCopies(server).copies().size(), SeepersData.remains(server).all().size(),
                lives.playerCount(), lives.history(owner).size(), current == null ? "-" : current.tag()), false);
        return 1;
    }

    private static int writeTest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();
        BlockPos pos = BlockPos.containing(source.getPosition());
        LevelChunk chunk = level.getChunkAt(pos);
        UUID owner = ownerOf(source);
        long now = level.getGameTime();

        PlayerPlacedBlocks placed = SeepersData.playerPlacedBlocks(chunk);
        placed.add(pos.below(1 + placed.size()));
        FireSources fire = SeepersData.fireSources(chunk);
        fire.put(pos.above(fire.size()), FireSources.Kind.TORCH);
        fire.setLastCheckedTime(now);
        AnomalyBlocks anomaly = SeepersData.anomalyBlocks(chunk);
        anomaly.markBlasted(pos.below(10 + anomaly.blastedCount()), now);
        anomaly.markChiseled(pos.below(20 + anomaly.chiseledCount()));

        SeepersData.chunkActivity(level).add(chunk.getPos(), 1);
        SeepersData.lidarPoints(level).add(pos, owner, now);

        SeepersData.baseCopies(server).add(new BaseCopyData.FullCopy(level.dimension(), pos, now));
        LifeData lives = SeepersData.lives(server);
        LifeData.Life life = lives.startNewLife(owner, HexFormat.of().toHexDigits((short) level.getRandom().nextInt()), now);
        SeepersData.remains(server).put(new RemainsData.Remains(UUID.randomUUID(), owner, life.index(),
                level.dimension(), pos, now, RemainsData.Stage.CORPSE));

        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.storage.test", pos.toShortString()), true);
        return show(source);
    }

    private static UUID ownerOf(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE_ID;
    }

    private StorageDebugCommand() {}
}
