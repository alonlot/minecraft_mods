package com.hogwartsmagic.spell;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Server side of how a spell beam ends. The beam itself (each spell's own jet or bolt, its strands, its wake) is
 * drawn on each client from the moving entity; how it ended is sent here, once, so the clients can play that
 * spell's strike where it landed.
 */
public final class SpellBeamFx {
    /** It did what it does: struck a target, or a block it acts on. */
    public static final byte HIT = 0;
    /** Into a wall, onto a shield, or onto something it can't touch. */
    public static final byte FIZZLE = 1;
    /** A boss slipped it: it bursts where he was. */
    public static final byte EVADE = 2;
    /** Flew its full range and died out in mid-air. */
    public static final byte FADE = 3;

    private SpellBeamFx() {
    }

    public static void impact(Entity beam, SpellType spell, Vec3 at, byte kind) {
        if (beam.level().isClientSide()) {
            return;
        }
        Vec3 dir = beam.getDeltaMovement();
        dir = dir.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        SpellPackets.CHANNEL.send(new SpellPackets.BeamImpactPacket(beam.getId(), spell.getId(), at.x, at.y, at.z,
                        (float) dir.x, (float) dir.y, (float) dir.z, kind),
                PacketDistributor.TRACKING_ENTITY.with(beam));
        if (spell == SpellType.AVADA_KEDAVRA) {
            float volume = kind == HIT ? 1.3f : kind == FADE ? 0.5f : 0.9f;
            float pitch = (kind == HIT ? 0.95f : 1.1f) + beam.level().getRandom().nextFloat() * 0.1f;
            beam.level().playSound(null, at.x, at.y, at.z, ModSounds.AVADA_IMPACT.get(), SoundSource.PLAYERS,
                    volume, pitch);
        }
    }

    /**
     * Where a beam moving from {@code from} along {@code motion} meets {@code target}: the entry point into its
     * box (grown a little, as the hit test is), or the nearest point on the beam's line to the box's centre. An
     * entity hit only reports the target's feet, which bent the drawn beam down to them.
     */
    public static Vec3 entryPoint(Vec3 from, Vec3 motion, Entity target) {
        AABB box = target.getBoundingBox().inflate(0.3);
        Vec3 to = from.add(motion.scale(2.0));
        Optional<Vec3> clip = box.clip(from.subtract(motion), to);
        if (clip.isPresent()) {
            return clip.get();
        }
        if (box.contains(from)) {
            return from;
        }
        Vec3 centre = box.getCenter();
        double lengthSqr = motion.lengthSqr();
        if (lengthSqr < 1.0E-8) {
            return centre;
        }
        double t = Math.max(0.0, centre.subtract(from).dot(motion) / lengthSqr);
        return from.add(motion.scale(t));
    }

    /** The middle of a living target, for a strike that has no beam line to follow. */
    public static Vec3 bodyCentre(LivingEntity living) {
        return living.position().add(0.0, living.getBbHeight() * 0.55, 0.0);
    }
}
