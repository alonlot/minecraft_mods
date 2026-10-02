package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Levicorpus: the target is hoisted 1.5 blocks up by the ankle and hangs there, hovering in place
 * (not drifting upward like the old Levitation). While hanging it can't use items or raise a shield;
 * wands are exempt, so a hanging duellist can still cast. From Adept it takes +25% damage. At Master,
 * casting Levicorpus again on a hanging target drops it hard (Liberacorpus): 6 blocks of fall damage.
 *
 * <p>The hang is the hidden levicorpus effect (hard crowd control via applyHiddenCrowdControl; its
 * amplifier is the caster's rank - 1). The hover anchor and the target's original no-gravity flag sit
 * in persistent NBT under {@link #ANCHOR_TAG}; gravity is restored the moment the effect goes. The
 * upside-down pose is drawn by ClientForgeEvents from the effect, which VisibleEffects syncs.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LevicorpusSpell {
    public static final String ANCHOR_TAG = "hogwartsmagic:levicorpus_anchor";

    private LevicorpusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LEVICORPUS);
        if (isHanging(target) && LevicorpusRules.liberacorpus(rank)) {
            liberacorpus(level, target);
            return;
        }
        int ticks = DisableSupport.applyHiddenCrowdControl(target, ExampleMod.LEVICORPUS_EFFECT,
                LevicorpusRules.hangTicks(rank), rank - 1, caster);
        if (ticks <= 0) {
            return;
        }
        if (target.isPassenger()) {
            target.stopRiding();
        }
        if (target.isUsingItem()) {
            target.stopUsingItem();
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) { // a refresh keeps the first anchor and the original gravity flag
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", target.getX());
            tag.putDouble("Y", target.getY() + hangHeight(level, target));
            tag.putDouble("Z", target.getZ());
            tag.putBoolean("HadNoGravity", target.isNoGravity());
            tag.putString("Dim", level.dimension().location().toString());
            data.put(ANCHOR_TAG, tag);
        }
        target.setNoGravity(true);
    }

    /** Works on both sides: the server has the effect, the local player gets its own, watchers get it from VisibleEffects. */
    public static boolean isHanging(LivingEntity entity) {
        return ExampleMod.LEVICORPUS_EFFECT.getHolder().map(entity::hasEffect).orElse(false);
    }

    /** Give gravity back and forget the anchor. Safe to call any time. */
    public static void release(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) {
            return;
        }
        boolean hadNoGravity = data.getCompound(ANCHOR_TAG).getBoolean("HadNoGravity");
        data.remove(ANCHOR_TAG);
        entity.setNoGravity(hadNoGravity);
        entity.fallDistance = 0.0F;
    }

    /** Up to 1.5 blocks, less under a low ceiling (0.25-block steps). */
    private static double hangHeight(ServerLevel level, LivingEntity target) {
        double height = LevicorpusRules.HANG_HEIGHT;
        while (height > 0.0 && !level.noCollision(target, target.getBoundingBox().move(0.0, height, 0.0))) {
            height -= 0.25;
        }
        return Math.max(0.0, height);
    }

    private static void liberacorpus(ServerLevel level, LivingEntity target) {
        Holder<MobEffect> hang = DisableSupport.holder(ExampleMod.LEVICORPUS_EFFECT);
        target.removeEffect(hang);
        if (target.hasEffect(hang)) {
            return; // another mod refused the removal
        }
        release(target);
        target.setDeltaMovement(0.0, LevicorpusRules.LIBERACORPUS_DROP_SPEED, 0.0);
        target.hurtMarked = true;
        target.hurt(target.damageSources().fall(), LevicorpusRules.LIBERACORPUS_FALL_DAMAGE);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.2, target.getZ(), 20, 0.5, 0.1, 0.5, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.NEUTRAL, 1.0f, 0.9f);
    }

    /** Hold the hanging target at its anchor; let go once the effect has ended. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!target.isNoGravity()) {
            return; // the hang always sets no-gravity; don't touch persistent data on every other entity
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) {
            return;
        }
        if (!target.isAlive() || !isHanging(target)) {
            release(target);
            return;
        }
        CompoundTag tag = data.getCompound(ANCHOR_TAG);
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (!tag.getString("Dim").equals(level.dimension().location().toString())) {
            // Changed dimension (portal, command): the anchor means nothing here, so let the hang go.
            target.removeEffect(DisableSupport.holder(ExampleMod.LEVICORPUS_EFFECT));
            release(target); // idempotent; covers a refused removal event path
            return;
        }
        double ax = tag.getDouble("X");
        double ay = tag.getDouble("Y");
        double az = tag.getDouble("Z");
        target.setNoGravity(true);
        target.fallDistance = 0.0F;
        if (target.isPassenger()) {
            target.stopRiding(); // no mounting up and riding away
        }
        if (target.isUsingItem()) {
            target.stopUsingItem(); // drops a raised shield or a drawn bow
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (!level.noCollision(target, target.getBoundingBox().move(ax - target.getX(), ay - target.getY(), az - target.getZ()))) {
            // The anchor got built over: hang where it stands instead of snapping into a block.
            tag.putDouble("X", target.getX());
            tag.putDouble("Y", target.getY());
            tag.putDouble("Z", target.getZ());
            return;
        }
        double dx = ax - target.getX();
        double dy = ay - target.getY();
        double dz = az - target.getZ();
        double snap = LevicorpusRules.SNAP_DISTANCE;
        if (dx * dx + dy * dy + dz * dz > snap * snap) {
            DisableSupport.snapTo(target, ax, ay, az); // a player gets a real teleport packet
            target.setDeltaMovement(Vec3.ZERO);
        } else if (!(target instanceof ServerPlayer)) { // player motion is client-authoritative: no velocity packets
            double gain = LevicorpusRules.HOLD_GAIN;
            target.setDeltaMovement(dx * gain, dy * gain, dz * gain);
        }
    }

    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (!event.getEntity().level().isClientSide() && event.getEffect() == ExampleMod.LEVICORPUS_EFFECT.get()) {
            release(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (!event.getEntity().level().isClientSide() && instance != null
                && instance.getEffect().value() == ExampleMod.LEVICORPUS_EFFECT.get()) {
            release(event.getEntity());
        }
    }

    /** Adept and up: a hanging target takes +25% damage (before armour). */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        MobEffectInstance hang = target.getEffect(DisableSupport.holder(ExampleMod.LEVICORPUS_EFFECT));
        if (hang != null) {
            event.setAmount(event.getAmount() * LevicorpusRules.damageTakenMultiplier(hang.getAmplifier() + 1));
        }
    }

    // ---- No items or shields while hanging (both sides, so the client never even swings). Wands are exempt.

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean isWand(ItemStack stack) {
        return stack.getItem() instanceof MagicWandItem;
    }
}
