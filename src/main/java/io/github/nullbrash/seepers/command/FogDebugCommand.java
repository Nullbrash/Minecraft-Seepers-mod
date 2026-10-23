package io.github.nullbrash.seepers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.nullbrash.seepers.fog.FogDensity;
import io.github.nullbrash.seepers.fog.FogField;
import io.github.nullbrash.seepers.fog.FogNoise;
import io.github.nullbrash.seepers.fog.FogSettings;
import io.github.nullbrash.seepers.fog.StormSchedule;
import io.github.nullbrash.seepers.network.FogProbeTracker;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** {@code /seepers debug fog [pos]} — density by component; {@code /seepers debug fog bench} — cost per sample. */
final class FogDebugCommand {
    private static final int BENCH_SAMPLES = 20000;

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fog")
                .executes(context -> show(context.getSource(), BlockPos.containing(context.getSource().getPosition())))
                .then(Commands.literal("bench").executes(context -> bench(context.getSource())))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> show(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"))));
    }

    private static int show(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        FogSettings settings = FogSettings.current();
        FogNoise noise = FogNoise.of(FogField.serverSeed(), level.dimension());
        FogDensity.Context context = FogDensity.Context.of(level, noise);
        FogDensity.Sample s = FogDensity.sample(context, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double total = FogField.density(context, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.total",
                pos.toShortString(), f(total), f(settings.visibility(total))), false);
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.parts",
                f(s.base()), f(s.layers()), f(s.wave()), f(s.weather()), f(s.water()), f(s.distortion()),
                f(s.pocketFactor()), f(s.storm())), false);

        double now = level.getDayTime();
        StormSchedule.Storm storm = StormSchedule.next(noise, settings, now);
        if (now >= storm.start()) {
            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.storm_now",
                    minutes(storm.end() - now)), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.storm_next",
                    minutes(storm.start() - now), minutes(storm.end() - storm.start()), (long) storm.start()), false);
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            FogProbeTracker.send(source, player, pos);
        }
        return (int) Math.round(total * 100);
    }

    private static int bench(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        double sink = 0;
        long started = System.nanoTime();
        // Same shape as the client grid update: one context per batch.
        FogDensity.Context context = FogField.context(level, FogField.serverSeed());
        for (int i = 0; i < BENCH_SAMPLES; i++) {
            sink += FogField.density(context, center.getX() + (i % 64), center.getY() + (i / 64 % 32), center.getZ() + (i / 2048));
        }
        long nanosPerSample = (System.nanoTime() - started) / BENCH_SAMPLES;
        double checksum = sink;
        source.sendSuccess(() -> Component.translatable("commands.seepers.debug.fog.bench",
                BENCH_SAMPLES, String.format(Locale.ROOT, "%.2f", nanosPerSample / 1000.0), f(checksum / BENCH_SAMPLES)), false);
        return (int) nanosPerSample;
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String minutes(double ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 1200.0);
    }

    private FogDebugCommand() {}
}
