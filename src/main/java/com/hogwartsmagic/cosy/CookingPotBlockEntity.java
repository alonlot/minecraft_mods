package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What is in a cooking pot. Up to six ingredients (one item each) wait in {@link #contents}; when they match a
 * {@code pot_cooking} recipe and the pot is hot and stirred, {@link #progress} climbs, and at the recipe's cook time
 * the ingredients become {@link #output} to take out. With no heat or no stirring progress simply stops; it is never
 * lost, and the ingredients never vanish. The client only ever reads the synced state (contents, output, whether it is
 * stirring and whether it is actually cooking) for the renderer, the steam and the sound.
 */
public class CookingPotBlockEntity extends BlockEntity {
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<ItemStack> output = new ArrayList<>();
    private int progress;
    private int stirTicks;
    private boolean enchanted;
    /** Synced: it is cooking right now (recipe, heat and stirring all present). */
    private boolean active;
    /** Synced: someone, or the enchanted ladle, is stirring. */
    private boolean stirring;

    @Nullable
    private RecipeHolder<PotCookingRecipe> recipe;
    private boolean recipeDirty = true;
    /** Client only: ticks until the bubbling sound plays again. */
    private int soundCooldown;

    public CookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(CosyKitchen.COOKING_POT_BLOCK_ENTITY.get(), pos, state);
    }

    // ---------------------------------------------------------------- reading

    public List<ItemStack> contents() {
        return this.contents;
    }

    public List<ItemStack> output() {
        return this.output;
    }

    public boolean isDone() {
        return !this.output.isEmpty();
    }

    public int progress() {
        return this.progress;
    }

    public boolean isEnchanted() {
        return this.enchanted;
    }

    public boolean isStirring() {
        return this.stirring;
    }

    public boolean isActive() {
        return this.active;
    }

    /** What the renderer floats in the pot: the finished dish, or the ingredients. */
    public List<ItemStack> renderItems() {
        return isDone() ? this.output : this.contents;
    }

    public boolean isEmpty() {
        return this.contents.isEmpty() && this.output.isEmpty();
    }

    // ---------------------------------------------------------------- changing

    /** Puts one item in. False when the pot is full or still holds a finished dish. Progress restarts. */
    public boolean addIngredient(ItemStack one) {
        if (isDone() || this.contents.size() >= PotRules.MAX_INGREDIENTS) {
            return false;
        }
        this.contents.add(one.copyWithCount(1));
        contentsChanged();
        return true;
    }

    /** Empties the ingredients out (to hand back to the player). Progress and the enchanted ladle reset. */
    public List<ItemStack> takeIngredients() {
        List<ItemStack> taken = new ArrayList<>(this.contents);
        this.contents.clear();
        this.enchanted = false;
        this.stirTicks = 0;
        contentsChanged();
        return taken;
    }

    /** Takes the finished dish out. */
    public List<ItemStack> takeOutput() {
        List<ItemStack> taken = new ArrayList<>(this.output);
        this.output.clear();
        contentsChanged();
        return taken;
    }

    /** Three seconds of stirring by hand. */
    public void stirByHand() {
        this.stirTicks = PotRules.MANUAL_STIR_TICKS;
        refreshFlags();
    }

    /** Tapping with a wand: the ladle stirs itself until the dish is done (or the ingredients are taken out). */
    public boolean enchantLadle() {
        if (this.contents.isEmpty() || isDone()) {
            return false;
        }
        this.enchanted = true;
        refreshFlags();
        return true;
    }

    private void contentsChanged() {
        this.progress = 0;
        this.recipe = null;
        this.recipeDirty = true;
        this.active = false;
        this.stirring = PotRules.isStirring(this.stirTicks, this.enchanted);
        BlockState state = getBlockState();
        if (this.level != null && state.hasProperty(CookingPotBlock.FILLED)
                && state.getValue(CookingPotBlock.FILLED) == isEmpty()) {
            this.level.setBlock(this.worldPosition, state.setValue(CookingPotBlock.FILLED, !isEmpty()), Block.UPDATE_ALL);
        }
        sync();
    }

    /** Re-reads the stirring flag and syncs when it (or the cooking flag) changed. */
    private void refreshFlags() {
        boolean now = PotRules.isStirring(this.stirTicks, this.enchanted);
        if (now != this.stirring) {
            this.stirring = now;
            sync();
        } else {
            setChanged();
        }
    }

    private void sync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- cooking

    @Nullable
    private PotCookingRecipe currentRecipe(Level level) {
        // Re-look-up now and then so a datapack /reload is picked up by a pot that is mid-cook.
        if (level.getGameTime() % 100 == 0) {
            this.recipeDirty = true;
        }
        if (this.recipeDirty) {
            this.recipeDirty = false;
            this.recipe = this.contents.isEmpty() ? null
                    : level.getRecipeManager().getRecipeFor(CosyKitchen.POT_COOKING_TYPE.get(),
                            new PotInput(List.copyOf(this.contents)), level).orElse(null);
        }
        return this.recipe == null ? null : this.recipe.value();
    }

    /** One server tick. Public so tests can drive a pot by hand. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
        if (pot.contents.isEmpty() || pot.isDone()) {
            return;
        }
        PotCookingRecipe recipe = pot.currentRecipe(level);
        boolean heated = CosyKitchen.isHeatedBelow(level, pos);
        boolean stirring = PotRules.isStirring(pot.stirTicks, pot.enchanted);
        boolean cooking = recipe != null && heated && stirring;
        pot.progress = PotRules.nextProgress(pot.progress, recipe != null && heated, stirring);
        pot.stirTicks = PotRules.nextStir(pot.stirTicks, pot.enchanted);
        boolean stirringAfter = PotRules.isStirring(pot.stirTicks, pot.enchanted);
        if (cooking != pot.active || stirringAfter != pot.stirring) {
            pot.active = cooking;
            pot.stirring = stirringAfter;
            pot.sync();
        }
        if (recipe != null && pot.progress >= recipe.cookTicks()) {
            pot.finish(level, recipe);
        }
    }

    private void finish(Level level, PotCookingRecipe recipe) {
        PotInput input = new PotInput(List.copyOf(this.contents));
        NonNullList<ItemStack> remains = recipe.getRemainingItems(input);
        this.output.clear();
        this.output.add(recipe.assemble(input, level.registryAccess()));
        for (ItemStack rest : remains) {
            if (!rest.isEmpty()) {
                this.output.add(rest.copy());
            }
        }
        this.contents.clear();
        this.enchanted = false;
        this.stirTicks = 0;
        contentsChanged();
        level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.1F);
    }

    /** One client tick: steam and bubbles while it cooks, and the bubbling sound. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity pot) {
        if (!pot.active) {
            pot.soundCooldown = 0;
            return;
        }
        RandomSource random = level.random;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.55;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.64, z, 0.0, 0.01, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.7, z, 0.0, 0.035, 0.0);
        }
        if (pot.soundCooldown-- <= 0) {
            pot.soundCooldown = 170; // the loop file is 8.5 s long
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    ModSounds.COSY_POT_BUBBLE_LOOP.get(), SoundSource.BLOCKS, 0.55F, 1.0F, false);
        }
    }

    // ---------------------------------------------------------------- saving and syncing

    private static ListTag saveList(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registries));
            }
        }
        return list;
    }

    private static void loadList(ListTag list, List<ItemStack> into, HolderLookup.Provider registries) {
        into.clear();
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(into::add);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Contents", saveList(this.contents, registries));
        tag.put("Output", saveList(this.output, registries));
        tag.putInt("Progress", this.progress);
        tag.putInt("Stir", this.stirTicks);
        tag.putBoolean("Enchanted", this.enchanted);
        tag.putBoolean("Active", this.active);
        tag.putBoolean("Stirring", this.stirring);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadList(tag.getList("Contents", Tag.TAG_COMPOUND), this.contents, registries);
        loadList(tag.getList("Output", Tag.TAG_COMPOUND), this.output, registries);
        this.progress = tag.getInt("Progress");
        this.stirTicks = tag.getInt("Stir");
        this.enchanted = tag.getBoolean("Enchanted");
        this.active = tag.getBoolean("Active");
        this.stirring = tag.getBoolean("Stirring");
        this.recipe = null;
        this.recipeDirty = true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
