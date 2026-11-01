package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the Killing Curse's windup visuals. Everything you see and hear while it gathers (the shroud,
 * the green drawn into the wand, the arcs, the raised arm, the darkening view and the swelling drone) is drawn
 * on each client from these few packets, so the server sends a start and an end and nothing per tick.
 */
public final class AvadaChargeFx {
    public static final byte BEGIN = 0;
    /** The curse left the wand. */
    public static final byte FIRED = 1;
    /** A hit, stun or silence broke it: the gathered curse collapses. */
    public static final byte BROKEN = 2;
    /** Wand or spell switched, logout, a caster that lost interest: it just fades. */
    public static final byte DROPPED = 3;

    private AvadaChargeFx() {
    }

    public static void begin(LivingEntity caster, int durationTicks) {
        send(caster, durationTicks, BEGIN);
    }

    public static void end(LivingEntity caster, byte phase) {
        send(caster, 0, phase);
        if (phase == BROKEN) {
            caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    ModSounds.AVADA_SNUFF.get(), caster instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                    1.0f, 0.9f + caster.getRandom().nextFloat() * 0.15f);
        }
    }

    private static void send(LivingEntity caster, int durationTicks, byte phase) {
        if (caster.level().isClientSide() || (caster instanceof ServerPlayer player && player.hasDisconnected())) {
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.AvadaChargePacket(caster.getId(), durationTicks, phase),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(caster));
    }
}
