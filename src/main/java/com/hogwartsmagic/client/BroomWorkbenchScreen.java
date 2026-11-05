package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity;
import com.hogwartsmagic.menu.BroomWorkbenchMenu;
import com.hogwartsmagic.menu.WorkbenchLayout;
import com.hogwartsmagic.spell.motion.AlatusRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class BroomWorkbenchScreen extends WorkbenchScreen<BroomWorkbenchMenu> {
    /** Oiled oak and brass, a Quidditch shed workbench. */
    private static final Theme THEME = ThemedGui.OAK;

    private static final String[] SLOT_KEYS = {
            "gui.hogwartsmagic.broom_workbench.slot.base",
            "gui.hogwartsmagic.broom_workbench.slot.boost",
            "gui.hogwartsmagic.broom_workbench.slot.magic",
            "gui.hogwartsmagic.broom_workbench.slot.catalyst"
    };

    private static List<BenchRecipe> recipes;
    private static BenchRecipe reinfuse;

    public BroomWorkbenchScreen(BroomWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected List<BenchRecipe> recipes() {
        if (recipes == null) {
            List<BenchRecipe> out = new ArrayList<>();
            // Same list the block entity filters slots and crafts from, so the book can never drift.
            for (BroomWorkbenchBlockEntity.BroomRecipe r : BroomWorkbenchBlockEntity.recipes()) {
                out.add(new BenchRecipe(List.of(
                        List.of(new ItemStack(r.base())),
                        List.of(new ItemStack(r.boost())),
                        List.of(new ItemStack(r.magic())),
                        List.of(new ItemStack(r.catalyst()))),
                        List.of(new ItemStack(r.result())), null));
            }
            List<ItemStack> brooms = List.of(new ItemStack(ExampleMod.BROOM.get()),
                    new ItemStack(ExampleMod.NIMBUS_2000.get()), new ItemStack(ExampleMod.FIREBOLT.get()));
            reinfuse = new BenchRecipe(List.of(
                    brooms,
                    List.of(),
                    List.of(new ItemStack(ExampleMod.ARCANE_DUST.get(), AlatusRules.REINFUSE_DUST_COST)),
                    List.of()),
                    brooms, Component.translatable("gui.hogwartsmagic.broom_workbench.reinfuse_note"));
            out.add(reinfuse);
            recipes = List.copyOf(out);
        }
        return recipes;
    }

    @Override
    protected ItemStack predictedResult() {
        BenchRecipe recipe = this.matchedRecipe();
        if (recipe != null && recipe == reinfuse) {
            return this.inputStack(BroomWorkbenchMenu.SLOT_A).copyWithCount(1);
        }
        return super.predictedResult();
    }

    @Override
    protected Theme theme() {
        return THEME;
    }

    @Override
    protected int inputCount() {
        return 4;
    }

    @Override
    protected int inputSlotX(int i) {
        return WorkbenchLayout.broomInputSlotX(i);
    }

    @Override
    protected int resultSlotX() {
        return WorkbenchLayout.BROOM_RESULT_SLOT_X;
    }

    @Override
    protected Component slotName(int i) {
        return Component.translatable(SLOT_KEYS[i]);
    }

    @Override
    protected int craftTotalTicks() {
        return this.menu.getCraftTotalTicks();
    }

    @Override
    protected int craftTicksRemaining() {
        return this.menu.getCraftTicksRemaining();
    }

    @Override
    protected Component craftingText() {
        return Component.translatable("gui.hogwartsmagic.broom_workbench.infusing");
    }

    @Override
    protected Component readyText() {
        return Component.translatable("gui.hogwartsmagic.broom_workbench.ready");
    }

    @Override
    protected Component bookTitle() {
        return Component.translatable("gui.hogwartsmagic.broom_workbench.recipes_title");
    }

    @Override
    protected Component bookHint() {
        return Component.translatable("gui.hogwartsmagic.broom_workbench.book_hint");
    }
}
