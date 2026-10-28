package io.github.nullbrash.seepers.client.network;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.fog.ClientFog;
import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import io.github.nullbrash.seepers.network.payload.DistortionPayload;
import io.github.nullbrash.seepers.network.payload.ServerLoadPayload;
import io.github.nullbrash.seepers.world.Distortion;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.client.touch.Silhouettes;
import io.github.nullbrash.seepers.network.payload.ClientCommandPayload;
import io.github.nullbrash.seepers.network.payload.SilhouettePayload;
import net.minecraft.world.entity.Entity;
import io.github.nullbrash.seepers.network.payload.FogProbePayload;
import io.github.nullbrash.seepers.network.payload.FogProbeReplyPayload;
import io.github.nullbrash.seepers.network.payload.FogSeedPayload;
import io.github.nullbrash.seepers.network.payload.PingPayload;
import io.github.nullbrash.seepers.network.payload.PongPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers for client-bound payloads. Client only — referenced from common code through lambdas. */
public final class ClientPayloadHandlers {
    public static void handlePing(PingPayload payload, IPayloadContext context) {
        if (ClientConfig.LOG_NETWORK.get()) {
            Seepers.LOGGER.info("Received ping {}", payload.id());
        }
        context.reply(new PongPayload(payload.id()));
    }

    public static void handleClientCommand(ClientCommandPayload payload, IPayloadContext context) {
        // Only our own client commands: the server must not drive anything else on the client.
        if (payload.command().startsWith("seepers ")) {
            ClientCommandHandler.runCommand(payload.command());
        }
    }

    public static void handleSilhouette(SilhouettePayload payload, IPayloadContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(payload.entityId());
        if (entity != null) Silhouettes.of(entity, payload.playerStruck() ? Silhouettes.Part.STRUCK : Silhouettes.Part.TORSO);
    }

    public static void handleDistortion(DistortionPayload payload, IPayloadContext context) {
        Distortion.set(payload.zones());
    }

    public static void handleCollapseShake(io.github.nullbrash.seepers.network.payload.CollapseShakePayload payload, IPayloadContext context) {
        io.github.nullbrash.seepers.client.CameraShake.start(payload.strength(), payload.ticks());
    }

    /**
     * The tremor rumble, heard where the player is (felt through the ground, not placed in the world). A sound is
     * capped at full volume, so three copies play in step: at the default volume each plays at 2/3 — up to 150% fits.
     */
    public static void handleTremorRumble(io.github.nullbrash.seepers.network.payload.TremorRumblePayload payload, IPayloadContext context) {
        float each = (float) (ClientConfig.TREMOR_VOLUME.get() * 2.0 / 3.0);
        if (each <= 0) return;
        long seed = net.minecraft.util.RandomSource.create().nextLong();
        for (int copy = 0; copy < 3; copy++) {
            Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                    io.github.nullbrash.seepers.registry.SeepersSounds.TREMOR_RUMBLE.get().getLocation(), net.minecraft.sounds.SoundSource.BLOCKS,
                    each, 1.0F, net.minecraft.util.RandomSource.create(seed), false, 0,
                    net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        }
    }

    public static void handleDynamicLight(io.github.nullbrash.seepers.network.payload.DynamicLightPayload payload, IPayloadContext context) {
        io.github.nullbrash.seepers.client.light.ClientDynamicLights.set(payload.entityId(), payload.level());
    }

    public static void handleServerLoad(ServerLoadPayload payload, IPayloadContext context) {
        AutoRenderDistance.onServerLoad(payload.tps());
    }

    public static void handleFogSeed(FogSeedPayload payload, IPayloadContext context) {
        if (ClientConfig.LOG_NETWORK.get()) {
            Seepers.LOGGER.info("Received fog seed");
        }
        ClientFog.setSeed(payload.seed());
    }

    public static void handleFogProbe(FogProbePayload payload, IPayloadContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        BlockPos pos = payload.pos();
        float direct = Float.NaN;
        float grid = Float.NaN;
        if (level != null && ClientFog.hasSeed()) {
            direct = (float) ClientFog.densityDirect(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            grid = (float) ClientFog.gridOnly(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        context.reply(new FogProbeReplyPayload(payload.id(), direct, grid, ClientFog.hasSeed()));
    }

    private ClientPayloadHandlers() {}

    public static void handleDynamoSound(io.github.nullbrash.seepers.network.payload.DynamoSoundPayload payload, IPayloadContext context) {
        io.github.nullbrash.seepers.client.lidar.DynamoSounds.set(payload.entityId(), payload.cranking());
    }
}
