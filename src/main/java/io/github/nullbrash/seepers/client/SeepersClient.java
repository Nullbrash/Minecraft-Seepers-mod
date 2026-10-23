package io.github.nullbrash.seepers.client;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.darkness.DarkSkyEffects;
import io.github.nullbrash.seepers.client.fog.ClientFog;
import io.github.nullbrash.seepers.client.fog.ClientFogRenderer;
import io.github.nullbrash.seepers.client.fog.ClientRooms;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.points.PointCommands;
import io.github.nullbrash.seepers.client.points.PointRenderer;
import io.github.nullbrash.seepers.client.touch.ClientTouch;
import io.github.nullbrash.seepers.client.touch.HandTouch;
import io.github.nullbrash.seepers.client.touch.TouchCaption;
import io.github.nullbrash.seepers.config.ClientConfig;
import io.github.nullbrash.seepers.light.LightDimension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client entry point; FML never loads this class on a dedicated server. */
@Mod(value = Seepers.MOD_ID, dist = Dist.CLIENT)
public final class SeepersClient {
    public SeepersClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Mods list -> Seepers -> Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(DarkSkyEffects::register);
        NeoForge.EVENT_BUS.addListener(ClientFogRenderer::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(ClientFogRenderer::onRenderFog);
        NeoForge.EVENT_BUS.addListener(ClientFog::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientFog::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientRooms::onClientTick);
        modBus.addListener(PointRenderer::onRegisterShaders);
        NeoForge.EVENT_BUS.addListener(PointRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PointCommands::register);
        NeoForge.EVENT_BUS.addListener(ClientTouch::onClientTick);
        NeoForge.EVENT_BUS.addListener(HandTouch::onInteraction);
        NeoForge.EVENT_BUS.addListener(HandTouch::onClientTick);
        modBus.addListener(TouchCaption::register);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            PointCloud cloud = PointCloud.get();
            if (cloud != null) cloud.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            PointCloud.reset();
            HandTouch.onLoggingOut();
        });
        // Chunk-mesh regions carry no level: tell the light overrides which dimension the client is in.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ClientLevel level) {
                LightDimension.setFallback(level.dimension());
            }
        });
    }
}
