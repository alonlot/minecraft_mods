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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spells cast by scene actors, drawn on the viewer's client level and nowhere else. A beam spell is a client-only
 * {@link SpellBeamEntity} marked {@code setVisualOnly()}: it flies from the wand to the target, strikes and
 * vanishes, hurting nothing, drawn by hand like every real beam. The spells that are not a beam in the memory are
 * drawn by hand too ({@code SpellBeamClientState} scenery): Lumos a held orb, Protego a dome, Revelio its scanning
 * strike, the Patronus and Fiendfyre held streams; Morsmordre is the Dark Mark. {@link SceneNames#SPELL_VISUALS} is exactly this class's
 * {@link #supported()} set, so the validator names any other spell before a memory is ever played.
 */
public final class SpellVisuals {
    /** Beam speed in blocks per tick, as the real wand fires it. */
    static final float BEAM_SPEED = 1.55f;
    private static final int LUMOS_TICKS = 60;
    private static final int PROTEGO_TICKS = 40;
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
    /** The weak shield hangs at most this far ahead of the wand. */
    private static final double WISP_MAX_REACH = 6.0;
    /** With no target the Patronus stream aims this far ahead. */
    private static final double PATRONUS_DEFAULT_REACH = 10.0;
    /** The stream never reaches farther than this, however distant the target. */
    private static final double PATRONUS_MAX_REACH = 40.0;
    /** Fiendfyre roars out of the wand for 1.5 s... */
    static final int FIENDFYRE_TICKS = 30;
    /** ...toward the target, this far ahead with none, and never farther than the max. */
    private static final double FIENDFYRE_DEFAULT_REACH = 8.0;
    private static final double FIENDFYRE_MAX_REACH = 14.0;
    /** The spell beam renders this far above its entity position; start low so the visible beam leaves the wand. */
    private static final double BEAM_RENDER_LIFT = 0.2;

    private static final List<SpellBeamEntity> BEAMS = new ArrayList<>();

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
            beam(level, from, to, SpellType.fromId(spellId), BEAM_SPEED, !recordedScene());
        } else if ("revelio".equals(spellId)) {
            SpellType spell = SpellType.REVELIO;
            sfx(spell.getSoundEvent(), from, 1.0f, 1.0f);
            // Drawn by hand like every other spell: Revelio's scanning strike where it was aimed.
            com.hogwartsmagic.client.SpellBeamClientState.addStrike(to, new Vec3(0.0, 1.0, 0.0), spell,
                    com.hogwartsmagic.spell.SpellBeamFx.HIT);
        } else if ("lumos".equals(spellId)) {
            sfx(SpellType.LUMOS.getSoundEvent(), from, 1.0f, 1.0f);
            com.hogwartsmagic.client.SpellBeamClientState.addScenery(
                    com.hogwartsmagic.client.SpellBeamClientState.SCENERY_ORB, from, from, SpellType.LUMOS, LUMOS_TICKS);
        } else if ("protego".equals(spellId)) {
            sfx(SpellType.PROTEGO.getSoundEvent(), from, 1.0f, 1.0f);
            com.hogwartsmagic.client.SpellBeamClientState.addScenery(
                    com.hogwartsmagic.client.SpellBeamClientState.SCENERY_DOME, from, from, SpellType.PROTEGO,
                    PROTEGO_TICKS);
        } else if ("morsmordre".equals(spellId)) {
            sfx(SpellType.MORSMORDRE.getSoundEvent(), from, 1.0f, 1.0f);
            EffectVisuals.darkMark(level, to, MORSMORDRE_TICKS);
        } else if ("expecto_patronum".equals(spellId)) {
            sfx(ModSounds.SPELL_EXPECTO_PATRONUM.get(), from, 1.0f, 1.0f);
            com.hogwartsmagic.client.SpellBeamClientState.addScenery(
                    com.hogwartsmagic.client.SpellBeamClientState.SCENERY_HELD, from,
                    clampReach(from, aim(from, to, PATRONUS_DEFAULT_REACH), PATRONUS_MAX_REACH),
                    SpellType.EXPECTO_PATRONUM, PATRONUS_TICKS);
        } else if ("patronus_wisp".equals(spellId)) {
            sfx(ModSounds.SPELL_EXPECTO_PATRONUM.get(), from, 0.5f, 0.85f);
            com.hogwartsmagic.client.SpellBeamClientState.addScenery(
                    com.hogwartsmagic.client.SpellBeamClientState.SCENERY_WISP, from,
                    clampReach(from, aim(from, to, 5.0), WISP_MAX_REACH), SpellType.EXPECTO_PATRONUM, WISP_TICKS);
        } else if ("fiendfyre".equals(spellId)) {
            sfx(ModSounds.SPELL_FIENDFYRE.get(), from, 1.4f, 0.9f);
            com.hogwartsmagic.client.SpellBeamClientState.addScenery(
                    com.hogwartsmagic.client.SpellBeamClientState.SCENERY_HELD, from,
                    clampReach(from, aim(from, to, FIENDFYRE_DEFAULT_REACH), FIENDFYRE_MAX_REACH),
                    SpellType.FIENDFYRE, FIENDFYRE_TICKS);
        }
    }

    /** {@code to}, pulled in to at most {@code max} blocks from {@code from}. */
    private static Vec3 clampReach(Vec3 from, Vec3 to, double max) {
        Vec3 span = to.subtract(from);
        double length = span.length();
        return length <= max || length < 1.0E-6 ? to : from.add(span.scale(max / length));
    }

    /** A recorded memory's own sounds are in the recording, so its casts draw without theirs (they would double). */
    private static boolean recordedScene() {
        return PensieveClientState.recording() != null;
    }

    private static void sfx(net.minecraft.sounds.SoundEvent event, Vec3 at, float volume, float pitch) {
        if (!recordedScene()) MemorySounds.play(event, at, volume, pitch);
    }

    // ---- Beams ------------------------------------------------------------------------------------------------

    /**
     * A recorded beam: the same flight as {@link #cast}'s, but over the {@code ticks} it really took (speed
     * {@code dist / max(1, ticks)}), and without the cast sound, which the recording already plays. A spell that is
     * not a beam draws nothing.
     */
    public static void beamTimed(ClientLevel level, Vec3 from, Vec3 to, String spellId, int ticks) {
        if (level == null || from == null || to == null || spellId == null) return;
        if (!SceneNames.SPELL_BEAMS.contains(spellId)) return;
        beam(level, from, to, SpellType.fromId(spellId), (float) (to.subtract(from).length() / Math.max(1, ticks)),
                false);
    }

    private static void beam(ClientLevel level, Vec3 from, Vec3 to, SpellType spell) {
        beam(level, from, to, spell, BEAM_SPEED, true);
    }

    private static void beam(ClientLevel level, Vec3 from, Vec3 to, SpellType spell, float speed, boolean sound) {
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist < 0.05) return;
        SpellBeamEntity beam = new SpellBeamEntity(ModEntities.SPELL_BEAM.get(), level);
        beam.setVisualSpell(spell);
        beam.setVisualOnly((int) Math.ceil(dist / speed));
        beam.setId(MemoryEvents.nextEntityId());
        beam.setPos(from.x, from.y - BEAM_RENDER_LIFT, from.z);
        beam.shoot(delta.x, delta.y, delta.z, speed, 0.0f);
        // Without this the first frame interpolates from the world origin: a one-frame streak across the sky.
        beam.setOldPosAndRot();
        level.addEntity(beam);
        BEAMS.add(beam);
        if (sound) MemorySounds.play(spell.getSoundEvent(), from, 1.0f, 1.0f);
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    /** Drop beams that have landed. The other spells are drawn by SpellBeamClientState and end on their own. */
    static void tick(ClientLevel level) {
        BEAMS.removeIf(SpellBeamEntity::isRemoved);
    }

    /** Remove every beam (the memory ended). */
    static void clear() {
        for (SpellBeamEntity b : BEAMS) b.discard();
        BEAMS.clear();
    }

    /** The vector from the wand tip toward {@code to}, or {@code fallback} blocks straight ahead (+Z) if they coincide. */
    private static Vec3 aim(Vec3 from, Vec3 to, double fallback) {
        Vec3 d = to.subtract(from);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, fallback) : d;
    }
}
