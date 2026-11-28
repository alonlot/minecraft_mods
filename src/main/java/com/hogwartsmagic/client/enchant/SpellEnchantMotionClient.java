package com.hogwartsmagic.client.enchant;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.enchant.SpellEnchantments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The movement halves of Leviosa and Alatus. A player's own movement is worked out on their client, so this is where
 * it has to change; the server only recognises the hover (SpellEnchantmentEffects) to forgive the fall and wear the
 * boots, and the server's own speed and float checks stay in force.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpellEnchantMotionClient {
    /** Horizontal glide speed Alatus never pushes past (blocks a tick). */
    private static final double ALATUS_CAP = 2.2;
    private static int hoverUsed;

    private SpellEnchantMotionClient() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer player) || player != Minecraft.getInstance().player || player.isSpectator()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            leviosa(player);
        } else {
            alatus(player);
        }
    }

    /** Hold jump in the air to hover, sinking slowly, for 2 s (I) or 4 s (II) each time you leave the ground. */
    private static void leviosa(LocalPlayer player) {
        if (player.onGround() || player.isInWater() || player.isInLava()) {
            hoverUsed = 0;
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (!player.input.jumping || v.y > 0.0 || player.isFallFlying() || player.getAbilities().flying
                || player.isPassenger() || player.onClimbable()) {
            return;
        }
        int level = SpellEnchantments.level(player, SpellEnchantments.LEVIOSA, EquipmentSlot.FEET);
        if (level <= 0 || hoverUsed >= SpellEnchantments.leviosaHoverTicks(level)
                || !com.hogwartsmagic.spell.enchant.SpellEnchantMana.clientHas(player, com.hogwartsmagic.spell.enchant.SpellEnchantMana.LEVIOSA)) {
            return;
        }
        hoverUsed++;
        player.setDeltaMovement(v.x, SpellEnchantments.LEVIOSA_SINK, v.z);
    }

    /** Gliding with Alatus elytra loses less speed. */
    private static void alatus(LocalPlayer player) {
        if (!player.isFallFlying()) {
            return;
        }
        int level = SpellEnchantments.level(player, SpellEnchantments.ALATUS, EquipmentSlot.CHEST);
        if (level <= 0 || !com.hogwartsmagic.spell.enchant.SpellEnchantMana.clientHas(player,
                com.hogwartsmagic.spell.enchant.SpellEnchantMana.ALATUS)) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        if (horizontal < 0.1 || horizontal >= ALATUS_CAP) {
            return;
        }
        double carry = Math.min(SpellEnchantments.alatusCarry(level), ALATUS_CAP / horizontal);
        player.setDeltaMovement(v.x * carry, v.y, v.z * carry);
    }
}
