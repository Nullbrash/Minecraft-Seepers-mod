package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import io.github.nullbrash.seepers.data.chunk.AnomalyBlocks;
import io.github.nullbrash.seepers.data.chunk.ChunkData;
import io.github.nullbrash.seepers.data.chunk.FireSources;
import io.github.nullbrash.seepers.data.chunk.HeatSources;
import io.github.nullbrash.seepers.data.chunk.PlayerPlacedBlocks;
import java.util.function.Function;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attachment types. Registry names are saved in chunk NBT — do not rename. */
public final class SeepersAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Seepers.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerPlacedBlocks>> PLAYER_PLACED_BLOCKS =
            chunk("player_placed_blocks", PlayerPlacedBlocks::new);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FireSources>> FIRE_SOURCES =
            chunk("fire_sources", FireSources::new);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AnomalyBlocks>> ANOMALY_BLOCKS =
            chunk("anomaly_blocks", AnomalyBlocks::new);
    /** Synced to clients: they render heated fog from it. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HeatSources>> HEAT_SOURCES =
            ATTACHMENT_TYPES.register("heat_sources", () -> AttachmentType.builder(HeatSources::new)
                    .serialize(ChunkData.serializer(HeatSources::new))
                    .sync(HeatSources.SYNC)
                    .build());

    private static <T extends ChunkData> DeferredHolder<AttachmentType<?>, AttachmentType<T>> chunk(
            String name, Function<IAttachmentHolder, T> constructor) {
        return ATTACHMENT_TYPES.register(name, () -> AttachmentType.builder(constructor)
                .serialize(ChunkData.serializer(constructor))
                .build());
    }

    private SeepersAttachments() {}
}
