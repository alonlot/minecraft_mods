package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.menu.BroomWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class BroomWorkbenchBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;
    public static final int SLOT_C = 2;
    public static final int SLOT_D = 3;
    public static final int SLOT_RESULT = 4;
    public static final int BROOM_CRAFT_TICKS_TOTAL = 110;

    private int craftTicksRemaining;
    private int craftAccentPhase;

    private final ItemStackHandler inventory = new ItemStackHandler(5) {
        @Override
        protected void onContentsChanged(int slot) {
            BroomWorkbenchBlockEntity.this.onInventoryChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_RESULT) {
                return false;
            }
            if (slot == SLOT_A) {
                return stack.is(ExampleMod.FINE_WOOD_STICK.get())
                        || stack.is(ExampleMod.BROOM.get())
                        || stack.is(ExampleMod.NIMBUS_2000.get());
            }
            if (slot == SLOT_B) {
                return stack.is(Items.WHEAT)
                        || stack.is(Items.FEATHER)
                        || stack.is(Items.NETHERITE_INGOT);
            }
            if (slot == SLOT_C) {
                return stack.is(ExampleMod.ARCANE_DUST.get())
                        || stack.is(Items.GOLD_BLOCK)
                        || stack.is(Items.PHANTOM_MEMBRANE);
            }
            if (slot == SLOT_D) {
                return stack.is(Items.IRON_INGOT)
                        || stack.is(ExampleMod.ARCANE_CORE.get())
                        || stack.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get());
            }
            return false;
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> craftTicksRemaining;
                case 1 -> isInfusing() ? BROOM_CRAFT_TICKS_TOTAL : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public BroomWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.BROOM_WORKBENCH_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public ContainerData getDataAccess() {
        return this.dataAccess;
    }

    public int getCraftTicksRemaining() {
        return this.craftTicksRemaining;
    }

    public boolean isInfusing() {
        return this.craftTicksRemaining > 0;
    }

    private void onInventoryChanged() {
        this.setChanged();
        this.syncToClients();
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BroomWorkbenchBlockEntity be) {
        be.serverTick(level, pos);
    }

    private void serverTick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack result = this.inventory.getStackInSlot(SLOT_RESULT);
        if (!result.isEmpty()) {
            if (this.craftTicksRemaining > 0) {
                this.craftTicksRemaining = 0;
                this.syncToClients();
            }
            return;
        }

        if (this.craftTicksRemaining <= 0) {
            return;
        }

        if (!canCraftBroom()) {
            this.craftTicksRemaining = 0;
            this.craftAccentPhase = 0;
            this.syncToClients();
            return;
        }

        this.tickInfusionEffects(serverLevel, pos);
        this.craftTicksRemaining--;
        if (this.craftTicksRemaining <= 0) {
            this.finishCrafting();
            this.craftTicksRemaining = 0;
            this.syncToClients();
        } else if (this.craftTicksRemaining % 4 == 0) {
            this.syncToClients();
        }
    }

    /**
     * Called when the Alatus charm hits this bench. Starts timed infusion if the recipe is valid and the bench is idle.
     *
     * @return true if infusion started (or bench was already valid target — caller may play impact FX)
     */
    public boolean beginInfusionFromSpell(ServerLevel level) {
        if (this.craftTicksRemaining > 0) {
            return false;
        }
        if (!this.inventory.getStackInSlot(SLOT_RESULT).isEmpty()) {
            return false;
        }
        if (!canCraftBroom()) {
            return false;
        }

        this.craftTicksRemaining = BROOM_CRAFT_TICKS_TOTAL;
        this.craftAccentPhase = 0;
        BlockPos pos = this.getBlockPos();
        Vec3 center = Vec3.atBottomCenterOf(pos).add(0.0, 0.62, 0.0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.15f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WHIRL, SoundSource.BLOCKS, 0.5f, 1.25f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55f, 1.1f);
        this.spawnInfusionStartBurst(level, pos);
        this.setChanged();
        this.syncToClients();
        return true;
    }

    private boolean canCraftBroom() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
            return false;
        }
        if (a.is(ExampleMod.FINE_WOOD_STICK.get()) && b.is(Items.WHEAT) && c.is(ExampleMod.ARCANE_DUST.get()) && d.is(Items.IRON_INGOT)) {
            return true;
        }
        if (a.is(ExampleMod.BROOM.get()) && b.is(Items.PHANTOM_MEMBRANE) && c.is(Items.NETHERITE_INGOT) && d.is(ExampleMod.ARCANE_CORE.get())) {
            return true;
        }
        return a.is(ExampleMod.NIMBUS_2000.get()) && b.is(Items.NETHERITE_INGOT) && c.is(Items.ELYTRA) && d.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get());
    }

    @Nullable
    private ItemStack getBroomResultForCurrentInputs() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
            return null;
        }
        if (a.is(ExampleMod.FINE_WOOD_STICK.get()) && b.is(Items.WHEAT) && c.is(ExampleMod.ARCANE_DUST.get()) && d.is(Items.IRON_INGOT)) {
            return new ItemStack(ExampleMod.BROOM.get());
        }
        if (a.is(ExampleMod.BROOM.get()) && b.is(Items.PHANTOM_MEMBRANE) && c.is(Items.NETHERITE_INGOT) && d.is(ExampleMod.ARCANE_CORE.get())) {
            return new ItemStack(ExampleMod.NIMBUS_2000.get());
        }
        if (a.is(ExampleMod.NIMBUS_2000.get()) && b.is(Items.NETHERITE_INGOT) && c.is(Items.ELYTRA) && d.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get())) {
            return new ItemStack(ExampleMod.FIREBOLT.get());
        }
        return null;
    }

    private void finishCrafting() {
        ItemStack out = getBroomResultForCurrentInputs();
        if (out == null) {
            return;
        }
        this.inventory.extractItem(SLOT_A, 1, false);
        this.inventory.extractItem(SLOT_B, 1, false);
        this.inventory.extractItem(SLOT_C, 1, false);
        this.inventory.extractItem(SLOT_D, 1, false);
        this.inventory.setStackInSlot(SLOT_RESULT, out);

        Level level = this.level;
        if (level != null) {
            BlockPos pos = this.getBlockPos();
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.72;
            double z = pos.getZ() + 0.5;
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.35, 0.2, 0.35, 0.04);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.4, 0.15, 0.4, 0.0);
                serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 14, 0.45, 0.18, 0.45, 0.02);
                serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y + 0.1, z, 12, 0.5, 0.25, 0.5, 0.03);
            }
            level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.28f, 1.35f);
            level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.BLOCKS, 0.4f, 1.1f);
            level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.55f, 1.2f);
        }
        this.setChanged();
    }

    private void spawnInfusionStartBurst(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.58;
        double cz = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.ENCHANT, cx, cy + 0.15, cz, 12, 0.4, 0.22, 0.4, 0.03);
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 8, 0.35, 0.12, 0.35, 0.02);
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * Math.PI * 2;
            double px = cx + Math.cos(a) * 0.55;
            double pz = cz + Math.sin(a) * 0.55;
            level.sendParticles(ParticleTypes.SMALL_GUST, px, cy, pz, 1, 0.12, 0.06, 0.12, 0.015);
        }
    }

    private void tickInfusionEffects(ServerLevel level, BlockPos pos) {
        int total = BROOM_CRAFT_TICKS_TOTAL;
        int elapsed = total - this.craftTicksRemaining;
        RandomSource rand = level.random;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.58;
        double cz = pos.getZ() + 0.5;
        long time = level.getGameTime();

        for (int i = 0; i < 5; i++) {
            double ox = (rand.nextDouble() - 0.5) * 1.1;
            double oy = rand.nextDouble() * 0.9;
            double oz = (rand.nextDouble() - 0.5) * 1.1;
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx + ox, cy + oy * 0.35, cz + oz, 1, 0.06, 0.04, 0.06, 0.003);
            if (rand.nextFloat() < 0.45f) {
                level.sendParticles(ParticleTypes.ENCHANT, cx + ox * 0.9, cy + oy * 0.4, cz + oz * 0.9, 1, 0.1, 0.06, 0.1, 0.015);
            }
        }

        double spin = time * 0.32 + elapsed * 0.05;
        for (int s = 0; s < 7; s++) {
            double ang = spin + s * Math.PI * 2 / 7;
            double r = 0.55 + 0.12 * Math.sin(time * 0.08);
            double px = cx + Math.cos(ang) * r;
            double pz = cz + Math.sin(ang) * r;
            double py = cy + rand.nextDouble() * 0.45;
            level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 1, 0.08, 0.05, 0.08, 0.008);
            if (s % 2 == 0) {
                level.sendParticles(ParticleTypes.WHITE_SMOKE, px, py, pz, 1, 0.06, 0.04, 0.06, 0.004);
            }
        }

        if (this.craftTicksRemaining % 6 == 0) {
            level.sendParticles(ParticleTypes.SMALL_GUST, cx, cy + 0.1, cz, 2, 0.4, 0.12, 0.4, 0.02);
        }
        if (elapsed > total / 2 && this.craftTicksRemaining % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.2, cz, 2, 0.25, 0.1, 0.25, 0.025);
        }

        this.craftAccentPhase++;
        if (this.craftAccentPhase >= 28) {
            this.craftAccentPhase = 0;
            level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.22f, 0.95f + rand.nextFloat() * 0.2f);
            level.playSound(null, cx, cy, cz, SoundEvents.BREEZE_IDLE_AIR, SoundSource.BLOCKS, 0.18f, 0.9f + rand.nextFloat() * 0.35f);
        }
        if (this.craftTicksRemaining % 16 == 0) {
            level.playSound(null, cx, cy, cz, SoundEvents.BREEZE_WHIRL, SoundSource.BLOCKS, 0.28f, 1.05f + rand.nextFloat() * 0.2f);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            try {
                this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            } catch (RuntimeException exception) {
                for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                    this.inventory.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
        this.craftTicksRemaining = Math.max(0, tag.getInt("CraftRemaining"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.hogwartsmagic.broom_workbench");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new BroomWorkbenchMenu(containerId, inv, this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return AABB.encapsulatingFullBlocks(getBlockPos(), getBlockPos().above(1));
    }

    public void dropAllContents(Level level, BlockPos pos) {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                this.inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
