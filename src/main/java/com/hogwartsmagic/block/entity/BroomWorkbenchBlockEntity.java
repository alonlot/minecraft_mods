package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.BroomItem;
import com.hogwartsmagic.menu.BroomWorkbenchMenu;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.motion.AlatusRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BroomWorkbenchBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;
    public static final int SLOT_C = 2;
    public static final int SLOT_D = 3;
    public static final int SLOT_RESULT = 4;
    public static final int BROOM_CRAFT_TICKS_TOTAL = 110;

    private int craftTicksRemaining;
    private int craftAccentPhase;
    /** Length of the current infusion, from the caster's Alatus rank (Spell Mastery Phase 7b). */
    private int craftTotalTicks = BROOM_CRAFT_TICKS_TOTAL;
    /** The caster's Alatus rank at the start of the current infusion; it decides the quality roll. */
    private int infusionRank = 1;
    /** True when the current infusion is a Master re-infusion; fixed when the infusion starts. */
    private boolean infusionReinfuse;

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
            // A finished broom of any kind may sit in Base for a Master's re-infusion.
            if (slot == SLOT_A && stack.getItem() instanceof BroomItem) {
                return true;
            }
            // Derived from the recipes, so every ingredient of every recipe fits its slot. The old
            // hand-written lists refused the Nimbus and Firebolt ingredients.
            for (BroomRecipe recipe : recipes()) {
                Item wanted = recipe.slot(slot);
                if (wanted != null && stack.is(wanted)) {
                    return true;
                }
            }
            return false;
        }
    };

    /** One broom recipe, in slot order: Base (A), Boost (B), Magic (C), Catalyst (D). */
    public record BroomRecipe(Item base, Item boost, Item magic, Item catalyst, Item result) {
        @Nullable
        public Item slot(int slot) {
            if (slot == SLOT_A) {
                return this.base;
            }
            if (slot == SLOT_B) {
                return this.boost;
            }
            if (slot == SLOT_C) {
                return this.magic;
            }
            if (slot == SLOT_D) {
                return this.catalyst;
            }
            return null;
        }

        boolean matches(ItemStack a, ItemStack b, ItemStack c, ItemStack d) {
            return a.is(this.base) && b.is(this.boost) && c.is(this.magic) && d.is(this.catalyst);
        }
    }

    /** The single source for the slot filter, the recipe check and the result (the recipe book text matches). */
    private static List<BroomRecipe> recipesCache;

    public static List<BroomRecipe> recipes() {
        List<BroomRecipe> cached = recipesCache;
        if (cached == null) {
            cached = buildRecipes();
            recipesCache = cached;
        }
        return cached;
    }

    private static List<BroomRecipe> buildRecipes() {
        return List.of(
                new BroomRecipe(ExampleMod.FINE_WOOD_STICK.get(), Items.WHEAT, ExampleMod.ARCANE_DUST.get(),
                        Items.IRON_INGOT, ExampleMod.BROOM.get()),
                new BroomRecipe(ExampleMod.BROOM.get(), Items.PHANTOM_MEMBRANE, Items.NETHERITE_INGOT,
                        ExampleMod.ARCANE_CORE.get(), ExampleMod.NIMBUS_2000.get()),
                new BroomRecipe(ExampleMod.NIMBUS_2000.get(), Items.NETHERITE_INGOT, Items.ELYTRA,
                        ExampleMod.DRAGON_HEARTSTRING_CORE.get(), ExampleMod.FIREBOLT.get()));
    }

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> craftTicksRemaining;
                case 1 -> isInfusing() ? craftTotalTicks : 0;
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

    public int getCraftTotalTicks() {
        return this.craftTotalTicks;
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

        if (this.infusionReinfuse ? !canReinfuse() : !canCraftBroom()) {
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
    public boolean beginInfusionFromSpell(ServerLevel level, @Nullable Entity caster) {
        if (this.craftTicksRemaining > 0) {
            return false;
        }
        if (!this.inventory.getStackInSlot(SLOT_RESULT).isEmpty()) {
            return false;
        }
        boolean crafting = canCraftBroom();
        boolean reinfusing = !crafting && canReinfuse();
        if (!crafting && !reinfusing) {
            return false;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALATUS);
        if (reinfusing && !AlatusRules.reinfuses(rank)) {
            if (CombatEffects.responsible(caster) instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.alatus.reinfuse_master")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return false;
        }
        this.infusionReinfuse = reinfusing;

        this.infusionRank = rank;
        this.craftTotalTicks = AlatusRules.infusionTicks(rank);
        this.craftTicksRemaining = this.craftTotalTicks;
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

    /** Master re-infusion: a finished broom in Base, enough Arcane Dust in Magic, Boost and Catalyst empty. */
    private boolean canReinfuse() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        return a.getItem() instanceof BroomItem && b.isEmpty() && d.isEmpty()
                && c.is(ExampleMod.ARCANE_DUST.get()) && c.getCount() >= AlatusRules.REINFUSE_DUST_COST;
    }

    private boolean canCraftBroom() {
        return currentRecipe() != null;
    }

    @Nullable
    private BroomRecipe currentRecipe() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
            return null;
        }
        for (BroomRecipe recipe : recipes()) {
            if (recipe.matches(a, b, c, d)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack getBroomResultForCurrentInputs() {
        BroomRecipe recipe = currentRecipe();
        return recipe == null ? null : new ItemStack(recipe.result());
    }

    private void finishCrafting() {
        ItemStack out;
        int floor = AlatusRules.QUALITY_NORMAL;
        if (!this.infusionReinfuse) {
            out = getBroomResultForCurrentInputs();
            if (out == null) {
                return;
            }
            // Upgrading a broom keeps the quality it already had; only a re-infusion is a true reroll.
            ItemStack base = this.inventory.getStackInSlot(SLOT_A);
            if (base.getItem() instanceof BroomItem) {
                floor = BroomItem.quality(base);
            }
            this.inventory.extractItem(SLOT_A, 1, false);
            this.inventory.extractItem(SLOT_B, 1, false);
            this.inventory.extractItem(SLOT_C, 1, false);
            this.inventory.extractItem(SLOT_D, 1, false);
        } else if (canReinfuse()) {
            // The same broom comes back with a fresh roll at the infusing Master's odds.
            out = this.inventory.getStackInSlot(SLOT_A).copyWithCount(1);
            this.inventory.extractItem(SLOT_A, 1, false);
            this.inventory.extractItem(SLOT_C, AlatusRules.REINFUSE_DUST_COST, false);
        } else {
            return;
        }
        int quality = this.rollQuality(out, floor);
        this.inventory.setStackInSlot(SLOT_RESULT, out);
        this.playFinishEffects(quality);
        this.setChanged();
    }

    /** From Adept an infusion may make a Fine or Masterwork broom (a normal one carries no component). */
    private int rollQuality(ItemStack broom, int floor) {
        RandomSource random = this.level != null ? this.level.random : RandomSource.create();
        int quality = Math.max(floor, AlatusRules.rollQuality(this.infusionRank, random.nextDouble()));
        BroomItem.setQuality(broom, quality);
        return quality;
    }

    private void playFinishEffects(int quality) {
        Level level = this.level;
        if (level == null) {
            return;
        }
        BlockPos pos = this.getBlockPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.72;
        double z = pos.getZ() + 0.5;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(MagicParticles.MOTE_WHITE, x, y, z, 10, 0.35, 0.2, 0.35, 0.04);
            serverLevel.sendParticles(MagicParticles.GLINT_GOLD, x, y, z, 8, 0.4, 0.15, 0.4, 0.0);
            serverLevel.sendParticles(MagicParticles.SMOKE_WHITE, x, y, z, 14, 0.45, 0.18, 0.45, 0.02);
            serverLevel.sendParticles(MagicParticles.MOTE_GOLD, x, y + 0.1, z, 12, 0.5, 0.25, 0.5, 0.03);
            if (quality == AlatusRules.QUALITY_MASTERWORK) {
                serverLevel.sendParticles(MagicParticles.GLINT_GOLD, x, y + 0.2, z, 24, 0.4, 0.3, 0.4, 0.15);
            } else if (quality == AlatusRules.QUALITY_FINE) {
                serverLevel.sendParticles(MagicParticles.GLINT_GOLD, x, y + 0.2, z, 16, 0.4, 0.3, 0.4, 0.05);
            }
        }
        level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.28f, 1.35f);
        level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.BLOCKS, 0.4f, 1.1f);
        level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.55f, 1.2f);
        if (quality != AlatusRules.QUALITY_NORMAL) {
            level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.9f, quality == AlatusRules.QUALITY_MASTERWORK ? 1.4f : 1.1f);
        }
    }

    private void spawnInfusionStartBurst(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.58;
        double cz = pos.getZ() + 0.5;
        level.sendParticles(MagicParticles.MOTE_GOLD, cx, cy + 0.15, cz, 12, 0.4, 0.22, 0.4, 0.03);
        level.sendParticles(MagicParticles.SMOKE_WHITE, cx, cy, cz, 8, 0.35, 0.12, 0.35, 0.02);
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * Math.PI * 2;
            double px = cx + Math.cos(a) * 0.55;
            double pz = cz + Math.sin(a) * 0.55;
            level.sendParticles(ParticleTypes.SMALL_GUST, px, cy, pz, 1, 0.12, 0.06, 0.12, 0.015);
        }
    }

    private void tickInfusionEffects(ServerLevel level, BlockPos pos) {
        int total = this.craftTotalTicks;
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
            level.sendParticles(MagicParticles.SMOKE_WHITE, cx + ox, cy + oy * 0.35, cz + oz, 1, 0.06, 0.04, 0.06, 0.003);
            if (rand.nextFloat() < 0.45f) {
                level.sendParticles(MagicParticles.MOTE_GOLD, cx + ox * 0.9, cy + oy * 0.4, cz + oz * 0.9, 1, 0.1, 0.06, 0.1, 0.015);
            }
        }

        double spin = time * 0.32 + elapsed * 0.05;
        for (int s = 0; s < 7; s++) {
            double ang = spin + s * Math.PI * 2 / 7;
            double r = 0.55 + 0.12 * Math.sin(time * 0.08);
            double px = cx + Math.cos(ang) * r;
            double pz = cz + Math.sin(ang) * r;
            double py = cy + rand.nextDouble() * 0.45;
            level.sendParticles(MagicParticles.SMOKE_WHITE, px, py, pz, 1, 0.08, 0.05, 0.08, 0.008);
            if (s % 2 == 0) {
                level.sendParticles(MagicParticles.SMOKE_WHITE, px, py, pz, 1, 0.06, 0.04, 0.06, 0.004);
            }
        }

        if (this.craftTicksRemaining % 6 == 0) {
            level.sendParticles(ParticleTypes.SMALL_GUST, cx, cy + 0.1, cz, 2, 0.4, 0.12, 0.4, 0.02);
        }
        if (elapsed > total / 2 && this.craftTicksRemaining % 10 == 0) {
            level.sendParticles(MagicParticles.MOTE_WHITE, cx, cy + 0.2, cz, 2, 0.25, 0.1, 0.25, 0.025);
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
        tag.putInt("CraftTotal", this.craftTotalTicks);
        tag.putInt("InfusionRank", this.infusionRank);
        tag.putBoolean("InfusionReinfuse", this.infusionReinfuse);
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
        this.craftTotalTicks = tag.contains("CraftTotal") ? Math.max(1, tag.getInt("CraftTotal")) : BROOM_CRAFT_TICKS_TOTAL;
        this.infusionRank = tag.contains("InfusionRank") ? tag.getInt("InfusionRank") : 1;
        this.infusionReinfuse = tag.getBoolean("InfusionReinfuse");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        tag.putInt("CraftTotal", this.craftTotalTicks);
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
