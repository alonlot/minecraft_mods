package com.hogwartsmagic.menu;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.WandWorkbenchBlockEntity;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

public class WandWorkbenchMenu extends AbstractContainerMenu {
    private static final String STARTER_MAGIC_AWARDED_KEY = "StarterMagicAwarded";

    private static final ItemStackHandler INVALID_PLACEHOLDER = new ItemStackHandler(4);
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
    public static final int SLOT_STICK = 0;
    public static final int SLOT_CORE = 1;
    public static final int SLOT_CATALYST = 2;
    public static final int RESULT_SLOT = 3;
    private static final int PLAYER_SLOTS_START = 4;

    private final ContainerLevelAccess access;
    private final WandWorkbenchBlockEntity blockEntity;
    private final boolean valid;

    /**
     * Client fallback when the block entity is not loaded (should close immediately).
     */
    public static WandWorkbenchMenu createInvalid(int containerId, Inventory inventory, BlockPos pos) {
        return new WandWorkbenchMenu(containerId, inventory, null, ContainerLevelAccess.create(inventory.player.level(), pos), false);
    }

    public WandWorkbenchMenu(int containerId, Inventory inventory, WandWorkbenchBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), true);
    }

    private WandWorkbenchMenu(int containerId, Inventory inventory, @Nullable WandWorkbenchBlockEntity blockEntity, ContainerLevelAccess access, boolean valid) {
        super(ExampleMod.WAND_WORKBENCH_MENU.get(), containerId);
        this.access = access;
        this.blockEntity = blockEntity;
        this.valid = valid;

        if (valid && blockEntity != null) {
            addWorkbenchSlots(blockEntity.getInventory(), blockEntity.getDataAccess(), true);
        } else {
            addWorkbenchSlots(INVALID_PLACEHOLDER, ZERO_DATA, false);
        }

        addPlayerInventory(inventory);
    }

    private void addWorkbenchSlots(ItemStackHandler handler, ContainerData data, boolean interactive) {
        this.addSlot(new SlotItemHandler(handler, SLOT_STICK, WorkbenchLayout.wandInputSlotX(0), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return interactive && stack.is(ExampleMod.FINE_WOOD_STICK.get());
            }

            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, SLOT_CORE, WorkbenchLayout.wandInputSlotX(1), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return interactive && (stack.isEmpty()
                        || stack.is(ExampleMod.PHOENIX_FEATHER_CORE.get())
                        || stack.is(ExampleMod.UNICORN_HAIR_CORE.get())
                        || stack.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get()));
            }

            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, SLOT_CATALYST, WorkbenchLayout.wandInputSlotX(2), WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return interactive && stack.is(ExampleMod.ARCANE_CORE.get());
            }

            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }
        });
        this.addSlot(new SlotItemHandler(handler, RESULT_SLOT, WorkbenchLayout.WAND_RESULT_SLOT_X, WorkbenchLayout.CRAFT_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return interactive;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                grantStarterMagicIfNeeded(player, stack);
            }
        });
        this.addDataSlots(data);
    }

    private static void grantStarterMagicIfNeeded(Player player, ItemStack craftedStack) {
        if (!(player instanceof ServerPlayer serverPlayer) || !MagicWandItem.isAnyWand(craftedStack)) {
            return;
        }

        net.minecraft.nbt.CompoundTag persisted = serverPlayer.getPersistentData().getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        if (modData.getBoolean(STARTER_MAGIC_AWARDED_KEY)) {
            return;
        }

        if (!MagicBookItem.learnSpellIfAbsent(serverPlayer, SpellType.LUMOS)) {
            return;
        }

        modData.putBoolean(STARTER_MAGIC_AWARDED_KEY, true);
        persisted.put(ExampleMod.MODID, modData);
        serverPlayer.getPersistentData().put("PlayerPersisted", persisted);
        serverPlayer.displayClientMessage(Component.translatable("gui.hogwartsmagic.starter_magic_awarded",
                Component.translatable("spell.hogwartsmagic.lumos")), false);
        serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                com.hogwartsmagic.ModSounds.SPELL_LEARN.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    public int getCraftTicksRemaining() {
        return this.valid && this.blockEntity != null ? this.blockEntity.getCraftTicksRemaining() : 0;
    }

    public int getCraftTotalTicks() {
        return this.valid && this.blockEntity != null && this.blockEntity.isCrafting()
                ? WandWorkbenchBlockEntity.WAND_CRAFT_TICKS_TOTAL
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
        return stillValid(this.access, player, ExampleMod.WAND_WORKBENCH.get());
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
            } else if (!this.moveItemStackTo(stack, SLOT_STICK, SLOT_CATALYST + 1, false)) {
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
