package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.WizardCauldronBlock;
import com.hogwartsmagic.brewing.AdvancedPotions;
import com.hogwartsmagic.brewing.BrewAdvancements;
import com.hogwartsmagic.brewing.BrewQuality;
import com.hogwartsmagic.brewing.BrewRules;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.polyjuice.PolyjuiceStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The brew in a Wizard Cauldron: water doses, the history of steps (the last simmer run is the simmer counter), the
 * ruined flag, the overbrew seconds, the Polyjuice target and the liquid colour. Every rule comes from BrewRules.
 * A cauldron nobody has used (from a structure or an old world) is not initialized: it shows the classic green and
 * becomes plain water (doses = its block state level) on first use.
 */
public class WizardCauldronBlockEntity extends BlockEntity {
    private boolean initialized;
    private int doses;
    private List<BrewRules.Step> history = new ArrayList<>();
    private boolean ruined;
    private int overbrewSeconds;
    private CompoundTag polyjuiceTarget = new CompoundTag();
    private int color = BrewRules.CLASSIC_GREEN;
    /** Simmered seconds since the client colour was last refreshed. Not saved. */
    private int simmerSinceSync;
    /** Quality counters: rare ingredients added and how many were fine, stirs and how many were precise. */
    private int gradableAdds;
    private int fineAdds;
    private int stirs;
    private int preciseStirs;
    /** Game time of the last ingredient or stir; -1 when unknown. */
    private long lastActionTick = -1L;

