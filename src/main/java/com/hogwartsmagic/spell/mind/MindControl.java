package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The shared rules for a body that isn't its owner's to drive. A controlled player's own attacks, item
 * use, block breaking, drops, hand swaps and containers are refused on both sides; the mod's own forced
 * actions (a thrall swinging at its owner's enemy) run inside {@link #forcedAttack} and pass. Each mind
 * spell filters LivingChangeTargetEvent for its mobs; {@link #setTargetBypassing} is how a spell sets the
 * one target it picked without tripping its own filter. Common code: safe on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MindControl {
    private static final ThreadLocal<Boolean> FORCED_ACTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> TARGET_BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static Holder<MobEffect> imperiusHolder;
    private static Holder<MobEffect> possessedHolder;
    private static Holder<MobEffect> rootedHolder;
    private static Holder<MobEffect> feralHolder;

    private MindControl() {
    }

    /** The puppeteer of the puppet beam currently resolving a hit (server thread only); null outside one. */
    @javax.annotation.Nullable
    private static java.util.UUID hitPuppeteer;

    @javax.annotation.Nullable
    public static java.util.UUID hitPuppeteer() {
        return hitPuppeteer;
    }

    /** Set the hit context, returning the previous one for the caller's finally block. */
    @javax.annotation.Nullable
    public static java.util.UUID swapHitPuppeteer(@javax.annotation.Nullable java.util.UUID puppeteer) {
        java.util.UUID previous = hitPuppeteer;
        hitPuppeteer = puppeteer;
        return previous;
    }

    /** True when a puppet's spell must not touch {@code hit}: the puppeteer's own body, or a player they may not harm. */
    public static boolean puppeteerSpares(net.minecraft.server.level.ServerLevel level, @javax.annotation.Nullable java.util.UUID puppeteer,
                                          net.minecraft.world.entity.player.Player hit) {
        if (puppeteer == null) {
            return false;
        }
        if (hit.getUUID().equals(puppeteer)) {
            return true;
        }
        net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(puppeteer);
        return owner == null || hit.isCreative() || hit.isSpectator() || !level.getServer().isPvpAllowed()
                || !owner.canHarmPlayer(hit) || hit.isAlliedTo(owner)
                || hit.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);
    }

    /** Explosions (Confringo) go off with the puppeteer's context: drop the players it may not harm. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onExplosionDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (hitPuppeteer == null) {
            return;
        }
        event.getAffectedEntities().removeIf(MindControl::spareInHitContext);
    }

    /** Splash and chain selection while a puppet beam resolves: true when {@code candidate} is off limits. */
    public static boolean spareInHitContext(Entity candidate) {
        if (hitPuppeteer == null) {
            return false;
        }
        if (candidate instanceof net.minecraft.world.entity.player.Player player) {
            return player.level() instanceof net.minecraft.server.level.ServerLevel level
                    && (puppeteerSpares(level, hitPuppeteer, player) || sparesThrallOrAlly(hitPuppeteer, candidate));
        }
        return sparesThrallOrAlly(hitPuppeteer, candidate);
    }

    /** A puppet never hits its puppeteer's own Imperio thralls or anything tagged allied. */
    public static boolean sparesThrallOrAlly(@javax.annotation.Nullable java.util.UUID puppeteer, Entity candidate) {
        return puppeteer != null && (ImperioRules.sameThrallHousehold(puppeteer, ImperioThralls.ownerIdOf(candidate))
                || candidate.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED));
    }

    /** Under someone else's control: an Imperio thrall, a possessed body or a feral werewolf. Safe on both sides. */
    public static boolean isControlled(LivingEntity entity) {
        Holder<MobEffect> imperius = imperiusHolder;
        if (imperius == null) {
            imperius = imperiusHolder = ExampleMod.IMPERIUS.getHolder().orElse(null);
            if (imperius == null) {
                return false;
            }
        }
        Holder<MobEffect> possessed = possessedHolder;
        if (possessed == null) {
            possessed = possessedHolder = ExampleMod.POSSESSED.getHolder().orElse(null);
        }
        Holder<MobEffect> feral = feralHolder;
        if (feral == null) {
            feral = feralHolder = ExampleMod.FERAL.getHolder().orElse(null);
        }
        return entity.hasEffect(imperius) || (possessed != null && entity.hasEffect(possessed))
                || (feral != null && entity.hasEffect(feral));
    }

    /**
     * The effects a dying creeper's lingering cloud must not hand on: the mind-control markers (Imperius,
     * possession, rooted, feral) belong to one body and one session, and so does the Strength Imperio lent
     * a thrall. Splashed, they'd make ownerless "thralls" that no session ever releases. Server side only.
     */
    public static java.util.Collection<net.minecraft.world.effect.MobEffectInstance> withoutBodyBoundEffects(
            LivingEntity body, java.util.Collection<net.minecraft.world.effect.MobEffectInstance> effects) {
        boolean imperioStrength = ImperioThralls.grantedStrength(body);
        java.util.List<net.minecraft.world.effect.MobEffectInstance> kept = new java.util.ArrayList<>(effects.size());
        for (net.minecraft.world.effect.MobEffectInstance effect : effects) {
            Holder<MobEffect> type = effect.getEffect();
            boolean bound = isMarker(type, ExampleMod.IMPERIUS) || isMarker(type, ExampleMod.POSSESSED)
                    || isMarker(type, ExampleMod.POSSESSING) || isMarker(type, ExampleMod.ROOTED)
                    || isMarker(type, ExampleMod.FERAL)
                    || (imperioStrength && type.value() == net.minecraft.world.effect.MobEffects.DAMAGE_BOOST.value()
                        && effect.getAmplifier() == 0);
            if (!bound) {
                kept.add(effect);
            }
        }
        return kept;
    }

    private static boolean isMarker(Holder<MobEffect> type,
                                    net.minecraftforge.registries.RegistryObject<MobEffect> marker) {
        return marker.isPresent() && type.value() == marker.get();
    }

    /** A feral werewolf: the curse, not a wizard, holds the body. Safe on both sides. */
    public static boolean isFeral(LivingEntity entity) {
        Holder<MobEffect> feral = feralHolder;
        if (feral == null) {
            feral = feralHolder = ExampleMod.FERAL.getHolder().orElse(null);
        }
        return feral != null && entity.hasEffect(feral);
    }

    /** PvP is off (server setting or team rules) and a player's curse would land on another player. */
    public static boolean pvpBlocked(LivingEntity target, @Nullable Entity caster) {
        return target instanceof Player victim
                && com.hogwartsmagic.spell.combat.CombatEffects.responsible(caster) instanceof ServerPlayer p
                && p != victim
                && !(p.serverLevel().getServer().isPvpAllowed() && p.canHarmPlayer(victim));
    }

    /** A melee swing the mod makes on a controlled player's behalf (vanilla damage, cooldown and enchantments). */
    public static void forcedAttack(ServerPlayer attacker, Entity target) {
        FORCED_ACTION.set(Boolean.TRUE);
        try {
            attacker.attack(target);
            attacker.swing(InteractionHand.MAIN_HAND, true);
        } finally {
            FORCED_ACTION.set(Boolean.FALSE);
        }
    }

    /** Set a mob's target past every mind spell's target filter (the filters only stop the mob's own picks). */
    public static void setTargetBypassing(Mob mob, @Nullable LivingEntity target) {
        TARGET_BYPASS.set(Boolean.TRUE);
        try {
            mob.setTarget(target);
        } finally {
            TARGET_BYPASS.set(Boolean.FALSE);
        }
    }

    /**
     * Wipe a brain-driven mob's attack memories (Piglins, Hoglins, Villagers, Warden, Goats, Frogs...), which
     * setTarget(null) does not touch. With {@code forgotten} given, it also forgets its anger and who hurt it.
     */
    public static void clearBrainTarget(Mob mob, @Nullable Entity forgotten) {
        net.minecraft.world.entity.ai.Brain<?> brain = mob.getBrain();
        eraseIfRegistered(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
        if (forgotten != null) {
            eraseIfRegistered(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
            eraseIfRegistered(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
            eraseIfRegistered(brain, net.minecraft.world.entity.ai.memory.MemoryModuleType.UNIVERSAL_ANGER);
            if (mob instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
                warden.clearAnger(forgotten);
            }
        }
    }

    /** Point a brain-driven mob's attack memory at {@code target} (a no-op for mobs without the module). */
    public static void setBrainTarget(Mob mob, LivingEntity target) {
        net.minecraft.world.entity.ai.Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        }
    }

    private static void eraseIfRegistered(net.minecraft.world.entity.ai.Brain<?> brain,
                                          net.minecraft.world.entity.ai.memory.MemoryModuleType<?> type) {
        if (brain.checkMemory(type, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
            brain.eraseMemory(type);
        }
    }

    public static boolean isBypassingTargetRules() {
        return TARGET_BYPASS.get();
    }

    /** A controlled body, or a caster whose own body stands in a possession trance (server only: the maps are). */
    private static boolean blocksOwnAction(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && !FORCED_ACTION.get()
                && (isControlled(living) || (!living.level().isClientSide() && PossessionManager.isPossessing(living)));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (blocksOwnAction(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player && blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * A cancelled toss would destroy the stack (vanilla already took it out of the inventory), so put it back.
     * Whatever doesn't fit still drops, so nothing is ever duplicated or lost.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide() || !blocksOwnAction(player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem().copy();
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            event.setCanceled(true);
        } else {
            event.getEntity().setItem(stack);
        }
    }

    /** Closes any container a player has open besides their own inventory; other entities are ignored. */
    public static void closeOpenContainer(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof ServerPlayer player && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && blocksOwnAction(player)) {
            player.closeContainer();
        }
    }

    /** Channels (Crucio, a possession) never survive a rejoin, so nobody logs in still rooted. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Holder<MobEffect> rooted = rootedHolder;
        if (rooted == null) {
            rooted = rootedHolder = ExampleMod.ROOTED.getHolder().orElse(null);
            if (rooted == null) {
                return;
            }
        }
        if (player.hasEffect(rooted)) {
            player.removeEffect(rooted);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        if (event.isCancelable() && blocksOwnAction(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
