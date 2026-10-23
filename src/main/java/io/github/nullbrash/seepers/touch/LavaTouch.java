package io.github.nullbrash.seepers.touch;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.network.payload.TouchLavaPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server side of touching lava with the hand (CLAUDE.md 5.1): damage without setting the player on fire. */
public final class LavaTouch {
    private static final int COOLDOWN_TICKS = 20;
    private static final Map<ServerPlayer, Long> LAST_BURN = new WeakHashMap<>();

    public static void handle(TouchLavaPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) return;
        // The client only reports; the lava must really be within reach.
        double reach = ServerConfig.get(ServerConfig.TOUCH_HAND_REACH) + 1.0;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(payload.pos())) > reach * reach) return;
        if (!player.level().getFluidState(payload.pos()).is(FluidTags.LAVA)) return;
        long now = player.level().getGameTime();
        Long last = LAST_BURN.get(player);
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST_BURN.put(player, now);
        // Hot floor burns like magma and never ignites; fire resistance protects.
        player.hurt(player.damageSources().hotFloor(), ServerConfig.get(ServerConfig.TOUCH_LAVA_DAMAGE).floatValue());
    }

    private LavaTouch() {}
}
