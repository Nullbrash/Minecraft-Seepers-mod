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
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import io.github.nullbrash.seepers.registry.SeepersItems;
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
        SeepersItems.ITEMS.register(modBus);
        modBus.addListener(SeepersItems::addToCreativeTabs);

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
        NeoForge.EVENT_BUS.addListener(FireTracker::onNeighborNotify);
        NeoForge.EVENT_BUS.addListener(FireTracker::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(FireTracker::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(FireTracker::onServerTick);
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
