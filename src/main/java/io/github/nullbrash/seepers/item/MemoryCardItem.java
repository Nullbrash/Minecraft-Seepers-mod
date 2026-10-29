package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.lidar.CardData;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A lidar's scan memory card, level I-III (CLAUDE.md 5.3): goes into the lidar's card slot and keeps what it scans; the
 * points live in the world under the card's id (component {@code seepers:card}), so a card handed to another player
 * shows them the same points. Without a card the lidar forgets in seconds.
 */
public class MemoryCardItem extends Item {
    private final int level;

    public MemoryCardItem(Properties properties, int level) {
        super(properties);
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** How many points a card of this level keeps; the oldest go when it is full. */
    public static int capacity(int level) {
        return switch (level) {
            case 1 -> ServerConfig.get(ServerConfig.LIDAR_CARD_1);
            case 2 -> ServerConfig.get(ServerConfig.LIDAR_CARD_2);
            default -> ServerConfig.get(ServerConfig.LIDAR_CARD_3);
        };
    }

    public static CardData data(ItemStack card) {
        return card.getOrDefault(SeepersComponents.CARD.get(), CardData.BLANK);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CardData card = data(stack);
        String points = String.format(java.util.Locale.ROOT, "%,d", card.points()).replace(',', ' ');
        String limit = String.format(java.util.Locale.ROOT, "%,d", capacity(level)).replace(',', ' ');
        tooltip.add((card.id().isEmpty() && card.copyOf().isEmpty() ? Component.translatable("item.seepers.memory_card.blank", limit)
                : Component.translatable("item.seepers.memory_card.points", points, limit)).withStyle(ChatFormatting.GRAY));
        if (card.copyOf().isPresent()) tooltip.add(Component.translatable("item.seepers.memory_card.copy").withStyle(ChatFormatting.DARK_GRAY));
        if (card.signed()) tooltip.add(Component.translatable("item.seepers.memory_card.author", card.authorName()).withStyle(ChatFormatting.GRAY));
        if (card.noWrite()) tooltip.add(Component.translatable("item.seepers.memory_card.no_write").withStyle(ChatFormatting.GOLD));
        if (card.noCopy()) tooltip.add(Component.translatable("item.seepers.memory_card.no_copy").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.seepers.memory_card.hint", Component.keybind("key.seepers.lidar_screen"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
