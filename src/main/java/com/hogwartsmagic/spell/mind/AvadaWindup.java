package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A player's Avada Kedavra winds up before it fires. The cast path calls {@link #begin}; the windup's look and
 * sound are drawn on every client from {@link AvadaChargeFx}'s start and end packets. When it completes it
 * re-enters the cast path with a one-shot ready flag, which charges the mana, fires the beam and starts the cooldown exactly like any other cast. Stuns and silences
 * (CombatEffects.onEffectAdded) and Stupefy or mind-spell interrupts (DisableSupport.interrupt) break it; so
 * does any single hit of 4 or more. State is two keys in the player's persistent data, which is saved with
 * the player, so the login handler clears them.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AvadaWindup {
    private static final String TAG_START = ExampleMod.MODID + ":avada_windup_start";
    private static final String TAG_READY = ExampleMod.MODID + ":avada_windup_ready";

    /** Server-only: who is winding up, so the per-tick handler skips everyone else without touching NBT. */
    private static final java.util.Set<java.util.UUID> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private AvadaWindup() {
    }

    public static boolean isWindingUp(ServerPlayer player) {
        return player.getPersistentData().contains(TAG_START);
    }

    /** Start the windup (once). Refuses without enough mana for the cast, so nobody winds up for nothing. */
    public static void begin(ServerPlayer player) {
        if (isWindingUp(player)
                || (MindControl.isControlled(player) && !PossessionManager.isPuppet(player))) {
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
        ACTIVE.add(player.getUUID());
        AvadaChargeFx.begin(player, AvadaRules.windupTicks(SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA)));
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
        ACTIVE.remove(player.getUUID());
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            return;
        }
        data.remove(TAG_START);
        data.remove(TAG_READY);
        AvadaChargeFx.end(player, interrupted ? AvadaChargeFx.BROKEN : AvadaChargeFx.DROPPED);
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
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!ACTIVE.contains(player.getUUID())) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!player.isAlive() || (MindControl.isControlled(player) && !PossessionManager.isPuppet(player))
                || !MagicWandItem.isAnyWand(wand)
                || MagicWandItem.getSelectedSpell(wand) != SpellType.AVADA_KEDAVRA) {
            cancel(player, false);
            return;
        }
        long elapsed = player.level().getGameTime() - data.getLong(TAG_START);
        int rank = SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA);
        if (elapsed % 6 == 0) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".avada.winding")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
        if (AvadaRules.windupDone(elapsed, rank)) {
            data.remove(TAG_START);
            ACTIVE.remove(player.getUUID());
            data.putBoolean(TAG_READY, true);
            AvadaChargeFx.end(player, AvadaChargeFx.FIRED);
            java.util.UUID previousPuppeteer = PossessionManager.castingPuppeteer;
            PossessionManager.castingPuppeteer = PossessionManager.puppeteerOf(player);
            java.util.UUID previousContext = MindControl.swapHitPuppeteer(PossessionManager.castingPuppeteer);
            try {
                SpellPackets.CastSpellPacket.castAs(player, new SpellPackets.CastSpellPacket(false, false, false, false));
            } finally {
                MindControl.swapHitPuppeteer(previousContext);
                PossessionManager.castingPuppeteer = previousPuppeteer;
            }
            data.remove(TAG_READY); // refused before it was consumed (cooldown, silence): never leave it armed
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
        ACTIVE.remove(event.getEntity().getUUID());
        CompoundTag data = event.getEntity().getPersistentData();
        data.remove(TAG_START);
        data.remove(TAG_READY);
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
