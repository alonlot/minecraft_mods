package com.hogwartsmagic.assignment;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.item.AssignmentItem;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Every chest that can roll a spell book calls this instead of creating the book itself. The spell is
 * already chosen (from the chest's pool, weighted by rarity), so the assignment is always for the same
 * spell, in the same chests, at the same rarity weight as its book.
 */
public final class AssignmentLoot {
    private AssignmentLoot() {
    }

    public static ItemStack bookOrAssignment(SpellType spell, RandomSource random) {
        if (AssignmentRegistry.has(spell) && random.nextDouble() < Config.assignmentShareOfBooks) {
            return AssignmentItem.create(spell);
        }
        return MagicBookItem.createBook(spell);
    }
}
