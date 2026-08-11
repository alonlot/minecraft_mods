package com.hogwartsmagic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

/**
 * The crown of a merchieftainess, taken off the hoard at the statue's feet.
 *
 * <p>The only place in the world it exists is that chest, and it is the reason
 * to open it. Worn, it grants the sea's own conduit power: you breathe water,
 * you see through it, and you work in it without a diver's clumsiness. It makes
 * the Black Lake yours in a way Gillyweed never quite does, because Gillyweed
 * runs out.
 *
 * <p>It costs something, and the cost is the point. Every merperson that lays
 * eyes on you while you are wearing their chieftainess's crown attacks on sight
 * — no rearing, no screech, none of the five seconds of grace they extend to
 * anyone else. See {@code MerpersonEntity#tickTerritory}. You may have the
 * freedom of the lake or the goodwill of the people in it. Not both.
 */
public class MerchieftainessCrownItem extends ArmorItem {

    public MerchieftainessCrownItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    /** True if this entity is currently wearing one. */
    public static boolean isWornBy(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof MerchieftainessCrownItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.merchieftainess_crown.desc")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.hogwartsmagic.merchieftainess_crown.cost")
                .withStyle(ChatFormatting.RED));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return slot == EquipmentSlot.HEAD
                ? ResourceLocation.fromNamespaceAndPath("hogwartsmagic",
                        "textures/models/armor/merchieftainess_crown_layer_1.png")
                : layer.texture(innerModel);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                if (slot == EquipmentSlot.HEAD) {
                    return new com.hogwartsmagic.client.model.MerchieftainessCrownModel<>(
                            net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(
                                    com.hogwartsmagic.client.model.MerchieftainessCrownModel.LAYER_LOCATION));
                }
                return original;
            }
        });
    }
}
