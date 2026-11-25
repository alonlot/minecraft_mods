package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A tankard of butterbeer. It drinks like a potion (32 ticks): food 4 and a little saturation, then Warmed for
 * 3600 ticks, and the tankard comes back. Warmed belongs to the home pack, so it is looked up by registry id when
 * the drink finishes and simply skipped when that effect does not exist.
 */
public class ButterbeerItem extends Item {
    /** How long the warmth lasts: three minutes. */
    public static final int WARMED_TICKS = 3600;
    public static final ResourceLocation WARMED_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "warmed");

    public ButterbeerItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            BuiltInRegistries.MOB_EFFECT.getHolder(WARMED_ID).ifPresent(warmed ->
                    entity.addEffect(new MobEffectInstance(warmed, WARMED_TICKS, 0, false, true, true)));
        }
        if (entity instanceof Player player && player.getAbilities().instabuild) {
            return left;
        }
        ItemStack tankard = new ItemStack(CosyFood.TANKARD.get());
        if (left.isEmpty()) {
            return tankard;
        }
        if (entity instanceof Player player && !level.isClientSide) {
            ChocolateFrogItem.giveOrDrop(player, tankard);
        }
        return left;
    }
}
