package com.hogwartsmagic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

public class SortingHatItem extends ArmorItem {
    public SortingHatItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientExtensions.create());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.hogwartsmagic.sorting_hat.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return slot == EquipmentSlot.HEAD
                ? ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/models/armor/sorting_hat_layer_1.png")
                : layer.texture(innerModel);
    }

    private static final class ClientExtensions {
        private ClientExtensions() {
        }

        private static IClientItemExtensions create() {
            return new IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                        LivingEntity entity,
                        ItemStack stack,
                        EquipmentSlot slot,
                        net.minecraft.client.model.HumanoidModel<?> original) {
                    if (slot == EquipmentSlot.HEAD) {
                        return new com.hogwartsmagic.client.model.SortingHatArmorModel<>(
                                net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(
                                        com.hogwartsmagic.client.model.SortingHatArmorModel.LAYER_LOCATION));
                    }
                    return original;
                }
            };
        }
    }
}
