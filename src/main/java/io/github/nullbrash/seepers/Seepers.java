package io.github.nullbrash.seepers;

import com.mojang.logging.LogUtils;
import io.github.nullbrash.seepers.command.SeepersCommands;
import io.github.nullbrash.seepers.config.CommonConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.datagen.SeepersDataGen;
import io.github.nullbrash.seepers.fire.FireSettings;
import io.github.nullbrash.seepers.fire.FireTracker;
import io.github.nullbrash.seepers.fog.FogField;
import io.github.nullbrash.seepers.fog.FogSettings;
import io.github.nullbrash.seepers.heat.HeatField;
import io.github.nullbrash.seepers.heat.HeatSettings;
import io.github.nullbrash.seepers.heat.HeatTracker;
import io.github.nullbrash.seepers.light.LightTable;
import io.github.nullbrash.seepers.network.payload.FogSeedPayload;
import io.github.nullbrash.seepers.room.RoomField;
import io.github.nullbrash.seepers.room.RoomSettings;
import io.github.nullbrash.seepers.room.RoomTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import io.github.nullbrash.seepers.light.Relighter;
import io.github.nullbrash.seepers.mob.HostileSpawnControl;
import io.github.nullbrash.seepers.network.SeepersNetwork;
import io.github.nullbrash.seepers.registry.SeepersBlocks;
import io.github.nullbrash.seepers.registry.SeepersWorldgen;
import io.github.nullbrash.seepers.world.AnomalyRegrowth;
import io.github.nullbrash.seepers.world.WorldShape;
import io.github.nullbrash.seepers.network.ServerLoad;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import io.github.nullbrash.seepers.item.CaneItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import io.github.nullbrash.seepers.registry.SeepersItems;
import io.github.nullbrash.seepers.touch.BlowSilhouettes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;

/** Common entry point (both physical sides). Client-only setup lives in {@code client.SeepersClient}. */
@Mod(Seepers.MOD_ID)
public final class Seepers {
    public static final String MOD_ID = "seepers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Seepers(IEventBus modBus, ModContainer container) {
        LOGGER.info("Seepers {} loading", container.getModInfo().getVersion());

        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        SeepersAttachments.ATTACHMENT_TYPES.register(modBus);
        SeepersBlocks.BLOCKS.register(modBus);
        SeepersItems.ITEMS.register(modBus);
        SeepersComponents.COMPONENTS.register(modBus);
        io.github.nullbrash.seepers.registry.SeepersSounds.SOUNDS.register(modBus);
        SeepersWorldgen.DENSITY_FUNCTION_TYPES.register(modBus);
        SeepersWorldgen.CHUNK_GENERATORS.register(modBus);
        SeepersWorldgen.BIOME_SOURCES.register(modBus);
        NeoForge.EVENT_BUS.addListener(WorldShape::onServerStarting);
        NeoForge.EVENT_BUS.addListener(WorldShape::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(WorldShape::onCreateSpawn);
        NeoForge.EVENT_BUS.addListener(CaneItem::onAnvilUpdate);
        modBus.addListener(SeepersItems::addToCreativeTabs);
        modBus.addListener(io.github.nullbrash.seepers.item.ChiselItem::registerDataMaps);

        modBus.addListener(SeepersNetwork::register);
        modBus.addListener(SeepersDataGen::gatherData);
        NeoForge.EVENT_BUS.addListener(SeepersCommands::register);
        NeoForge.EVENT_BUS.addListener(HostileSpawnControl::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(HostileSpawnControl::onFinalizeSpawn);

        // Light overrides depend on tags and the server config: rebuild whenever either changes.
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> LightTable.rebuild());
        NeoForge.EVENT_BUS.addListener(Relighter::onServerTick);
        NeoForge.EVENT_BUS.addListener(Relighter::onServerStopping);

        NeoForge.EVENT_BUS.addListener(FogField::onServerStarting);
        FogField.addModifier(HeatField::densityAt);
        NeoForge.EVENT_BUS.addListener(HeatTracker::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(HeatTracker::onChunkLoad);
        FogField.addModifier(RoomField::densityAt);
        NeoForge.EVENT_BUS.addListener(RoomTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(RoomTracker::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(BlowSilhouettes::onDamaged);
        NeoForge.EVENT_BUS.addListener(FireTracker::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(FireTracker::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(FireTracker::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(FireTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerLoad::onServerTick);
        NeoForge.EVENT_BUS.addListener(AnomalyRegrowth::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.light.DynamicLights::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.light.DynamicLights::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.light.DynamicLights::onServerStopping);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.light.DynamicLights::onStartTracking);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.Collapses::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.Collapses::onCaneCeilingTap);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.LooseGround::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.LooseGround::onPlaced);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.Tremors::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.Tremors::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(io.github.nullbrash.seepers.collapse.Tremors::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(AnomalyRegrowth::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(AnomalyRegrowth::onChunkUnload);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new FogSeedPayload(FogField.serverSeed()));
            }
        });
        modBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event.getConfig()));
    }

    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() == ServerConfig.SPEC) {
            LightTable.rebuild();
            FogSettings.reload();
            HeatSettings.reload();
            RoomSettings.reload();
            FireSettings.reload();
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
