package io.github.nullbrash.seepers.client.darkness;

import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Air fog is black, so the horizon and the screen clear color (taken from the fog color) stay dark.
 * Placeholder until the fog system (milestone 3); water/lava/powder snow fog are left vanilla for now.
 */
public final class DarkFog {
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() == FogType.NONE) {
            event.setRed(0.0F);
            event.setGreen(0.0F);
            event.setBlue(0.0F);
        }
    }

    private DarkFog() {}
}
