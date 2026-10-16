package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.spell.ProtegoEffect;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
     * Protego: the real spell's shield sparkle ({@link ProtegoEffect#emitShieldParticles}) around the caster,
     * held for 40 ticks. The caster stands one wand-hand height below {@code hand}.
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
            // A wider, taller sphere than a bare player's, and thrice the sparkle, so the dome reads from across a hall.
            for (int i = 0; i < 3; i++) {
                ProtegoEffect.emitShieldParticles(level, feet, 2.2, 2.4, false);
            }
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, feet.x, feet.y + 1.2, feet.z, 0, 0, 0);
            }
            return ++age < PROTEGO_TICKS;
        }
    }

    private static Vec3 randomUnit(ClientLevel level) {
        double y = level.random.nextDouble() * 2 - 1;
        double theta = level.random.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - y * y);
        return new Vec3(s * Math.cos(theta), y, s * Math.sin(theta));
    }
}
