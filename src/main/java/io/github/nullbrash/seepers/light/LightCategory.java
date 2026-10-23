package io.github.nullbrash.seepers.light;

import io.github.nullbrash.seepers.Seepers;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Light categories (CLAUDE.md 4.2): a block in tag {@code #seepers:light/<id>} emits the category's level.
 * Order matters — the first matching tag wins. Defaults are [overworld, nether, end];
 * {@link #byCandles} categories hold one value per candle count instead.
 */
public enum LightCategory {
    TORCH("torch", 4, 4, 4),
    SOUL_FIRE("soul_fire", 3, 6, 3),
    CANDLE("candle", true, 1, 2, 3, 3),
    LAVA("lava", 5, 5, 5),
    GLOWSTONE("glowstone", 9, 12, 9),
    GLOW("glow", 9, 9, 9),
    SHROOMLIGHT("shroomlight", 12, 15, 9),
    LAMP("lamp", 13, 13, 13),
    COPPER_BULB_EXPOSED("copper_bulb_exposed", 10, 10, 10),
    COPPER_BULB_WEATHERED("copper_bulb_weathered", 7, 7, 7),
    COPPER_BULB_OXIDIZED("copper_bulb_oxidized", 4, 4, 4),
    SOUL_LANTERN("soul_lantern", 10, 15, 10),
    MAX("max", 15, 15, 15),
    REDSTONE_TORCH("redstone_torch", 6, 6, 6),
    ARCANE("arcane", 5, 5, 5),
    EMBER("ember", 4, 4, 4),
    FAINT("faint", 1, 1, 1);

    public final String id;
    public final boolean byCandles;
    public final List<Integer> defaults;
    public final TagKey<Block> tag;

    LightCategory(String id, int... defaults) {
        this(id, false, defaults);
    }

    LightCategory(String id, boolean byCandles, int... defaults) {
        this.id = id;
        this.byCandles = byCandles;
        this.defaults = Arrays.stream(defaults).boxed().toList();
        this.tag = TagKey.create(Registries.BLOCK, Seepers.id("light/" + id));
    }
}
