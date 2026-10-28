package io.github.nullbrash.seepers.item;

import net.minecraft.world.item.Item;

/**
 * The lidar (CLAUDE.md 5.3). Scans run on the client while the use key is held with it in hand
 * ({@code client.lidar.LidarScan}) — not through item use, which would slow the player like eating does.
 */
public class LidarItem extends Item {
    public LidarItem(Properties properties) {
        super(properties);
    }
}
