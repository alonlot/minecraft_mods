package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DespairBasinBlock;
import com.hogwartsmagic.entity.InferiusEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.locket.AmbushSchedule;
import com.hogwartsmagic.locket.BasinStage;
import com.hogwartsmagic.locket.CaveGeometry;
import com.hogwartsmagic.locket.DespairRules;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Drinking, refilling, the Locket, the lake warning and the Inferi ambush of the Basin of Despair. */
public class DespairBasinBlockEntity extends BlockEntity {
    @Nullable private UUID drinker;
    private int drinks;
    private long lastDrink;
    private boolean ambushActive;
    private int ambushTicks;
    private int ambushSpawned;
    private final Set<UUID> ambushMobs = new HashSet<>();
    @Nullable private UUID ambushTrigger;
    private long lastWarning = -100000;
    private int owedSpawns;
    private static final int AMBUSH_GRACE_TICKS = 200;

    public DespairBasinBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.DESPAIR_BASIN_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean isAmbushActive() {
        return ambushActive;
    }

    public void onUse(ServerPlayer p) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        BasinStage stage = getBlockState().getValue(DespairBasinBlock.STAGE);
        BlockPos d = p.blockPosition().subtract(worldPosition);
        if (stage == BasinStage.LOOTED || !CaveGeometry.onIsland(d.getX(), d.getY(), d.getZ())) {
            return;
        }
        if (drinks >= DespairRules.DRINKS_TO_EMPTY) { // locket waiting in the empty basin
            if (!p.getUUID().equals(drinker)) {
                p.displayClientMessage(Component.translatable("message.hogwartsmagic.basin_in_use"), true);
                return;
            }
            p.getInventory().placeItemBackInInventory(new ItemStack(ExampleMod.SLYTHERINS_LOCKET.get()));
            ExampleMod.UNQUENCHABLE_THIRST.getHolder().ifPresent(h -> p.addEffect(
                    new MobEffectInstance(h, DespairRules.THIRST_DURATION_TICKS, 0, false, false, true)));
            setStage(BasinStage.LOOTED);
            drinker = null;
            drinks = 0;
            LocketCaves.recordLooted(level, worldPosition);
            p.displayClientMessage(Component.translatable("message.hogwartsmagic.locket_taken")
                    .withStyle(ChatFormatting.DARK_GREEN), false);
            setChanged();
            return;
        }
        if (drinker != null && !drinker.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.hogwartsmagic.basin_in_use"), true);
            return;
        }
        long now = level.getGameTime();
        if (drinker != null && drinks > 0 && now - lastDrink < DespairRules.MIN_TICKS_BETWEEN_DRINKS) {
            return;
        }
        drinker = p.getUUID();
        drinks++;
        lastDrink = now;
        for (DespairRules.DrinkEffect e : DespairRules.effectsFor(drinks)) {
            BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace(e.effectId()))
                    .ifPresent(h -> p.addEffect(new MobEffectInstance(h, e.ticks(), e.amplifier())));
        }
        float dmg = DespairRules.cappedDamage(DespairRules.damageFor(drinks), p.getHealth());
        if (dmg > 0) {
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().magic(), dmg);
        }
        p.displayClientMessage(Component.literal(DespairRules.whisper(drinks - 1))
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), false);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_DRINK, SoundSource.BLOCKS, 1f, 0.6f);
        setStage(BasinStage.DRINKING); // also shows the Locket once the sixth drink is taken
        setChanged();
    }

    private void setStage(BasinStage s) {
        if (this.level == null) {
            return;
        }
        BlockState state = getBlockState().setValue(DespairBasinBlock.STAGE, s)
                .setValue(DespairBasinBlock.LOCKET, s == BasinStage.DRINKING && drinks >= DespairRules.DRINKS_TO_EMPTY);
        this.level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
    }

    public static void serverTick(Level lvl, BlockPos pos, BlockState state, DespairBasinBlockEntity be) {
        if (!(lvl instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        // Refill. Once the Locket shows, the basin only waits for the drinker to take it (no timeout).
        if (be.drinker != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(be.drinker);
            boolean alive = p != null && p.isAlive() && p.level() == level;
            BlockPos d = alive ? p.blockPosition().subtract(pos) : BlockPos.ZERO;
            boolean onIsland = alive && CaveGeometry.onIsland(d.getX(), d.getY(), d.getZ());
            boolean timedOut = be.drinks < DespairRules.DRINKS_TO_EMPTY
                    && DespairRules.shouldRefill(now, be.lastDrink, onIsland, alive);
            boolean leftWithLocketShowing = be.drinks >= DespairRules.DRINKS_TO_EMPTY && !onIsland;
            if (timedOut || leftWithLocketShowing) {
                be.drinker = null;
                be.drinks = 0;
                be.setStage(BasinStage.FULL);
                be.setChanged();
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1f, 0.5f);
            }
        }
        if (now % 10 == 0) {
            be.checkLake(level, now);
        }
        if (be.ambushActive) {
            be.tickAmbush(level);
        }
    }

    private boolean inCave(ServerPlayer p) {
        BlockPos d = p.blockPosition().subtract(worldPosition);
        return CaveGeometry.inCave(d.getX(), d.getY(), d.getZ());
    }

    private void checkLake(ServerLevel level, long now) {
        var thirst = ExampleMod.UNQUENCHABLE_THIRST.getHolder().orElse(null);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !inCave(p)) {
                continue;
            }
            if (thirst != null && p.hasEffect(thirst) && p.isEyeInFluid(FluidTags.WATER)) {
                if (LocketCaves.cureThirst(p) && !ambushActive) {
                    startAmbush(p);
                }
            } else if (p.isInWater() && !ambushActive && now - lastWarning > AmbushSchedule.WARNING_COOLDOWN) {
                int count = AmbushSchedule.WARNING_MIN
                        + level.random.nextInt(AmbushSchedule.WARNING_MAX - AmbushSchedule.WARNING_MIN + 1);
                for (int i = 0; i < count; i++) {
                    BlockPos at = findWater(level, p.blockPosition(), 2, 5, 10);
                    if (at != null) {
                        spawnInferius(level, at, false, null);
                    }
                }
                lastWarning = now;
                setChanged();
            }
        }
    }

    public void startAmbush(ServerPlayer trigger) {
        ambushActive = true;
        ambushTicks = 0;
        ambushSpawned = 0;
        owedSpawns = 0;
        ambushMobs.clear();
        ambushTrigger = trigger.getUUID();
        if (this.level instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && inCave(p)) {
                    p.displayClientMessage(Component.translatable("message.hogwartsmagic.water_stirs")
                            .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), false);
                }
            }
        }
        setChanged();
    }

    private void tickAmbush(ServerLevel level) {
        owedSpawns += AmbushSchedule.spawnsDueAt(ambushTicks++);
        ServerPlayer trigger = ambushTrigger == null ? null : level.getServer().getPlayerList().getPlayer(ambushTrigger);
        // A spawn that finds no water stays owed and is retried; after the grace period it counts as attempted
        // so a lake with no usable water cannot make the ambush unwinnable.
        for (int i = 0, tries = owedSpawns; i < tries; i++) {
            BlockPos at = findWater(level, worldPosition, CaveGeometry.ISLAND_RADIUS + 1, CaveGeometry.ISLAND_RADIUS + 5, 10);
            InferiusEntity e = at == null ? null : spawnInferius(level, at, true, trigger);
            if (e != null) {
                ambushMobs.add(e.getUUID());
                ambushSpawned++;
                owedSpawns--;
            }
        }
        int lastWaveTick = (AmbushSchedule.TOTAL / AmbushSchedule.WAVE_SIZE - 1) * AmbushSchedule.WAVE_INTERVAL;
        if (owedSpawns > 0 && ambushTicks > lastWaveTick + AMBUSH_GRACE_TICKS) {
            ambushSpawned += owedSpawns;
            owedSpawns = 0;
        }
        // An entity that is not loaded counts as gone.
        ambushMobs.removeIf(id -> !(level.getEntity(id) instanceof InferiusEntity e) || !e.isAlive());
        boolean anyPlayerInCave = false;
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && inCave(p)) {
                anyPlayerInCave = true;
                break;
            }
        }
        if (AmbushSchedule.isOver(ambushSpawned, ambushMobs.size(), anyPlayerInCave)) {
            endAmbush(level);
        } else if (ambushTicks % 20 == 0) {
            setChanged();
        }
    }

    /** Survivors lose their target and wander back into the lake; their idle rule despawns them there. */
    private void endAmbush(ServerLevel level) {
        for (UUID id : ambushMobs) {
            if (level.getEntity(id) instanceof InferiusEntity e && e.isAlive()) {
                e.setTarget(null);
                e.setAmbush(false);
                BlockPos water = findWater(level, e.blockPosition(), 3, 10, 12);
                if (water != null) {
                    e.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
                }
            }
        }
        ambushMobs.clear();
        owedSpawns = 0;
        ambushActive = false;
        ambushTrigger = null;
        setChanged();
    }

    @Nullable
    private InferiusEntity spawnInferius(ServerLevel level, BlockPos at, boolean ambush, @Nullable LivingEntity target) {
        InferiusEntity e = ModEntities.INFERIUS.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        e.bindToCave(worldPosition);
        e.setAmbush(ambush);
        if (target != null) {
            e.setTarget(target);
        }
        level.addFreshEntity(e);
        e.startRise();
        return e;
    }

    /**
     * A random water block {@code minR..maxR} blocks (horizontally) from {@code center}, inside the cave and
     * outside the island, with water or air above and room for the body. Never loads chunks.
     */
    @Nullable
    private BlockPos findWater(ServerLevel level, BlockPos center, int minR, int maxR, int tries) {
        int islandSq = CaveGeometry.ISLAND_RADIUS * CaveGeometry.ISLAND_RADIUS;
        for (int t = 0; t < tries; t++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = minR + level.random.nextDouble() * (maxR - minR);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
            int dx = x - worldPosition.getX();
            int dz = z - worldPosition.getZ();
            if (dx * dx + dz * dz <= islandSq || !level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                continue;
            }
            for (int y = center.getY() + 3; y >= center.getY() - 8; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!CaveGeometry.inCave(dx, y - worldPosition.getY(), dz)) {
                    continue;
                }
                if (level.getFluidState(pos).is(FluidTags.WATER)) {
                    if (roomAbove(level, pos.above()) && roomAbove(level, pos.above(2))) {
                        return pos;
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static boolean roomAbove(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (drinker != null) {
            tag.putUUID("Drinker", drinker);
        }
        tag.putInt("Drinks", drinks);
        tag.putLong("LastDrink", lastDrink);
        tag.putBoolean("AmbushActive", ambushActive);
        tag.putInt("AmbushTicks", ambushTicks);
        tag.putInt("AmbushSpawned", ambushSpawned);
        tag.putInt("OwedSpawns", owedSpawns);
        tag.putLong("LastWarning", lastWarning);
        if (ambushTrigger != null) {
            tag.putUUID("AmbushTrigger", ambushTrigger);
        }
        ListTag mobs = new ListTag();
        for (UUID id : ambushMobs) {
            mobs.add(NbtUtils.createUUID(id));
        }
        tag.put("AmbushMobs", mobs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drinker = tag.hasUUID("Drinker") ? tag.getUUID("Drinker") : null;
        drinks = tag.getInt("Drinks");
        lastDrink = tag.getLong("LastDrink");
        ambushActive = tag.getBoolean("AmbushActive");
        ambushTicks = tag.getInt("AmbushTicks");
        ambushSpawned = tag.getInt("AmbushSpawned");
        owedSpawns = tag.getInt("OwedSpawns");
        lastWarning = tag.contains("LastWarning") ? tag.getLong("LastWarning") : -100000;
        ambushTrigger = tag.hasUUID("AmbushTrigger") ? tag.getUUID("AmbushTrigger") : null;
        ambushMobs.clear();
        for (Tag t : tag.getList("AmbushMobs", Tag.TAG_INT_ARRAY)) {
            ambushMobs.add(NbtUtils.loadUUID(t));
        }
    }
}
