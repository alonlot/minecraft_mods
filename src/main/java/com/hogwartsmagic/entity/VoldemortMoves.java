package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Voldemort's set pieces, run on the server one at a time: the entrance, the attacks that have their own wind-up
 * and timing (Crucio, the triple Sectumsempra, Fiendfyre, the dark shockwave, the lightning call, the smoke dash,
 * a plain cast) and the turn at half health. Each move is a timeline in ticks; the entity syncs which one is
 * running ({@link VoldemortEntity#setAction}) and the clients play its clip ({@code VoldemortAnimations}) and draw
 * its effects ({@link VoldemortFx}) from the same clock, so the beam leaves the wand on the frame the arm lunges.
 *
 * <p>Action ids are plain ints (shared by the model, the effects and the clips), never an enum switched on in
 * render code. The Killing Curse keeps its own 60-tick wind-up in the entity (AvadaChargeFx).
 */
public final class VoldemortMoves {
    public static final int NONE = 0;
    public static final int ENTRANCE = 1;
    public static final int CAST = 2;
    public static final int CRUCIO = 3;
    public static final int SLASH = 4;
    public static final int FIENDFYRE = 5;
    public static final int SHOCKWAVE = 6;
    public static final int PHASE = 7;
    public static final int STRIKE = 8;
    public static final int DASH = 9;

    /** Ticks into SHOCKWAVE when the ground slams; into FIENDFYRE when the fire leaves; into STRIKE when it lands. */
    public static final int SLAM_TICK = 24;
    public static final int FIRE_TICK = 24;
    public static final int STRIKE_TICK = 26;
    public static final int PHASE_BLAST_TICK = 50;
    /** The three Sectumsempra slashes. */
    public static final int[] SLASH_TICKS = {5, 11, 17};
    /** The summoning's Tenebris Flight runs this long before he re-forms. */
    static final int ENTRANCE_SMOKE_TICKS = 24;

    /** How long each action's clip plays on the clients (they stop it here). */
    public static int length(int action) {
        switch (action) {
            case ENTRANCE: return 60;
            case CAST: return 18;
            case CRUCIO: return 28;
            case SLASH: return 24;
            case FIENDFYRE: return 32;
            case SHOCKWAVE: return 36;
            case PHASE: return 70;
            case STRIKE: return 30;
            default: return 0;
        }
    }

    private final VoldemortEntity v;
    int move = NONE;
    int tick;
    @Nullable
    private LivingEntity target;
    @Nullable
    private SpellType spell;
    private final List<Vec3> strikeSpots = new ArrayList<>();
    private double hoverBase;

    VoldemortMoves(VoldemortEntity v) {
        this.v = v;
    }

    boolean busy() {
        return move != NONE;
    }

    /** Nothing hurts him while he makes his entrance or turns at half health. */
    boolean untouchable() {
        return move == ENTRANCE || move == PHASE;
    }

    /** Stuns and silences break an attack, never the entrance or the turn. */
    boolean interruptible() {
        return move != NONE && move != ENTRANCE && move != PHASE;
    }

    void start(int m, @Nullable LivingEntity target, @Nullable SpellType spell) {
        this.move = m;
        this.tick = m == ENTRANCE ? -ENTRANCE_SMOKE_TICKS : 0;
        this.target = target;
        this.spell = spell;
        this.strikeSpots.clear();
        if (m != ENTRANCE && m != DASH) {
            v.setAction(m);
        }
        if (m != ENTRANCE && m != PHASE) {
            v.setCasting(true);
        }
        v.getNavigation().stop();
    }

    void cancel() {
        if (move == NONE) return;
        move = NONE;
        v.setCasting(false);
        v.setAction(NONE);
    }

    /**
     * A move ran its course. The action goes back to none so a player who starts tracking him later does not
     * replay the last clip; every move runs exactly its clip's length, so this never cuts one short.
     */
    private void end(int cooldown) {
        move = NONE;
        v.setCasting(false);
        v.setAction(NONE);
        v.attackCooldownTicks(cooldown);
    }

    /**
     * Wizards in Tenebris Flight smoke-ram their target (ModEvents.handleTenebrisFlight) unless this flag says
     * they already struck: set it for the flights that are part of a move, so he goes where the move sends him.
     */
    static void noRam(LivingEntity e) {
        e.getPersistentData().putBoolean("hogwartsmagic:tenebris_attacked", true);
        e.getPersistentData().putInt("hogwartsmagic:tenebris_hit_and_run_ticks", 0);
    }

    /** One server tick of the running move. */
    void tick(ServerLevel level) {
        if (move == NONE) return;
        tick++;
        LivingEntity t = target != null && target.isAlive() ? target : v.getTarget();
        if (t != null && move != ENTRANCE) {
            v.getLookControl().setLookAt(t, 40.0F, 40.0F);
        }
        if (move != DASH) v.getNavigation().stop();
        boolean phase2 = v.isEnraged();
        if (move == ENTRANCE && tick < 0) noRam(v);
        switch (move) {
            case ENTRANCE -> {
                if (tick == 0) {
                    v.setAction(ENTRANCE);
                    sound(level, ModSounds.VOLDEMORT_ENTRANCE.get(), 1.6F, 1.0F);
                    sound(level, ModSounds.VOLDEMORT_VOICE_ENTRANCE.get(), 2.0F, 1.0F);
                }
                if (tick == 4) blast(level, 6.0D, 0.0F, 0.9D);          // the smoke he condenses from shoves them back
                if (tick == 44) sound(level, ModSounds.VOLDEMORT_VOICE_LAUGH.get(), 2.0F, 1.0F);
                if (tick >= length(ENTRANCE)) end(20);
            }
            case CAST -> {
                if (tick == 4 && t != null && spell != null) fire(spell, t, 0.0F, true);
                if (tick >= length(CAST)) end(22 + v.getRandom().nextInt(16));
            }
            case CRUCIO -> {
                if (tick == 1) sound(level, ModSounds.VOLDEMORT_VOICE_CRUCIO.get(), 1.8F, 1.0F);
                if (tick == 7 && t != null) fire(SpellType.CRUCIO, t, 0.0F, true);
                if (tick >= length(CRUCIO)) end(24);
            }
            case SLASH -> {
                for (int i = 0; i < SLASH_TICKS.length; i++) {
                    if (tick == SLASH_TICKS[i] && t != null) {
                        sound(level, ModSounds.VOLDEMORT_SLASH.get(), 1.2F, 0.9F + 0.1F * i);
                        fire(SpellType.SECTUMSEMPRA, t, i == 0 ? -9.0F : i == 1 ? 9.0F : 0.0F, i == 0);
                    }
                }
                if (tick >= length(SLASH)) end(28);
            }
            case FIENDFYRE -> {
                if (tick == 1) sound(level, ModSounds.VOLDEMORT_CURSE_GATHER.get(), 1.4F, 0.7F);
                if (tick == 12) sound(level, ModSounds.VOLDEMORT_CURSE_GATHER.get(), 1.4F, 0.6F);
                if (tick == FIRE_TICK && t != null) fire(SpellType.FIENDFYRE, t, 0.0F, true);
                if (tick >= 32) end(36);
            }
            case SHOCKWAVE -> {
                if (tick == 1 || tick == 12) sound(level, ModSounds.VOLDEMORT_SHOCKWAVE_CHARGE.get(), 1.5F, tick == 1 ? 0.8F : 1.0F);
                if (tick == SLAM_TICK) {
                    sound(level, ModSounds.VOLDEMORT_SHOCKWAVE.get(), 2.2F, 1.0F);
                    blast(level, phase2 ? 9.0D : 7.5D, phase2 ? 13.0F : 10.0F, 1.7D);
                }
                if (tick >= 36) end(24);
            }
            case STRIKE -> {
                if (tick == 1) markStrikes(level, t, phase2);
                if (tick % 4 == 1 && tick < STRIKE_TICK) {
                    for (Vec3 s : strikeSpots) telegraph(level, s, tick / (float) STRIKE_TICK);
                }
                if (tick == STRIKE_TICK) {
                    for (Vec3 s : strikeSpots) strike(level, s, phase2 ? 12.0F : 9.0F);
                }
                if (tick >= 30) end(24);
            }
            case DASH -> {
                if (tick == 1) {
                    sound(level, ModSounds.VOLDEMORT_SMOKE_WHOOSH.get(), 1.5F, 1.0F);
                    v.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 12, 0));
                }
                noRam(v);
                if (tick < 10 && t != null) {
                    // A streak of smoke toward them, low over the ground, without touching (no ram).
                    Vec3 to = t.position().subtract(v.position());
                    Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                    double d = flat.length();
                    if (d > 3.5D) {
                        Vec3 step = flat.scale(Math.min(1.1D, d - 3.5D) / d);
                        v.setDeltaMovement(step.x, v.getDeltaMovement().y, step.z);
                    } else {
                        v.setDeltaMovement(0.0D, v.getDeltaMovement().y, 0.0D);
                    }
                }
                if (tick == 10 && t != null) behind(level, t);
                if (tick >= 13) {
                    end(0);
                    SpellType follow = v.getRandom().nextBoolean() ? SpellType.STUPEFY : SpellType.BOMBARDA;
                    if (t != null) start(CAST, t, follow);
                }
            }
            case PHASE -> {
                if (tick == 1) {
                    v.setAction(PHASE);
                    hoverBase = v.getY();
                    sound(level, ModSounds.VOLDEMORT_PHASE_ROAR.get(), 2.4F, 1.0F);
                    sound(level, ModSounds.VOLDEMORT_VOICE_PHASE.get(), 2.4F, 1.0F);
                }
                // Held by position each tick, after his own movement (he is ticked from VoldemortEntity.tick, so a
                // stun cannot freeze the turn, and ModEvents resetting gravity cannot drop him).
                double rise = Math.min(tick, 30) * 0.055D;
                if (tick > 60) rise *= (length(PHASE) - tick) / 10.0D;   // settle back down over the last half second
                v.setPos(v.getX(), hoverBase + rise, v.getZ());
                v.setDeltaMovement(0.0D, 0.0D, 0.0D);
                v.fallDistance = 0.0F;
                if (tick % 7 == 3 && tick < PHASE_BLAST_TICK) {
                    double a = tick * 0.9D;
                    visualBolt(level, v.position().add(Math.cos(a) * 7.0D, 0.0D, Math.sin(a) * 7.0D));
                }
                if (tick == PHASE_BLAST_TICK) {
                    sound(level, ModSounds.VOLDEMORT_SHOCKWAVE.get(), 2.6F, 0.8F);
                    blast(level, 10.0D, 12.0F, 2.0D);
                    summonGuards(level, 3);
                }
                if (tick >= length(PHASE)) {
                    v.becomeEnraged();
                    end(20);
                }
            }
            default -> end(20);
        }
    }

    private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, v.getX(), v.getY() + 1.6D, v.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** A beam from his wand at {@code t}, swung {@code yawOffset} degrees off the line; plays the spell's sound once. */
    private void fire(SpellType s, LivingEntity t, float yawOffset, boolean withSound) {
        long now = v.level().getGameTime();
        v.setSpellCooldown(s, now + v.bossCooldown(s));
        if (withSound) {
            v.level().playSound(null, v.getX(), v.getY(), v.getZ(), s.getSoundEvent(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }
        v.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        SpellBeamEntity beam = new SpellBeamEntity(v.level(), v, s);
        double dx = t.getX() - v.getX();
        double dy = t.getY(0.5D) - beam.getY();
        double dz = t.getZ() - v.getZ();
        if (yawOffset != 0.0F) {
            double r = Math.toRadians(yawOffset);
            double c = Math.cos(r), sn = Math.sin(r);
            double nx = dx * c - dz * sn;
            dz = dx * sn + dz * c;
            dx = nx;
        }
        beam.shoot(dx, dy, dz, 1.8F, 0.1F);
        v.level().addFreshEntity(beam);
    }

    /** A ring of force from his feet: damage falling off with distance, a shove out and up. */
    private void blast(ServerLevel level, double radius, float damage, double push) {
        AABB box = v.getBoundingBox().inflate(radius, 3.0D, radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != v && e.isAlive() && !isHisOwn(e) && !exempt(e))) {
            double d = e.distanceTo(v);
            if (d > radius) continue;
            double f = 1.0D - 0.6D * d / radius;
            if (damage > 0.0F) {
                e.hurt(v.damageSources().indirectMagic(v, v), (float) (damage * f));
            }
            Vec3 away = e.position().subtract(v.position());
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
            e.push(flat.x * push * f, 0.45D * f + 0.1D, flat.z * push * f);
            e.hurtMarked = true;
        }
    }

    private static boolean exempt(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator());
    }

    private static boolean isHisOwn(LivingEntity e) {
        return e instanceof VoldemortEntity || (e instanceof WizardEntity && e.getTags().contains(WizardEntity.VOLDEMORT_GUARD_TAG));
    }

    /** Where the storm will land: under his target, under up to two more players near him, and (enraged) two near misses. */
    private void markStrikes(ServerLevel level, @Nullable LivingEntity t, boolean phase2) {
        strikeSpots.clear();
        if (t != null) strikeSpots.add(ground(level, t.position()));
        for (ServerPlayer p : level.players()) {
            if (strikeSpots.size() >= 3) break;
            if (p != t && p.isAlive() && !p.isSpectator() && p.distanceToSqr(v) < 24.0D * 24.0D) {
                strikeSpots.add(ground(level, p.position()));
            }
        }
        if (phase2 && t != null) {
            for (int i = 0; i < 2; i++) {
                double a = v.getRandom().nextDouble() * Math.PI * 2.0D;
                strikeSpots.add(ground(level, t.position().add(Math.cos(a) * 3.5D, 0.0D, Math.sin(a) * 3.5D)));
            }
        }
        for (Vec3 s : strikeSpots) {
            level.playSound(null, s.x, s.y, s.z, ModSounds.VOLDEMORT_STRIKE_WARNING.get(), SoundSource.HOSTILE, 1.4F, 0.8F);
        }
    }

    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
        return new Vec3(at.x, Math.min(at.y, top.getY()) + 0.05D, at.z);
    }

    /** The warning on the ground: a closing ring of soul fire and a column of dark sparks rising out of it. */
    private static void telegraph(ServerLevel level, Vec3 s, float progress) {
        double r = 1.9D - progress * 0.6D;
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2.0D / 10.0D + progress * 3.0D;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.x + Math.cos(a) * r, s.y + 0.1D, s.z + Math.sin(a) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, s.x, s.y + 0.4D, s.z, 6, 0.25D, 0.4D, 0.25D, 0.02D);
    }

    private void strike(ServerLevel level, Vec3 s, float damage) {
        visualBolt(level, s);
        level.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 0.3D, s.z, 30, 0.6D, 0.2D, 0.6D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, s.x, s.y + 0.3D, s.z, 20, 0.8D, 0.3D, 0.8D, 0.04D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s, s).inflate(1.8D, 2.5D, 1.8D),
                e -> e != v && e.isAlive() && !isHisOwn(e) && !exempt(e))) {
            e.hurt(v.damageSources().indirectMagic(v, v), damage);
            e.setRemainingFireTicks(60);
        }
    }

    private static void visualBolt(ServerLevel level, Vec3 at) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** Re-form behind the target: three blocks back along the way it faces, if he fits there; else beside it. */
    private void behind(ServerLevel level, LivingEntity t) {
        Vec3 look = Vec3.directionFromRotation(0.0F, t.getYRot());
        Vec3[] tries = {t.position().subtract(look.scale(3.0D)), t.position().add(look.yRot(Mth.HALF_PI).scale(3.0D)),
                t.position().add(look.yRot(-Mth.HALF_PI).scale(3.0D))};
        for (Vec3 p : tries) {
            Vec3 g = ground(level, p.add(0.0D, 1.5D, 0.0D));
            AABB box = v.getDimensions(v.getPose()).makeBoundingBox(g);
            if (level.noCollision(v, box) && Math.abs(g.y - t.getY()) < 3.0D) {
                v.teleportTo(g.x, g.y, g.z);
                level.playSound(null, g.x, g.y, g.z, ModSounds.VOLDEMORT_SMOKE_WHOOSH.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
                return;
            }
        }
    }

    /** Death Eaters answering him, each out of a lightning strike, as the ritual calls them. */
    private void summonGuards(ServerLevel level, int count) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0D / count + v.getRandom().nextDouble();
            BlockPos at = floorNear(level, BlockPos.containing(v.getX() + Math.cos(a) * 5.0D, v.getY(), v.getZ() + Math.sin(a) * 5.0D));
            if (at == null) continue;
            WizardEntity w = ModEntities.WIZARD.get().create(level);
            if (w == null) continue;
            w.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
            w.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            w.setTarget(v.getTarget());
            w.addTag(WizardEntity.VOLDEMORT_GUARD_TAG);
            w.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 30, 0));
            level.addFreshEntity(w);
            visualBolt(level, Vec3.atBottomCenterOf(at));
        }
    }

    /** A floor within four blocks up or down of {@code near} with two blocks of air on it, or null. */
    @Nullable
    private static BlockPos floorNear(ServerLevel level, BlockPos near) {
        for (int dy = 0; dy <= 8; dy++) {
            int off = (dy & 1) == 0 ? dy / 2 : -(dy + 1) / 2;
            BlockPos p = near.above(off);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                return p;
            }
        }
        return null;
    }

    /** The boss bar turns purple once he is enraged. */
    static void enragedBar(net.minecraft.server.level.ServerBossEvent bar) {
        bar.setColor(BossEvent.BossBarColor.PURPLE);
    }
}
