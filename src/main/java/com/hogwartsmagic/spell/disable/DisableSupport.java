package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Helpers shared by the five disabling spells. Server side only (callers already are); common code
 * with no client imports.
 */
public final class DisableSupport {
    private DisableSupport() {
    }

    /**
     * The caster's mastery rank for this spell. Players use their own rank; boss NPCs (Voldemort and
     * friends) cast at Expert (3) so the rank-scaled spells do not weaken them; other NPCs, projectiles
     * without a player owner and null are rank 1.
     */
    public static int casterRank(@Nullable Entity caster, SpellType spell) {
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player) {
            return player.level().isClientSide() ? 1 : SpellMasteryData.getRank(player, spell);
        }
        if (CombatTags.isBoss(responsible)) {
            return 3;
        }
        return 1;
    }

    public static Holder<MobEffect> holder(RegistryObject<MobEffect> effect) {
        return effect.getHolder().orElseThrow();
    }

    /**
     * Break whatever the target is casting: a player's channel, or Voldemort's queued windup. A stunned
     * Voldemort skips his AI step, so his own lockout check can't cancel the windup until the stun ends.
     */
    public static void interrupt(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
            ModEvents.interruptActiveCasting(player);
        } else if (target instanceof VoldemortEntity voldemort) {
            voldemort.interruptCasting();
        }
    }

    /**
     * Hard crowd control carried by a hidden marker effect (Incarcerous chains, the Levicorpus hang).
     * Delegates to Phase 2's hidden-effect overload CrowdControl.apply(target, effect, baseTicks, amplifier,
     * source, visible=false), which probes immunity, applies diminishing returns, the boss rule and the PvP
     * rule, and records the diminishing-returns step only if the marker actually landed.
     *
     * @return the ticks applied; 0 if nothing landed
     */
    public static int applyHiddenCrowdControl(LivingEntity target, RegistryObject<MobEffect> effect, int baseTicks,
                                              int amplifier, @Nullable Entity source) {
        return CrowdControl.apply(target, holder(effect), baseTicks, Math.max(0, amplifier), source, false);
    }

    /** A hidden marker riding on crowd control that already landed (same ticks); not itself crowd control. */
    public static void addHiddenMarker(LivingEntity target, RegistryObject<MobEffect> effect, int ticks, int amplifier,
                                       @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return;
        }
        target.addEffect(new MobEffectInstance(holder(effect), ticks, Math.max(0, amplifier), false, false, false),
                CombatEffects.responsible(source));
    }

    /**
     * Who a chain (Stupefy) or an area (Incarcerous) may also catch. Never the caster, the main target,
     * allied or owned mobs, team-mates, armour stands or spectators. Two perspectives, chosen by who is
     * responsible for the spell:
     * <ul>
     * <li><b>Player caster (or no caster):</b> players only when the main target is a player (so a PvE
     * cast never stuns a nearby friend). Otherwise hostile mobs (except Students, which are illagers in
     * code but usually friendly) and any mob currently targeting the caster.</li>
     * <li><b>NPC caster (a Mob, e.g. Voldemort):</b> every {@code Enemy} is NOT a foe, because the
     * caster's own Death Eaters are Enemies too. See {@link #npcFoe}: only the caster's own
     * target, mobs fighting the caster, and players (all of them for a hostile caster, otherwise only
     * those it is fighting) qualify.</li>
     * </ul>
     */
    public static boolean isEnemyOf(@Nullable Entity caster, LivingEntity candidate, LivingEntity mainTarget) {
        Entity owner = CombatEffects.responsible(caster);
        if (candidate == owner || candidate == mainTarget || !candidate.isAlive() || candidate.isSpectator()
                || candidate instanceof ArmorStand) {
            return false;
        }
        if (candidate.getTags().contains(CombatTags.ALLIED)) {
            return false;
        }
        if (owner != null) {
            if (candidate.isAlliedTo(owner)) {
                return false;
            }
            if (candidate instanceof OwnableEntity ownable) {
                UUID ownerId = ownable.getOwnerUUID();
                if (ownerId != null && ownerId.equals(owner.getUUID())) {
                    return false;
                }
            }
        }
        if (owner != null && !(owner instanceof Player)) {
            return npcFoe(owner, candidate, mainTarget);
        }
        if (candidate instanceof Player) {
            return mainTarget instanceof Player;
        }
        if (candidate instanceof Mob mob && owner != null && mob.getTarget() == owner) {
            return true;
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }

    /**
     * NPC-perspective foe test, used when the responsible caster is a non-player. The caller has already
     * excluded the caster, dead or spectating candidates, armour stands, the allied tag, team-mates
     * ({@code isAlliedTo}) and the caster's pets. Two perspectives, by what the candidate is:
     * <ul>
     * <li><b>Non-player candidate:</b> a foe only if it is the caster's current target or
     * its own target is the caster. A mob that merely shares the caster's target is an ally (Death Eaters
     * fighting the same player as Voldemort) and never qualifies.</li>
     * <li><b>Player candidate</b> (survival, not allied to the caster): a foe only if the caster is hostile
     * (an {@code Enemy} that is not a Student, see {@link #isHostileCaster}), or the player is the caster's current target, or the player is fighting
     * the caster (it last hurt the caster, or the caster was last hurt by it, as players have no AI
     * target; hitting it counts only for the last 5 seconds). A friendly NPC caster, such as a Student, therefore leaves bystander players alone.</li>
     * </ul>
     * {@code mainTarget} is unused: callers already exclude the main target or pass null. It stays in the
     * signature so call sites are unchanged.
     */
    public static boolean npcFoe(Entity caster, LivingEntity candidate, @Nullable LivingEntity mainTarget) {
        if (candidate == caster || candidate.isAlliedTo(caster)) {
            return false;
        }
        LivingEntity casterTarget = caster instanceof Mob m ? m.getTarget() : null;
        if (candidate instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (isHostileCaster(caster) || player == casterTarget) {
                return true;
            }
            boolean recentlyHitCaster = player.getLastHurtMob() == caster
                    && player.tickCount - player.getLastHurtMobTimestamp() <= 100;
            return recentlyHitCaster
                    || (caster instanceof LivingEntity living && living.getLastHurtByMob() == player);
        }
        if (candidate == casterTarget) {
            return true;
        }
        return candidate instanceof Mob mob && mob.getTarget() == caster;
    }

    /** Students are illagers, so vanilla makes them {@code Enemy}, but they are friendly casters. */
    private static boolean isHostileCaster(Entity caster) {
        return caster instanceof Enemy && !(caster instanceof StudentEntity);
    }

    /** Put a target back on its leash or anchor. Players need a real teleport packet; mobs just move. */
    public static void snapTo(LivingEntity target, double x, double y, double z) {
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        } else {
            target.teleportTo(x, y, z);
        }
    }
}
