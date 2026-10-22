package io.github.nullbrash.seepers.client.darkness;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Dimension effects with a black sky: no sky dome, sun, moon, stars or clouds (CLAUDE.md 4.1).
 * Selected by the "effects" field of our dimension_type overrides (data/minecraft/dimension_type).
 */
public final class DarkSkyEffects extends DimensionSpecialEffects {
    public static final ResourceLocation OVERWORLD = Seepers.id("dark_overworld");
    public static final ResourceLocation END = Seepers.id("dark_end");

    private DarkSkyEffects(boolean hasGround) {
        // NaN cloud level = no clouds; SkyType.NONE = nothing drawn for the sky;
        // forceBrightLightmap = false, unlike the vanilla End which would stay lit.
        super(Float.NaN, hasGround, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(OVERWORLD, new DarkSkyEffects(true));
        event.register(END, new DarkSkyEffects(false));
    }
}
