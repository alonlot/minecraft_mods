package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ThemedGui.Theme;
import com.hogwartsmagic.menu.WandWorkbenchMenu;
import com.hogwartsmagic.menu.WorkbenchLayout;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class WandWorkbenchScreen extends WorkbenchScreen<WandWorkbenchMenu> {
    /** Midnight violet with old gold, Ollivander's shop at closing time. */
    private static final Theme THEME = ThemedGui.VIOLET;

    private static final String[] SLOT_KEYS = {
            "gui.hogwartsmagic.wand_workbench.slot.wood",
            "gui.hogwartsmagic.wand_workbench.slot.core",
            "gui.hogwartsmagic.wand_workbench.slot.catalyst"
    };

    /** Mirrors WandWorkbenchBlockEntity#getWandResultForCurrentInputs; a plain wand when the core is left empty. */
    private static List<BenchRecipe> recipes;

    public WandWorkbenchScreen(WandWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static BenchRecipe wand(@Nullable Item core, Item result) {
        return new BenchRecipe(List.of(
                List.of(new ItemStack(ExampleMod.FINE_WOOD_STICK.get())),
                core == null ? List.of() : List.of(new ItemStack(core)),
                List.of(new ItemStack(ExampleMod.ARCANE_CORE.get()))),
                List.of(new ItemStack(result)), null);
    }

    @Override
    protected List<BenchRecipe> recipes() {
        if (recipes == null) {
            recipes = List.of(
                    wand(ExampleMod.PHOENIX_FEATHER_CORE.get(), ExampleMod.PHOENIX_CORE_WAND.get()),
                    wand(ExampleMod.UNICORN_HAIR_CORE.get(), ExampleMod.UNICORN_CORE_WAND.get()),
                    wand(ExampleMod.DRAGON_HEARTSTRING_CORE.get(), ExampleMod.DRAGON_CORE_WAND.get()),
                    wand(null, ExampleMod.HARRY_POTTER_WAND.get()));
        }
        return recipes;
    }

    @Override
    protected Theme theme() {
        return THEME;
    }

    @Override
    protected int inputCount() {
        return 3;
    }

    @Override
    protected int inputSlotX(int i) {
        return WorkbenchLayout.wandInputSlotX(i);
    }

    @Override
    protected int resultSlotX() {
        return WorkbenchLayout.WAND_RESULT_SLOT_X;
    }

    @Override
    protected Component slotName(int i) {
        return Component.translatable(SLOT_KEYS[i]);
    }

    @Nullable
    @Override
    protected Component slotHint(int i) {
        return i == WandWorkbenchMenu.SLOT_CORE ? Component.translatable("gui.hogwartsmagic.wand_workbench.core_optional") : null;
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
        return Component.translatable("gui.hogwartsmagic.wand_workbench.crafting");
    }

    @Override
    protected Component readyText() {
        // Forging starts on its own the next server tick, so "ready" is only ever a flicker.
        return Component.translatable("gui.hogwartsmagic.wand_workbench.crafting");
    }

    @Override
    protected Component bookTitle() {
        return Component.translatable("gui.hogwartsmagic.wand_workbench.recipes_title");
    }

    @Override
    protected Component bookHint() {
        return Component.translatable("gui.hogwartsmagic.wand_workbench.book_hint");
    }
}
