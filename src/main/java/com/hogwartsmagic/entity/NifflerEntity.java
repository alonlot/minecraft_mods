package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ConcealedRockBlock;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.util.ModAdvancements;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The Niffler: a small, black, velvet-furred burrower with a bill for a nose and a pouch that holds
 * far more than it should. It cannot leave anything shiny alone.
 *
 * <p><b>Wild</b>, it is a thief, not a fighter. It never attacks.
 * <ul>
 *   <li>It picks up shiny things (the {@link #SHINIES} tag: gold, gems, Galleons) dropped on the
 *       ground, and takes them out of chests and barrels nearby. A chest that has not been opened
 *       yet (its loot not rolled) is left alone, so a Niffler never empties a tomb before a player
 *       finds it.</li>
 *   <li>It runs to a player who carries shinies, takes part of one stack out of their inventory
 *       and runs off. If it gets clear, it burrows into the ground and is gone.</li>
 *   <li>A Niffler that burrows buries its pouch where it went down, as suspicious sand or gravel,
 *       one stack to a block. Nothing it takes is ever lost: a player with a brush gets it back.
 *       A Niffler that sits on a full pouch with nobody near also burrows, after a minute.</li>
 *   <li>Each hit shakes something out of the pouch. Killing it drops the lot.</li>
 * </ul>
 *
 * <p><b>Tame</b> (a gold nugget, one chance in four), it follows its owner and works for them:
 * <ul>
 *   <li>It still picks up shinies it finds on the ground, but never ones its owner threw.</li>
 *   <li>It smells treasure within eight blocks: gold ore, gold and raw gold blocks, unopened loot
 *       chests, suspicious sand and gravel, and the concealed walls of tombs and caves. It runs
 *       toward the find, sniffs at the ground and digs, and a trail of gold sparks points at it.</li>
 *   <li>Every two to four minutes on earth or sand, it digs up a small treasure of its own into its
 *       pouch (desert ground gives tomb treasure).</li>
 *   <li>Its owner empties the pouch with an empty hand: the Niffler rolls on its back to be tickled
 *       and the treasure falls out. Sneak with an empty hand to make it sit or stand.</li>
 * </ul>
 */
public class NifflerEntity extends TamableAnimal {
    public static final TagKey<Item> SHINIES =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "niffler_shinies"));
    public static final int POUCH_SLOTS = 27;

    /** What the Niffler is busy with; every action roots it in place and the model plays it. */
    public enum Action {
        NONE(0), SNIFF(30), DIG(60), BURROW(50), TICKLED(50);

        public final int ticks;

        Action(int ticks) {
            this.ticks = ticks;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_FULLNESS =
            SynchedEntityData.defineId(NifflerEntity.class, EntityDataSerializers.INT);

    private static final ResourceKey<LootTable> DIG_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gameplay/niffler_dig"));
    private static final ResourceKey<LootTable> DIG_LOOT_DESERT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gameplay/niffler_dig_desert"));

    public static final double STEAL_RANGE = 10.0D;
    public static final int STEAL_COOLDOWN = 600;
    public static final int FLEE_TICKS = 400;
    /** How far it must get from the player it robbed before it burrows. */
    public static final double ESCAPE_DISTANCE = 14.0D;
    /** How long a wild Niffler sits on loot, with nobody near, before it buries it. */
    public static final int HOARD_TICKS = 1200;
    public static final int SENSE_RADIUS = 8;
    private static final int SENSE_INTERVAL = 100;
    private static final int SHOWN_MEMORY = 32;

    private final SimpleContainer pouch = new SimpleContainer(POUCH_SLOTS);

    private int actionTicks;
    private int stealCooldown;
    private int raidCooldown;
    private int fleeTicks;
    private int hoardTicks;
    private int digCooldown = 1200;
    private int senseCooldown;
    private int ignoreShiniesTicks;
    @Nullable
    private UUID chaser;
    @Nullable
    private UUID tickler;
    @Nullable
    private BlockPos digTarget;
    @Nullable
    private BlockPos pendingTarget;
    private final Deque<BlockPos> shown = new ArrayDeque<>();

    /** Client only: when the current action began, in the entity's ticks. */
    private int actionStart;

    public NifflerEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        pouch.addListener(c -> updateFullness());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    /** Anywhere on solid earth, sand or stone, at any light: Nifflers live underground as much as on it. */
    public static boolean canSpawn(EntityType<NifflerEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        return (spawnType == MobSpawnType.SPAWNER || below.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(BlockTags.TERRACOTTA)
                || below.is(BlockTags.SAND)) && checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    public static boolean isShiny(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SHINIES);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_FULLNESS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BusyGoal());
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new FleeGoal());
        this.goalSelector.addGoal(4, new StealGoal());
        this.goalSelector.addGoal(5, new SeekShinyGoal());
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.2D, 8.0F, 2.5F));
        this.goalSelector.addGoal(7, new TreasureGoal());
        this.goalSelector.addGoal(8, new RaidGoal());
        this.goalSelector.addGoal(9, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
    }

    // --- state ---------------------------------------------------------------------------------------

    public Action action() {
        Action[] all = Action.values();
        int i = this.entityData.get(DATA_ACTION);
        return i >= 0 && i < all.length ? all[i] : Action.NONE;
    }

    /** Client: ticks since the current action began. */
    public float actionTime(float partialTick) {
        return this.tickCount - this.actionStart + partialTick;
    }

    /** How many of the pouch's slots hold something, synced so the pouch can swell. */
    public int fullness() {
        return this.entityData.get(DATA_FULLNESS);
    }

    public SimpleContainer pouch() {
        return pouch;
    }

    public boolean isFleeing() {
        return fleeTicks > 0;
    }

    private void updateFullness() {
        if (this.level().isClientSide) {
            return;
        }
        int n = 0;
        for (int i = 0; i < pouch.getContainerSize(); i++) {
            if (!pouch.getItem(i).isEmpty()) {
                n++;
            }
        }
        this.entityData.set(DATA_FULLNESS, n);
    }

    private boolean pouchHasRoom() {
        for (int i = 0; i < pouch.getContainerSize(); i++) {
            if (pouch.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Puts the stack in the pouch; returns what would not fit. */
    public ItemStack addToPouch(ItemStack stack) {
        return pouch.addItem(stack);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) {
            this.actionStart = this.tickCount;
        }
    }

    public void startAction(Action action) {
        this.entityData.set(DATA_ACTION, (byte) action.ordinal());
        this.actionTicks = 0;
        this.getNavigation().stop();
    }

    private void endAction() {
        this.entityData.set(DATA_ACTION, (byte) 0);
        this.actionTicks = 0;
        this.digTarget = null;
    }

    // --- ticking ---------------------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (stealCooldown > 0) stealCooldown--;
        if (raidCooldown > 0) raidCooldown--;
        if (ignoreShiniesTicks > 0) ignoreShiniesTicks--;
        if (digCooldown > 0) digCooldown--;
        if (isTame()) {
            fleeTicks = 0;
            hoardTicks = 0;
            if (--senseCooldown <= 0) {
                senseCooldown = SENSE_INTERVAL;
                if (pendingTarget == null && !isOrderedToSit()) {
                    pendingTarget = senseTreasure(level);
                }
            }
        } else {
            tickWild(level);
        }
        if (action() != Action.NONE) {
            tickAction(level);
        }
    }

    private void tickWild(ServerLevel level) {
        if (fleeTicks > 0) {
            fleeTicks--;
            Player from = chaser == null ? null : level.getPlayerByUUID(chaser);
            boolean clear = from == null || from.distanceTo(this) > ESCAPE_DISTANCE;
            if (clear && action() == Action.NONE && !pouch.isEmpty() && canBurrowHere()) {
                startAction(Action.BURROW);
            }
            return;
        }
        if (pouch.isEmpty() || action() != Action.NONE) {
            hoardTicks = 0;
            return;
        }
        Player near = level.getNearestPlayer(this, 12.0D);
        if (near != null && !near.isSpectator()) {
            hoardTicks = 0;
        } else if (++hoardTicks > HOARD_TICKS && canBurrowHere()) {
            startAction(Action.BURROW);
        }
    }

    private void tickAction(ServerLevel level) {
        Action a = action();
        actionTicks++;
        BlockPos under = this.blockPosition().below();
        BlockState ground = level.getBlockState(under);
        Vec3 front = this.position().add(Vec3.directionFromRotation(0.0F, this.getYRot()).scale(0.35D));
        switch (a) {
            case SNIFF -> {
                if (actionTicks % 8 == 1) {
                    playSound(SoundEvents.FOX_SNIFF, 0.6F, 1.6F + random.nextFloat() * 0.3F);
                }
            }
            case DIG -> {
                if (actionTicks % 3 == 0 && !ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            front.x, front.y + 0.1D, front.z, 3, 0.12D, 0.05D, 0.12D, 0.15D);
                }
                if (actionTicks % 12 == 1) {
                    playSound(SoundEvents.SNIFFER_DIGGING, 0.5F, 1.7F + random.nextFloat() * 0.2F);
                }
                if (digTarget != null && actionTicks % 5 == 0) {
                    sparkTrail(level, digTarget);
                }
            }
            case BURROW -> {
                if (actionTicks % 2 == 0 && !ground.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            getX(), getY() + 0.1D, getZ(), 4, 0.2D, 0.05D, 0.2D, 0.2D);
                }
                if (actionTicks % 10 == 1) {
                    playSound(SoundEvents.SNIFFER_DIGGING, 0.7F, 1.5F + random.nextFloat() * 0.2F);
                }
            }
            case TICKLED -> {
                if (actionTicks % 9 == 1) {
                    playSound(SoundEvents.ARMADILLO_AMBIENT, 0.8F, 1.8F + random.nextFloat() * 0.3F);
                }
                if (actionTicks >= 8 && actionTicks <= 44 && actionTicks % 3 == 0) {
                    popOne(level);
                }
            }
            default -> {
            }
        }
        if (actionTicks >= a.ticks) {
            switch (a) {
                case DIG -> finishDig(level);
                case BURROW -> {
                    finishBurrow(level);
                    return;
                }
                case TICKLED -> {
                    // Whatever did not come out in the tickle comes out now: the owner asked for all of it.
                    while (!pouch.isEmpty()) {
                        if (!popOne(level)) {
                            break;
                        }
                    }
                }
                default -> {
                }
            }
            endAction();
        }
    }

    // --- stealing ---------------------------------------------------------------------------------------

    /** The inventory slot (main inventory only, never armour) of a shiny stack the player carries, or -1. */
    public static int shinySlot(Player player, RandomSource random) {
        List<Integer> slots = new ArrayList<>();
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (isShiny(items.get(i))) {
                slots.add(i);
            }
        }
        return slots.isEmpty() ? -1 : slots.get(random.nextInt(slots.size()));
    }

    /** Takes part of one shiny stack (half at most, sixteen at most, one at least) and runs. */
    public boolean steal(Player player) {
        int slot = shinySlot(player, this.random);
        if (slot < 0 || !pouchHasRoom()) {
            return false;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        int take = Math.max(1, Math.min(16, stack.getCount() / 2));
        if (stack.getCount() > 1) {
            take = 1 + this.random.nextInt(take);
        }
        ItemStack taken = stack.split(take);
        ItemStack back = addToPouch(taken.copy());
        if (!back.isEmpty()) {
            player.getInventory().add(back);
        }
        player.getInventory().setChanged();
        int count = take - back.getCount();
        this.playSound(SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.8F, 1.6F);
        this.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.4F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xFFD060, 0.06F, 16),
                    getX(), getY() + 0.3D, getZ(), 8, 0.2D, 0.2D, 0.2D, 0.05D);
        }
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.niffler.stole",
                count, taken.getHoverName()).withStyle(ChatFormatting.GOLD), true);
        if (player instanceof ServerPlayer sp) {
            ModAdvancements.award(sp, "niffler_robbed");
        }
        this.chaser = player.getUUID();
        this.fleeTicks = FLEE_TICKS;
        this.stealCooldown = STEAL_COOLDOWN;
        this.hoardTicks = 0;
        return true;
    }

    /** A player the wild Niffler can rob: near, alive, not creative or spectating, carrying shinies. */
    @Nullable
    private Player victim() {
        Player best = null;
        double bestD = STEAL_RANGE * STEAL_RANGE;
        for (Player p : this.level().players()) {
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) || !p.isAlive()) {
                continue;
            }
            double d = p.distanceToSqr(this);
            if (d < bestD && shinySlot(p, this.random) >= 0) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    // --- burrowing ---------------------------------------------------------------------------------------

    public boolean canBurrowHere() {
        BlockState below = this.level().getBlockState(this.blockPosition().below());
        return this.onGround() && !this.isInWater() && below.is(BlockTags.MINEABLE_WITH_SHOVEL)
                && !below.hasBlockEntity();
    }

    /**
     * Gone into the ground. The pouch is buried where it went down, one stack to a suspicious block
     * (sand under sand, gravel under anything else), so a brush gets every item back. A stack with no
     * place to go is dropped, never deleted.
     */
    private void finishBurrow(ServerLevel level) {
        List<ItemStack> loot = pouch.removeAllItems();
        List<BlockPos> spots = stashSpots(level, loot.size());
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (i < spots.size() && bury(level, spots.get(i), stack)) {
                continue;
            }
            this.spawnAtLocation(stack);
        }
        BlockState ground = level.getBlockState(this.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                    getX(), getY() + 0.1D, getZ(), 24, 0.3D, 0.1D, 0.3D, 0.25D);
        }
        level.playSound(null, blockPosition(), SoundEvents.SNIFFER_DIGGING_STOP, SoundSource.NEUTRAL, 0.8F, 1.6F);
        this.discard();
    }

    /** Places to bury: the block under it, then the ring round that, then the layer below. */
    private List<BlockPos> stashSpots(ServerLevel level, int wanted) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos base = this.blockPosition().below();
        for (int dy = 0; dy >= -2 && out.size() < wanted; dy--) {
            for (int r = 0; r <= 1 && out.size() < wanted; r++) {
                for (int dx = -r; dx <= r && out.size() < wanted; dx++) {
                    for (int dz = -r; dz <= r && out.size() < wanted; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos p = base.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(p);
                        // Earth only, nothing with contents, and never over a hole: a suspicious
                        // block over air would fall and its item would go with it.
                        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL) && !s.hasBlockEntity()
                                && !FallingBlock.isFree(level.getBlockState(p.below()))) {
                            out.add(p);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean bury(ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockState was = level.getBlockState(pos);
        BlockState sus = was.is(BlockTags.SAND) ? Blocks.SUSPICIOUS_SAND.defaultBlockState()
                : Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();
        level.setBlock(pos, sus, 3);
        if (!(level.getBlockEntity(pos) instanceof BrushableBlockEntity be)) {
            level.setBlock(pos, was, 3);
            return false;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("item", stack.save(level.registryAccess()));
        be.loadWithComponents(tag, level.registryAccess());
        be.setChanged();
        return true;
    }

    // --- the tame Niffler's work ----------------------------------------------------------------------------

    /** The nearest treasure within {@link #SENSE_RADIUS} that it has not shown already, or null. */
    @Nullable
    public BlockPos senseTreasure(ServerLevel level) {
        BlockPos at = this.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int r = SENSE_RADIUS;
        // Block entities first: unopened chests, brushable blocks.
        for (int cx = (at.getX() - r) >> 4; cx <= (at.getX() + r) >> 4; cx++) {
            for (int cz = (at.getZ() - r) >> 4; cz <= (at.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (!isTreasure(be) || wasShown(p) || Math.abs(p.getX() - at.getX()) > r
                            || Math.abs(p.getZ() - at.getZ()) > r || Math.abs(p.getY() - at.getY()) > r) {
                        continue;
                    }
                    double d = p.distSqr(at);
                    if (d < bestD) {
                        best = p;
                        bestD = d;
                    }
                }
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, 3, r))) {
            BlockState s = level.getBlockState(p);
            if (isTreasure(s) && !wasShown(p)) {
                double d = p.distSqr(at);
                if (d < bestD) {
                    best = p.immutable();
                    bestD = d;
                }
            }
        }
        return best;
    }

    private static boolean isTreasure(BlockEntity be) {
        if (be instanceof RandomizableContainerBlockEntity c) {
            return c.getLootTable() != null;
        }
        if (be instanceof BrushableBlockEntity b) {
            // Its loot table is private; an unbrushed block saves it, a brushed-out one has neither.
            return !b.getItem().isEmpty() || b.getLevel() != null
                    && b.saveWithoutMetadata(b.getLevel().registryAccess()).contains("LootTable");
        }
        return false;
    }

    private static boolean isTreasure(BlockState s) {
        return s.is(BlockTags.GOLD_ORES) || s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.RAW_GOLD_BLOCK)
                || s.is(Blocks.GILDED_BLACKSTONE) || s.getBlock() instanceof ConcealedRockBlock;
    }

    private boolean canDigHere() {
        BlockState below = this.level().getBlockState(this.blockPosition().below());
        return this.onGround() && (below.is(BlockTags.MINEABLE_WITH_SHOVEL) || below.is(BlockTags.SAND));
    }

    private void finishDig(ServerLevel level) {
        if (digTarget != null) {
            remember(digTarget);
            if (getOwner() instanceof ServerPlayer owner && owner.distanceTo(this) < 32.0F) {
                int depth = this.blockPosition().getY() - digTarget.getY();
                owner.displayClientMessage(Component.translatable(depth > 1
                                ? "message.hogwartsmagic.niffler.found_below" : "message.hogwartsmagic.niffler.found",
                        Math.max(1, depth)).withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.4F);
            return;
        }
        digCooldown = 2400 + this.random.nextInt(2400);
        ResourceKey<LootTable> key = level.getBiome(blockPosition()).is(ModBiomes.CURSEBREAKERS_DESERT)
                ? DIG_LOOT_DESERT : DIG_LOOT;
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, position())
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .create(LootContextParamSets.GIFT);
        boolean any = false;
        for (ItemStack stack : table.getRandomItems(params)) {
            ItemStack back = addToPouch(stack);
            if (!back.isEmpty()) {
                spawnAtLocation(back);
            }
            any = true;
        }
        if (any) {
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xFFD060, 0.06F, 18),
                    getX(), getY() + 0.2D, getZ(), 10, 0.2D, 0.15D, 0.2D, 0.06D);
            level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.NEUTRAL, 0.8F, 1.7F);
        }
    }

    /** A find it has shown already, or one within three blocks of it: a vein is one find, not eight. */
    private boolean wasShown(BlockPos p) {
        for (BlockPos q : shown) {
            if (q.distSqr(p) <= 9.0D) {
                return true;
            }
        }
        return false;
    }

    private void remember(BlockPos p) {
        shown.addLast(p.immutable());
        while (shown.size() > SHOWN_MEMORY) {
            shown.removeFirst();
        }
    }

    /** Gold sparks from the Niffler's nose along the way to the find, two blocks long: a pointer. */
    private void sparkTrail(ServerLevel level, BlockPos target) {
        Vec3 from = position().add(0.0D, 0.25D, 0.0D);
        Vec3 dir = Vec3.atCenterOf(target).subtract(from);
        double len = dir.length();
        if (len < 1.0E-3) {
            return;
        }
        dir = dir.scale(1.0D / len);
        for (int i = 1; i <= 6; i++) {
            Vec3 p = from.add(dir.scale(Math.min(len, i * 0.35D)));
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xFFD060, 0.05F, 14),
                    p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    /** One stack out of the pouch, tossed toward the tickler. False when the pouch is empty. */
    private boolean popOne(ServerLevel level) {
        for (int i = 0; i < pouch.getContainerSize(); i++) {
            ItemStack s = pouch.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            pouch.setItem(i, ItemStack.EMPTY);
            Player to = tickler == null ? null : level.getPlayerByUUID(tickler);
            Vec3 dir = to == null ? Vec3.ZERO : to.position().subtract(position()).normalize();
            ItemEntity item = new ItemEntity(level, getX(), getY() + 0.35D, getZ(), s,
                    dir.x * 0.2D + (random.nextDouble() - 0.5D) * 0.1D, 0.3D,
                    dir.z * 0.2D + (random.nextDouble() - 0.5D) * 0.1D);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
            ignoreShiniesTicks = 200;
            level.playSound(null, blockPosition(), SoundEvents.CHAIN_FALL, SoundSource.NEUTRAL, 0.6F,
                    1.8F + random.nextFloat() * 0.2F);
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xFFD060, 0.05F, 14),
                    getX(), getY() + 0.35D, getZ(), 3, 0.1D, 0.1D, 0.1D, 0.04D);
            return true;
        }
        return false;
    }

    // --- interaction ---------------------------------------------------------------------------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLD_NUGGET);
    }

    @Override
    public boolean canMate(Animal other) {
        return this.isTame() && other instanceof NifflerEntity n && n.isTame() && super.canMate(other);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = this.level().isClientSide;
        if (!isTame()) {
            if (stack.is(Items.GOLD_NUGGET)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (!client) {
                    if (this.random.nextInt(4) == 0 && !ForgeEventFactory.onAnimalTame(this, player)) {
                        this.tame(player);
                        this.navigation.stop();
                        this.fleeTicks = 0;
                        this.chaser = null;
                        this.setOrderedToSit(false);
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.sidedSuccess(client);
            }
            return super.mobInteract(player, hand);
        }
        if (isOwnedBy(player)) {
            if (stack.is(Items.GOLD_NUGGET) && this.getHealth() < this.getMaxHealth()) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.heal(2.0F);
                return InteractionResult.sidedSuccess(client);
            }
            if (stack.isEmpty()) {
                if (!client) {
                    if (!player.isShiftKeyDown() && fullness() > 0 && action() == Action.NONE) {
                        this.tickler = player.getUUID();
                        this.setOrderedToSit(false);
                        startAction(Action.TICKLED);
                    } else {
                        this.setOrderedToSit(!this.isOrderedToSit());
                        this.navigation.stop();
                    }
                }
                return InteractionResult.sidedSuccess(client);
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive()) {
            if (action() == Action.BURROW || action() == Action.DIG || action() == Action.SNIFF) {
                endAction();
            }
            if (!isTame()) {
                // Every knock shakes something out of the pouch.
                for (int n = 1 + this.random.nextInt(2); n > 0; n--) {
                    if (!shakeOne()) {
                        break;
                    }
                }
                Entity from = source.getEntity();
                if (from instanceof Player p) {
                    this.chaser = p.getUUID();
                }
                this.fleeTicks = Math.max(this.fleeTicks, 200);
            }
        }
        return hurt;
    }

    private boolean shakeOne() {
        List<Integer> full = new ArrayList<>();
        for (int i = 0; i < pouch.getContainerSize(); i++) {
            if (!pouch.getItem(i).isEmpty()) {
                full.add(i);
            }
        }
        if (full.isEmpty()) {
            return false;
        }
        int slot = full.get(this.random.nextInt(full.size()));
        ItemStack s = pouch.removeItemNoUpdate(slot);
        pouch.setChanged();
        updateFullness();
        ItemEntity e = this.spawnAtLocation(s, 0.3F);
        if (e != null) {
            e.setDeltaMovement((random.nextDouble() - 0.5D) * 0.25D, 0.3D, (random.nextDouble() - 0.5D) * 0.25D);
        }
        ignoreShiniesTicks = Math.max(ignoreShiniesTicks, 60);
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        for (ItemStack s : pouch.removeAllItems()) {
            if (!s.isEmpty()) {
                this.spawnAtLocation(s);
            }
        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        NifflerEntity baby = ModEntities.NIFFLER.get().create(level);
        if (baby != null && this.getOwnerUUID() != null) {
            baby.setOwnerUUID(this.getOwnerUUID());
            baby.setTame(true, true);
        }
        return baby;
    }

    // --- persistence -----------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Pouch", pouch.createTag(this.registryAccess()));
        tag.putInt("DigCooldown", digCooldown);
        tag.putInt("StealCooldown", stealCooldown);
        tag.putInt("HoardTicks", hoardTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Pouch", Tag.TAG_LIST)) {
            pouch.fromTag(tag.getList("Pouch", Tag.TAG_COMPOUND), this.registryAccess());
        }
        digCooldown = tag.getInt("DigCooldown");
        stealCooldown = tag.getInt("StealCooldown");
        hoardTicks = tag.getInt("HoardTicks");
        updateFullness();
    }

    /** A wild Niffler with loot in its pouch stays in the world: despawning would delete the loot. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || !pouch.isEmpty();
    }

    // --- sounds ------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return action() == Action.NONE && this.random.nextInt(3) == 0 ? SoundEvents.FOX_SNIFF : SoundEvents.ARMADILLO_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMADILLO_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMADILLO_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ARMADILLO_STEP, 0.1F, 1.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.45F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    // --- goals ---------------------------------------------------------------------------------------------------

    /** While it digs, burrows, sniffs or is tickled, it does nothing else and stays where it is. */
    private final class BusyGoal extends Goal {
        BusyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return action() != Action.NONE;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getNavigation().stop();
            if (digTarget != null) {
                getLookControl().setLookAt(Vec3.atCenterOf(digTarget));
            } else if (action() == Action.TICKLED && tickler != null) {
                Player p = level().getPlayerByUUID(tickler);
                if (p != null) {
                    getLookControl().setLookAt(p);
                }
            }
        }
    }

    /** Away from the player it robbed (or the one that hit it), fast, until it is clear or the time runs out. */
    private final class FleeGoal extends Goal {
        private int repath;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !isTame() && fleeTicks > 0 && action() == Action.NONE;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0 && !getNavigation().isDone()) {
                return;
            }
            repath = 20;
            Player from = chaser == null ? null : level().getPlayerByUUID(chaser);
            Vec3 away = from != null ? from.position() : position();
            Vec3 to = DefaultRandomPos.getPosAway(NifflerEntity.this, 16, 7, away);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, 1.6D);
            }
        }
    }

    /** To a player who carries something shiny, quickly, and into their pockets. */
    private final class StealGoal extends Goal {
        @Nullable
        private Player target;
        private int ticks;

        StealGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isTame() || stealCooldown > 0 || fleeTicks > 0 || action() != Action.NONE || !pouchHasRoom()
                    || getRandom().nextInt(reducedTickDelay(10)) != 0) {
                return false;
            }
            target = victim();
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && ticks < 240 && fleeTicks == 0
                    && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)
                    && target.distanceToSqr(NifflerEntity.this) < 256.0D;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            if (fleeTicks == 0) {
                stealCooldown = Math.max(stealCooldown, 200);   // it lost them: it tries again later
            }
            target = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (ticks % 10 == 1) {
                getNavigation().moveTo(target, 1.4D);
            }
            if (distanceToSqr(target) < 2.0D) {
                if (!steal(target)) {
                    stealCooldown = 200;
                }
            }
        }
    }

    /** To a shiny thing lying on the ground, and into the pouch with it. */
    private final class SeekShinyGoal extends Goal {
        @Nullable
        private ItemEntity item;
        private int ticks;

        SeekShinyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (ignoreShiniesTicks > 0 || action() != Action.NONE || !pouchHasRoom() || isOrderedToSit()
                    || (!isTame() && fleeTicks > 0) || getRandom().nextInt(reducedTickDelay(10)) != 0) {
                return false;
            }
            item = nearestShiny();
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && ticks < 200 && action() == Action.NONE && !isOrderedToSit();
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            item = null;
        }

        @Override
        public void tick() {
            ticks++;
            getLookControl().setLookAt(item);
            if (ticks % 10 == 1) {
                getNavigation().moveTo(item, 1.3D);
            }
            if (distanceToSqr(item) < 1.4D) {
                ItemStack back = addToPouch(item.getItem().copy());
                int got = item.getItem().getCount() - back.getCount();
                if (got > 0) {
                    take(item, got);
                    playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.5F);
                }
                if (back.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(back);
                }
                item = null;
            }
        }

        @Nullable
        private ItemEntity nearestShiny() {
            Entity owner = getOwner();
            ItemEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(12.0D, 4.0D, 12.0D))) {
                if (!e.isAlive() || e.hasPickUpDelay() || !isShiny(e.getItem())) {
                    continue;
                }
                if (isTame() && owner != null && e.getOwner() == owner) {
                    continue;       // the owner threw it: it is not for the Niffler
                }
                double d = e.distanceToSqr(NifflerEntity.this);
                if (d < bestD) {
                    best = e;
                    bestD = d;
                }
            }
            return best;
        }
    }

    /** The tame Niffler goes to the treasure it smelled and digs at it, or digs up its own. */
    private final class TreasureGoal extends Goal {
        private int ticks;

        TreasureGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isTame() || isOrderedToSit() || action() != Action.NONE) {
                return false;
            }
            LivingEntity owner = getOwner();
            if (owner == null || owner.distanceToSqr(NifflerEntity.this) > 400.0D) {
                return false;
            }
            return pendingTarget != null || (digCooldown <= 0 && canDigHere());
        }

        @Override
        public boolean canContinueToUse() {
            return pendingTarget != null && ticks < 160 && action() == Action.NONE && !isOrderedToSit();
        }

        @Override
        public void start() {
            ticks = 0;
            if (pendingTarget == null) {
                digTarget = null;
                startAction(Action.DIG);
                return;
            }
            playSound(SoundEvents.FOX_SNIFF, 0.8F, 1.7F);
            getNavigation().moveTo(pendingTarget.getX() + 0.5D, pendingTarget.getY(), pendingTarget.getZ() + 0.5D, 1.3D);
        }

        @Override
        public void tick() {
            ticks++;
            BlockPos t = pendingTarget;
            if (t == null) {
                return;
            }
            double dx = t.getX() + 0.5D - getX();
            double dz = t.getZ() + 0.5D - getZ();
            if (dx * dx + dz * dz < 6.25D || getNavigation().isDone() || ticks >= 159) {
                digTarget = t;
                pendingTarget = null;
                startAction(Action.DIG);
            } else if (ticks % 20 == 0) {
                getNavigation().moveTo(t.getX() + 0.5D, t.getY(), t.getZ() + 0.5D, 1.3D);
            }
        }

        @Override
        public void stop() {
            if (pendingTarget != null) {
                remember(pendingTarget);    // could not reach it: do not try forever
                pendingTarget = null;
            }
        }
    }

    /** The wild Niffler's raid: into a chest or barrel nearby for one shiny stack. */
    private final class RaidGoal extends Goal {
        @Nullable
        private BlockPos chest;
        private int ticks;

        RaidGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isTame() || raidCooldown > 0 || fleeTicks > 0 || action() != Action.NONE || !pouchHasRoom()) {
                return false;
            }
            raidCooldown = 80;
            chest = findChest();
            return chest != null;
        }

        @Override
        public boolean canContinueToUse() {
            return chest != null && ticks < 200 && fleeTicks == 0;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            chest = null;
        }

        @Override
        public void tick() {
            ticks++;
            getLookControl().setLookAt(Vec3.atCenterOf(chest));
            if (ticks % 20 == 1) {
                getNavigation().moveTo(chest.getX() + 0.5D, chest.getY(), chest.getZ() + 0.5D, 1.2D);
            }
            if (distanceToSqr(Vec3.atBottomCenterOf(chest)) < 3.5D) {
                raid(chest);
                raidCooldown = 400;
                chest = null;
            }
        }

        @Nullable
        private BlockPos findChest() {
            BlockPos at = blockPosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            ChunkPos c = new ChunkPos(at);
            for (int cx = c.x - 1; cx <= c.x + 1; cx++) {
                for (int cz = c.z - 1; cz <= c.z + 1; cz++) {
                    if (!level().hasChunk(cx, cz)) {
                        continue;
                    }
                    for (BlockEntity be : level().getChunk(cx, cz).getBlockEntities().values()) {
                        double d = be.getBlockPos().distSqr(at);
                        if (d < 64.0D && d < bestD && raidable(be)) {
                            best = be.getBlockPos();
                            bestD = d;
                        }
                    }
                }
            }
            return best;
        }
    }

    /** A container with a shiny in it whose loot has been rolled (an unopened tomb chest is left alone). */
    public static boolean raidable(BlockEntity be) {
        if (!(be instanceof Container c)) {
            return false;
        }
        if (be instanceof RandomizableContainerBlockEntity r && r.getLootTable() != null) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (isShiny(c.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Takes one shiny stack out of the container at pos. */
    public boolean raid(BlockPos pos) {
        if (!(level().getBlockEntity(pos) instanceof Container c) || !raidable(level().getBlockEntity(pos))) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (isShiny(s)) {
                ItemStack back = addToPouch(s.copy());
                c.setItem(i, back);
                c.setChanged();
                level().playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.4F);
                level().playSound(null, pos, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.NEUTRAL, 0.7F, 1.6F);
                hoardTicks = 0;
                return true;
            }
        }
        return false;
    }

    /** Pouch swelling, for the renderer: 0 empty to 1 full. */
    public float pouchFill() {
        return Mth.clamp(fullness() / 9.0F, 0.0F, 1.0F);
    }
}
