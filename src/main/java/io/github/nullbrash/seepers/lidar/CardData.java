package io.github.nullbrash.seepers.lidar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A memory card's identity (component {@code seepers:card}, CLAUDE.md 5.3): the points themselves live in the world's
 * files under the id (lidar.CardStore), never in the item. A blank card has no id; one is given when it first goes into
 * a lidar. copyOf: a copy crafted from that card, filled from it when it gets its id. The author signed it on an anvil
 * and alone sets its locks. points: how many it holds, kept fresh by the server for the tooltip.
 */
public record CardData(Optional<UUID> id, Optional<UUID> copyOf, Optional<UUID> author, String authorName, boolean noWrite,
                       boolean noCopy, int points) {
    public static final CardData BLANK = new CardData(Optional.empty(), Optional.empty(), Optional.empty(), "", false, false, 0);

    public static final Codec<CardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("id").forGetter(CardData::id),
            UUIDUtil.CODEC.optionalFieldOf("copy_of").forGetter(CardData::copyOf),
            UUIDUtil.CODEC.optionalFieldOf("author").forGetter(CardData::author),
            Codec.STRING.optionalFieldOf("author_name", "").forGetter(CardData::authorName),
            Codec.BOOL.optionalFieldOf("no_write", false).forGetter(CardData::noWrite),
            Codec.BOOL.optionalFieldOf("no_copy", false).forGetter(CardData::noCopy),
            Codec.INT.optionalFieldOf("points", 0).forGetter(CardData::points)
    ).apply(instance, CardData::new));

    public static final StreamCodec<ByteBuf, CardData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CardData decode(ByteBuf buf) {
            return new CardData(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf),
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf),
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean(), buf.readBoolean(), ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, CardData card) {
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, card.id);
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, card.copyOf);
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buf, card.author);
            ByteBufCodecs.STRING_UTF8.encode(buf, card.authorName);
            buf.writeBoolean(card.noWrite);
            buf.writeBoolean(card.noCopy);
            ByteBufCodecs.VAR_INT.encode(buf, card.points);
        }
    };

    public boolean signed() {
        return author.isPresent();
    }

    public CardData withId(UUID value) {
        return new CardData(Optional.of(value), Optional.empty(), author, authorName, noWrite, noCopy, points);
    }

    public CardData withPoints(int value) {
        return new CardData(id, copyOf, author, authorName, noWrite, noCopy, value);
    }

    public CardData withAuthor(UUID value, String name) {
        return new CardData(id, copyOf, Optional.of(value), name, noWrite, noCopy, points);
    }

    public CardData withLocks(boolean write, boolean copy) {
        return new CardData(id, copyOf, author, authorName, write, copy, points);
    }

    /** A blank copy of this card: no id yet, filled from this one when it gets its id. */
    public CardData copy() {
        return new CardData(Optional.empty(), id, Optional.empty(), "", false, false, points);
    }
}
