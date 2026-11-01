package com.hogwartsmagic.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Hogwarts clothing worn on a custom model: the film school robes (a shin-length open robe with a
 * folded hood), the striped house scarves and the pointed first-year hat.
 *
 * <p>The model kind is an int, not an enum, because the client picks the model with a plain
 * if-chain: a switch on an enum compiles to a synthetic switch-map class that has failed to load in
 * this mod's render code before. The client model classes are only reached from
 * {@link #initializeClient}, which a dedicated server never calls.
 */
public class CosmeticArmorItem extends ArmorItem {
    public static final int ROBE = 0;
    public static final int SCARF = 1;
    public static final int POINTED_HAT = 2;

    private final int modelKind;
    private final ResourceLocation texture;

    public CosmeticArmorItem(Holder<ArmorMaterial> material, Type type, int modelKind, String textureName, Properties properties) {
        super(material, type, properties);
        this.modelKind = modelKind;
        this.texture = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/models/armor/" + textureName + ".png");
    }

    public int modelKind() {
        return modelKind;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return slot == getEquipmentSlot() ? texture : layer.texture(innerModel);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientExtensions.create(modelKind));
    }

    private static final class ClientExtensions {
        private static net.minecraft.client.model.HumanoidModel<?> robe;
        private static net.minecraft.client.model.HumanoidModel<?> scarf;
        private static net.minecraft.client.model.HumanoidModel<?> hat;

        private ClientExtensions() {
        }

        private static IClientItemExtensions create(int kind) {
            return new IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                        LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                        net.minecraft.client.model.HumanoidModel<?> original) {
                    net.minecraft.client.model.HumanoidModel<?> model = model(kind);
                    if (model instanceof com.hogwartsmagic.client.model.HouseRobeModel<?> robeModel) {
                        // A scarf's turn round the collar would have the folded hood poking through it.
                        robeModel.setHoodVisible(!(entity.getItemBySlot(EquipmentSlot.HEAD).getItem()
                                instanceof CosmeticArmorItem worn && worn.modelKind() == SCARF));
                    }
                    return model;
                }
            };
        }

        /** Baked once per kind: the layer definitions are code, so a resource reload cannot change them. */
        private static net.minecraft.client.model.HumanoidModel<?> model(int kind) {
            net.minecraft.client.model.geom.EntityModelSet models = net.minecraft.client.Minecraft.getInstance().getEntityModels();
            if (kind == SCARF) {
                if (scarf == null) {
                    scarf = new com.hogwartsmagic.client.model.HouseScarfModel<>(
                            models.bakeLayer(com.hogwartsmagic.client.model.HouseScarfModel.LAYER_LOCATION));
                }
                return scarf;
            }
            if (kind == POINTED_HAT) {
                if (hat == null) {
                    hat = new com.hogwartsmagic.client.model.PointedHatModel<>(
                            models.bakeLayer(com.hogwartsmagic.client.model.PointedHatModel.LAYER_LOCATION));
                }
                return hat;
            }
            if (robe == null) {
                robe = new com.hogwartsmagic.client.model.HouseRobeModel<>(
                        models.bakeLayer(com.hogwartsmagic.client.model.HouseRobeModel.LAYER_LOCATION));
            }
            return robe;
        }
    }
}
