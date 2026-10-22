package io.github.nullbrash.seepers.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Entry point for {@code runData}; output goes to src/generated/resources. */
public final class SeepersDataGen {
    public static void gatherData(GatherDataEvent event) {
        // No providers yet — tags, loot tables and worldgen data are added here as content appears.
    }

    private SeepersDataGen() {}
}
