package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.SpellTransmuterBlockEntity;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.enchant.SpellEnchantmentEffects;
import com.hogwartsmagic.spell.enchant.SpellEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraftforge.gametest.GameTestHolder;

/** The Spell Transmuter's rite, and the rules of the enchantments it makes. */
@GameTestHolder(ExampleMod.MODID)
public final class SpellTransmuterGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private SpellTransmuterGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void protegoBindsIntoWardingOne(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, ExampleMod.SPELL_TRANSMUTER.get());
        SpellTransmuterBlockEntity be = (SpellTransmuterBlockEntity) helper.getBlockEntity(rel);
        var level = helper.getLevel();

        ItemStack stupefy = MagicBookItem.createBook(SpellType.STUPEFY);
        helper.assertTrue(SpellTransmuterBlockEntity.slotFor(stupefy) < 0, "a spell with no enchantment is refused");
        helper.assertFalse(be.insert(level, stupefy), "and not taken");

        helper.assertTrue(be.insert(level, MagicBookItem.createBook(SpellType.PROTEGO)), "the spell book goes on");
        helper.assertFalse(be.insert(level, MagicBookItem.createBook(SpellType.PROTEGO)), "only one spell book");
        helper.assertTrue(be.insert(level, new ItemStack(Items.BOOK)), "the plain book goes on");
        helper.assertTrue(be.rite() < 0, "no rite without the dust");
        ItemStack dust = new ItemStack(ExampleMod.ARCANE_DUST.get(), 5);
        helper.assertTrue(be.insert(level, dust), "the dust goes on");
        helper.assertTrue(dust.getCount() == 4, "one dust taken, not the stack");
        helper.assertTrue(be.rite() == 0, "all three start the rite");
        helper.assertTrue(be.takeOne().isEmpty(), "nothing comes off mid-rite");

        helper.runAfterDelay(SpellTransmuterBlockEntity.RITE_TICKS + 5, () -> {
            ItemStack book = be.result();
            helper.assertTrue(book.is(Items.ENCHANTED_BOOK), "an enchanted book came out, got " + book);
            ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            int warding = SpellEnchantments.holder(level, SpellEnchantments.WARDING)
                    .map(stored::getLevel).orElse(0);
            helper.assertTrue(warding == 1, "it holds Warding I, got " + warding);
            helper.assertTrue(be.slot(0).isEmpty() && be.slot(1).isEmpty() && be.slot(2).isEmpty(), "the three were used");
            helper.assertTrue(be.takeOne().is(Items.ENCHANTED_BOOK), "an empty hand takes the book");
            helper.assertTrue(be.isEmpty(), "and the transmuter is clear again");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void everySpellBindingExists(GameTestHelper helper) {
        for (var entry : SpellEnchantments.FROM_SPELL.entrySet()) {
            helper.assertTrue(SpellEnchantments.holder(helper.getLevel(), entry.getValue()).isPresent(),
                    entry.getKey() + " binds into a missing enchantment " + entry.getValue().location());
        }
        helper.assertTrue(SpellEnchantmentEffects.douseDelay(1) == 60, "Douse I waits 3 s");
        helper.assertTrue(SpellEnchantmentEffects.douseDelay(5) == 0, "Douse V puts fire out at once");
        helper.succeed();
    }
}
