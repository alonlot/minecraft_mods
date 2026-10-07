package com.hogwartsmagic.client;

import com.hogwartsmagic.block.entity.WizardCauldronBlockEntity;
import com.hogwartsmagic.brewing.BrewRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Colours the Wizard Cauldron's brew face (tintindex 0). The colour comes from the block entity, which the server
 * keeps in sync; a cauldron with no block entity yet, and the item, show the classic green.
 */
public final class WizardCauldronColors {
    private WizardCauldronColors() {
    }

    public static int block(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof WizardCauldronBlockEntity cauldron) {
            return FastColor.ARGB32.opaque(cauldron.getColor());
        }
        return FastColor.ARGB32.opaque(BrewRules.CLASSIC_GREEN);
    }

    public static int item(ItemStack stack, int tintIndex) {
        return tintIndex == 0 ? FastColor.ARGB32.opaque(BrewRules.CLASSIC_GREEN) : -1;
    }
}
