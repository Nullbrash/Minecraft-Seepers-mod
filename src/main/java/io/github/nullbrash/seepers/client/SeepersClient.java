package io.github.nullbrash.seepers.client;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.client.darkness.DarkSkyEffects;
import io.github.nullbrash.seepers.client.fog.ClientFog;
import io.github.nullbrash.seepers.client.fog.ClientFogRenderer;
import io.github.nullbrash.seepers.client.fog.ClientRooms;
import io.github.nullbrash.seepers.client.points.PointCloud;
import io.github.nullbrash.seepers.client.render.AutoRenderDistance;
import io.github.nullbrash.seepers.client.diagnostics.Diagnostics;
import io.github.nullbrash.seepers.client.diagnostics.ReportCommand;
import io.github.nullbrash.seepers.client.points.PointCommands;
import io.github.nullbrash.seepers.client.points.PointRenderer;
import io.github.nullbrash.seepers.client.touch.CaneTouch;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import io.github.nullbrash.seepers.block.AnomalyRockBlock;
import io.github.nullbrash.seepers.registry.SeepersItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import io.github.nullbrash.seepers.world.BrokenNames;
import io.github.nullbrash.seepers.world.Distortion;
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
        NeoForge.EVENT_BUS.addListener(CaneTouch::onClientTick);
        modBus.addListener(TouchCaption::register);
        NeoForge.EVENT_BUS.addListener(AutoRenderDistance::onClientTick);
        NeoForge.EVENT_BUS.addListener(AutoRenderDistance::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(AutoRenderDistance::onDebugText);
        // Distorted twins show a broken vanilla-looking name in F3 (CLAUDE.md 4.8); the real id: /seepers debug zone.
        NeoForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.DebugText event) -> {
            var left = event.getLeft();
            for (int i = 0; i < left.size(); i++) {
                String line = left.get(i);
                if (!line.startsWith("Biome: seepers:")) continue;
                String id = line.substring("Biome: seepers:".length());
                int slash = id.indexOf('/');
                if (slash > 0) {
                    left.set(i, "Biome: minecraft:" + BrokenNames.of(id.substring(0, slash), id.substring(slash + 1)));
                } else if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    // Special biomes (broken gravity) break in the style of the zone they lie in.
                    var player = net.minecraft.client.Minecraft.getInstance().player;
                    String zone = switch (Distortion.zone(Distortion.level(player.getX(), player.getZ()))) {
                        case 1 -> "seams";
                        case 2 -> "repeats";
                        default -> "far_lands";
                    };
                    left.set(i, "Biome: minecraft:" + BrokenNames.of(zone, id));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.DebugText event) -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;
            double level = Distortion.level(player.getX(), player.getZ());
            event.getLeft().add(String.format(java.util.Locale.ROOT, "Seepers zone: %d (distortion %.2f)", Distortion.zone(level), level));
        });
        NeoForge.EVENT_BUS.addListener(AutoRenderDistance::register);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.client.CameraShake::onClientTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.client.light.ClientDynamicLights::onClientTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.client.CameraShake::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(Diagnostics::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReportCommand::register);
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            for (var cane : java.util.List.of(SeepersItems.CANE.get(), SeepersItems.BOUND_CANE.get())) {
                ItemProperties.register(cane, Seepers.id("iron_tip"), (stack, level, entity, seed) -> CaneItem.hasIronTip(stack) ? 1 : 0);
            }
            // A rock stack carrying kind=hewn (EMI's chisel result, pick-block) shows the hewn face.
            ItemProperties.register(SeepersBlocks.ANOMALY_ROCK_ITEM.get(), Seepers.id("hewn"), (stack, level, entity, seed) -> {
                var state = stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
                return state != null && state.get(AnomalyRockBlock.KIND) == AnomalyRockBlock.Kind.HEWN ? 1 : 0;
            });
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            PointCloud cloud = PointCloud.get();
            if (cloud != null) cloud.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            PointCloud.reset();
            HandTouch.onLoggingOut();
            Distortion.set(Distortion.DEFAULT);
        });
        // Chunk-mesh regions carry no level: tell the light overrides which dimension the client is in.
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ClientLevel level) {
                LightDimension.setFallback(level.dimension());
            }
        });
    }
}
