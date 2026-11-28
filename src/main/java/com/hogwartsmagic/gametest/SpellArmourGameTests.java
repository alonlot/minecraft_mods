package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.hogwartsmagic.spell.damage.DamageSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;

/** Armour counts for half against spells; Warding cuts spells further and is exclusive with Protection. */
@GameTestHolder(ExampleMod.MODID)
public final class SpellArmourGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final float HIT = 10.0F;

    private SpellArmourGameTests() {
    }

    private static Husk target(GameTestHelper helper, int x, boolean armour, int warding) {
        return target(helper, x, armour, warding, false);
    }

    private static Husk target(GameTestHelper helper, int x, boolean armour, int warding, boolean netherite) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(x % 3, 2, x / 3));
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
        husk.setHealth(100.0F);
        if (armour) {
            var enchantments = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack piece = new ItemStack(switch (slot.getIndex()) {
                    case 3 -> netherite ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET;
                    case 2 -> netherite ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
                    case 1 -> netherite ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS;
                    default -> netherite ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS;
                });
                if (warding > 0) {
                    piece.enchant(enchantments.getHolderOrThrow(CrowdControl.WARDING), warding);
                }
                husk.setItemSlot(slot, piece);
            }
        }
        return husk;
    }

    private static float spellHit(GameTestHelper helper, Husk husk) {
        float before = husk.getHealth();
        husk.invulnerableTime = 0;
        husk.hurt(DamageSupport.spell(helper.getLevel(), null, null), HIT);
        return before - husk.getHealth();
    }

    @GameTest(template = EMPTY)
    public static void armourAndWardingSoftenSpells(GameTestHelper helper) {
        Husk bare = target(helper, 0, false, 0);
        Husk plated = target(helper, 1, true, 0);
        Husk warded = target(helper, 2, true, 4);
        Husk best = target(helper, 3, true, 4, true);
        // Worn armour reaches the ARMOR attribute only on the wearer's next tick.
        helper.runAfterDelay(3, () -> {
            float bareLoss = spellHit(helper, bare);
            float platedLoss = spellHit(helper, plated);
            float wardedLoss = spellHit(helper, warded);
            // A husk has 2 natural armour, so even "bare" is softened a touch.
            helper.assertTrue(bareLoss > 9.0F, "a nearly bare target takes the spell, lost " + bareLoss);
            // Full diamond: vanilla armour would leave ~2.4 of 10; 40% armour leaves most of it (~7.9).
            helper.assertTrue(platedLoss > 7.0F && platedLoss < 9.0F, "armour only softens spells, lost " + platedLoss);
            // Warding IV on all four pieces: a third off at most, so the target is still plainly hurt (~5.4).
            helper.assertTrue(wardedLoss > platedLoss * 0.6F && wardedLoss < platedLoss * 0.75F,
                    "Warding softens spells but never shuts them out, lost " + wardedLoss);
            // The best defence there is: full netherite, Warding IV on every piece. Still easily hurt (~5.3 of 10).
            float bestLoss = spellHit(helper, best);
            helper.assertTrue(bestLoss > 4.5F, "netherite + Warding IV still takes half a spell, lost " + bestLoss);
            helper.assertTrue(CrowdControl.wardingLevels(warded) == 16, "levels summed over the four pieces");

            Enchantment protection = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION);
            var warding = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(CrowdControl.WARDING);
            var protectionHolder = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.PROTECTION);
            helper.assertFalse(Enchantment.areCompatible(warding, protectionHolder), "Warding and Protection exclude each other");
            helper.assertTrue(protection != null, "protection present");
            for (Husk husk : new Husk[]{bare, plated, warded, best}) {
                husk.discard(); // no stray husks left to wander into the neighbouring tests
            }
            helper.succeed();
        });
    }

    /** Auror robes: barely leather against a blow, netherite against a spell. */
    @GameTest(template = EMPTY)
    public static void wizardRobesWardSpellsNotBlows(GameTestHelper helper) {
        Husk robed = target(helper, 0, false, 0);
        Husk bladeTest = target(helper, 1, false, 0);
        for (Husk husk : new Husk[]{robed, bladeTest}) {
            husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ExampleMod.AUROR_HOOD.get()));
            husk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ExampleMod.AUROR_COAT.get()));
            husk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ExampleMod.DEATH_EATER_TROUSERS.get()));
            husk.setItemSlot(EquipmentSlot.FEET, new ItemStack(ExampleMod.DEATH_EATER_BOOTS.get()));
        }
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(robed.getArmorValue() == 7, "robes are 5 armour (+2 husk), got " + robed.getArmorValue());
            // Netherite against a 10-point blow leaves ~2.8; the robes against a 10-point spell leave ~2.5.
            float spellLoss = spellHit(helper, robed);
            helper.assertTrue(spellLoss > 1.5F && spellLoss < 3.2F, "robes ward spells like netherite, lost " + spellLoss);
            float before = bladeTest.getHealth();
            bladeTest.invulnerableTime = 0;
            bladeTest.hurt(helper.getLevel().damageSources().generic(), HIT);
            float bladeLoss = before - bladeTest.getHealth();
            helper.assertTrue(bladeLoss > 8.5F, "robes barely stop a blow, lost " + bladeLoss);
            robed.discard();
            bladeTest.discard();
            helper.succeed();
        });
    }
}
