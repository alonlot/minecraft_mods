package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the Killing Curse beam's end. The beam itself (the jet of green, its dark sheath, the crackle
 * round it and the smoke it leaves) is drawn on each client from the moving entity; how it ended is sent here,
 * once, so the clients can play the strike, and the strike's sound is played for everyone in earshot.
 */
public final class AvadaBeamFx {
    /** Struck a living target. */
    public static final byte HIT = 0;
    /** Ran into a wall or the ground, or was stopped. */
    public static final byte WALL = 1;
    /** A boss slipped it: it bursts into black where he was. */
    public static final byte EVADE = 2;
    /** Flew its full range and died out in mid-air. */
    public static final byte FADE = 3;

    private AvadaBeamFx() {
    }

    public static void impact(Entity beam, Vec3 at, byte kind) {
        if (beam.level().isClientSide()) {
            return;
        }
        Vec3 dir = beam.getDeltaMovement();
        dir = dir.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        SpellPackets.CHANNEL.send(new SpellPackets.AvadaImpactPacket(beam.getId(), at.x, at.y, at.z,
                        (float) dir.x, (float) dir.y, (float) dir.z, kind),
                PacketDistributor.TRACKING_ENTITY.with(beam));
        float volume = kind == HIT ? 1.3f : kind == FADE ? 0.5f : 0.9f;
        float pitch = (kind == HIT ? 0.95f : 1.1f) + beam.level().getRandom().nextFloat() * 0.1f;
        beam.level().playSound(null, at.x, at.y, at.z, ModSounds.AVADA_IMPACT.get(), SoundSource.PLAYERS, volume, pitch);
    }
}
