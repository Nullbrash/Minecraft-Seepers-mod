package io.github.nullbrash.seepers.client.render;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional Sodium integration: Sodium replaces the video settings screen, so the automatic distance is added to
 * its render distance slider through Sodium's config API. Sodium finds this class by name (mod property
 * {@code sodium:config_api_user}); without Sodium it is never loaded.
 */
public final class SodiumConfig implements ConfigEntryPoint {
    private static final ResourceLocation RENDER_DISTANCE = ResourceLocation.parse("sodium:general.render_distance");

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions().registerOptionOverlay(RENDER_DISTANCE, builder.createIntegerOption(RENDER_DISTANCE)
                .setValueFormatter(value -> AutoRenderDistance.sliderCaption(Component.translatable("options.chunks", value))));
    }
}
