package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.HorcruxBlock;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.util.MagicParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Horcrux vessel. Dormant, it is a carved pedestal holding a black stone heart. A Killing Curse kill over it
 * primes it, and the caster must then stand by it through {@link #RITUAL_PULSES} pulses while it tears out a piece
 * of their soul (driven from the owner's tick, {@code ModEvents.tickHorcruxRitual}); then it is bound (active).
 *
 * <p>The beating heart, its light and the ambient smoke and whispers are client-side only: the server syncs
 * active / primed / pulses / flare and both sides derive the heartbeat from the game time ({@link #beatPeriod},
 * {@link #beat}), so the renderer, the sound and the light all beat together without a packet per beat. The big
 * moments go out as {@link Fx} kinds ({@code client.fx.HorcruxFx} draws them).
 */
public class HorcruxBlockEntity extends BlockEntity {
    /** Pulses of the rite, one a second: the caster must stay this long. */
    public static final int RITUAL_PULSES = 6;
    /** How far from the vessel the caster may stand during the rite. */
    public static final double RITUAL_RANGE = 8.0;
    /** Pulses the owner may spend out of reach before the rite breaks. */
    public static final int STRAY_PULSES = 4;
    /** Ticks a primed vessel waits for an owner who has left the game before it gives up the rite. */
    private static final int OWNERLESS_GRACE = 200;
    /** A bound vessel beats once every this many ticks. */
    public static final int BOUND_BEAT = 40;
    /** Soul green and rite crimson, shared by the renderer, the particles and the effects. */
    public static final int SOUL_GREEN = 0x4DFF7A;
    public static final int RITE_CRIMSON = 0xFF2238;

    private UUID ownerUuid;
    private boolean active;
    private boolean primed;
    private int ritualPulses;
    private int flareTicks;
    private boolean suppressForcedBreakEffects;
    /** Rite pulses in a row the owner has been out of reach (server only, not saved: a reload forgives). */
    private int strayPulses;
    /** Ticks the primed vessel's owner has been out of the game (server only). */
    private int ownerlessTicks;

    public HorcruxBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.HORCRUX_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------------------------------------ the beat

    /** Ticks between heartbeats: slow and heavy once bound, quickening through the rite as it feeds. */
    public int beatPeriod() {
        return primed ? Math.max(9, 20 - ritualPulses * 2) : BOUND_BEAT;
    }

    /**
     * The heartbeat envelope at {@code t} ticks after a beat began: a strong "lub" and a softer "dub" a few ticks
     * later, 0 between beats. Pure maths, safe on either side.
     */
    public static float beat(float t, int period) {
        float dub = Math.min(6.0f, period * 0.3f);
        float a = (float) Math.exp(-(t * t) / 4.0f);
        float b = 0.6f * (float) Math.exp(-((t - dub) * (t - dub)) / 4.0f);
        float c = (float) Math.exp(-((t - period) * (t - period)) / 4.0f); // the next lub, rising early
        return Math.min(1.0f, a + b + c);
    }

    /** The beat right now (0..1) for a vessel that is awake, 0 for a dormant one. */
    public float beatNow(float partialTick) {
        if (level == null || !(active || primed)) {
            return 0.0f;
        }
        int period = beatPeriod();
        float t = (float) Math.floorMod(level.getGameTime(), (long) period) + partialTick;
        return beat(t, period);
    }

    /** Centre of the heart as the renderer draws it floating over the cradle. */
    public Vec3 heartPos() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0, 1.05, 0.0);
    }

    // ------------------------------------------------------------------------------------------------ ticking

    public static void tick(Level level, BlockPos pos, BlockState state, HorcruxBlockEntity horcrux) {
        if (horcrux.flareTicks > 0) {
            horcrux.flareTicks--;
        }
        if (level.isClientSide) {
            horcrux.clientTick(level);
        } else if (horcrux.primed && level instanceof ServerLevel server) {
            // The rite is driven from the owner's tick; one who has left the game cannot finish or break it.
            boolean online = horcrux.ownerUuid != null && server.getServer().getPlayerList().getPlayer(horcrux.ownerUuid) != null;
            horcrux.ownerlessTicks = online ? 0 : horcrux.ownerlessTicks + 1;
            if (horcrux.ownerlessTicks > OWNERLESS_GRACE) {
                horcrux.abortRitual(server, null);
            }
        }
    }

    /** Counts one more rite pulse with the owner out of reach; returns how many in a row before this one. */
    public int stray() {
        return strayPulses++;
    }

    /** Cosmetic, on each client: the beat's sound, soul motes and smoke, flares, and now and then a whisper. */
    private void clientTick(Level level) {
        if (!(active || primed)) {
            return;
        }
        RandomSource random = level.random;
        Vec3 heart = heartPos();
        int rgb = primed ? RITE_CRIMSON : SOUL_GREEN;
        Player near = level.getNearestPlayer(heart.x, heart.y, heart.z, 24.0, false);
        if (near == null) {
            return;
        }
        double distSq = near.distanceToSqr(heart);

        int period = beatPeriod();
        if (Math.floorMod(level.getGameTime(), (long) period) == 0L) {
            float volume = primed ? 0.9f : 0.45f;
            float pitch = primed ? 0.85f + ritualPulses * 0.05f : 0.8f + random.nextFloat() * 0.06f;
            level.playLocalSound(heart.x, heart.y, heart.z, ModSounds.HORCRUX_HEARTBEAT.get(), SoundSource.BLOCKS,
                    volume, pitch, false);
            // The beat pushes a breath of light out of the heart.
            for (int i = 0; i < (primed ? 6 : 3); i++) {
                double yaw = random.nextDouble() * Math.PI * 2.0;
                double s = 0.03 + random.nextDouble() * 0.03;
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, rgb, 0.12f, 12 + random.nextInt(6)),
                        heart.x, heart.y, heart.z, Math.cos(yaw) * s, 0.01, Math.sin(yaw) * s);
            }
        }

        if (distSq < 32.0 * 32.0 && level.getGameTime() % 3L == 0L) {
            // Soul motes rising off the heart, and dark smoke pooling round the foot of the pedestal.
            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, rgb, 0.05f, 30 + random.nextInt(20)),
                    heart.x + (random.nextDouble() - 0.5) * 0.4, heart.y + (random.nextDouble() - 0.3) * 0.3,
                    heart.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.012, 0.0);
            if (random.nextInt(2) == 0) {
                double ang = random.nextDouble() * Math.PI * 2.0;
                level.addParticle(MagicParticles.SMOKE_DARK,
                        worldPosition.getX() + 0.5 + Math.cos(ang) * 0.6, worldPosition.getY() + 0.12,
                        worldPosition.getZ() + 0.5 + Math.sin(ang) * 0.6, Math.cos(ang) * 0.01, 0.004, Math.sin(ang) * 0.01);
            }
        }
        if (flareTicks > 0 && level.getGameTime() % 2L == 0L) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.FLAME, rgb, 0.09f, 16),
                        heart.x + (random.nextDouble() - 0.5) * 0.5, heart.y - 0.2 + random.nextDouble() * 0.3,
                        heart.z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.03, 0.0);
            }
        }
        if (active && distSq < 7.0 * 7.0 && random.nextInt(700) == 0) {
            level.playLocalSound(heart.x, heart.y, heart.z, ModSounds.HORCRUX_WHISPER.get(), SoundSource.BLOCKS,
                    0.55f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ the rite

    public boolean beginRitual(ServerLevel level, Player player) {
        if ((active || primed) && ownerUuid != null && !ownerUuid.equals(player.getUUID())) {
            return false;
        }
        ownerUuid = player.getUUID();
        primed = true;
        active = false;
        ritualPulses = 0;
        strayPulses = 0;
        ownerlessTicks = 0;
        flareTicks = 40;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(HorcruxBlock.LIT, true), 3);
        syncToClients();
        Vec3 heart = heartPos();
        Fx.send(level, FxKinds.HORCRUX_RITE, heart, 48.0, player.getId());
        Fx.shake(level, heart, 0.5f, 10.0f, 14);
        level.playSound(null, worldPosition, ModSounds.HORCRUX_PRIME.get(), SoundSource.BLOCKS, 1.2f, 1.0f);
        return true;
    }

    /** One pulse of the rite: another piece of the owner's soul goes into the stone. Returns the pulse count. */
    public int feed(ServerLevel level, Player owner) {
        strayPulses = 0;
        ritualPulses = Math.min(RITUAL_PULSES, ritualPulses + 1);
        setChanged();
        syncToClients();
        Vec3 heart = heartPos();
        Fx.send(level, FxKinds.HORCRUX_FEED, heart, 48.0, owner.getId(), ritualPulses, RITUAL_PULSES);
        level.playSound(null, owner.blockPosition(), ModSounds.HORCRUX_FEED.get(), SoundSource.PLAYERS,
                0.9f, 0.85f + ritualPulses * 0.06f);
        return ritualPulses;
    }

    public int getRitualPulses() {
        return ritualPulses;
    }

    public boolean completeBinding(ServerLevel level, Player player) {
        if (!primed || ownerUuid == null || !ownerUuid.equals(player.getUUID())) {
            return false;
        }
        active = true;
        primed = false;
        ritualPulses = 0;
        if (player instanceof net.minecraft.server.level.ServerPlayer binder) {
            com.hogwartsmagic.util.ModAdvancements.award(binder, "horcrux_bound");
        }
        flareTicks = 60;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(HorcruxBlock.LIT, true), 3);
        syncToClients();
        Vec3 heart = heartPos();
        // The shake is the client's: it lands with the sound's slam, ~2 s into its swell (HorcruxFx.BOUND_SLAM).
        Fx.send(level, FxKinds.HORCRUX_BOUND, heart, 64.0, player.getId());
        level.playSound(null, worldPosition, ModSounds.HORCRUX_BIND.get(), SoundSource.BLOCKS, 1.6f, 1.0f);
        return true;
    }

    /** The rite breaks before the soul is sealed: the vessel goes dormant again, ownerless. */
    public void abortRitual(ServerLevel level, @Nullable Player owner) {
        if (!primed) {
            return;
        }
        primed = false;
        ritualPulses = 0;
        ownerUuid = null;
        flareTicks = 0;
        setChanged();
        level.setBlock(worldPosition, getBlockState().setValue(HorcruxBlock.LIT, false), 3);
        syncToClients();
        Fx.send(level, FxKinds.HORCRUX_ABORT, heartPos(), 48.0, owner == null ? -1 : owner.getId());
        level.playSound(null, worldPosition, ModSounds.HORCRUX_ABORT.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------------------------------------ destruction

    public void shatter(ServerLevel level) {
        suppressForcedBreakEffects = true;
        active = false;
        primed = false;
        ownerUuid = null;
        flareTicks = 0;
        setChanged();
        syncToClients();
        Vec3 heart = heartPos();
        Fx.send(level, FxKinds.HORCRUX_SHATTER, heart, 64.0, -1, 0.0f);
        Fx.shake(level, heart, 0.9f, 12.0f, 18);
        level.playSound(null, worldPosition, ModSounds.HORCRUX_SHATTER.get(), SoundSource.BLOCKS, 1.3f, 1.0f);
        level.destroyBlock(worldPosition, false);
    }

    public void handleForcedDestruction(ServerLevel level, @Nullable Player breaker) {
        if (suppressForcedBreakEffects) {
            return;
        }
        suppressForcedBreakEffects = true;
        boolean hostileBreak = ownerUuid != null && breaker != null && !ownerUuid.equals(breaker.getUUID());
        // An owner in the game gets their heart back now, wherever they are (offline ones on their next login).
        net.minecraft.server.level.ServerPlayer owner = ownerUuid == null ? null
                : level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (active && owner != null && worldPosition.equals(com.hogwartsmagic.util.HorcruxData.getBoundPos(owner))
                && level == com.hogwartsmagic.util.HorcruxData.getBoundLevel(owner)
                && !com.hogwartsmagic.util.HorcruxData.isPendingRebirth(owner)) {
            com.hogwartsmagic.util.HorcruxData.releaseForgedHorcrux(owner);
        }
        Vec3 heart = heartPos();
        Fx.send(level, FxKinds.HORCRUX_SHATTER, heart, 64.0, -1, 1.0f);
        Fx.shake(level, heart, 1.1f, 12.0f, 20);
        level.playSound(null, worldPosition, ModSounds.HORCRUX_BREAK.get(), SoundSource.BLOCKS, 1.1f, 1.0f);
        level.playSound(null, worldPosition, ModSounds.HORCRUX_SHATTER.get(), SoundSource.BLOCKS, 1.0f, 0.9f);
        if (hostileBreak) {
            summonDementor(level, breaker);
        }
        active = false;
        primed = false;
        ownerUuid = null;
        flareTicks = 0;
        setChanged();
        syncToClients();
    }

    private void summonDementor(ServerLevel level, Player breaker) {
        DementorEntity dementor = ModEntities.DEMENTOR.get().create(level);
        if (dementor == null) {
            return;
        }
        dementor.moveTo(worldPosition.getX() + 0.5D, worldPosition.getY() + 1.8D, worldPosition.getZ() + 0.5D, breaker.getYRot(), 0.0F);
        dementor.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.EVENT, null);
        dementor.setTarget(breaker);
        level.addFreshEntity(dementor);
        level.sendParticles(MagicParticles.MOTE_GREEN, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                26, 0.35, 0.55, 0.35, 0.02);
        level.playSound(null, worldPosition, ModSounds.HORCRUX_SCREAM.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    // ------------------------------------------------------------------------------------------------ state

    public boolean isActive() {
        return active;
    }

    public boolean isPrimed() {
        return primed;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * True once a bound (primed or active) vessel has been destroyed or shattered. The break handler clears
     * active/primed before vanilla asks for drops, so HorcruxBlock.getDrops reads this instead.
     */
    public boolean wasDestroyedWhileBound() {
        return suppressForcedBreakEffects;
    }

    public Component getStatusMessage() {
        if (active) {
            return Component.translatable("message.hogwartsmagic.horcrux.active");
        }
        if (primed) {
            return Component.translatable("message.hogwartsmagic.horcrux.primed", ritualPulses, RITUAL_PULSES);
        }
        return Component.translatable("message.hogwartsmagic.horcrux.dormant");
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The rite circle on the floor reaches past the block. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.6, 0.0, 2.6).expandTowards(0.0, 1.5, 0.0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putBoolean("Active", active);
        tag.putBoolean("Primed", primed);
        tag.putInt("RitualPulses", ritualPulses);
        tag.putInt("FlareTicks", flareTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        active = tag.getBoolean("Active");
        primed = tag.getBoolean("Primed");
        ritualPulses = tag.getInt("RitualPulses");
        flareTicks = tag.getInt("FlareTicks");
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
