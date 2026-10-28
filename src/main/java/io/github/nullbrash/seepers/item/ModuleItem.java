package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.lidar.LidarModules;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A lidar module (CLAUDE.md 5.3): goes into its own slot of the lidar's screen; level I-III. */
public class ModuleItem extends Item {
    private final LidarModules.Kind kind;
    private final int level;

    public ModuleItem(Properties properties, LidarModules.Kind kind, int level) {
        super(properties);
        this.kind = kind;
        this.level = level;
    }

    public LidarModules.Kind kind() {
        return kind;
    }

    public int level() {
        return level;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.seepers.module." + kind.id() + ".effect" + (kind.maxLevel > 1 ? "." + level : ""))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.seepers.module.hint", Component.keybind("key.seepers.lidar_screen"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
