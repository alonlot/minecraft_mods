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
 * One piece of an Auror's or a Death Eater's battle robes: woven from magical silk and arcane dust, so
 * a blade goes through them like cloth but a curse meets netherite. The physical armour is the
 * material's (barely leather); against {@code hogwartsmagic:spell} damage each piece adds its
 * {@link #spellArmour()} and {@link #spellToughness()} at full strength, a netherite piece's numbers,
 * where ordinary armour only counts for 40% (see {@code SpellArmour}).
 *
 * <p>The set is an int, not an enum: the client picks models with plain ifs (an enum switch compiles
 * to a synthetic class that has failed to load in this mod's render code). The client model classes
 * are only reached from {@link #initializeClient}, which a dedicated server never calls.
 */
public class WizardRobeItem extends ArmorItem {
    public static final int AUROR = 0;
    public static final int DEATH_EATER = 1;

    private final int set;
    private final ResourceLocation texture;

    public WizardRobeItem(Holder<ArmorMaterial> material, Type type, int set, Properties properties) {
        super(material, type, properties);
        this.set = set;
        String name = (set == AUROR ? "auror" : "death_eater") + "_robes_" + part(type);
        this.texture = ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/models/armor/" + name + ".png");
    }

    private static String part(Type type) {
        if (type == Type.HELMET) {
            return "hood";
        }
        if (type == Type.CHESTPLATE) {
            return "coat";
        }
        return type == Type.LEGGINGS ? "trousers" : "boots";
    }

    public int set() {
        return set;
    }

    /** A netherite piece's armour for this slot: 3 / 8 / 6 / 3. */
    public double spellArmour() {
        Type type = getType();
        if (type == Type.CHESTPLATE) {
            return 8.0;
        }
        return type == Type.LEGGINGS ? 6.0 : 3.0;
    }

    /** A netherite piece's toughness. */
    public double spellToughness() {
        return 3.0;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return slot == getEquipmentSlot() ? texture : layer.texture(innerModel);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.wizard_robes.tooltip").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        if (getType() == Type.HELMET || getType() == Type.CHESTPLATE) {
            consumer.accept(ClientExtensions.create(getType() == Type.HELMET, set));
        }
    }

    private static final class ClientExtensions {
        private static net.minecraft.client.model.HumanoidModel<?> hood;
        private static net.minecraft.client.model.HumanoidModel<?> coat;

        private ClientExtensions() {
        }

        private static IClientItemExtensions create(boolean head, int set) {
            return new IClientItemExtensions() {
                @Override
                public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                        LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                        net.minecraft.client.model.HumanoidModel<?> original) {
                    net.minecraft.client.model.geom.EntityModelSet models =
                            net.minecraft.client.Minecraft.getInstance().getEntityModels();
                    if (head) {
                        if (hood == null) {
                            hood = new com.hogwartsmagic.client.model.WizardHoodModel<>(
                                    models.bakeLayer(com.hogwartsmagic.client.model.WizardHoodModel.LAYER_LOCATION));
                        }
                        ((com.hogwartsmagic.client.model.WizardHoodModel<?>) hood).setMasked(set == DEATH_EATER);
                        return hood;
                    }
                    if (coat == null) {
                        coat = new com.hogwartsmagic.client.model.WizardRobeModel<>(
                                models.bakeLayer(com.hogwartsmagic.client.model.WizardRobeModel.LAYER_LOCATION));
                    }
                    ((com.hogwartsmagic.client.model.WizardRobeModel<?>) coat).prepare(entity, set == DEATH_EATER);
                    return coat;
                }
            };
        }
    }
}
