package com.hogwartsmagic.spell;

import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A cast that would not come, shown to whoever is watching: the wand sputters when its spell is still on cooldown,
 * and droops, drained, when the caster has not the mana for it (client.CastFizzle draws both). Common code: it only
 * sends an {@link FxKinds#CAST_FIZZLE} effect and never touches a client class.
 *
 * <p>Your own client plays its fizzle the moment you press (it knows the cooldown and your mana) and tells the
 * server ({@link #onNotice}) so the players round you see it too; a cast the server refuses when your client let it
 * through ({@link #refused}) shows it to you as well. Purely visual: nothing here casts, charges or blocks anything.
 */
public final class CastFizzles {

    public static final int COOLDOWN = 0;
    public static final int DRAINED = 1;

    /** Players further than this from the caster are not told. */
    private static final double RANGE = 48.0;
    /**
     * At most one fizzle per caster per move: a held key or a channel's heartbeats (refused every tick) replay it
     * once it has played out, never restart it halfway. The moves are 14 and 24 ticks (client.CastFizzle).
     */
    private static long gap(int kind) {
        return kind == DRAINED ? 26L : 16L;
    }

    /** Server thread only. Weak: a player object that is gone (logged out, respawned) takes its entry with it. */
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();

    private CastFizzles() {
    }

    /**
     * Whether a press of {@code spell} on {@code wand} only fizzles because the spell is still on cooldown. Asked
     * by the client to play the sputter at once; the last two ticks of a cooldown are let through, since the client's
     * clock runs a little behind the server's (the server still refuses them if it must). Left out: a lock pick
     * (Alohomora, Revelio) works a piston lock while the wand cools down, and Ascendio may be cast twice.
     */
    public static boolean cooldownFizzles(net.minecraft.world.entity.player.Player player, ItemStack wand,
                                          SpellType spell) {
        return spell != null && !player.isCreative() && MagicWandItem.isAnyWand(wand)
                && spell != SpellType.ALOHOMORA && spell != SpellType.REVELIO && spell != SpellType.ASCENDIO
                && com.hogwartsmagic.spell.SpellCooldowns.isOnCooldown(player, wand, spell, player.level().getGameTime() + 2L);
    }

    /** The server refused {@code player}'s cast of {@code spell} for {@code kind}: everyone watching, them too, sees it. */
    public static void refused(ServerPlayer player, SpellType spell, int kind) {
        tell(player, spell, kind, true);
    }

    /**
     * {@code player}'s client fizzled a cast itself (and played it already): the players round them see it, if it
     * holds up here. Anything else is dropped; it is only a picture, so a client that lies gains nothing.
     */
    public static void onNotice(ServerPlayer player, int kind) {
        if (player.isCreative() || player.isSpectator() || (kind != COOLDOWN && kind != DRAINED)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        SpellType spell = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (spell == null) {
            return;
        }
        long now = player.level().getGameTime();
        boolean holds;
        if (kind == COOLDOWN) {
            // A few ticks of slack: the client's clock and the wand data reach it a moment late.
            holds = com.hogwartsmagic.spell.SpellCooldowns.get(player, wand, spell) > now - 5L;
        } else {
            CompoundTag modData = PlayerMagicData.getModData(player);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : Float.MAX_VALUE;
            holds = mana < SpellCosts.manaCost(player, wand, spell) + 2.0F;
        }
        if (holds) {
            tell(player, spell, kind, false);
        }
    }

    private static void tell(ServerPlayer player, SpellType spell, int kind, boolean includeSelf) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST.get(player);
        if (last != null && now >= last && now - last < gap(kind)) {
            return;
        }
        LAST.put(player, now);
        SpellPackets.FxPacket packet = new SpellPackets.FxPacket(FxKinds.CAST_FIZZLE, player.getX(), player.getY(),
                player.getZ(), player.getId(), new float[]{kind, spell == null ? -1.0F : spell.ordinal()});
        double r2 = RANGE * RANGE;
        for (ServerPlayer other : level.players()) {
            if (other == player ? !includeSelf : other.distanceToSqr(player) > r2) {
                continue;
            }
            SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(other));
        }
    }
}
