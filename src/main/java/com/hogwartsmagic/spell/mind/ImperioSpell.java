package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.entity.HouseElfEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * An Imperio beam landing. Ranks I-III (and every NPC caster): the target becomes the caster's thrall.
 * Master: the hit is remembered for 3 seconds so the held channel (Phase 6b: possession) can take it.
 */
public final class ImperioSpell {
    /** The existing keys the Master hold reads through ModEvents.consumeRecentImperioHitTarget. */
    public static final String TAG_RECENT_HIT_TARGET = "hogwartsmagic:imperio_recent_hit_target";
    public static final String TAG_RECENT_HIT_EXPIRY = "hogwartsmagic:imperio_recent_hit_expiry";

    private ImperioSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        if (MindControl.pvpBlocked(target, caster)) {
            MindFx.tell(caster, "mind.pvp_off");
            return;
        }
        if (target instanceof net.minecraft.world.entity.player.Player && !com.hogwartsmagic.Config.isImperioAffectsPlayers()) {
            MindFx.tell(caster, "imperio.players_off");
            return;
        }
        if (PossessionManager.isImmune(target) || PossessionManager.isPuppet(target)) {
            MindFx.resisted(target, caster);
            return;
        }
        DisableSupport.interrupt(target);
        if (target instanceof HouseElfEntity elf) {
            // An elf under the Imperius Curse cannot will itself away: the dark road to binding one.
            elf.markSubdued(200);
        }
        Entity owner = CombatEffects.responsible(caster);
        int rank = DisableSupport.casterRank(caster, SpellType.IMPERIO);
        if (owner instanceof ServerPlayer player && ImperioRules.isPossessionRank(rank)) {
            CompoundTag data = player.getPersistentData();
            data.putUUID(TAG_RECENT_HIT_TARGET, target.getUUID());
            data.putLong(TAG_RECENT_HIT_EXPIRY, player.level().getGameTime() + 60L);
            return;
        }
        if (owner instanceof LivingEntity livingOwner) {
            ImperioThralls.enthrall(livingOwner, target, rank);
        }
    }
}
