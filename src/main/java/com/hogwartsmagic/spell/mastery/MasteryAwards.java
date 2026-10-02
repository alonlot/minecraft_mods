package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides when a cast was meaningful enough to earn mastery XP. Three kinds of spell:
 * beams that hit living things (paid per target, with anti-farm), beams whose job is a block
 * (paid when the block effect worked), and self/channel spells (paid when the cast completes and
 * its cooldown starts). Every path is server-only and players-only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MasteryAwards {
    public static final String SPAWNER_TAG = "hogwartsmagic:from_spawner";

    private static final Set<SpellType> SELF_OR_CHANNEL = EnumSet.of(
            SpellType.LUMOS, SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA, SpellType.TENEBRIS_FLIGHT,
            SpellType.EXPECTO_PATRONUM, SpellType.APPARITION, SpellType.FIENDFYRE,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO);

    private static final Set<SpellType> BLOCK_UTILITY = EnumSet.of(
            SpellType.ACCIO, SpellType.ALOHOMORA, SpellType.INCENDIO, SpellType.AQUA_ERUCTO,
            SpellType.MORSMORDRE, SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.REPARO,
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA);

    private static final Map<UUID, HitLedger> LEDGERS = new HashMap<>();

    private MasteryAwards() {
    }

    public static void onLivingHit(ServerPlayer caster, SpellType spell, LivingEntity target) {
        if (!eligible(caster) || target == caster || SELF_OR_CHANNEL.contains(spell)) {
            return;
        }
        if (!(target instanceof net.minecraft.world.entity.Mob) && !(target instanceof net.minecraft.world.entity.player.Player)) {
            return; // armor stands and similar dummies give no XP
        }
        boolean playerTarget = target instanceof Player;
        double multiplier = MasteryRules.targetMultiplier(
                isAllied(caster, target), isPassive(target), target.getTags().contains(SPAWNER_TAG), playerTarget);
        if (multiplier <= 0.0) {
            return;
        }
        HitLedger ledger = ledger(caster);
        long now = caster.level().getGameTime();
        String key = "hit:" + spell.getId() + ":" + target.getUUID();
        // PvP: one player pays a given caster at most once per PLAYER_PAIR_WINDOW_TICKS whatever the
        // spell, so two friends can't farm each other by cycling through their spell wheel.
        String pairKey = playerTarget ? "pvp:" + target.getUUID() : null;
        if (!ledger.isOpen(key, now) || (pairKey != null && !ledger.isOpen(pairKey, now))) {
            return;
        }
        ledger.tryAward(key, now, MasteryRules.SAME_TARGET_WINDOW_TICKS);
        if (pairKey != null) {
            ledger.tryAward(pairKey, now, MasteryRules.PLAYER_PAIR_WINDOW_TICKS);
        }
        grant(caster, spell, multiplier);
    }

    public static void onBlockEffect(ServerPlayer caster, SpellType spell) {
        if (eligible(caster) && BLOCK_UTILITY.contains(spell)) {
            gated(caster, spell, "block:");
        }
    }

    public static void onCastCompleted(ServerPlayer caster, SpellType spell) {
        if (eligible(caster) && SELF_OR_CHANNEL.contains(spell)) {
            gated(caster, spell, "cast:");
        }
    }

    public static String rankKey(int rank) {
        return "gui." + ExampleMod.MODID + ".mastery.rank." + rank;
    }

    private static void gated(ServerPlayer caster, SpellType spell, String prefix) {
        long window = MasteryRules.gateWindowTicks(spell.getCooldownTicks());
        if (ledger(caster).tryAward(prefix + spell.getId(), caster.level().getGameTime(), window)) {
            grant(caster, spell, 1.0);
        }
    }

    private static void grant(ServerPlayer caster, SpellType spell, double multiplier) {
        float xp = MasteryMath.scaledXp(MasteryMath.xpPerCast(spell.getCooldownTicks()),
                multiplier * Config.getMasteryXpMultiplier());
        int newRank = SpellMasteryData.addXp(caster, spell, xp);
        if (newRank > 0) {
            celebrate(caster, spell, newRank);
        }
    }

    /** The rank-up moment: gold chat line, level-up chime, enchant and spell-coloured particles. Also used by Tomes of Mastery. */
    public static void celebrate(ServerPlayer caster, SpellType spell, int newRank) {
        caster.sendSystemMessage(Component.translatable("gui." + ExampleMod.MODID + ".mastery.rank_up",
                        Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId()),
                        MasteryMath.roman(newRank),
                        Component.translatable(rankKey(newRank)))
                .withStyle(ChatFormatting.GOLD));
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
        if (caster.level() instanceof ServerLevel level) {
            double x = caster.getX();
            double y = caster.getY() + 1.0;
            double z = caster.getZ();
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 40, 0.5, 0.8, 0.5, 0.6);
            level.sendParticles(spell.getDustParticle(), x, y, z, 24, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private static boolean eligible(ServerPlayer caster) {
        return !caster.isCreative() && !caster.isSpectator();
    }

    private static boolean isAllied(ServerPlayer caster, LivingEntity target) {
        if (target.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED) || caster.isAlliedTo(target)) {
            return true;
        }
        return target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    /** Mobs that neither hunt you nor fight back: cows, villagers, owls. */
    private static boolean isPassive(LivingEntity target) {
        if (target instanceof Player || target instanceof Enemy || target instanceof NeutralMob) {
            return false;
        }
        if (target instanceof Mob mob && mob.getTarget() instanceof Player) {
            return false;
        }
        return target instanceof Mob;
    }

    private static HitLedger ledger(ServerPlayer caster) {
        return LEDGERS.computeIfAbsent(caster.getUUID(), id -> new HitLedger());
    }

    /** receiveCanceled: another mod cancelling FinalizeSpawn must not make a spawner mob pay full XP. */
    @SubscribeEvent(receiveCanceled = true)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER || type == MobSpawnType.TRIAL_SPAWNER) {
            event.getEntity().addTag(SPAWNER_TAG);
        }
    }

    /**
     * Fallback tag. PositionCheck fires for every spawner attempt before FinalizeSpawn, whatever NBT
     * the spawner carries. A mob that then fails the check is discarded, so tagging it is harmless.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onSpawnerPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER || type == MobSpawnType.TRIAL_SPAWNER) {
            event.getEntity().addTag(SPAWNER_TAG);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEDGERS.remove(event.getEntity().getUUID());
    }
}
