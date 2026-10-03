package com.hogwartsmagic.spell.mind;

import net.minecraft.world.entity.player.Player;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The attack button of a possessed body, through that body's own attack: a player swings their held item
 * (Player.attack: its damage, enchantments and cooldown), a mob bites with doHurtTarget or fires its ranged
 * attack, a creeper lights its fuse, the Horntail (and Ironbelly) breathes fire along the look, the Basilisk
 * spits venom, and wand users (Voldemort, Death Eaters, Students) fire one of their spells. Aim is the
 * body's own look (the caster's virtual look, applied by PossessionManager.drive).
 */
final class PossessedAttacks {
    private static final double PLAYER_REACH = 3.5;
    private static final double RANGED_REACH = 24.0;
    private static final double AREA_PROTECT_RANGE = 22.0;
    private static final float FALLBACK_DAMAGE = 2.0F;
    private static final SpellType[] VOLDEMORT_SPELLS = {
            SpellType.STUPEFY, SpellType.SECTUMSEMPRA, SpellType.BOMBARDA};
    private static final SpellType[] WAND_SPELLS = {SpellType.STUPEFY, SpellType.EXPELLIARMUS};

    private PossessedAttacks() {
    }

    /** @return ticks until the next attack may fire */
    static int attack(LivingEntity puppet, ServerPlayer caster) {
        return attack(puppet, caster, null);
    }

    /** @param session when given, the protected-player refusal line is sent once per attack press */
    static int attack(LivingEntity puppet, ServerPlayer caster, @Nullable PossessionSession session) {
        if (puppet instanceof ServerPlayer victim) {
            LivingEntity target = aimed(puppet, PLAYER_REACH, caster);
            if (target != null) {
                MindControl.forcedAttack(victim, target);
            } else {
                victim.swing(InteractionHand.MAIN_HAND, true);
            }
            return PossessionRules.ATTACK_COOLDOWN;
        }
        if (!(puppet instanceof Mob mob)) {
            return PossessionRules.ATTACK_COOLDOWN;
        }
        if (isAreaSpecial(mob) && protectedPlayerNearby(mob, caster)) {
            if (session == null || !session.refusalShown) {
                MindFx.tell(caster, "possession.protected_nearby");
            }
            if (session != null) {
                session.refusalShown = true;
            }
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof HungarianHorntailEntity horntail) {
            horntail.possessedAttack();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof BasiliskEntity basilisk) {
            basilisk.possessedAttack();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof VoldemortEntity) {
            fireBeam(mob, VOLDEMORT_SPELLS[mob.getRandom().nextInt(VOLDEMORT_SPELLS.length)], caster);
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof WizardEntity || mob instanceof StudentEntity) {
            fireBeam(mob, WAND_SPELLS[mob.getRandom().nextInt(WAND_SPELLS.length)], caster);
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof Creeper creeper) {
            creeper.ignite();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof RangedAttackMob ranged) {
            LivingEntity target = aimed(mob, RANGED_REACH, caster);
            mob.swing(InteractionHand.MAIN_HAND);
            if (target != null) {
                ranged.performRangedAttack(target, 1.0F);
            }
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        LivingEntity target = aimed(mob, mob.getBbWidth() * 2.0 + 1.5, caster);
        mob.swing(InteractionHand.MAIN_HAND);
        if (target != null) {
            melee(mob, target);
        }
        return PossessionRules.ATTACK_COOLDOWN;
    }

    private static boolean isAreaSpecial(Mob mob) {
        return mob instanceof HungarianHorntailEntity || mob instanceof BasiliskEntity || mob instanceof Creeper; // beams check their own hits
    }

    /** True when a player the caster may not harm (spared, allied, PvP off) stands within reach of an area attack. */
    private static boolean protectedPlayerNearby(Mob puppet, ServerPlayer caster) {
        boolean pvp = caster.server.isPvpAllowed();
        double rangeSqr = AREA_PROTECT_RANGE * AREA_PROTECT_RANGE;
        for (Player other : puppet.level().players()) {
            if (other == caster || other.isCreative() || other.isSpectator()
                    || other.distanceToSqr(puppet) > rangeSqr) {
                continue;
            }
            if (!pvp || !caster.canHarmPlayer(other) || other.isAlliedTo(caster)
                    || other.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED)) {
                return true;
            }
        }
        return false;
    }

    /** The first living thing along the body's look within {@code reach}, stopping at walls; never the caster's body. */
    @Nullable
    private static LivingEntity aimed(LivingEntity puppet, double reach, ServerPlayer caster) {
        Vec3 start = puppet.getEyePosition();
        Vec3 end = start.add(puppet.getLookAngle().scale(reach));
        HitResult wall = puppet.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, puppet));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB box = puppet.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(puppet.level(), puppet, start, end, box,
                entity -> entity instanceof LivingEntity living && entity.isPickable() && !entity.isSpectator()
                        && entity != caster && entity != puppet && ImperioThralls.isFairTarget(caster, puppet, living));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static void melee(Mob mob, LivingEntity target) {
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            target.hurt(mob.damageSources().mobAttack(mob), FALLBACK_DAMAGE); // passive mobs have no attack of their own
            return;
        }
        try {
            mob.doHurtTarget(target);
        } catch (IllegalArgumentException ex) {
            // Some attack paths still expect the legacy attack-damage id in 1.21 (seen with the old Imperio channel).
            target.hurt(mob.damageSources().mobAttack(mob), FALLBACK_DAMAGE);
        }
    }

    private static void fireBeam(Mob caster, SpellType spell, ServerPlayer puppeteer) {
        SpellBeamEntity beam = new SpellBeamEntity(caster.level(), caster, spell);
        beam.setPuppeteer(puppeteer.getUUID());
        beam.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0F, 1.75F, 0.5F);
        caster.level().addFreshEntity(beam);
        caster.swing(InteractionHand.MAIN_HAND);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), spell.getSoundEvent(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
