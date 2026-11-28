package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.CastFizzles;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;

/** A cast that would not come: when a press fizzles, and that a refused cast still casts and charges nothing. */
@GameTestHolder(ExampleMod.MODID)
public final class CastFizzleGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private CastFizzleGameTests() {
    }

    /** A real survival player: the helper's mock player always reports creative, which never waits or pays. */
    private static ServerPlayer caster(GameTestHelper helper, SpellType spell, String name) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer p = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p, cookie);
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5));
        p.moveTo(at.x, at.y, at.z);
        p.setGameMode(GameType.SURVIVAL);
        ItemStack wand = new ItemStack(ExampleMod.HARRY_POTTER_WAND.get());
        MagicWandItem.setSelectedSpell(wand, spell);
        p.setItemInHand(InteractionHand.MAIN_HAND, wand);
        MagicBookItem.learnSpellIfAbsent(p, spell);
        return p;
    }

    private static void setMana(ServerPlayer p, float mana) {
        CompoundTag modData = PlayerMagicData.getModData(p);
        modData.putFloat("Mana", mana);
        PlayerMagicData.saveModData(p, modData);
    }

    private static float mana(ServerPlayer p) {
        return PlayerMagicData.getModData(p).getFloat("Mana");
    }

    @GameTest(template = EMPTY)
    public static void onlyACoolingSpellFizzlesOnPress(GameTestHelper helper) {
        ServerPlayer p = caster(helper, SpellType.STUPEFY, "fizzle-cooling");
        ItemStack wand = p.getMainHandItem();
        long now = p.level().getGameTime();
        helper.assertFalse(CastFizzles.cooldownFizzles(p, wand, SpellType.STUPEFY), "a ready spell casts");
        MagicWandItem.setSpellCooldown(wand, SpellType.STUPEFY, now + 100L);
        helper.assertTrue(CastFizzles.cooldownFizzles(p, wand, SpellType.STUPEFY), "a cooling spell fizzles");
        MagicWandItem.setSpellCooldown(wand, SpellType.ALOHOMORA, now + 100L);
        helper.assertFalse(CastFizzles.cooldownFizzles(p, wand, SpellType.ALOHOMORA),
                "a lock pick still works a piston lock while it cools");
        helper.assertFalse(CastFizzles.cooldownFizzles(p, wand, null), "no spell, no fizzle");
        helper.assertFalse(CastFizzles.cooldownFizzles(p, ItemStack.EMPTY, SpellType.STUPEFY), "no wand, no fizzle");
        p.setGameMode(GameType.CREATIVE);
        helper.assertFalse(CastFizzles.cooldownFizzles(p, wand, SpellType.STUPEFY), "creative never waits");
        helper.getLevel().getServer().getPlayerList().remove(p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cooldownFollowsThePlayerToAnotherWand(GameTestHelper helper) {
        ServerPlayer p = caster(helper, SpellType.STUPEFY, "cooldown-swap");
        ItemStack first = p.getMainHandItem();
        long now = p.level().getGameTime();
        com.hogwartsmagic.spell.SpellCooldowns.set(p, first, SpellType.STUPEFY, now + 200L);
        ItemStack second = first.copy();
        MagicWandItem.setSpellCooldown(second, SpellType.STUPEFY, 0L); // a wand that never cast it
        helper.assertTrue(com.hogwartsmagic.spell.SpellCooldowns.isOnCooldown(p, second, SpellType.STUPEFY, now),
                "a second wand does not skip the cooldown");
        com.hogwartsmagic.spell.SpellCooldowns.mirrorOnto(p, second);
        helper.assertTrue(MagicWandItem.getSpellCooldown(second, SpellType.STUPEFY) == now + 200L,
                "the held wand shows the player's timer");
        helper.assertFalse(com.hogwartsmagic.spell.SpellCooldowns.isOnCooldown(p, second, SpellType.STUPEFY, now + 200L),
                "the cooldown still ends");
        helper.getLevel().getServer().getPlayerList().remove(p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void refusedCastsFizzleAndChargeNothing(GameTestHelper helper) {
        ServerPlayer p = caster(helper, SpellType.LUMOS, "fizzle-refused");
        ItemStack wand = p.getMainHandItem();
        long now = p.level().getGameTime();
        SpellPackets.CastSpellPacket press = new SpellPackets.CastSpellPacket(false, false, false, false);

        // On cooldown: refused (and shown as a fizzle), no mana taken.
        setMana(p, 50.0F);
        MagicWandItem.setSpellCooldown(wand, SpellType.LUMOS, now + 200L);
        SpellPackets.CastSpellPacket.castAs(p, press);
        helper.assertTrue(mana(p) == 50.0F, "a cooling cast takes no mana, got " + mana(p));
        helper.assertTrue(MagicWandItem.getSpellCooldown(wand, SpellType.LUMOS) == now + 200L,
                "a cooling cast leaves its cooldown alone");

        // Not enough mana: refused, the cooldown not started.
        MagicWandItem.setSpellCooldown(wand, SpellType.LUMOS, 0L);
        setMana(p, 0.0F);
        SpellPackets.CastSpellPacket.castAs(p, press);
        helper.assertTrue(mana(p) == 0.0F, "a drained cast takes nothing, got " + mana(p));
        helper.assertTrue(!MagicWandItem.isOnCooldown(wand, SpellType.LUMOS, now),
                "a drained cast starts no cooldown");

        // The client's notices: true ones and false ones alike are only pictures, and never throw.
        CastFizzles.onNotice(p, CastFizzles.DRAINED);
        CastFizzles.onNotice(p, CastFizzles.COOLDOWN);
        CastFizzles.onNotice(p, 7);
        helper.getLevel().getServer().getPlayerList().remove(p);
        helper.succeed();
    }
}
