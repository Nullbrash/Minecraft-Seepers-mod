package io.github.nullbrash.seepers;

import com.mojang.logging.LogUtils;
import io.github.nullbrash.seepers.command.SeepersCommands;
import io.github.nullbrash.seepers.config.CommonConfig;
import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.datagen.SeepersDataGen;
import io.github.nullbrash.seepers.network.SeepersNetwork;
import io.github.nullbrash.seepers.registry.SeepersAttachments;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
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
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
