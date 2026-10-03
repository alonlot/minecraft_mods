package com.hogwartsmagic.menu;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class BroomWorkbenchMenu extends AbstractContainerMenu {

    private static final ItemStackHandler INVALID_PLACEHOLDER = new ItemStackHandler(5);
    private static final ContainerData ZERO_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public static final int SLOT_A = BroomWorkbenchBlockEntity.SLOT_A;
    public static final int SLOT_B = BroomWorkbenchBlockEntity.SLOT_B;
    public static final int SLOT_C = BroomWorkbenchBlockEntity.SLOT_C;
    public static final int SLOT_D = BroomWorkbenchBlockEntity.SLOT_D;
    public static final int RESULT_SLOT = BroomWorkbenchBlockEntity.SLOT_RESULT;
    private static final int PLAYER_SLOTS_START = 5;

    private final ContainerLevelAccess access;
    private final BroomWorkbenchBlockEntity blockEntity;
    private final boolean valid;

    public static BroomWorkbenchMenu createInvalid(int containerId, Inventory inventory, BlockPos pos) {
        return new BroomWorkbenchMenu(containerId, inventory, null, ContainerLevelAccess.create(inventory.player.level(), pos), false);
    }

    public BroomWorkbenchMenu(int containerId, Inventory inventory, BroomWorkbenchBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), true);
    }

    private BroomWorkbenchMenu(int containerId, Inventory inventory, @Nullable BroomWorkbenchBlockEntity blockEntity, ContainerLevelAccess access, boolean valid) {
        super(ExampleMod.BROOM_WORKBENCH_MENU.get(), containerId);
        this.access = access;
        this.blockEntity = blockEntity;
        this.valid = valid;

        if (valid && blockEntity != null) {
            addBenchSlots(blockEntity.getInventory(), blockEntity.getDataAccess(), true);
        } else {
            addBenchSlots(INVALID_PLACEHOLDER, ZERO_DATA, false);
        }

        addPlayerInventory(inventory);
    }

    private void addBenchSlots(ItemStackHandler handler, ContainerData data, boolean interactive) {
        this.addSlot(new SlotItemHandler(handler, SLOT_A, WorkbenchLayout.broomInputSlotX(0), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, SLOT_B, WorkbenchLayout.broomInputSlotX(1), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, SLOT_C, WorkbenchLayout.broomInputSlotX(2), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, SLOT_D, WorkbenchLayout.broomInputSlotX(3), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, RESULT_SLOT, WorkbenchLayout.BROOM_RESULT_SLOT_X, WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addDataSlots(data);
    }

    public int getCraftTicksRemaining() {
        return this.valid && this.blockEntity != null ? this.blockEntity.getCraftTicksRemaining() : 0;
    }

    public int getCraftTotalTicks() {
        return this.valid && this.blockEntity != null && this.blockEntity.isInfusing()
                ? this.blockEntity.getCraftTotalTicks()
                : 0;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, WorkbenchLayout.PLAYER_INV_Y + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            this.addSlot(new Slot(inventory, hotbar, 8 + hotbar * 18, WorkbenchLayout.HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (!this.valid || this.blockEntity == null || this.blockEntity.isRemoved()) {
            return false;
        }
        return stillValid(this.access, player, ExampleMod.BROOM_WORKBENCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index == RESULT_SLOT) {
                if (!this.moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_START + 36, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, itemstack);
            } else if (index < PLAYER_SLOTS_START) {
                if (!this.moveItemStackTo(stack, PLAYER_SLOTS_START, PLAYER_SLOTS_START + 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, SLOT_A, SLOT_D + 1, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
        }
        return itemstack;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }
}
