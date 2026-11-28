package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Armour counts for 40% against spells, and Warding takes up to a third more off: full netherite with Warding IV
 * on every piece still takes about 55% of a spell. The {@code hogwartsmagic:spell} damage type is in
 * {@code #minecraft:bypasses_armor} and {@code #minecraft:bypasses_enchantments}, so vanilla skips its own armour
 * and Protection steps and this one runs instead (Resistance and absorption still apply). Server side: the hurt
 * event only fires there.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellArmour {
    /** Share of armour and toughness a spell goes through. */
    public static final double ARMOUR_IGNORED = 0.6;

    private SpellArmour() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!event.getSource().is(DamageSupport.SPELL) || !(event.getAmount() > 0.0F)) {
            return;
        }
        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        double armour = target.getArmorValue();
        if (armour > 0.0) {
            amount = DiffindoRules.damageAfterArmor(amount, armour,
                    target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), ARMOUR_IGNORED);
        }
        amount *= (float) com.hogwartsmagic.spell.combat.CrowdControlMath.wardingMultiplier(
                com.hogwartsmagic.spell.combat.CrowdControl.wardingLevels(target));
        event.setAmount(amount);
    }
}
