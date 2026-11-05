package com.hogwartsmagic.client;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class ModEventsClientHooks {
    private ModEventsClientHooks() {
    }

    /** How long a server action-bar line keeps the wand cooldown line from overwriting it. */
    private static final long SERVER_LINE_TICKS = 40L;
    private static long serverLineUntil;

    /**
     * The server sent an action-bar line (a refusal, a status, a warning). The cooldown line below is
     * rewritten every tick while a wand is held, so without this the server's line was gone after one tick.
     */
    public static void onServerActionBar() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            serverLineUntil = minecraft.level.getGameTime() + SERVER_LINE_TICKS;
        }
    }

    /** Forget the window when the world goes away, so a new world does not start with a stale one. */
    public static void clearServerActionBar() {
        serverLineUntil = 0L;
    }

    public static void onClientPlayerTick(Player player, boolean serpensortiaUseLatched, boolean fiendfyreUseLatched, boolean wingardiumUseLatched, boolean imperioUseLatched) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player != minecraft.player) {
            return;
        }

        boolean resetLatch = serpensortiaUseLatched && !minecraft.options.keyUse.isDown();
        boolean resetFiendfyreLatch = fiendfyreUseLatched && !minecraft.options.keyUse.isDown();
        boolean resetWingardiumLatch = wingardiumUseLatched && !minecraft.options.keyUse.isDown();
        boolean resetImperioLatch = imperioUseLatched && !minecraft.options.keyUse.isDown();
        if (resetLatch || resetFiendfyreLatch || resetWingardiumLatch || resetImperioLatch) {
            com.hogwartsmagic.event.ModEvents.resetUseLatches(resetLatch, resetFiendfyreLatch, resetWingardiumLatch, resetImperioLatch);
        }
        ItemStack stack = player.getMainHandItem();
        if (!MagicWandItem.isAnyWand(stack)) {
            stack = player.getOffhandItem();
        }

        if (MagicWandItem.isAnyWand(stack)) {
            SpellType selected = MagicWandItem.getSelectedSpell(stack);
            // While a lock is picked the server's progress bar owns the action bar, and a fresh server line
            // keeps it for a moment too.
            if (selected != null && !player.isCreative() && !LockPickClient.ownsActionBar()
                    && player.level().getGameTime() >= serverLineUntil) {
                long now = player.level().getGameTime();
                long cooldownEnd = MagicWandItem.getSpellCooldown(stack, selected);
                long remaining = cooldownEnd - now;

                if (remaining > 0) {
                    String timeText = String.format("%.1fs", remaining / 20.0f);
                    Component msg = Component.translatable("spell.hogwartsmagic." + selected.getId())
                            .append(": ")
                            .append(Component.literal(timeText).withStyle(net.minecraft.ChatFormatting.RED));
                    player.displayClientMessage(msg, true);
                } else if (player.tickCount % 40 < 20) {
                    Component msg = Component.translatable("spell.hogwartsmagic." + selected.getId())
                            .append(": ")
                            .append(Component.translatable("gui.hogwartsmagic.ready").withStyle(net.minecraft.ChatFormatting.GREEN));
                    player.displayClientMessage(msg, true);
                }
            }
        }
    }

    public static void openSpellMenu(Player player) {
        Minecraft.getInstance().setScreen(new SpellMenuScreen(player));
    }

    /** Radial menu cast: an Alohomora or Revelio hold started here is held by right-click, with a grace period. */
    public static boolean selectSpellAndCast(Player player, SpellType spell) {
        return selectSpellAndCast(player, spell, Minecraft.getInstance().options.keyUse, true);
    }

    /** Slot key cast: holdKey is the slot key that holds an Alohomora or Revelio pick (no grace). */
    public static boolean selectSpellAndCast(Player player, SpellType spell, net.minecraft.client.KeyMapping holdKey) {
        return selectSpellAndCast(player, spell, holdKey, false);
    }

    private static boolean selectSpellAndCast(Player player, SpellType spell, net.minecraft.client.KeyMapping holdKey, boolean grace) {
        if (player == null || spell == null) {
            return false;
        }

        ItemStack wand = getHeldWand(player);
        if (wand.isEmpty()) {
            return false;
        }

        MagicWandItem.setSelectedSpell(wand, spell);
        SpellPackets.CHANNEL.send(new SpellPackets.SelectSpellPacket(spell.getId()), PacketDistributor.SERVER.noArg());
        return castSelectedSpell(player, wand, spell, holdKey, grace);
    }

    private static boolean castSelectedSpell(Player player, ItemStack wand, SpellType selected,
                                             net.minecraft.client.KeyMapping holdKey, boolean grace) {
        // Immediate feedback only; the server refuses on its own (CastSpellPacket.handle).
        if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
            com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
            return true;
        }
        if (selected == SpellType.PROTEGO_MAXIMA && com.hogwartsmagic.event.ClientModEvents.startsProtegoMaximaChannel()) {
            com.hogwartsmagic.event.ClientModEvents.setProtegoMaximaChannelActive(true);
            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
            return true;
        }
        if (selected == SpellType.FIENDFYRE) {
            com.hogwartsmagic.event.ClientModEvents.startFiendfyreHold();
            return true;
        }
        if (selected == SpellType.EXPECTO_PATRONUM) {
            com.hogwartsmagic.event.ClientModEvents.startPatronusHold();
            return true;
        }
        if (selected == SpellType.WINGARDIUM_LEVIOSA) {
            com.hogwartsmagic.event.ClientModEvents.startWingardiumHold();
            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
            player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        if (selected == SpellType.IMPERIO) {
            if (com.hogwartsmagic.spell.mind.ImperioRules.isPossessionRank(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.IMPERIO))) {
                com.hogwartsmagic.event.ClientModEvents.startImperioHold(); // Master: hold to take control
            }
            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
            player.swing(InteractionHand.MAIN_HAND);
            return true;
        }

        net.minecraft.nbt.CompoundTag modData = player.getPersistentData().getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : Config.getBaseMaxMana();
        int requiredMana = com.hogwartsmagic.spell.SpellCosts.manaCost(player, wand, selected);
        if (!player.isCreative() && currentMana < requiredMana) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return true;
        }

        if (selected == SpellType.CRUCIO) {
            if (com.hogwartsmagic.event.ClientModEvents.isCrucioHoldPending()) {
                return true;
            }
            com.hogwartsmagic.event.ClientModEvents.startCrucioHold();
        }
        if (selected == SpellType.ALOHOMORA || selected == SpellType.REVELIO) {
            if (com.hogwartsmagic.event.ClientModEvents.isLockHoldPending()) {
                return true; // one cast per press: a pick is already held
            }
            com.hogwartsmagic.event.ClientModEvents.startLockHold(holdKey, grace);
        }

        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static ItemStack getHeldWand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (MagicWandItem.isAnyWand(main)) {
            return main;
        }

        ItemStack off = player.getOffhandItem();
        if (MagicWandItem.isAnyWand(off)) {
            return off;
        }

        return ItemStack.EMPTY;
    }
}
