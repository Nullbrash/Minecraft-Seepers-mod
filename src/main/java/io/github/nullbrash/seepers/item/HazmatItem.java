package io.github.nullbrash.seepers.item;

import io.github.nullbrash.seepers.config.ServerConfig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;

/** Кожаные характеристики сохраняют слабую защиту; собственная ткань окрашивается ванильным рецептом. */
public final class HazmatItem extends ArmorItem {
    public HazmatItem(Type type) {
        super(ArmorMaterials.LEATHER, type, new Properties().durability(type.getDurability(5)));
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getType().getDurability(ServerConfig.get(ServerConfig.HAZMAT_DURABILITY_FACTOR));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.LEATHER) || repair.is(ItemTags.WOOL);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.fromNamespaceAndPath("seepers", "textures/models/armor/hazmat_layer_" + (innerModel ? "2" : "1")
                + (layer.dyeable() ? "" : "_overlay") + ".png");
    }
}
