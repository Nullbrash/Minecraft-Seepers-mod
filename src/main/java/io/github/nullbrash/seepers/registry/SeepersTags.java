package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class SeepersTags {
    public static final class EntityTypes {
        /** Hostile mobs that keep their natural spawning (CLAUDE.md 7.3: drowned in water). */
        public static final TagKey<EntityType<?>> NATURAL_SPAWN_EXEMPT =
                TagKey.create(Registries.ENTITY_TYPE, Seepers.id("natural_spawn_exempt"));

        private EntityTypes() {}
    }

    private SeepersTags() {}
}
