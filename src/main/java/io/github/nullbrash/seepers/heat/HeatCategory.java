package io.github.nullbrash.seepers.heat;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Heat source categories (CLAUDE.md 4.5, 6.10): a block in tag {@code #seepers:heat/<id>} clears fog
 * around it. Strength is in fog density halvings at the source, fading to 0 at the radius.
 * Order matters — the first matching tag wins. Ids are saved in chunk data: never reorder.
 */
public enum HeatCategory {
    FURNACE("furnace", 2.0, 8),
    SMOKER("smoker", 1.5, 6),
    BLAST_FURNACE("blast_furnace", 1.0, 4),
    CAMPFIRE("campfire", 1.5, 6),
    FIRE("fire", 1.0, 4),
    LAVA("lava", 1.5, 5),
    MAGMA("magma", 0.5, 2),
    TORCH("torch", 0.5, 2),
    LANTERN("lantern", 0.3, 2);

    public final String id;
    public final double defaultStrength;
    public final double defaultRadius;
    public final TagKey<Block> tag;

    HeatCategory(String id, double defaultStrength, double defaultRadius) {
        this.id = id;
        this.defaultStrength = defaultStrength;
        this.defaultRadius = defaultRadius;
        this.tag = TagKey.create(Registries.BLOCK, Seepers.id("heat/" + id));
    }

    public static HeatCategory byId(int ordinal) {
        HeatCategory[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
