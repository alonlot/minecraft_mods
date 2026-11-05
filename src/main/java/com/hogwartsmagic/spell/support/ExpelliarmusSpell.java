package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.DisarmableWandUser;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Expelliarmus takes the wand, not just the item: the main-hand item drops, NPC casters go after
 * their wand (RetrieveDroppedWandGoal), and players and NPC casters are silenced ("disarmed"). A
 * player's dropped item can't be picked back up while the silence lasts. At Master the Elder Wand
 * changes allegiance: disarming its holder sends it to you, and disarming Voldemort wins you one
 * (once; he then drops none on death). Common code; callers are server side.
 */
public final class ExpelliarmusSpell {
    public static final String ELDER_WAND_WON_TAG = ExampleMod.MODID + ":elder_wand_won";

    private ExpelliarmusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        apply(target, caster, true);
    }

    /** @param masteryEligible false for a Protego-reflected beam: no Elder Wand allegiance for the reflector */
    public static void apply(LivingEntity target, @Nullable Entity caster, boolean masteryEligible) {
        if (target.level().isClientSide()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.EXPELLIARMUS);
        Entity owner = CombatEffects.responsible(caster);
        int silenceTicks = ExpelliarmusRules.silenceTicks(rank);
        ServerPlayer winner = masteryEligible && owner instanceof ServerPlayer player && player != target
                && ExpelliarmusRules.winsElderWand(rank)
                && mayClaimFrom(player, target) ? player : null;

        ItemStack held = target.getMainHandItem();
        if (!held.isEmpty()) {
            ItemStack taken = held.copy();
            target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (winner != null && taken.is(ExampleMod.ELDER_WAND.get())) {
                claimElderWand(winner, taken, target);
                if (target instanceof VoldemortEntity) {
                    // He just surrendered his one Elder Wand; he owes no second.
                    target.getPersistentData().putBoolean(ELDER_WAND_WON_TAG, true);
                }
            } else {
                ItemEntity dropped = target.spawnAtLocation(taken);
                if (dropped != null && target instanceof Player) {
                    dropped.setPickUpDelay(silenceTicks);
                }
                if (dropped != null) {
                    // Flung out of the hand, away from the caster, high enough to be seen go.
                    Vec3 away = owner == null ? target.getLookAngle().scale(-1.0)
                            : target.position().subtract(owner.position());
                    away = new Vec3(away.x, 0.0, away.z);
                    away = away.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
                    dropped.setPos(target.getX(), target.getEyeY() - 0.3, target.getZ());
                    // Mostly up: a wand thrown far could land in lava or over an edge.
                    dropped.setDeltaMovement(away.x * 0.1, 0.42, away.z * 0.1);
                    dropped.hasImpulse = true; // send the launch now, not at the next 20-tick sync
                    com.hogwartsmagic.spell.SpellBeamFx.react(dropped, SpellType.EXPELLIARMUS, away,
                            com.hogwartsmagic.spell.SpellBeamFx.REACT_FLIGHT);
                }
            }
        }
        if (winner != null && target instanceof VoldemortEntity
                && !target.getPersistentData().getBoolean(ELDER_WAND_WON_TAG)) {
            target.getPersistentData().putBoolean(ELDER_WAND_WON_TAG, true);
            claimElderWand(winner, new ItemStack(ExampleMod.ELDER_WAND.get()), target);
        }

        // NPC wand-wielders enter a SEEKING_WAND state: their top-priority AI becomes
        // running to and picking up the dropped wand. See RetrieveDroppedWandGoal.
        if (target instanceof DisarmableWandUser disarmable) {
            disarmable.setDisarmed(true);
        }
        // A disarm during a formal duel decides it, win or lose.
        com.hogwartsmagic.duel.DuelManager.onDisarmed(target);
        if (owner != null) {
            target.knockback(ExpelliarmusRules.knockback(rank), owner.getX() - target.getX(), owner.getZ() - target.getZ());
            target.hurtMarked = true; // a player target only sees the knockback once its motion is sent
        }
        if (target instanceof Player || target instanceof DisarmableWandUser || target instanceof VoldemortEntity) {
            CombatEffects.applySilenced(target, silenceTicks, caster);
        }
    }

    /** A player target only yields allegiance where PvP is on and the teams allow the attacker to harm them. */
    private static boolean mayClaimFrom(ServerPlayer winner, LivingEntity target) {
        if (!(target instanceof ServerPlayer targetPlayer)) {
            return true;
        }
        return winner.serverLevel().getServer().isPvpAllowed() && winner.canHarmPlayer(targetPlayer);
    }

    private static void claimElderWand(ServerPlayer winner, ItemStack wand, LivingEntity from) {
        if (!winner.getInventory().add(wand)) {
            winner.drop(wand, false);
        }
        winner.displayClientMessage(Component.translatable("message.hogwartsmagic.expelliarmus.elder_wand",
                from.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        winner.level().playSound(null, winner.getX(), winner.getY(), winner.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }
}
