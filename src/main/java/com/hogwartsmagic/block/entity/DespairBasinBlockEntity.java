package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DespairBasinBlock;
import com.hogwartsmagic.entity.InferiusEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.locket.AmbushSchedule;
import com.hogwartsmagic.locket.BasinStage;
import com.hogwartsmagic.locket.CaveGeometry;
import com.hogwartsmagic.locket.CaveLayout;
import com.hogwartsmagic.item.CaveGobletItem;
import com.hogwartsmagic.locket.DespairRules;
import com.hogwartsmagic.locket.GobletFill;
import com.hogwartsmagic.locket.GobletRules;
import com.hogwartsmagic.locket.LocketCaves;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    /** Null until the first successful scan (or a load of a saved layout); version-1 answers meanwhile. */
    @Nullable private CaveLayout layout;

    public DespairBasinBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.DESPAIR_BASIN_BLOCK_ENTITY.get(), pos, state);
    }

    /** This cave's layout; a version-1 (old constants) layout until the waypoints have been scanned. */
    public CaveLayout layout() {
        return layout != null ? layout : CaveLayout.legacy(worldPosition);
    }

    /** Sets the layout directly, skipping the marker scan. For tests, which build a tiny cave by hand. */
    public void setLayoutDirectly(CaveLayout l) {
        this.layout = l;
        setChanged();
    }

    public boolean isAmbushActive() {
        return ambushActive;
    }

    /**
     * A goblet dipped into the basin. Fills the goblet with a cup of the potion and claims the basin for this
     * player; refused when someone else is mid-run, the basin is empty or looted, the player is off the island, or
     * the last cup was too recent. Returns whether the goblet was filled.
     */
    public boolean dip(ServerPlayer p, ItemStack goblet) {
        if (!(this.level instanceof ServerLevel level) || !(goblet.getItem() instanceof CaveGobletItem)) {
            return false;
        }
        if (getBlockState().getValue(DespairBasinBlock.STAGE) == BasinStage.LOOTED || !layout().onIsland(p.blockPosition())) {
            return false;
        }
        if (CaveGobletItem.fillOf(goblet) != GobletFill.EMPTY) {
            return false;
        }
        if (!GobletRules.mayDip(drinker, p.getUUID(), drinks)) {
            if (drinks < DespairRules.DRINKS_TO_EMPTY) {
                p.displayClientMessage(Component.translatable("message.hogwartsmagic.basin_in_use"), true);
            }
            return false;
        }
        long now = level.getGameTime();
        if (drinker != null && drinks > 0 && now - lastDrink < DespairRules.MIN_TICKS_BETWEEN_DRINKS) {
            return false;
        }
        drinker = p.getUUID();
        lastDrink = now;
        CaveGobletItem.setFill(goblet, GobletFill.POTION);
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 0.6f);
        setChanged();
        return true;
    }

    /** One cup drunk by the player who dipped it: its effects and damage (never lethal alone), and the basin drops a level. */
    public void drink(ServerPlayer p) {
        if (!(this.level instanceof ServerLevel level) || !p.getUUID().equals(drinker)
                || drinks >= DespairRules.DRINKS_TO_EMPTY
                || getBlockState().getValue(DespairBasinBlock.STAGE) == BasinStage.LOOTED) {
            return;
        }
        drinks++;
        lastDrink = level.getGameTime();
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
        setStage(BasinStage.DRINKING); // drops the level, and shows the Locket once the sixth cup is drunk
        setChanged();
    }

    /**
     * The cave has exactly one goblet and nothing may lose it: when no goblet block stands near the basin, no goblet
     * item lies in the cave and nobody in the cave carries one, a new goblet block is set beside the basin.
     * Returns whether one was placed. Never loads a chunk.
     */
    public boolean restoreGoblet(ServerLevel level) {
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-3, -3, -3), worldPosition.offset(3, 3, 3))) {
            if (level.hasChunkAt(at) && level.getBlockState(at).is(ExampleMod.CRYSTAL_GOBLET.get())) {
                return false;
            }
        }
        CaveLayout l = layout();
        int r = l.radius();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                worldPosition.getX() - r, worldPosition.getY() - l.down(), worldPosition.getZ() - r,
                worldPosition.getX() + r + 1, worldPosition.getY() + l.up() + 1, worldPosition.getZ() + r + 1);
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                e -> e.getItem().getItem() instanceof CaveGobletItem).isEmpty()) {
            return false;
        }
        for (ServerPlayer p : level.players()) {
            if (l.inCave(p.blockPosition()) && p.getInventory().hasAnyMatching(st -> st.getItem() instanceof CaveGobletItem)) {
                return false;
            }
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos at = worldPosition.offset(dx, 0, dz);
                double dist = at.distSqr(worldPosition);
                if (dist == 0 || dist >= bestDist || !level.hasChunkAt(at)) {
                    continue;
                }
                if (level.getBlockState(at).isAir() && level.getFluidState(at).isEmpty()
                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                    best = at;
                    bestDist = dist;
                }
            }
        }
        if (best == null) {
            return false;
        }
        level.setBlock(best, ExampleMod.CRYSTAL_GOBLET.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /** Test hook: forgets the last cup so the next dip is not held back by the spacing rule. */
    public void forgetLastDrinkForTest() {
        lastDrink = -100000;
    }

    public int drinks() {
        return drinks;
    }

    /** An empty hand lifts the Locket from the dry basin, if this player is the one who drank it dry. */
    public void takeLocket(ServerPlayer p) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        BasinStage stage = getBlockState().getValue(DespairBasinBlock.STAGE);
        if (stage == BasinStage.LOOTED || drinks < DespairRules.DRINKS_TO_EMPTY || !layout().onIsland(p.blockPosition())) {
            return;
        }
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
    }

    private void setStage(BasinStage s) {
        if (this.level == null) {
            return;
        }
        boolean looted = s == BasinStage.LOOTED;
        BlockState state = getBlockState().setValue(DespairBasinBlock.STAGE, s)
                .setValue(DespairBasinBlock.LOCKET, s == BasinStage.DRINKING && drinks >= DespairRules.DRINKS_TO_EMPTY)
                .setValue(DespairBasinBlock.LEVEL, looted ? 0 : Math.max(0, DespairRules.DRINKS_TO_EMPTY - drinks));
        this.level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
    }

    public static void serverTick(Level lvl, BlockPos pos, BlockState state, DespairBasinBlockEntity be) {
        if (!(lvl instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (be.layout == null && now % 20 == 0) {
            be.scanLayout(level);
        }
        // Refill. Once the Locket shows, the basin only waits for the drinker to take it (no timeout).
        if (be.drinker != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(be.drinker);
            boolean alive = p != null && p.isAlive() && p.level() == level;
            boolean onIsland = alive && be.layout().onIsland(p.blockPosition());
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
        if (now % 100 == 0 && be.drinker == null && state.getValue(DespairBasinBlock.STAGE) != BasinStage.LOOTED) {
            be.restoreGoblet(level);
        }
        if (now % 10 == 0) {
            be.checkLake(level, now);
        }
        if (be.ambushActive) {
            be.tickAmbush(level);
        }
    }

    /**
     * Reads the waypoint markers within SEARCH_RADIUS once, and saves the layout. Waits until every chunk in
     * range is loaded so a half-loaded cave is never mistaken for an old one; a cave with no markers at all
     * is version 1 and is saved as such.
     */
    private void scanLayout(ServerLevel level) {
        int r = CaveGeometry.SEARCH_RADIUS;
        int minCx = SectionPos.blockToSectionCoord(worldPosition.getX() - r);
        int maxCx = SectionPos.blockToSectionCoord(worldPosition.getX() + r);
        int minCz = SectionPos.blockToSectionCoord(worldPosition.getZ() - r);
        int maxCz = SectionPos.blockToSectionCoord(worldPosition.getZ() + r);
        List<CaveLayout.Marker> found = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return; // try again in a second
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (be instanceof CaveWaypointBlockEntity w && w.getBlockPos().distSqr(worldPosition) <= (double) r * r) {
                        found.add(new CaveLayout.Marker(w.role(), w.index(), w.getBlockPos()));
                    }
                }
            }
        }
        layout = CaveLayout.fromMarkers(worldPosition, found);
        setChanged();
    }

    private boolean inCave(ServerPlayer p) {
        return layout().inCave(p.blockPosition());
    }

    /** In the black lake proper; the antechamber pool and the tunnel are not the lake. */
    private boolean inLake(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        return layout().inLake(at, LocketCaves.isCoveredWater(p.level(), at));
    }

    private int idleGuardians(ServerLevel level) {
        CaveLayout l = layout();
        int r = l.radius();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                worldPosition.getX() - r, worldPosition.getY() - l.down(), worldPosition.getZ() - r,
                worldPosition.getX() + r + 1, worldPosition.getY() + l.up() + 1, worldPosition.getZ() + r + 1);
        return level.getEntitiesOfClass(InferiusEntity.class, box,
                e -> e.isAlive() && worldPosition.equals(e.caveBasin())).size();
    }

    private void checkLake(ServerLevel level, long now) {
        var thirst = ExampleMod.UNQUENCHABLE_THIRST.getHolder().orElse(null);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !inLake(p)) {
                continue;
            }
            if (thirst != null && p.hasEffect(thirst) && p.isEyeInFluid(FluidTags.WATER)) {
                if (LocketCaves.cureThirst(p) && !ambushActive) {
                    startAmbush(p);
                }
            } else if (p.isInWater() && !(p.getVehicle() instanceof com.hogwartsmagic.entity.CaveBoatEntity) && !ambushActive && now - lastWarning > AmbushSchedule.WARNING_COOLDOWN
                    && AmbushSchedule.mayAddGuardian(idleGuardians(level))) {
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
            BlockPos at = findWater(level, worldPosition, layout().islandRadius() + 1, layout().islandRadius() + 5, 10);
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

    /**
     * Survivors lose their target and get a first nudge toward the water; InferiusEntity's return-to-lake goal
     * keeps herding them back, and their idle rule despawns them.
     */
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
     * A random water block {@code minR..maxR} blocks (horizontally) from {@code center}, inside the lake and
     * outside the island, with water or air above and room for the body. Never loads chunks.
     */
    @Nullable
    private BlockPos findWater(ServerLevel level, BlockPos center, int minR, int maxR, int tries) {
        CaveLayout l = layout();
        for (int t = 0; t < tries; t++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = minR + level.random.nextDouble() * (maxR - minR);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
            if (l.onIsland(new BlockPos(x, worldPosition.getY(), z)) || !level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                continue;
            }
            for (int y = center.getY() + 3; y >= center.getY() - 8; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                boolean water = LocketCaves.isCoveredWater(level, pos);
                if (!l.inLake(pos, water)) {
                    continue;
                }
                if (water) {
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
        if (layout != null) {
            tag.put("Layout", layout.save());
        }
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
        layout = tag.contains("Layout") ? CaveLayout.load(tag.getCompound("Layout"), worldPosition) : null;
        lastWarning = tag.contains("LastWarning") ? tag.getLong("LastWarning") : -100000;
        ambushTrigger = tag.hasUUID("AmbushTrigger") ? tag.getUUID("AmbushTrigger") : null;
        ambushMobs.clear();
        for (Tag t : tag.getList("AmbushMobs", Tag.TAG_INT_ARRAY)) {
            ambushMobs.add(NbtUtils.loadUUID(t));
        }
    }
}
