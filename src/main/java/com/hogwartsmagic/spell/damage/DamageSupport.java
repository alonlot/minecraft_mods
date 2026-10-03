package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.UUID;

/** Helpers shared by the six damage spells. Common code with no client imports; callers are server side. */
public final class DamageSupport {
    /** Blocks Diffindo cuts free as items (dropped as if sheared). */
    public static final TagKey<Block> DIFFINDO_CUTTABLE = tag("diffindo_cuttable");
    /** Blocks Bombarda never breaks, on top of block entities and unbreakable blocks. */
    public static final TagKey<Block> BOMBARDA_IMMUNE = tag("bombarda_immune");
    /** Obsidian-hard blocks: a 25% chance per block at Expert, certain at Master. */
    public static final TagKey<Block> BOMBARDA_OBSIDIAN_TIER = tag("bombarda_obsidian_tier");

    private DamageSupport() {
    }

    /** Spell damage after the caster's house multiplier (Gryffindor +10%), as the old SpellType code did. */
    public static float scaled(@Nullable Entity caster, float base) {
        return base * HousePerks.getSpellDamageMultiplier(CombatEffects.responsible(caster));
    }

    /**
     * Magic damage that credits the responsible caster, so kills drop player-kill loot and XP. Falls back to
     * plain {@code magic()} (no attacker) when there is no responsible entity. {@code indirect_magic} is in
     * {@code #minecraft:bypasses_armor} just like {@code magic}, so armour maths is unchanged.
     */
    public static DamageSource magicFrom(LivingEntity target, @Nullable Entity caster) {
        Entity attacker = CombatEffects.responsible(caster);
        return attacker == null ? target.damageSources().magic() : target.damageSources().indirectMagic(null, attacker);
    }

    /**
     * Who a splash (Confringo's burning debris, Bombarda's blast) may catch. With a primary target this is
     * exactly {@link DisableSupport#isEnemyOf}. Without one (a blast on a block) it is the same rule minus
     * the player case, from the caster's point of view:
     * <ul>
     * <li><b>Player caster (or none):</b> never players, the caster, allied, owned or team-mate mobs,
     * armour stands; otherwise hostile mobs (not Students) and any mob targeting the caster.</li>
     * <li><b>NPC caster:</b> {@code Enemy} no longer means foe (Voldemort's Death Eaters are Enemies), so
     * {@link DisableSupport#npcFoe} decides: the caster's target, mobs fighting the caster, and survival
     * players (all of them for a hostile caster, only those fighting it for a friendly one).</li>
     * </ul>
     */
    public static boolean isSplashTarget(@Nullable Entity caster, LivingEntity candidate, @Nullable LivingEntity primary) {
        if (com.hogwartsmagic.spell.mind.MindControl.spareInHitContext(candidate)) {
            return false; // a puppet beam never splashes a player its caster may not harm, nor the caster's own body
        }
        if (primary != null) {
            return DisableSupport.isEnemyOf(caster, candidate, primary);
        }
        Entity owner = CombatEffects.responsible(caster);
        boolean npcCaster = owner != null && !(owner instanceof Player);
        if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()
                || candidate instanceof ArmorStand || (candidate instanceof Player && !npcCaster)) {
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
            if (npcCaster) {
                return DisableSupport.npcFoe(owner, candidate, null);
            }
            if (candidate instanceof Mob mob && mob.getTarget() == owner) {
                return true;
            }
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
    }
}
