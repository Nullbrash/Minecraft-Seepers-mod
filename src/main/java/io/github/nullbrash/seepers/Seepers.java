package io.github.nullbrash.seepers;

import com.mojang.logging.LogUtils;
import io.github.nullbrash.seepers.command.SeepersCommands;
import io.github.nullbrash.seepers.config.CommonConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.datagen.SeepersDataGen;
import io.github.nullbrash.seepers.light.LightTable;
import io.github.nullbrash.seepers.mob.HostileSpawnControl;
import io.github.nullbrash.seepers.network.SeepersNetwork;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
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

        modBus.addListener(SeepersNetwork::register);
        modBus.addListener(SeepersDataGen::gatherData);
        NeoForge.EVENT_BUS.addListener(SeepersCommands::register);
        NeoForge.EVENT_BUS.addListener(HostileSpawnControl::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(HostileSpawnControl::onFinalizeSpawn);

        // Light overrides depend on tags and the server config: rebuild whenever either changes.
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> LightTable.rebuild());
        modBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event.getConfig()));
    }

    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() == ServerConfig.SPEC) {
            LightTable.rebuild();
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
