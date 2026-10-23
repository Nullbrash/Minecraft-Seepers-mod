package io.github.nullbrash.seepers.data.chunk;

import io.github.nullbrash.seepers.heat.HeatCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Heat sources in this chunk with lit/out game times (CLAUDE.md 4.5: heat has inertia). Written by the
 * server only and synced to clients, so both sides compute the same heated fog.
 */
public final class HeatSources extends ChunkData {
    public static final long LONG_AGO = Long.MIN_VALUE / 4;
    public static final long LIT = -1L;

    /** {@code outSince} is {@link #LIT} while the source burns. */
    public record Source(HeatCategory category, long litSince, long outSince) {}

    private final Int2ObjectOpenHashMap<Source> sources = new Int2ObjectOpenHashMap<>();

    public HeatSources(IAttachmentHolder holder) {
        super(holder);
    }

    public @Nullable Source get(BlockPos pos) {
        return sources.get(packLocal(pos));
    }

    /** @return true if anything changed (a full chunk silently ignores new sources) */
    public boolean light(BlockPos pos, HeatCategory category, long litSince, int maxSources) {
        int key = packLocal(pos);
        Source old = sources.get(key);
        if (old != null && old.outSince() == LIT && old.category() == category) return false;
        if (old == null && sources.size() >= maxSources) return false;
        sources.put(key, new Source(category, litSince, LIT));
        markDirty();
        return true;
    }

    /** @return true if a burning source went out */
    public boolean extinguish(BlockPos pos, long now) {
        int key = packLocal(pos);
        Source old = sources.get(key);
        if (old == null || old.outSince() != LIT) return false;
        sources.put(key, new Source(old.category(), old.litSince(), now));
        markDirty();
        return true;
    }

    /** Drops sources that have fully cooled down; @return true if any were dropped. */
    public boolean forgetCooled(long now, double cooldownTicks) {
        boolean removed = sources.int2ObjectEntrySet()
                .removeIf(e -> e.getValue().outSince() != LIT && now - e.getValue().outSince() > cooldownTicks);
        if (removed) markDirty();
        return removed;
    }

    public void forEach(BiConsumer<BlockPos, Source> action) {
        for (Int2ObjectMap.Entry<Source> entry : sources.int2ObjectEntrySet()) {
            action.accept(unpackLocal(chunkPos(), entry.getIntKey()), entry.getValue());
        }
    }

    /** Positions as packed local ints, for hot loops that avoid BlockPos allocation. */
    public Int2ObjectMap<Source> raw() {
        return sources;
    }

    public int size() {
        return sources.size();
    }

    @Override
    public boolean isEmpty() {
        return sources.isEmpty();
    }

    @Override
    public int formatVersion() {
        return 1;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        int n = sources.size();
        int[] positions = new int[n];
        byte[] categories = new byte[n];
        long[] lit = new long[n];
        long[] out = new long[n];
        int i = 0;
        for (Int2ObjectMap.Entry<Source> e : sources.int2ObjectEntrySet()) {
            positions[i] = e.getIntKey();
            categories[i] = (byte) e.getValue().category().ordinal();
            lit[i] = e.getValue().litSince();
            out[i] = e.getValue().outSince();
            i++;
        }
        tag.putIntArray("positions", positions);
        tag.putByteArray("categories", categories);
        tag.putLongArray("lit_since", lit);
        tag.putLongArray("out_since", out);
    }

    @Override
    public void read(CompoundTag tag, int version, HolderLookup.Provider registries) {
        sources.clear();
        int[] positions = tag.getIntArray("positions");
        byte[] categories = tag.getByteArray("categories");
        long[] lit = tag.getLongArray("lit_since");
        long[] out = tag.getLongArray("out_since");
        int n = Math.min(Math.min(positions.length, categories.length), Math.min(lit.length, out.length));
        for (int i = 0; i < n; i++) {
            HeatCategory category = HeatCategory.byId(categories[i]);
            if (category != null) sources.put(positions[i], new Source(category, lit[i], out[i]));
        }
    }

    /** Whole-chunk sync: a chunk holds at most a few dozen sources. */
    public static final AttachmentSyncHandler<HeatSources> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, HeatSources attachment, boolean initialSync) {
            buf.writeVarInt(attachment.sources.size());
            for (Int2ObjectMap.Entry<Source> e : attachment.sources.int2ObjectEntrySet()) {
                buf.writeInt(e.getIntKey());
                buf.writeByte(e.getValue().category().ordinal());
                buf.writeLong(e.getValue().litSince());
                buf.writeLong(e.getValue().outSince());
            }
        }

        @Override
        public HeatSources read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable HeatSources previous) {
            HeatSources result = new HeatSources(holder);
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                int pos = buf.readInt();
                HeatCategory category = HeatCategory.byId(buf.readByte());
                long lit = buf.readLong();
                long out = buf.readLong();
                if (category != null) result.sources.put(pos, new Source(category, lit, out));
            }
            return result;
        }

        @Override
        public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
            return true;
        }
    };
}