    public WizardCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.WIZARD_CAULDRON_BLOCK_ENTITY.get(), pos, state);
    }

    /** RGB of the liquid, read by the client BlockColor. */
    public int getColor() {
        return color;
    }

    /** The items the cauldron reacts to; anything else passes through to vanilla. Same answer on both sides. */
    public static boolean handles(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)
                || stack.is(ExampleMod.FINE_WOOD_STICK.get()) || isWaterBottle(stack)) {
            return true;
        }
        return BrewRules.isIngredient(itemId(stack));
    }

    public static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ---------------------------------------------------------------- interactions (server only)

    public void useItem(ServerLevel level, ItemStack stack, Player player, InteractionHand hand) {
        ensureInitialized();
        if (stack.is(Items.WATER_BUCKET)) {
            pourWater(level, player, hand, stack, BrewRules.MAX_DOSES, true);
        } else if (isWaterBottle(stack)) {
            pourWater(level, player, hand, stack, doses + 1, false);
        } else if (stack.is(Items.BUCKET)) {
            useBucket(level, player, hand, stack);
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            bottle(level, player, hand, stack);
        } else if (stack.is(ExampleMod.FINE_WOOD_STICK.get())) {
            stir(level, player);
        } else {
            addIngredient(level, player, hand, stack);
        }
    }

    public void showStatus(Player player) {
        if (!initialized) {
            // An untouched cauldron stays untouched (classic green): read the block state, never initialize.
            BlockState state = getBlockState();
            int fill = state.hasProperty(WizardCauldronBlock.LEVEL) ? state.getValue(WizardCauldronBlock.LEVEL) : 0;
            player.displayClientMessage(line(fill == 0 ? "status.empty" : "status.untouched"), true);
            return;
        }
        player.displayClientMessage(statusLine(), true);
    }

    private Component statusLine() {
        if (doses == 0) {
            return line("status.empty");
        }
        if (ruined) {
            return line("status.ruined");
        }
        if (history.isEmpty()) {
            return line("status.water", doses);
        }
        boolean heated = WizardCauldronBlock.isHeated(this.level, this.worldPosition);
        BrewRules.Match match = BrewRules.match(history);
        if (match.status() == BrewRules.Status.FINISHED) {
            Component name = Component.translatable("brew.hogwartsmagic." + match.recipe().id());
            return heated
                    ? line("status.ready_heat", name, doses, BrewRules.overbrewLeft(overbrewSeconds))
                    : line("status.ready", name, doses);
        }
        if (BrewRules.wantsSimmer(history)) {
            return line(heated ? "status.simmering" : "status.needs_heat", BrewRules.simmerLeft(history));
        }
        if (BrewRules.overbrewClock(history)) {
            return heated ? line("status.waiting", BrewRules.overbrewLeft(overbrewSeconds)) : line("status.waiting_cold");
        }
        return line("status.brewing");
    }

    private void pourWater(ServerLevel level, Player player, InteractionHand hand, ItemStack stack, int newDoses, boolean bucket) {
        if (ruined || !history.isEmpty()) {
            tell(player, "already_brewing");
            return;
        }
        if (doses >= BrewRules.MAX_DOSES) {
            tell(player, "full");
            return;
        }
        doses = Math.min(BrewRules.MAX_DOSES, newDoses);
        ItemStack empty = new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE);
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, empty));
        level.playSound(null, worldPosition, bucket ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        changed();
    }

    private void useBucket(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            reset();
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.SMOKE, cx(), surfaceY(), cz(), 8, 0.2, 0.05, 0.2, 0.01);
            tell(player, "poured");
            changed();
            return;
        }
        if (history.isEmpty() && doses == BrewRules.MAX_DOSES) {
            reset();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            changed();
            return;
        }
        tell(player, "bucket_refused");
    }

    private void bottle(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        ItemStack result;
        if (ruined) {
            result = new ItemStack(ExampleMod.FAILED_POTION.get());
        } else if (history.isEmpty()) {
            result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        } else {
            BrewRules.Match match = BrewRules.match(history);
            if (match.status() != BrewRules.Status.FINISHED) {
                tell(player, "not_ready");
                return;
            }
            result = bottledPotion(match.recipe());
            if (!result.is(ExampleMod.FAILED_POTION.get())) {
                BrewAdvancements.grant(player, BrewAdvancements.FIRST_BREW);
                if (result.getItem() instanceof com.hogwartsmagic.item.BrewedPotionItem
                        || result.getItem() instanceof com.hogwartsmagic.item.SplashBrewItem) {
                    int grade = BrewQuality.grade(currentScore());
                    result.set(ExampleMod.BREW_QUALITY.get(), grade);
                    if (grade == BrewQuality.EXCEPTIONAL) {
                        BrewAdvancements.grant(player, BrewAdvancements.OUTSTANDING_BREW);
                    }
                }
            }
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        doses--;
        if (doses == 0) {
            reset();
        }
        changed();
    }

    private ItemStack bottledPotion(BrewRules.Recipe recipe) {
        String id = recipe.id();
        if (BrewRules.WIGGENWELD.id().equals(id)) {
            return new ItemStack(ExampleMod.WIGGENWELD_POTION.get());
        }
        if (BrewRules.PEPPERUP.id().equals(id)) {
            return new ItemStack(ExampleMod.PEPPERUP_POTION.get());
        }
        if (BrewRules.DITTANY_ESSENCE.id().equals(id)) {
            return new ItemStack(ExampleMod.ESSENCE_OF_DITTANY.get());
        }
        if (BrewRules.FELIX_FELICIS.id().equals(id)) {
            return new ItemStack(ExampleMod.FELIX_FELICIS.get());
        }
        if (BrewRules.REMORSE.id().equals(id)) {
            return new ItemStack(ExampleMod.POTION_OF_REMORSE.get());
        }
        if (BrewRules.MANDRAKE_DRAUGHT.id().equals(id)) {
            return new ItemStack(BrewRules.usedTail(history, BrewRules.GUNPOWDER)
                    ? ExampleMod.SPLASH_MANDRAKE_DRAUGHT.get() : ExampleMod.MANDRAKE_DRAUGHT.get());
        }
        if (BrewRules.LIVING_DEATH.id().equals(id)) {
            return new ItemStack(ExampleMod.DRAUGHT_OF_LIVING_DEATH.get());
        }
        if (BrewRules.POLYJUICE.id().equals(id) && !polyjuiceTarget.isEmpty()) {
            return PolyjuiceStacks.make(polyjuiceTarget,
                    BrewRules.usedTail(history, BrewRules.REDSTONE),
                    BrewRules.usedTail(history, BrewRules.GUNPOWDER));
        }
        return new ItemStack(ExampleMod.FAILED_POTION.get());
    }

    private void stir(ServerLevel level, Player player) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            tell(player, "ruined_already");
            return;
        }
        BrewRules.Step turn = player.isShiftKeyDown()
                ? BrewRules.Step.stirAnticlockwise(1)
                : BrewRules.Step.stirClockwise(1);
        if (refusedForWantOfBook(player, turn)) {
            return;
        }
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
        level.sendParticles(ParticleTypes.SPLASH, cx(), surfaceY(), cz(), 6, 0.2, 0.02, 0.2, 0.05);
        if (history.isEmpty()) {
            tell(player, "stir_water");
            return;
        }
        BrewRules.Kind lastKind = history.get(history.size() - 1).kind();
        boolean previousWasStir = lastKind == BrewRules.Kind.STIR_CLOCKWISE || lastKind == BrewRules.Kind.STIR_ANTICLOCKWISE;
        long now = level.getGameTime();
        long gap = lastActionTick < 0 ? Long.MAX_VALUE : now - lastActionTick;
        if (applyStep(level, turn, player)) {
            stirs++;
            if (BrewQuality.preciseStir(previousWasStir, gap)) {
                preciseStirs++;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.5f, 1.6f);
                level.sendParticles(ParticleTypes.ENCHANT, cx(), surfaceY(), cz(), 4, 0.2, 0.1, 0.2, 0.1);
            }
            lastActionTick = now;
        }
        changed();
    }

    /**
     * True (after telling the player) when the step would start or continue a recipe that only the Advanced
     * Potion-Making book teaches and this player has not read it. Nothing is consumed and the brew is untouched.
     */
    private boolean refusedForWantOfBook(Player player, BrewRules.Step action) {
        if (BrewRules.needsBook(BrewRules.append(history, action)) && !AdvancedPotions.hasRead(player)) {
            tell(player, "needs_book");
            return true;
        }
        return false;
    }

    /** Test accessor: how many runs the brew has. */
    public int historySize() {
        return history.size();
    }

    /** Test accessor. */
    public boolean isRuined() {
        return ruined;
    }

    private void addIngredient(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            tell(player, "ruined_already");
            return;
        }
        if (history.isEmpty() && doses < BrewRules.MAX_DOSES) {
            tell(player, "needs_full", doses);
            return;
        }
        String id = itemId(stack);
        boolean filledSample = stack.is(ExampleMod.POLYJUICE_SAMPLE.get()) && PolyjuiceData.hasSample(stack);
        if (BrewRules.refusesEmptySample(id, filledSample)) {
            tell(player, "empty_sample");
            return;
        }
        if (refusedForWantOfBook(player, BrewRules.Step.add(id, 1))) {
            return;
        }
        CompoundTag sample = filledSample ? PolyjuiceData.getSample(stack) : null;
        boolean fine = stack.getOrDefault(ExampleMod.FINE_INGREDIENT.get(), false);
        consumeOne(player, hand, stack);
        boolean accepted = applyStep(level, BrewRules.Step.add(id, 1), player);
        if (accepted) {
            if (BrewQuality.gradable(id)) {
                gradableAdds++;
                if (fine) {
                    fineAdds++;
                }
            }
            lastActionTick = level.getGameTime();
        }
        if (accepted && sample != null) {
            // BrewRules sees only the item name; the target travels with the brew.
            polyjuiceTarget = sample;
        }
        changed();
    }

    private static void consumeOne(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.HONEY_BOTTLE)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    /** Adds one action to the history. Returns false when it ruined the brew. The caller syncs. */
    private boolean applyStep(ServerLevel level, BrewRules.Step action, @Nullable Player player) {
        boolean wasFinished = BrewRules.match(history).status() == BrewRules.Status.FINISHED;
        history = BrewRules.append(history, action);
        BrewRules.Match match = BrewRules.match(history);
        if (match.status() == BrewRules.Status.RUINED) {
            ruin(level, player);
            return false;
        }
        if (match.status() == BrewRules.Status.FINISHED && !wasFinished) {
            finish(level, match.recipe(), player);
            return true;
        }
        level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.8f,
                1.0f + level.random.nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, cx(), surfaceY(), cz(), 6, 0.2, 0.05, 0.2, 0.01);
        return true;
    }

    private void ruin(ServerLevel level, @Nullable Player player) {
        ruined = true;
        history = new ArrayList<>();
        overbrewSeconds = 0;
        polyjuiceTarget = new CompoundTag();
        resetQuality();
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.7f);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.SMOKE, cx(), surfaceY(), cz(), 16, 0.25, 0.1, 0.25, 0.02);
        if (player != null) {
            tell(player, "ruin");
            BrewAdvancements.grant(player, BrewAdvancements.RUINED_BREW);
        }
    }

    private void finish(ServerLevel level, BrewRules.Recipe recipe, @Nullable Player player) {
        doses = recipe.doses();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, cx(), surfaceY() + 0.1, cz(), 16, 0.25, 0.2, 0.25, 0.02);
        if (player != null) {
            Component name = Component.translatable("brew.hogwartsmagic." + recipe.id());
            tell(player, "finished_quality", name,
                    Component.translatable(BrewQuality.langKey(BrewQuality.grade(currentScore()))));
        }
    }

    private double currentScore() {
        return BrewQuality.score(gradableAdds, fineAdds, stirs, preciseStirs);
    }

    private void resetQuality() {
        gradableAdds = 0;
        fineAdds = 0;
        stirs = 0;
        preciseStirs = 0;
        lastActionTick = -1L;
    }

    private void reset() {
        resetQuality();
        doses = 0;
        history = new ArrayList<>();
        ruined = false;
        overbrewSeconds = 0;
        polyjuiceTarget = new CompoundTag();
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        BlockState state = getBlockState();
        int fill = state.hasProperty(WizardCauldronBlock.LEVEL) ? state.getValue(WizardCauldronBlock.LEVEL) : 0;
        reset();
        doses = fill; // a structure or old-world cauldron (level 3) becomes 3 doses of plain water
        changed();
    }

    /** Recomputes the colour, keeps the block state level equal to the doses, saves and sends the change. */
    private void changed() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.color = !initialized ? BrewRules.CLASSIC_GREEN
                : ruined ? BrewRules.RUINED_COLOR
                : BrewRules.color(history);
        BlockState state = getBlockState();
        if (state.hasProperty(WizardCauldronBlock.LEVEL) && state.getValue(WizardCauldronBlock.LEVEL) != doses) {
            serverLevel.setBlock(worldPosition, state.setValue(WizardCauldronBlock.LEVEL, doses), Block.UPDATE_ALL);
        }
        setChanged();
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    // ---------------------------------------------------------------- ticker

    public static void serverTick(Level level, BlockPos pos, BlockState state, WizardCauldronBlockEntity cauldron) {
        if (level.getGameTime() % 20L != Math.floorMod((long) pos.hashCode(), 20L)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            cauldron.secondTick(serverLevel);
        }
    }

    private void secondTick(ServerLevel level) {
        if (!initialized || ruined || doses == 0 || history.isEmpty()) {
            return;
        }
        if (!WizardCauldronBlock.isHeated(level, worldPosition)) {
            return; // off the heat nothing simmers and nothing overbrews
        }
        if (BrewRules.wantsSimmer(history)) {
            history = BrewRules.append(history, BrewRules.Step.simmer(1));
            level.sendParticles(ParticleTypes.BUBBLE_POP, cx(), surfaceY(), cz(), 3, 0.2, 0.02, 0.2, 0.0);
            BrewRules.Match match = BrewRules.match(history);
            if (match.status() == BrewRules.Status.FINISHED) {
                finish(level, match.recipe(), null);
                simmerSinceSync = 0;
                changed();
                return;
            }
            if (!BrewRules.wantsSimmer(history)) {
                level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.2f);
                simmerSinceSync = 0;
                changed();
                return;
            }
            simmerSinceSync++;
            if (simmerSinceSync >= BrewRules.SIMMER_SYNC_SECONDS) {
                simmerSinceSync = 0;
                changed();
            } else {
                setChanged();
            }
            return;
        }
        if (BrewRules.overbrewClock(history)) {
            overbrewSeconds++;
            if (BrewRules.overbrewed(overbrewSeconds)) {
                ruin(level, null);
                changed();
                return;
            }
            if (BrewRules.overbrewWarning(overbrewSeconds)) {
                level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            if (BrewRules.match(history).status() == BrewRules.Status.FINISHED) {
                level.sendParticles(ParticleTypes.END_ROD, cx(), surfaceY() + 0.1, cz(), 2, 0.2, 0.1, 0.2, 0.01);
            }
            setChanged();
        }
    }

    // ---------------------------------------------------------------- save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("Doses", doses);
        tag.putBoolean("Ruined", ruined);
        tag.putInt("Overbrew", overbrewSeconds);
        tag.putInt("Color", color);
        tag.putInt("QGradable", gradableAdds);
        tag.putInt("QFine", fineAdds);
        tag.putInt("QStirs", stirs);
        tag.putInt("QPrecise", preciseStirs);
        tag.putLong("QLastAction", lastActionTick);
        ListTag list = new ListTag();
        for (BrewRules.Step step : history) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Kind", step.kind().name());
            entry.putString("Item", step.item());
            entry.putInt("Amount", step.amount());
            list.add(entry);
        }
        tag.put("History", list);
        if (!polyjuiceTarget.isEmpty()) {
            tag.put("PolyjuiceTarget", polyjuiceTarget.copy());
        }
    }

    /** Also reads the small client update tag (Initialized, Doses, Color): missing keys fall back to defaults. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        initialized = tag.getBoolean("Initialized");
        doses = Mth.clamp(tag.getInt("Doses"), 0, BrewRules.MAX_DOSES);
        ruined = tag.getBoolean("Ruined");
        overbrewSeconds = tag.getInt("Overbrew");
        gradableAdds = Math.max(0, tag.getInt("QGradable"));
        fineAdds = Math.max(0, tag.getInt("QFine"));
        stirs = Math.max(0, tag.getInt("QStirs"));
        preciseStirs = Math.max(0, tag.getInt("QPrecise"));
        lastActionTick = tag.contains("QLastAction") ? tag.getLong("QLastAction") : -1L;
        history = new ArrayList<>();
        boolean damaged = false;
        ListTag list = tag.getList("History", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BrewRules.Kind kind = BrewRules.Kind.byName(entry.getString("Kind"));
            if (kind == null) {
                damaged = true; // a damaged save: a ruined brew, never a crash
                continue;
            }
            String item = entry.getString("Item");
            int amount = entry.getInt("Amount");
            if (amount < 1 || (kind == BrewRules.Kind.ADD && item.isEmpty())) {
                damaged = true; // a damaged run: never a Step with a bad item or amount
                continue;
            }
            history.add(new BrewRules.Step(kind, kind == BrewRules.Kind.ADD ? item : "", amount));
        }
        polyjuiceTarget = tag.getCompound("PolyjuiceTarget").copy();
        color = tag.contains("Color") ? tag.getInt("Color") : BrewRules.CLASSIC_GREEN;
        if (damaged) {
            ruined = true;
            history = new ArrayList<>();
            color = BrewRules.RUINED_COLOR;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("Doses", doses);
        tag.putInt("Color", color);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        if (this.level != null && this.level.isClientSide) {
            // The colour is not in the block state: re-mesh this block so the BlockColor runs again.
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    // ---------------------------------------------------------------- helpers

    private double cx() {
        return worldPosition.getX() + 0.5;
    }

    private double cz() {
        return worldPosition.getZ() + 0.5;
    }

    private double surfaceY() {
        return worldPosition.getY() + 0.65;
    }

    private static Component line(String key, Object... args) {
        return Component.translatable("message.hogwartsmagic.cauldron." + key, args);
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(line(key, args), true);
    }
}
