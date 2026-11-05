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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.hogwartsmagic.ExampleMod.MODID,
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellBeamFx {
    /** It did what it does: struck a target, or a block it acts on. */
    public static final byte HIT = 0;
    /** Into a wall, onto a shield, or onto something it can't touch. */
    public static final byte FIZZLE = 1;
    /** A boss slipped it: it bursts where he was. */
    public static final byte EVADE = 2;
    /** Flew its full range and died out in mid-air. */
    public static final byte FADE = 3;
    /** Stopped by a shield (Protego, a ward, a layer shield): it ripples across it. */
    public static final byte SHIELD = 4;
    /** The magic took (a ward primed, a lock opened, a hidden thing revealed): a soft glow and rising motes. */
    public static final byte GLOW = 5;
    /** Water on fire: a billow of steam. */
    public static final byte STEAM = 6;

    /** Two beams met and destroyed each other. */
    public static final byte CLASH_MUTUAL = 0;
    /** The first beam (an Elder Wand's) shattered the second and flew on. */
    public static final byte CLASH_A_WINS = 1;
    /** The second beam shattered the first and flew on. */
    public static final byte CLASH_B_WINS = 2;

    /** A body struck by a spell: it reacts (tumbles, flinches, goes rigid, is drained green). */
    public static final byte REACT_HIT = 0;
    /** A disarmed wand flying out of the hand: it trails light. */
    public static final byte REACT_FLIGHT = 1;

    private SpellBeamFx() {
    }

    /**
     * A spell's mark at a spot with no beam to hang it on (a primed ward, a revealed chest, a ritual): drawn in the
     * spell's own style for everyone within 64 blocks.
     */
    public static void effectAt(net.minecraft.server.level.ServerLevel level, SpellType spell, Vec3 at, byte kind) {
        effectAt(level, spell, at, new Vec3(0.0, 1.0, 0.0), kind);
    }

    /** As above, its rings laid across {@code facing} (a ward wall's normal) instead of flat on the air. */
    public static void effectAt(net.minecraft.server.level.ServerLevel level, SpellType spell, Vec3 at, Vec3 facing,
                                byte kind) {
        Vec3 d = unit(facing);
        SpellPackets.BeamImpactPacket packet = new SpellPackets.BeamImpactPacket(-1, spell.getId(), at.x, at.y, at.z,
                (float) d.x, (float) d.y, (float) d.z, kind);
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < 64.0 * 64.0) {
                SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    /**
     * An NPC's beam entering the world: everyone watching the caster sees its gesture's wand trail and the beam
     * leaving its wand (players send theirs from the cast path). Low, so a cancelled beam (a locked duellist's)
     * sends nothing.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onBeamJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.isCanceled() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof com.hogwartsmagic.entity.SpellBeamEntity beam) || beam.isVisualOnly()) {
            return;
        }
        if (beam.getOwner() instanceof LivingEntity caster && !(caster instanceof net.minecraft.world.entity.player.Player)
                && beam.getSpellType() != SpellType.AVADA_KEDAVRA) {
            SpellPackets.CHANNEL.send(new SpellPackets.CastMotionPacket(caster.getId(), beam.getSpellType().getId()),
                    PacketDistributor.TRACKING_ENTITY.with(caster));
        }
    }

    /** Tell everyone watching {@code entity} how it reacts to {@code spell}, arriving along {@code dir}. */
    public static void react(Entity entity, SpellType spell, Vec3 dir, byte kind) {
        if (entity.level().isClientSide()) {
            return;
        }
        Vec3 d = unit(dir);
        SpellPackets.CHANNEL.send(new SpellPackets.HitReactionPacket(entity.getId(), spell.getId(),
                        (float) d.x, (float) d.y, (float) d.z, kind),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
    }

    public static void impact(Entity beam, SpellType spell, Vec3 at, byte kind) {
        impact(beam, spell, at, beam.getDeltaMovement(), kind);
    }

    /** As above, laid across {@code facing} (a ward wall's normal) rather than across the beam's flight. */
    public static void impact(Entity beam, SpellType spell, Vec3 at, Vec3 facing, byte kind) {
        if (beam.level().isClientSide()) {
            return;
        }
        Vec3 dir = unit(facing);
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
     * Two beams met at {@code at}. Sent before either is discarded, so it lands on the clients before they vanish
     * and the drawn beams end in the clash rather than striking on their own.
     */
    public static void clash(Entity a, SpellType spellA, Entity b, SpellType spellB, Vec3 at, byte outcome) {
        clash(a, spellA, b, spellB, at, a.getDeltaMovement(), b.getDeltaMovement(), outcome);
    }

    /** As above, with the directions the two spells were travelling given outright (a lock's casters stand still). */
    public static void clash(Entity a, SpellType spellA, Entity b, SpellType spellB, Vec3 at, Vec3 travelA,
                             Vec3 travelB, byte outcome) {
        if (a.level().isClientSide()) {
            return;
        }
        Vec3 dirA = unit(travelA);
        Vec3 dirB = unit(travelB);
        SpellPackets.CHANNEL.send(new SpellPackets.BeamClashPacket(a.getId(), spellA.getId(), b.getId(), spellB.getId(),
                        at.x, at.y, at.z, (float) dirA.x, (float) dirA.y, (float) dirA.z,
                        (float) dirB.x, (float) dirB.y, (float) dirB.z, outcome),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(a));
        // And everyone tracking the other side; a client that gets both draws it once.
        SpellPackets.CHANNEL.send(new SpellPackets.BeamClashPacket(a.getId(), spellA.getId(), b.getId(), spellB.getId(),
                        at.x, at.y, at.z, (float) dirA.x, (float) dirA.y, (float) dirA.z,
                        (float) dirB.x, (float) dirB.y, (float) dirB.z, outcome),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(b));
        float pitch = (outcome == CLASH_MUTUAL ? 0.95f : 1.15f) + a.level().getRandom().nextFloat() * 0.1f;
        // The grinding lock now; each client plays the boom when the blast goes off.
        a.level().playSound(null, at.x, at.y, at.z, ModSounds.SPELL_CLASH_GRIND.get(), SoundSource.PLAYERS, 1.4f, pitch);
    }

    private static Vec3 unit(Vec3 v) {
        return v.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : v.normalize();
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
