package io.github.nullbrash.seepers;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Seepers.MOD_ID)
public final class Seepers {
    public static final String MOD_ID = "seepers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Seepers(IEventBus modBus, ModContainer container) {
        LOGGER.info("Seepers {} loading", container.getModInfo().getVersion());
    }
}
