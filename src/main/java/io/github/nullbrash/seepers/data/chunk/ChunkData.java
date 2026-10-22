package io.github.nullbrash.seepers.data.chunk;

import io.github.nullbrash.seepers.data.VersionedData;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Base for per-chunk data stored as a NeoForge data attachment (saved inside the chunk NBT).
 * Mutators must call {@link #markDirty()}: changing an attachment's contents does not mark the chunk unsaved.
 */
public abstract class ChunkData implements VersionedData {
    private final ChunkAccess chunk;

    protected ChunkData(IAttachmentHolder holder) {
        if (!(holder instanceof ChunkAccess chunkAccess)) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " can only be attached to chunks, got " + holder);
        }
        this.chunk = chunkAccess;
    }

    public abstract boolean isEmpty();

    protected final void markDirty() {
        chunk.setUnsaved(true);
    }

    protected final ChunkPos chunkPos() {
        return chunk.getPos();
    }

    /** Packs a block position into an int relative to its chunk: x and z in 4 bits each, y in the rest (signed). */
    public static int packLocal(BlockPos pos) {
        return (pos.getY() << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    public static BlockPos unpackLocal(ChunkPos chunkPos, int packed) {
        return new BlockPos(chunkPos.getMinBlockX() + (packed & 15), packed >> 8, chunkPos.getMinBlockZ() + ((packed >> 4) & 15));
    }

    /** Empty data is not written, so chunks without Seepers data get no extra NBT. */
    public static <T extends ChunkData> IAttachmentSerializer<CompoundTag, T> serializer(Function<IAttachmentHolder, T> constructor) {
        return new IAttachmentSerializer<>() {
            @Override
            public T read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                return VersionedData.load(constructor.apply(holder), tag, provider);
            }

            @Override
            public @Nullable CompoundTag write(T attachment, HolderLookup.Provider provider) {
                return attachment.isEmpty() ? null : VersionedData.save(attachment, new CompoundTag(), provider);
            }
        };
    }
}
