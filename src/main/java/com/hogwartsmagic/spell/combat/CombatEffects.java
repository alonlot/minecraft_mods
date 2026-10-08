package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * The five shared combat effects: queries, helpers for the non-crowd-control ones, and the event
 * hooks that make them behave. Hard crowd control (stun and friends) is applied through
 * {@code CrowdControl} instead, so diminishing returns and the boss and PvP rules always run.
 *
 * <p>Common code. The queries also run on the client (input lock, spell wheel), where they read the
 * effect instances vanilla syncs to the local player; every hook that changes the world checks for
 * the server side first.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEffects {
    public static final int MAX_CURSED_HEARTS = 10;

    /** Persistent-data key holding the UUID of whoever applied the target's bleeding (kill credit). */
    public static final String BLEED_SOURCE_KEY = ExampleMod.MODID + ":bleed_source";

    /** Set only inside {@link #cureCursedWound}, the one removal path the milk guard lets through. */
    private static boolean cursedWoundCureInProgress;
    /** Set only inside {@link #cureUnquenchableThirst}, so a submerged player holding a shield or bow still gets cured. */
    private static boolean thirstCureInProgress;

    private CombatEffects() {
    }

    // ---- Queries (both sides) ---------------------------------------------------------------

    // Hot path (isImmobile runs for every living entity every tick on both sides): resolve each
    // holder once, after registration, and skip the Optional and lambda per call.
    private static Holder<MobEffect> stunnedHolder;
    private static Holder<MobEffect> silencedHolder;
    private static Holder<MobEffect> bleedingHolder;

    public static boolean has(LivingEntity entity, RegistryObject<MobEffect> effect) {
        Holder<MobEffect> holder = effect.getHolder().orElse(null);
        return holder != null && entity.hasEffect(holder);
    }

    public static boolean isStunned(LivingEntity entity) {
        Holder<MobEffect> holder = stunnedHolder;
        if (holder == null) {
            holder = stunnedHolder = ExampleMod.STUNNED.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        // Basilisk stone (an infinite basilisk_petrification) locks everything a stun does.
        return entity.hasEffect(holder) || com.hogwartsmagic.petrification.Petrification.isPetrified(entity);
    }

    public static boolean isSilenced(LivingEntity entity) {
        Holder<MobEffect> holder = silencedHolder;
        if (holder == null) {
            holder = silencedHolder = ExampleMod.SILENCED.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        return entity.hasEffect(holder);
    }

    public static boolean isBleeding(LivingEntity entity) {
        Holder<MobEffect> holder = bleedingHolder;
        if (holder == null) {
            holder = bleedingHolder = ExampleMod.BLEEDING.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        return entity.hasEffect(holder);
    }

    /** Stunned or silenced: no casts, no channel starts, no channel heartbeats. */
    public static boolean isCastingBlocked(LivingEntity entity) {
        return isStunned(entity) || isSilenced(entity);
    }

    public static void notifyCastBlocked(Player player) {
        String key = com.hogwartsmagic.petrification.Petrification.isPetrified(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_petrified"
                : isStunned(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_stunned"
                : "gui." + ExampleMod.MODID + ".combat.cast_blocked_silenced";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Who is responsible for a hit: a projectile's (or spell beam's) owner, otherwise the entity itself. */
    @Nullable
    public static Entity responsible(@Nullable Entity source) {
        if (source instanceof Projectile projectile && projectile.getOwner() != null) {
            return projectile.getOwner();
        }
        return source;
    }

    // ---- Helpers for later phases (server side) ----------------------------------------------

    /** Adds one bleed stack (max 3) and refreshes the duration to at least {@code ticks}. Returns the stacks now on the target. */
    public static int addBleeding(LivingEntity target, int ticks, @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return 0;
        }
        Holder<MobEffect> holder = ExampleMod.BLEEDING.getHolder().orElse(null);
        if (holder == null) {
            return 0;
        }
        MobEffectInstance current = target.getEffect(holder);
        int amplifier = BleedRules.nextAmplifier(current == null ? -1 : current.getAmplifier());
        return applyBleeding(target, ticks, amplifier + 1, source);
    }

    /**
     * Applies bleeding at {@code stacks} (capped at {@link BleedRules#MAX_STACKS}), lasting at least
     * {@code ticks}, and remembers {@code source} so a bleed-out kill is credited to them. Returns the
     * stacks now on the target.
     */
    public static int applyBleeding(LivingEntity target, int ticks, int stacks, @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return 0;
        }
        Holder<MobEffect> holder = ExampleMod.BLEEDING.getHolder().orElse(null);
        if (holder == null) {
            return 0;
        }
        MobEffectInstance current = target.getEffect(holder);
        int amplifier = Math.max(0, Math.min(BleedRules.MAX_STACKS, stacks) - 1);
        int duration = Math.max(ticks, current == null ? 0 : current.getDuration());
        Entity credited = responsible(source);
        if (target.addEffect(new MobEffectInstance(holder, duration, amplifier, false, true, true), credited)
                && credited != null) {
            target.getPersistentData().putUUID(BLEED_SOURCE_KEY, credited.getUUID());
        }
        MobEffectInstance now = target.getEffect(holder);
        return now == null ? 0 : BleedRules.stacks(now.getAmplifier());
    }

    @SubscribeEvent
    public static void onBleedingRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ExampleMod.BLEEDING.get() && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(BLEED_SOURCE_KEY);
        }
    }

    @SubscribeEvent
    public static void onBleedingExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().value() == ExampleMod.BLEEDING.get()
                && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(BLEED_SOURCE_KEY);
        }
    }

    /** Takes {@code hearts} (1..10) of max health for {@code ticks}. */
    public static boolean applyCursedWound(LivingEntity target, int ticks, int hearts, @Nullable Entity source) {
        int amplifier = Math.max(1, Math.min(MAX_CURSED_HEARTS, hearts)) - 1;
        return add(target, ExampleMod.CURSED_WOUND, ticks, amplifier, source);
    }

    public static boolean applyBrittle(LivingEntity target, int ticks, int amplifier, @Nullable Entity source) {
        return add(target, ExampleMod.BRITTLE, ticks, Math.max(0, amplifier), source);
    }

    /** Silence is not hard crowd control, so it skips diminishing returns. */
    public static boolean applySilenced(LivingEntity target, int ticks, @Nullable Entity source) {
        return add(target, ExampleMod.SILENCED, ticks, 0, source);
    }

    /** The only way to lift a cursed wound early (Phase 5: Heal at Master). */
    public static boolean cureCursedWound(LivingEntity target) {
        Holder<MobEffect> holder = ExampleMod.CURSED_WOUND.getHolder().orElse(null);
        if (holder == null || target.level().isClientSide()) {
            return false;
        }
        cursedWoundCureInProgress = true;
        try {
            return target.removeEffect(holder);
        } finally {
            cursedWoundCureInProgress = false;
        }
    }

    /** The only way to lift Unquenchable Thirst (Locket cave lake water). Returns whether it was actually removed. */
    public static boolean cureUnquenchableThirst(LivingEntity target) {
        Holder<MobEffect> holder = ExampleMod.UNQUENCHABLE_THIRST.getHolder().orElse(null);
        if (holder == null || target.level().isClientSide()) {
            return false;
        }
        thirstCureInProgress = true;
        try {
            return target.removeEffect(holder);
        } finally {
            thirstCureInProgress = false;
        }
    }

    private static boolean add(LivingEntity target, RegistryObject<MobEffect> effect, int ticks, int amplifier,
                               @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return false;
        }
        Holder<MobEffect> holder = effect.getHolder().orElse(null);
        if (holder == null) {
            return false;
        }
        return target.addEffect(new MobEffectInstance(holder, ticks, amplifier, false, true, true), responsible(source));
    }

    // ---- Bleeding ----------------------------------------------------------------------------

    /** Natural regeneration is blocked by the FoodData and Player mixins; every other heal is halved. */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (isBleeding(event.getEntity())) {
            event.setAmount(event.getAmount() * BleedRules.HEAL_MULTIPLIER);
        }
    }

    // ---- Brittle -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        Holder<MobEffect> brittle = ExampleMod.BRITTLE.getHolder().orElse(null);
        MobEffectInstance instance = brittle == null ? null : target.getEffect(brittle);
        if (instance == null) {
            return;
        }
        DamageSource source = event.getSource();
        boolean counts = BrittleRules.countsAsBrittleHit(
                source.is(DamageTypeTags.IS_EXPLOSION),
                source.is(DamageTypeTags.IS_PROJECTILE),
                source.getDirectEntity() instanceof LivingEntity,
                source.is(DamageTypeTags.BYPASSES_ARMOR),
                source.is(DamageTypeTags.IS_FIRE));
        if (!counts) {
            return;
        }
        event.setAmount(event.getAmount() * BrittleRules.bonusMultiplier(instance.getAmplifier()));
        target.removeEffect(brittle);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                    target.getZ(), 16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.4F);
        }
    }

    // ---- Cursed wound ------------------------------------------------------------------------

    /**
     * Forge 51 has no curative-item list: milk calls removeAllEffects() from finishUsingItem, while
     * the bucket is still the entity's use item. So any removal during item use (milk, honey, a modded
     * drink) is refused for cursed_wound. /effect clear and natural expiry are not item use (expiry
     * fires MobEffectEvent.Expired, not Remove), so they still work.
     */
    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        boolean cursedWound = !cursedWoundCureInProgress && event.getEffect() == ExampleMod.CURSED_WOUND.get();
        // Unquenchable Thirst is only lifted through LocketCaves.cureThirst, never by milk or another drink.
        boolean thirst = !thirstCureInProgress && event.getEffect() == ExampleMod.UNQUENCHABLE_THIRST.get();
        if (!cursedWound && !thirst) {
            return;
        }
        if (event.getEntity().isUsingItem()) {
            event.setCanceled(true);
        }
    }

    // ---- Stun and silence --------------------------------------------------------------------

    /** Stun or silence landing on a player drops whatever channel they were holding. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect().value();
        if (effect == ExampleMod.STUNNED.get() || effect == ExampleMod.SILENCED.get()) {
            com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
            com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
        }
    }

    /** Server housekeeping for a stunned entity; the movement lock itself is LivingEntityStunMixin. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !isStunned(entity)) {
            return;
        }
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setJumping(false);
        }
        if (entity instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
        }
        if (entity instanceof Player player) {
            player.setSprinting(false);
        }
    }

    /** A stunned mob or player can't land a melee blow (projectiles already in flight and thorns still hit). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity()
                && !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypes.THORNS) && isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (isStunned(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * NPC casters (Students, Death Eaters, Voldemort) cast by spawning a SpellBeamEntity from about
     * ten places in their goals. Refusing the beam here silences all of them in one spot; a player's
     * beam never gets this far because the cast packet is refused first.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (event.getEntity() instanceof SpellBeamEntity beam
                && beam.getOwner() instanceof LivingEntity caster
                && isCastingBlocked(caster)) {
            event.setCanceled(true);
        }
    }

    private static void cancelIfStunned(PlayerInteractEvent event) {
        if (event.isCancelable() && isStunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
