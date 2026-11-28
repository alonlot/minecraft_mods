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
        int warding = com.hogwartsmagic.spell.combat.CrowdControl.wardingLevels(target);
        if (warding > 0 && !com.hogwartsmagic.spell.enchant.SpellEnchantMana.pay(target,
                com.hogwartsmagic.spell.enchant.SpellEnchantMana.WARDING)) {
            warding = 0; // out of mana: the ward does not hold
        }
        amount *= (float) com.hogwartsmagic.spell.combat.CrowdControlMath.wardingMultiplier(warding);
        event.setAmount(amount);
        if (warding > 0 && target.level() instanceof net.minecraft.server.level.ServerLevel level) {
            // The ward takes the edge off: a pale shell flickers round the wearer.
            level.sendParticles(new com.hogwartsmagic.spell.vfx.SpellVfxOptions(
                            com.hogwartsmagic.spell.vfx.SpellVfxOptions.Kind.GLOW, 0xA8D8FF, 0.25f + 0.02f * warding, 8),
                    target.getX(), target.getY(0.55), target.getZ(), 2, target.getBbWidth() * 0.3,
                    target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.0);
            level.sendParticles(new com.hogwartsmagic.spell.vfx.SpellVfxOptions(
                            com.hogwartsmagic.spell.vfx.SpellVfxOptions.Kind.SPARK, 0xDDF0FF, 0.04f, 10),
                    target.getX(), target.getY(0.55), target.getZ(), 6 + warding, target.getBbWidth() * 0.4,
                    target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.08);
            level.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }
}
