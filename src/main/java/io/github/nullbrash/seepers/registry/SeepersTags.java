package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/** Non-category tags; light category tags live in {@link io.github.nullbrash.seepers.light.LightCategory}. */
public final class SeepersTags {
    public static final class Blocks {
        /** Keep vanilla light, without the uncategorized cap (state-driven 0-15 like respawn anchors). */
        public static final TagKey<Block> LIGHT_VANILLA = TagKey.create(Registries.BLOCK, Seepers.id("light/vanilla"));
        /** Never a room wall for fog even with a full collision shape (leaves). */
        public static final TagKey<Block> ROOM_NOT_WALL = TagKey.create(Registries.BLOCK, Seepers.id("room/not_wall"));
        /** Not dimmed when waterlogged: blocks that only glow in water. */
        public static final TagKey<Block> LIGHT_NO_WATER_DIMMING =
                TagKey.create(Registries.BLOCK, Seepers.id("light/no_water_dimming"));

        private Blocks() {}
    }

    public static final class EntityTypes {
        /** Hostile mobs that keep their natural spawning (CLAUDE.md 7.3: drowned in water). */
        public static final TagKey<EntityType<?>> NATURAL_SPAWN_EXEMPT =
                TagKey.create(Registries.ENTITY_TYPE, Seepers.id("natural_spawn_exempt"));

        private EntityTypes() {}
    }

    private SeepersTags() {}
}
