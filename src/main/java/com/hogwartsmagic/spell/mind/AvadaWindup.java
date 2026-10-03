package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * A player's Avada Kedavra winds up before it fires. The cast path calls {@link #begin}; each server tick
 * the windup sparks, and when it completes it re-enters the cast path with a one-shot ready flag, which
 * charges the mana, fires the beam and starts the cooldown exactly like any other cast. Stuns and silences
 * (CombatEffects.onEffectAdded) and Stupefy or mind-spell interrupts (DisableSupport.interrupt) break it; so
 * does any single hit of 4 or more. State is two keys in the player's persistent data, which is saved with
 * the player, so the login handler clears them.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AvadaWindup {
    private static final String TAG_START = ExampleMod.MODID + ":avada_windup_start";
    private static final String TAG_READY = ExampleMod.MODID + ":avada_windup_ready";
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.15f, 1.0f, 0.25f), 1.2f);

    private AvadaWindup() {
    }

    public static boolean isWindingUp(ServerPlayer player) {
        return player.getPersistentData().contains(TAG_START);
    }

    /** Start the windup (once). Refuses without enough mana for the cast, so nobody winds up for nothing. */
    public static void begin(ServerPlayer player) {
        if (isWindingUp(player) || MindControl.isControlled(player)) {
            return;
        }
        if (!player.isCreative()) {
            ItemStack wand = player.getMainHandItem();
            CompoundTag modData = PlayerMagicData.getModData(player);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
            if (mana < SpellCosts.manaCost(player, wand, SpellType.AVADA_KEDAVRA)) {
                player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        player.getPersistentData().putLong(TAG_START, player.level().getGameTime());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 0.5f);
    }

    /** One-shot: true right after the windup finished, so the cast path fires the beam instead of winding up again. */
    public static boolean consumeReady(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_READY)) {
            return false;
        }
        data.remove(TAG_READY);
        return true;
    }

    /**
     * Drop the windup. Interrupted: no mana is spent and Avada is locked for 5s; dropped (wand or spell
     * switched, logout): no penalty.
     */
    public static void cancel(ServerPlayer player, boolean interrupted) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            return;
        }
        data.remove(TAG_START);
        data.remove(TAG_READY);
        if (!interrupted) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        long now = player.level().getGameTime();
        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            long lockout = now + AvadaRules.INTERRUPT_LOCKOUT_TICKS;
            MagicWandItem.setSpellCooldown(wand, SpellType.AVADA_KEDAVRA,
                    Math.max(lockout, MagicWandItem.getSpellCooldown(wand, SpellType.AVADA_KEDAVRA)));
        }
        player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".avada.interrupted")
                .withStyle(ChatFormatting.RED), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.3, player.getZ(),
                    12, 0.25, 0.25, 0.25, 0.02);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!player.isAlive() || MindControl.isControlled(player) || !MagicWandItem.isAnyWand(wand)
                || MagicWandItem.getSelectedSpell(wand) != SpellType.AVADA_KEDAVRA) {
            cancel(player, false);
            return;
        }
        long elapsed = player.level().getGameTime() - data.getLong(TAG_START);
        int rank = SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA);
        sparkle(player, elapsed, AvadaRules.windupTicks(rank));
        if (elapsed % 6 == 0) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".avada.winding")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
        if (AvadaRules.windupDone(elapsed, rank)) {
            data.remove(TAG_START);
            data.putBoolean(TAG_READY, true);
            SpellPackets.CastSpellPacket.castAs(player, new SpellPackets.CastSpellPacket(false, false, false, false));
            data.remove(TAG_READY); // refused before it was consumed (cooldown, silence): never leave it armed
        }
    }

    /** A green spiral that tightens around the wand hand as the curse gathers; everyone nearby sees it. */
    private static void sparkle(ServerPlayer player, long elapsed, int windupTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double charge = Math.min(1.0, elapsed / (double) Math.max(1, windupTicks));
        double radius = 0.9 - charge * 0.6;
        for (int i = 0; i < 3; i++) {
            double angle = elapsed * 0.5 + i * (Math.PI * 2.0 / 3.0);
            level.sendParticles(GREEN, player.getX() + Math.cos(angle) * radius, player.getY() + 1.2 + charge * 0.4,
                    player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && isWindingUp(player)
                && AvadaRules.interrupts(event.getAmount())) {
            cancel(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        CompoundTag data = event.getEntity().getPersistentData();
        data.remove(TAG_START);
        data.remove(TAG_READY);
    }
}
