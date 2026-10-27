package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.client.ProtegoShieldRenderer;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Spells cast by scene actors, drawn on the viewer's client level and nowhere else. A beam spell is a client-only
 * {@link SpellBeamEntity} marked {@code setVisualOnly()}: it flies from the wand to the target, shows its impact
 * particles and vanishes, hurting nothing. The four spells that are not a beam in the memory (revelio, lumos,
 * protego, morsmordre) have the small adapters below. {@link SceneNames#SPELL_VISUALS} is exactly this class's
 * {@link #supported()} set, so the validator names any other spell before a memory is ever played.
 */
public final class SpellVisuals {
    /** Beam speed in blocks per tick, as the real wand fires it. */
    static final float BEAM_SPEED = 1.55f;
    private static final int LUMOS_TICKS = 60;
    private static final int PROTEGO_TICKS = 40;
    private static final int REVELIO_TICKS = 16;
    private static final int MORSMORDRE_TICKS = 120;
    /** The full Patronus cast pours for 2 s. */
    static final int PATRONUS_TICKS = 40;
    /** The weak Patronus: the mist bursts out and spreads into a shield over 6 ticks... */
    static final int WISP_BLOOM_TICKS = 6;
    /** ...holds, flickering, for 2 s... */
    static final int WISP_HOLD_TICKS = 40;
    /** ...then breaks up and fades over 1 s. */
    static final int WISP_FADE_TICKS = 20;
    static final int WISP_TICKS = WISP_BLOOM_TICKS + WISP_HOLD_TICKS + WISP_FADE_TICKS;
    /** The weak shield hangs this far ahead of the wand: the aim distance, held to 4-6 blocks. */
    private static final double WISP_MIN_REACH = 4.0;
    private static final double WISP_MAX_REACH = 6.0;
    /** The shield's radius: a hazy disc about three blocks across. */
    private static final double WISP_SHIELD_RADIUS = 1.6;
    /** With no target the Patronus stream aims this far ahead. */
    private static final double PATRONUS_DEFAULT_REACH = 10.0;
    /** The stream never reaches farther than this, however distant the target. */
    private static final double PATRONUS_MAX_REACH = 40.0;
    /** The silver-white of the Patronus (#DDEEFF). */
    private static final DustParticleOptions SILVER_MIST =
            new DustParticleOptions(new Vector3f(0xDD / 255f, 0xEE / 255f, 1f), 1.6f);
    /** Fiendfyre roars out of the wand for 1.5 s... */
    static final int FIENDFYRE_TICKS = 30;
    /** ...toward the target, this far ahead with none, and never farther than the max. */
    private static final double FIENDFYRE_DEFAULT_REACH = 8.0;
    private static final double FIENDFYRE_MAX_REACH = 14.0;
    /** The spell beam renders this far above its entity position; start low so the visible beam leaves the wand. */
    private static final double BEAM_RENDER_LIFT = 0.2;

    private static final List<SpellBeamEntity> BEAMS = new ArrayList<>();
    private static final List<Active> ACTIVE = new ArrayList<>();

    private SpellVisuals() {
    }

    /** The spell ids {@link #cast} can draw. */
    public static Set<String> supported() {
        return SceneNames.SPELL_VISUALS;
    }

    /**
     * Draw {@code spellId} cast from {@code from} (the wand hand) toward {@code to}. An unsupported id draws
     * nothing. Beam spells also play their cast sound through {@link MemorySounds}.
     */
    public static void cast(ClientLevel level, Vec3 from, Vec3 to, String spellId) {
        if (level == null || from == null || to == null || spellId == null) return;
        if (SceneNames.SPELL_BEAMS.contains(spellId)) {
            beam(level, from, to, SpellType.fromId(spellId));
        } else if ("revelio".equals(spellId)) {
            SpellType spell = SpellType.REVELIO;
            MemorySounds.play(spell.getSoundEvent(), from, 1.0f, 1.0f);
            ACTIVE.add(new RevelioPulse(spell, to));
        } else if ("lumos".equals(spellId)) {
            MemorySounds.play(SpellType.LUMOS.getSoundEvent(), from, 1.0f, 1.0f);
            ACTIVE.add(new LumosOrb(from));
        } else if ("protego".equals(spellId)) {
            MemorySounds.play(SpellType.PROTEGO.getSoundEvent(), from, 1.0f, 1.0f);
            ACTIVE.add(new ProtegoDome(from));
        } else if ("morsmordre".equals(spellId)) {
            MemorySounds.play(SpellType.MORSMORDRE.getSoundEvent(), from, 1.0f, 1.0f);
            EffectVisuals.darkMark(level, to, MORSMORDRE_TICKS);
        } else if ("expecto_patronum".equals(spellId)) {
            MemorySounds.play(ModSounds.SPELL_EXPECTO_PATRONUM.get(), from, 1.0f, 1.0f);
            ACTIVE.add(new PatronusStream(from, aim(from, to, PATRONUS_DEFAULT_REACH), PATRONUS_MAX_REACH, false));
        } else if ("patronus_wisp".equals(spellId)) {
            MemorySounds.play(ModSounds.SPELL_EXPECTO_PATRONUM.get(), from, 0.5f, 0.85f);
            ACTIVE.add(new PatronusStream(from, aim(from, to, 5.0), WISP_MAX_REACH, true));
        } else if ("fiendfyre".equals(spellId)) {
            MemorySounds.play(ModSounds.SPELL_FIENDFYRE.get(), from, 1.4f, 0.9f);
            ACTIVE.add(new FiendfyreGout(from, aim(from, to, FIENDFYRE_DEFAULT_REACH)));
        }
    }

    // ---- Beams ------------------------------------------------------------------------------------------------

    private static void beam(ClientLevel level, Vec3 from, Vec3 to, SpellType spell) {
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist < 0.05) return;
        SpellBeamEntity beam = new SpellBeamEntity(ModEntities.SPELL_BEAM.get(), level);
        beam.setVisualSpell(spell);
        beam.setVisualOnly((int) Math.ceil(dist / BEAM_SPEED));
        beam.setId(MemoryEvents.nextEntityId());
        beam.setPos(from.x, from.y - BEAM_RENDER_LIFT, from.z);
        beam.shoot(delta.x, delta.y, delta.z, BEAM_SPEED, 0.0f);
        // Without this the first frame interpolates from the world origin: a one-frame streak across the sky.
        beam.setOldPosAndRot();
        level.addEntity(beam);
        BEAMS.add(beam);
        MemorySounds.play(spell.getSoundEvent(), from, 1.0f, 1.0f);
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    /** Advance every adapter one client tick. */
    static void tick(ClientLevel level) {
        BEAMS.removeIf(SpellBeamEntity::isRemoved);
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            if (!it.next().tick(level)) it.remove();
        }
    }

    /** Remove every beam and stop every adapter (the memory ended). */
    static void clear() {
        ACTIVE.clear();
        for (SpellBeamEntity b : BEAMS) b.discard();
        BEAMS.clear();
    }

    private interface Active {
        /** @return false once finished */
        boolean tick(ClientLevel level);
    }

    /** Revelio: a cyan sphere of light sweeps outward from the target, glinting off the block faces it crosses. */
    private static final class RevelioPulse implements Active {
        private final SpellType spell;
        private final Vec3 centre;
        private int age;

        RevelioPulse(SpellType spell, Vec3 centre) {
            this.spell = spell;
            this.centre = centre;
        }

        @Override
        public boolean tick(ClientLevel level) {
            double r = 0.6 + age * 0.4;
            for (int i = 0; i < 36; i++) {
                Vec3 d = randomUnit(level);
                level.addParticle(spell.getDustParticle(), centre.x + d.x * r, centre.y + d.y * r, centre.z + d.z * r, 0, 0, 0);
                if (i % 6 == 0) {
                    level.addParticle(ParticleTypes.END_ROD, centre.x + d.x * r, centre.y + d.y * r, centre.z + d.z * r,
                            d.x * 0.02, d.y * 0.02, d.z * 0.02);
                }
            }
            // The reveal shimmer: wherever the wavefront touches a block with an open face, that face glints.
            for (int i = 0; i < 18; i++) {
                Vec3 d = randomUnit(level);
                double rr = r - 0.6 + level.random.nextDouble() * 1.2;
                BlockPos p = BlockPos.containing(centre.x + d.x * rr, centre.y + d.y * rr, centre.z + d.z * rr);
                BlockState state = level.getBlockState(p);
                if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                for (Direction face : Direction.values()) {
                    if (!level.getBlockState(p.relative(face)).isAir()) continue;
                    level.addParticle(ParticleTypes.GLOW,
                            p.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? level.random.nextDouble() - 0.5 : 0),
                            p.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? level.random.nextDouble() - 0.5 : 0),
                            p.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? level.random.nextDouble() - 0.5 : 0),
                            0, 0.01, 0);
                    break;
                }
            }
            return ++age < REVELIO_TICKS;
        }
    }

    /** Lumos: a ball of white light hanging at the wand tip. */
    private static final class LumosOrb implements Active {
        private final Vec3 tip;
        private int age;

        LumosOrb(Vec3 tip) {
            this.tip = tip;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 0, 0, 0);
            }
            double fade = Math.min(1.0, (LUMOS_TICKS - age) / 10.0);
            int n = (int) Math.ceil(3 * fade);
            for (int i = 0; i < n; i++) {
                Vec3 d = randomUnit(level);
                double r = 0.05 + level.random.nextDouble() * 0.1;
                level.addParticle(ParticleTypes.END_ROD, tip.x + d.x * r, tip.y + d.y * r, tip.z + d.z * r,
                        d.x * 0.005, d.y * 0.005 + 0.003, d.z * 0.005);
            }
            if (age % 2 == 0) {
                level.addParticle(ParticleTypes.GLOW, tip.x, tip.y, tip.z, 0, 0, 0);
            }
            if (age % 4 == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
                        (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.1,
                        (level.random.nextDouble() - 0.5) * 0.1);
            }
            return ++age < LUMOS_TICKS;
        }
    }

    /**
     * Protego: the real spell's bubble ({@link ProtegoShieldRenderer}) around the caster, held for 40 ticks. The caster stands one wand-hand height below {@code hand}.
     */
    private static final class ProtegoDome implements Active {
        private static final double HAND_ABOVE_FEET = 1.3;
        private final Vec3 feet;
        private int age;

        ProtegoDome(Vec3 hand) {
            this.feet = hand.subtract(0, HAND_ABOVE_FEET, 0);
        }

        @Override
        public boolean tick(ClientLevel level) {
            ProtegoShieldRenderer.holdAt(this, feet, age, PROTEGO_TICKS);
            return ++age < PROTEGO_TICKS;
        }
    }

    /** The vector from the wand tip toward {@code to}, or {@code fallback} blocks straight ahead (+Z) if they coincide. */
    private static Vec3 aim(Vec3 from, Vec3 to, double fallback) {
        Vec3 d = to.subtract(from);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, fallback) : d;
    }

    /**
     * Expecto Patronum. The full cast ({@code wisp} false) pours a widening cone of silver-white mist and a tight
     * bright core from the wand tip toward the target for {@link #PATRONUS_TICKS}, with a flare at the tip. The
     * wisp ({@code wisp} true) is the real-but-weak cast: a silver mist bursts from the wand and spreads into a
     * hazy disc of glowing mist 4-6 blocks ahead, facing the target, over {@link #WISP_BLOOM_TICKS}; it holds,
     * flickering, for {@link #WISP_HOLD_TICKS}, then breaks up and fades over {@link #WISP_FADE_TICKS}. No stag,
     * and far smaller than the full cast. Its phases follow the tick count alone. All emissive (end rod, glow and
     * silver dust particles).
     */
    private static final class PatronusStream implements Active {
        private final Vec3 tip;
        private final Vec3 dir;
        private final double reach;
        private final boolean wisp;
        private final Vec3 u;
        private final Vec3 v;
        private final int total;
        private int age;

        /** {@code toTarget}: tip to target (its length is the aim distance); {@code maxReach} caps the stream. */
        PatronusStream(Vec3 tip, Vec3 toTarget, double maxReach, boolean wisp) {
            this.tip = tip;
            this.wisp = wisp;
            this.reach = Math.min(toTarget.length(), maxReach);
            this.dir = toTarget.normalize();
            Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            this.u = dir.cross(up).normalize();
            this.v = dir.cross(u).normalize();
            this.total = wisp ? WISP_TICKS : PATRONUS_TICKS;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (wisp) {
                tickWisp(level);
            } else {
                tickStream(level);
            }
            return ++age < total;
        }

        private void tickStream(ClientLevel level) {
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 0, 0, 0);
            }
            // Rushes out over the first 6 ticks, holds, then thins out over the last 8.
            double front = reach * Math.min(1.0, (age + 1) / 6.0);
            double fade = Math.min(1.0, (total - age) / 8.0);
            int cone = (int) Math.ceil(16 * fade);
            for (int i = 0; i < cone; i++) {
                double s = level.random.nextDouble() * front;
                put(level, ParticleTypes.END_ROD, s, 0.05 + s * 0.13, 0.12);
            }
            for (int i = 0; i < (int) Math.ceil(5 * fade); i++) {
                put(level, ParticleTypes.END_ROD, level.random.nextDouble() * front, 0.03, 0.2);
            }
            for (int i = 0; i < (int) Math.ceil(4 * fade); i++) {
                put(level, SILVER_MIST, level.random.nextDouble() * front, 0.1 + level.random.nextDouble() * 0.25, 0.04);
            }
            for (int i = 0; i < 2; i++) {
                put(level, ParticleTypes.GLOW, level.random.nextDouble() * front, 0.05, 0.02);
            }
            // The flare at the wand tip.
            level.addParticle(ParticleTypes.GLOW, tip.x, tip.y, tip.z, 0, 0, 0);
            for (int i = 0; i < 2; i++) {
                Vec3 d = randomUnit(level);
                level.addParticle(ParticleTypes.END_ROD, tip.x + d.x * 0.08, tip.y + d.y * 0.08, tip.z + d.z * 0.08,
                        d.x * 0.03, d.y * 0.03, d.z * 0.03);
            }
        }

        private void tickWisp(ClientLevel level) {
            double dist = Math.max(WISP_MIN_REACH, reach);
            double radius = WISP_SHIELD_RADIUS;
            if (age == 0) {
                // The burst at the wand tip: a puff of motes thrown mostly forward.
                for (int i = 0; i < 12; i++) {
                    Vec3 d = randomUnit(level).add(dir.scale(1.2)).normalize();
                    level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, d.x * 0.12, d.y * 0.12, d.z * 0.12);
                }
                level.addParticle(ParticleTypes.GLOW, tip.x, tip.y, tip.z, 0, 0, 0);
            }
            if (age < WISP_BLOOM_TICKS) {
                // The mist rushes out and opens into the disc.
                double f = (age + 1) / (double) WISP_BLOOM_TICKS;
                double front = dist * f;
                for (int i = 0; i < 8; i++) {
                    double s = level.random.nextDouble() * front;
                    put(level, i % 3 == 0 ? ParticleTypes.END_ROD : SILVER_MIST, s, 0.05 + (s / dist) * radius * f, 0.05);
                }
                disc(level, dist, radius * f, 6, 0.0);
                return;
            }
            int hold = age - WISP_BLOOM_TICKS;
            if (hold < WISP_HOLD_TICKS) {
                // Holds, flickering: the density wavers and the edge breathes, but it never drops out.
                double flicker = 0.7 + 0.3 * Math.sin(age * 1.7) * Math.sin(age * 0.53 + 0.9);
                int n = (int) Math.round(10 * flicker);
                disc(level, dist, radius * (0.92 + 0.08 * flicker), n, 0.0);
                // A thin thread still feeding it from the wand.
                for (int i = 0; i < 2; i++) {
                    put(level, SILVER_MIST, level.random.nextDouble() * dist, 0.04, 0.03);
                }
                if (age % 2 == 0) {
                    level.addParticle(ParticleTypes.GLOW, tip.x, tip.y, tip.z, 0, 0, 0);
                }
                return;
            }
            // Breaks up: fewer motes, scattered wider, drifting outward and down, with gaps.
            double f = 1.0 - (hold - WISP_HOLD_TICKS) / (double) WISP_FADE_TICKS;
            if (Math.sin(age * 2.3) < -0.6) return;
            int n = (int) Math.ceil(7 * f);
            disc(level, dist, radius * (1.0 + 0.6 * (1.0 - f)), n, 0.03 * (1.0 - f));
        }

        /**
         * {@code n} motes in the shield disc {@code dist} ahead (a thin slab facing the target), mostly silver mist
         * with end rods at the rim; {@code scatter} is an outward and downward drift for the break-up.
         */
        private void disc(ClientLevel level, double dist, double radius, int n, double scatter) {
            Vec3 c = tip.add(dir.scale(dist));
            for (int i = 0; i < n; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(level.random.nextDouble());
                double r = radius * rr;
                double ca = Math.cos(a);
                double sa = Math.sin(a);
                double depth = (level.random.nextDouble() - 0.5) * 0.3;
                double x = c.x + dir.x * depth + (u.x * ca + v.x * sa) * r;
                double y = c.y + dir.y * depth + (u.y * ca + v.y * sa) * r;
                double z = c.z + dir.z * depth + (u.z * ca + v.z * sa) * r;
                double ox = (u.x * ca + v.x * sa) * scatter;
                double oy = (u.y * ca + v.y * sa) * scatter - scatter * 0.5;
                double oz = (u.z * ca + v.z * sa) * scatter;
                // End rods (long-lived) only at the rim and only while it holds, so the break-up really fades.
                boolean rod = scatter == 0.0 && (rr > 0.9 || level.random.nextInt(8) == 0);
                ParticleOptions type = rod ? ParticleTypes.END_ROD : SILVER_MIST;
                level.addParticle(type, x, y, z, ox + dir.x * 0.005, oy + dir.y * 0.005, oz + dir.z * 0.005);
            }
        }

        /** One particle {@code s} blocks down the stream, scattered {@code spread} sideways, drifting forward. */
        private void put(ClientLevel level, ParticleOptions type, double s, double spread, double speed) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = spread * Math.sqrt(level.random.nextDouble());
            double ca = Math.cos(a) * r;
            double sa = Math.sin(a) * r;
            level.addParticle(type, tip.x + dir.x * s + u.x * ca + v.x * sa, tip.y + dir.y * s + u.y * ca + v.y * sa,
                    tip.z + dir.z * s + u.z * ca + v.z * sa, dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }

    /**
     * {@code fiendfyre}: the cursed fire bursting out of the wand. A flash and a ball of flame at the tip, then a
     * widening, writhing gout of flame, lava sparks and black smoke rushes out toward the target over 5 ticks, roars
     * for {@link #FIENDFYRE_TICKS} and gutters out over the last 8. The fire that fills the area is the separate
     * {@code fiendfyre_rings} effect.
     */
    private static final class FiendfyreGout implements Active {
        private final Vec3 tip;
        private final Vec3 dir;
        private final double reach;
        private final Vec3 u;
        private final Vec3 v;
        private int age;

        FiendfyreGout(Vec3 tip, Vec3 toTarget) {
            this.tip = tip;
            this.reach = Math.min(toTarget.length(), FIENDFYRE_MAX_REACH);
            this.dir = toTarget.normalize();
            Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            this.u = dir.cross(up).normalize();
            this.v = dir.cross(u).normalize();
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 0, 0, 0);
                for (int i = 0; i < 30; i++) {
                    Vec3 d = randomUnit(level);
                    level.addParticle(ParticleTypes.FLAME, tip.x, tip.y, tip.z, d.x * 0.18, d.y * 0.18, d.z * 0.18);
                }
            }
            double front = reach * Math.min(1.0, (age + 1) / 5.0);
            double fade = Math.min(1.0, (FIENDFYRE_TICKS - age) / 8.0);
            for (int i = 0; i < (int) Math.ceil(34 * fade); i++) {
                double s = level.random.nextDouble() * front;
                // The gout widens and writhes along its length.
                double wobble = Math.sin(s * 1.3 - age * 0.9) * 0.25 * s / Math.max(1.0, reach);
                put(level, i % 7 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, s, 0.1 + s * 0.22,
                        wobble, 0.06 + level.random.nextDouble() * 0.08);
            }
            for (int i = 0; i < (int) Math.ceil(4 * fade); i++) {
                put(level, ParticleTypes.LAVA, level.random.nextDouble() * front, 0.3, 0.0, 0.0);
            }
            for (int i = 0; i < (int) Math.ceil(5 * fade); i++) {
                put(level, ParticleTypes.LARGE_SMOKE, level.random.nextDouble() * front, 0.2 + front * 0.15, 0.0, 0.03);
            }
            level.addParticle(ParticleTypes.FLAME, tip.x, tip.y, tip.z, 0, 0.01, 0);
            return ++age < FIENDFYRE_TICKS;
        }

        /** One particle {@code s} blocks down the gout, scattered {@code spread} sideways, shifted along u by wobble. */
        private void put(ClientLevel level, ParticleOptions type, double s, double spread, double wobble, double speed) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = spread * Math.sqrt(level.random.nextDouble());
            double ca = Math.cos(a) * r + wobble;
            double sa = Math.sin(a) * r;
            level.addParticle(type, tip.x + dir.x * s + u.x * ca + v.x * sa, tip.y + dir.y * s + u.y * ca + v.y * sa,
                    tip.z + dir.z * s + u.z * ca + v.z * sa, dir.x * speed, dir.y * speed + 0.01, dir.z * speed);
        }
    }

    private static Vec3 randomUnit(ClientLevel level) {
        double y = level.random.nextDouble() * 2 - 1;
        double theta = level.random.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - y * y);
        return new Vec3(s * Math.cos(theta), y, s * Math.sin(theta));
    }
}
