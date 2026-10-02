package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import javax.annotation.Nullable;

/**
 * A ranked Protego: the shield a player raises (self-cast or a Protego beam), as opposed to the NPC
 * layer shields and Protego Maxima's bubble, which keep their old rules. The shield faces one way.
 * Blows from the front are cut by the rank's block fraction; a beam from the front inside the parry
 * window is parried (dissolved at Novice, reflected from Adept) and the shield stays; a front beam
 * outside the window breaks the shield. Avada Kedavra, Heal and Protego beams pass through; Crucio
 * and Imperio are never reflected. State lives in the holder's persistent data (server only).
 */
public final class ProtegoSpell {
    public static final String TAG_RAISED_AT = ExampleMod.MODID + ":protego_raised_at";
    public static final String TAG_UNTIL = ExampleMod.MODID + ":protego_until";
    public static final String TAG_RANK = ExampleMod.MODID + ":protego_rank";
    public static final String TAG_CASTER = ExampleMod.MODID + ":protego_caster";
    public static final String TAG_REFUND = ExampleMod.MODID + ":protego_refund";

    public enum BeamOutcome { NOT_SHIELDED, BLOCKED, PARRIED, REFLECTED }

    private ProtegoSpell() {
    }

    private static Holder<MobEffect> shield() {
        return ExampleMod.PROTEGO_EFFECT.getHolder().orElseThrow();
    }

