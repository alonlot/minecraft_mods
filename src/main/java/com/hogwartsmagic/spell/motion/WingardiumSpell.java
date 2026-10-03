package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.CarriedBlockEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Wingardium Leviosa from Expert (Spell Mastery Phase 7b): the beam lifts a plain block into a
 * CarriedBlockEntity that the existing channel holds like a mob, and a fresh attack press while holding
 * throws what is held. A thrown mob is tracked for a moment so it hurts what it crashes into. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WingardiumSpell {
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wingardium_immune"));
    /** The channel's held target: the same key ModEvents and SpellPackets use. */
    private static final String TAG_TARGET = "hogwartsmagic:wingardium_target";
    private static final String TAG_ATTACK_DOWN = "hogwartsmagic:wingardium_attack_down";
    private static final String TAG_THROWN = "hogwartsmagic:wingardium_thrown";
    /** Thrown mobs still in flight, per dimension. */
    private static final Map<ResourceKey<Level>, Map<UUID, ThrownMob>> THROWN_MOBS = new HashMap<>();

    private record ThrownMob(@Nullable UUID throwerId, float damage, long until) {
    }

    private WingardiumSpell() {
    }

    /** The beam struck a block: from Expert, lift it and offer it to the channel. */
    public static boolean liftBlock(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        if (isControlledOrPossessing(player)) {
            return false;
        }
        if (!WingardiumRules.liftsBlocks(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA))) {
            return false;
        }
        CarriedBlockEntity carried = liftOne(level, pos, player);
        if (carried == null) {
            return false;
        }
        ModEvents.markRecentWingardiumTarget(player, carried);
        return true;
    }

    @Nullable
    private static CarriedBlockEntity liftOne(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!isLiftable(level, pos, state)) {
            return null;
        }
        // The last check, after every filter: canBreak posts the real BreakEvent (claims, a Horcrux, the
        // Reparo log), so a true result means the block is taken right now.
        if (!TerrainRules.canBreak(level, pos, state, player)) {
            return null;
        }
        level.removeBlock(pos, false);
        CarriedBlockEntity carried = new CarriedBlockEntity(ModEntities.CARRIED_BLOCK.get(), level);
        carried.setCarried(state, player.getUUID());
        carried.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(carried);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                16, 0.4D, 0.4D, 0.4D, 0.05D);
        return carried;
    }

    /** A thrall, a possessed body or a possessing caster neither lifts nor throws. */
    private static boolean isControlledOrPossessing(ServerPlayer player) {
        return com.hogwartsmagic.spell.mind.MindControl.isControlled(player)
                || com.hogwartsmagic.spell.mind.PossessionManager.isPossessing(player);
    }

    /** True when the block drops as its own plain block item (never air, a bucket or another block's item). */
    private static boolean hasOwnPlainItem(BlockState state) {
        net.minecraft.world.item.Item item = state.getBlock().asItem();
        return item instanceof net.minecraft.world.item.BlockItem blockItem
                && !(item instanceof net.minecraft.world.item.SolidBucketItem)
                && blockItem.getBlock() == state.getBlock();
    }

    private static boolean isLiftable(ServerLevel level, BlockPos pos, BlockState state) {
        boolean multipart = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART)
                || (state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED));
        return WingardiumRules.isLiftable(state.getDestroySpeed(level, pos), state.isAir(), state.hasBlockEntity(),
                state.canBeReplaced(), state.getFluidState().isEmpty(), multipart,
                state.is(IMMUNE) || !hasOwnPlainItem(state));
    }

    /** The channel just started holding something: remember whether attack was already down. */
    public static void onChannelStart(ServerPlayer player, boolean attackDown) {
        player.getPersistentData().putBoolean(TAG_ATTACK_DOWN, attackDown);
    }

    /** Every held tick: a fresh attack press throws what is held (Expert and up). */
    public static void onHeartbeat(ServerPlayer player, ItemStack wand, boolean attackDown) {
        CompoundTag data = player.getPersistentData();
        boolean wasDown = data.getBoolean(TAG_ATTACK_DOWN);
        data.putBoolean(TAG_ATTACK_DOWN, attackDown);
        if (WingardiumRules.risingEdge(wasDown, attackDown) && !isControlledOrPossessing(player)
                && WingardiumRules.canThrow(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA))) {
            throwHeld(player, wand);
        }
    }

    /** ModEvents.stopWingardiumChannel: clears this spell's keys; true if the channel ended in a throw. */
    public static boolean onChannelStop(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        boolean thrown = data.getBoolean(TAG_THROWN);
        data.remove(TAG_THROWN);
        data.remove(TAG_ATTACK_DOWN);
        return thrown;
    }

    private static void throwHeld(ServerPlayer player, ItemStack wand) {
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        float damage = WingardiumRules.throwDamage(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA));
        Vec3 velocity = player.getLookAngle().normalize().scale(WingardiumRules.THROW_SPEED)
                .add(0.0D, WingardiumRules.THROW_LIFT, 0.0D);
        boolean threw = false;
        if (data.hasUUID(TAG_TARGET)) {
            Entity held = level.getEntity(data.getUUID(TAG_TARGET));
            // Re-check the hold right before the throw: range and line of sight, like the channel's own tick.
            if (held != null && held.distanceToSqr(player) <= WingardiumRules.HOLD_RANGE * WingardiumRules.HOLD_RANGE
                    && player.hasLineOfSight(held)) {
                threw = launch(level, player, held, velocity, damage);
            }
        }
        if (!threw) {
            return;
        }
        data.putBoolean(TAG_THROWN, true); // stopWingardiumChannel then skips its gentle-release damping
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.8f, 1.1f);
        ModEvents.stopWingardiumChannel(player, wand);
    }

    private static boolean launch(ServerLevel level, ServerPlayer thrower, @Nullable Entity target, Vec3 velocity, float damage) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof CarriedBlockEntity carried) {
            carried.launch(velocity, damage);
            return true;
        }
        if (target instanceof ServerPlayer victim && (!level.getServer().isPvpAllowed()
                || !thrower.canHarmPlayer(victim) || victim.isAlliedTo(thrower))) {
            return false; // PvP off, teams: a player victim is not thrown; the hold simply continues
        }
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.hasImpulse = true;
        if (target instanceof ServerPlayer thrownPlayer) {
            thrownPlayer.connection.send(new ClientboundSetEntityMotionPacket(thrownPlayer)); // once, not per tick
        }
        if (target instanceof LivingEntity && damage > 0.0F) {
            THROWN_MOBS.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(target.getUUID(),
                    new ThrownMob(thrower.getUUID(), damage, level.getGameTime() + WingardiumRules.THROW_TRACK_TICKS));
        }
        return true;
    }

    /** A thrown mob hurts the first creature it crashes into (and itself), or itself on a wall. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, ThrownMob> thrown = THROWN_MOBS.get(level.dimension());
        if (thrown == null) {
            return;
        }
        if (thrown.isEmpty()) {
            THROWN_MOBS.remove(level.dimension());
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, ThrownMob>> it = thrown.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ThrownMob> entry = it.next();
            ThrownMob record = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity mob) || !mob.isAlive() || now > record.until()) {
                it.remove();
                continue;
            }
            Entity thrower = record.throwerId() == null ? null : level.getEntity(record.throwerId());
            LivingEntity struck = null;
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.3D),
                    candidate -> candidate != mob && candidate != thrower && candidate.isAlive() && !candidate.isSpectator()
                            && candidate.getRootVehicle() != mob.getRootVehicle()
                            && !(thrower == null && candidate instanceof net.minecraft.world.entity.player.Player))) {
                struck = other;
                break;
            }
            if (struck != null) {
                struck.hurt(level.damageSources().thrown(mob, thrower), record.damage());
                mob.hurt(level.damageSources().thrown(mob, thrower), record.damage());
                it.remove();
            } else if (mob.horizontalCollision) {
                mob.hurt(level.damageSources().flyIntoWall(), record.damage());
                it.remove();
            } else if (mob.onGround() && now > record.until() - WingardiumRules.THROW_TRACK_TICKS + 3) {
                it.remove(); // landed without hitting anything; ordinary fall damage applies
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        THROWN_MOBS.clear();
    }
}
