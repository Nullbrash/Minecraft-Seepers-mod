package io.github.nullbrash.seepers.world;

import net.minecraft.world.level.dimension.DimensionType;

/**
 * Our dimension types have no skylight (true darkness), but vanilla gates weather on skylight.
 * Weather stays where the sky is open in vanilla terms: natural dimensions without a ceiling (overworld).
 */
public final class WeatherDimensions {
    public static boolean hasWeather(DimensionType type) {
        return type.natural() && !type.hasCeiling();
    }

    private WeatherDimensions() {}
}