    public static void raise(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.PROTEGO);
        long now = target.level().getGameTime();
        target.addEffect(new MobEffectInstance(shield(), ProtegoRules.SHIELD_TICKS, 0));
        CompoundTag data = target.getPersistentData();
        data.putLong(TAG_RAISED_AT, now);
        data.putLong(TAG_UNTIL, now + ProtegoRules.SHIELD_TICKS);
        data.putInt(TAG_RANK, rank);
        data.remove(TAG_CASTER);
        data.remove(TAG_REFUND);
        if (CombatEffects.responsible(caster) instanceof ServerPlayer player) {
            data.putUUID(TAG_CASTER, player.getUUID());
            float refund = ProtegoRules.refundFraction(rank)
                    * SpellCosts.manaCost(player, player.getMainHandItem(), SpellType.PROTEGO);
            if (refund > 0.0f && !player.isCreative()) {
                data.putFloat(TAG_REFUND, refund);
            }
        }
    }

    /** A ranked shield: raised by {@link #raise}, still inside its 60 ticks, and not Protego Maxima's amplifier-3 bubble. */
    public static boolean isRankedShield(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(shield());
        if (instance == null || instance.getAmplifier() != 0) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        return data.contains(TAG_UNTIL) && entity.level().getGameTime() < data.getLong(TAG_UNTIL);
    }

    public static BeamOutcome onBeamHit(LivingEntity holder, SpellBeamEntity beam) {
        if (!(holder.level() instanceof ServerLevel level) || !isRankedShield(holder)) {
            return BeamOutcome.NOT_SHIELDED;
        }
        SpellType spell = beam.getSpellType();
        if (spell == SpellType.AVADA_KEDAVRA || spell == SpellType.HEAL || spell == SpellType.PROTEGO
                || spell == SpellType.LUMOS) {
            return BeamOutcome.NOT_SHIELDED;
        }
        Vec3 look = holder.getLookAngle();
        Vec3 travel = beam.getDeltaMovement();
        if (!ProtegoRules.isFront(look.x, look.z, -travel.x, -travel.z)) {
            return BeamOutcome.NOT_SHIELDED; // from behind: the shield faces the other way
        }
        CompoundTag data = holder.getPersistentData();
        int rank = data.getInt(TAG_RANK);
        boolean unforgivable = spell == SpellType.CRUCIO || spell == SpellType.IMPERIO;
        if (!unforgivable && ProtegoRules.inParryWindow(data.getLong(TAG_RAISED_AT), level.getGameTime(), rank)) {
            refundOnce(level, data);
            int mode = ProtegoRules.parryMode(rank);
            if (mode != ProtegoRules.PARRY_DISSOLVE && ProtegoRules.canReflect(unforgivable, beam.isReflected())) {
                reflect(level, holder, beam, mode);
                parryFeedback(level, holder, true);
                return BeamOutcome.REFLECTED;
            }
            parryFeedback(level, holder, false);
            return BeamOutcome.PARRIED;
        }
        // Outside the window, or an Unforgivable: the shield takes the beam and breaks.
        holder.removeEffect(shield());
        data.remove(TAG_UNTIL);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT, holder.getX(), holder.getY() + 1.0, holder.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);
        return BeamOutcome.BLOCKED;
    }

    /** Ranked shield damage rule: blows with a position in front lose the block fraction; Master knocks melee back. */
    public static void onShieldedHurt(LivingHurtEvent event) {
        LivingEntity holder = event.getEntity();
        DamageSource source = event.getSource();
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return; // falls, bleeding, starvation: no direction, so a one-sided shield can't catch them
        }
        Vec3 look = holder.getLookAngle();
        if (!ProtegoRules.isFront(look.x, look.z, from.x - holder.getX(), from.z - holder.getZ())) {
            return;
        }
        int rank = holder.getPersistentData().getInt(TAG_RANK);
        event.setAmount(ProtegoRules.reducedDamage(event.getAmount(), rank));
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (ProtegoRules.knocksBackMelee(rank)
                && source.getDirectEntity() instanceof LivingEntity attacker
                && attacker == source.getEntity()
                && attacker != holder) {
            attacker.knockback(ProtegoRules.MASTER_MELEE_KNOCKBACK, holder.getX() - attacker.getX(), holder.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }
    }

    private static void refundOnce(ServerLevel level, CompoundTag data) {
        if (!data.contains(TAG_REFUND) || !data.hasUUID(TAG_CASTER)) {
            return;
        }
        float refund = data.getFloat(TAG_REFUND);
        data.remove(TAG_REFUND);
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(data.getUUID(TAG_CASTER));
        if (caster != null) {
            SupportMana.add(caster, refund);
        }
    }

    private static void reflect(ServerLevel level, LivingEntity holder, SpellBeamEntity beam, int mode) {
        Vec3 direction = null;
        Entity original = beam.getOwner();
        if (mode == ProtegoRules.PARRY_AT_CASTER && original != null && original.isAlive()
                && original.level() == level && original != holder) {
            Vec3 toCaster = original.getEyePosition().subtract(holder.getEyePosition());
            if (toCaster.lengthSqr() > 1.0e-4) {
                direction = toCaster.normalize();
            }
        }
        if (direction == null) {
            float yaw = ProtegoRules.randomReflectYaw(holder.getYRot(), level.random.nextDouble());
            float pitch = (float) ((level.random.nextDouble() * 2.0 - 1.0) * ProtegoRules.RANDOM_REFLECT_PITCH_DEGREES);
            direction = Vec3.directionFromRotation(pitch, yaw);
        }
        // A fresh beam owned by the reflector: it starts at the holder's eyes and can't hit them on the way out.
        SpellBeamEntity copy = new SpellBeamEntity(level, holder, beam.getSpellType());
        copy.setCharge(beam.getCharge());
        copy.setElderWandCast(beam.isElderWandCast());
        copy.markReflected();
        float speed = (float) Math.max(1.0, beam.getDeltaMovement().length());
        copy.shoot(direction.x, direction.y, direction.z, speed, 0.0f);
        level.addFreshEntity(copy);
    }

    private static void parryFeedback(ServerLevel level, LivingEntity holder, boolean reflected) {
        Vec3 front = holder.getEyePosition().add(holder.getLookAngle().scale(0.8));
        level.sendParticles(ParticleTypes.END_ROD, front.x, front.y, front.z, reflected ? 18 : 10, 0.25, 0.25, 0.25, 0.04);
        level.sendParticles(ParticleTypes.FLASH, front.x, front.y, front.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, reflected ? 1.7f : 1.4f);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.6f);
    }
}
