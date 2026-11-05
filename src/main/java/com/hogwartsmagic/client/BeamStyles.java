package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

import static com.hogwartsmagic.client.BeamStyle.JET;
import static com.hogwartsmagic.client.BeamStyle.WAKE_EMBERS;
import static com.hogwartsmagic.client.BeamStyle.WAKE_MIST;
import static com.hogwartsmagic.client.BeamStyle.WAKE_MOTES;
import static com.hogwartsmagic.client.BeamStyle.WAKE_SMOKE;
import static com.hogwartsmagic.client.BeamStyle.WAKE_SPARKS;

/**
 * Every spell's beam, each its own. The families share a language (curses are jets with dark sheaths and
 * crackle, fire trails cinders and smoke, charms are clean bolts, the light spells shed motes) but no two spells
 * look the same: Accio's beads run back to the wand, Depulso sheds shockwaves, Serpensortia slithers, Sectumsempra
 * is a thin dark blade, Confundo wobbles, Riddikulus leaves confetti.
 *
 * <p>A map, not a switch: switch-on-enum has failed to load in this mod's render code before.
 */
public final class BeamStyles {
    private static final Map<SpellType, BeamStyle> STYLES = new EnumMap<>(SpellType.class);

    static {
        // ---- the Unforgivables and dark curses: dark sheaths; most are full jets, the Killing Curse a long bolt ----
        STYLES.put(SpellType.AVADA_KEDAVRA, new BeamStyle().colours(0xCCFFD1, 0x38FF57, 0x38FF57).tail(9.0f)
                .jitter(0.045f).writhe(0.035f, 1.3f, 0.8f).darkSheath(0.34f, 0.5f).crackle(3, 0.14f)
                .head(0.75f, 0.26f, 0.45f, true).wake(WAKE_SMOKE, 0.7f, 22.0f, 0.22f, 0.32f, 0.035f)
                .afterglow(10.0f).strike(1.0f, 1.0f, 0.55f).darkBurst().ring(4.0f, false, false).arcs(7, 0.18f)
                .screenFlash(1.0f));
        STYLES.put(SpellType.CRUCIO, new BeamStyle().colours(0xFFD0C8, 0xFF1A1A, 0xC00010).tail(JET)
                .jitter(0.12f).writhe(0.02f, 2.0f, 1.5f).flicker(0.3f).darkSheath(0.30f, 0.45f).crackle(4, 0.18f)
                .head(0.6f, 0.22f, 0.5f, true).wake(WAKE_SMOKE, 0.9f, 16.0f, 0.18f, 0.22f, 0.02f)
                .afterglow(8.0f).strike(0.9f, 0.8f, 0.5f).darkBurst().arcs(10, 0.3f).screenFlash(0.5f));
        // Puppet strings: pale gold-chartreuse threads, barely a beam at all.
        STYLES.put(SpellType.IMPERIO, new BeamStyle().colours(0xFFFBE0, 0xE8F09A, 0xC8D860).tail(JET)
                .alphas(0.35f, 0.18f).jitter(0.0f).writhe(0.08f, 0.7f, 0.5f).flicker(0.04f).twist(5, 0.08f, 4.0f, 0.3f)
                .beads(4, 0.35f, 0.1f).wake(WAKE_MIST, 0.8f, 26.0f, 0.3f, 0.1f, 0.01f).afterglow(9.0f)
                .strike(0.9f, 0.5f, 0.5f).ring(3.0f, false, false).mistBurst());
        STYLES.put(SpellType.MORSMORDRE, new BeamStyle().colours(0x9CFFB0, 0x1A8A30, 0x0A5018).tail(JET)
                .jitter(0.03f).writhe(0.05f, 0.8f, 0.6f).darkSheath(0.6f, 0.6f).head(0.9f, 0.3f, 0.4f, true)
                .wake(WAKE_SMOKE, 0.6f, 30.0f, 0.35f, 0.45f, 0.03f).wakeShape(0.9f, 0.35f).afterglow(12.0f)
                .strike(1.2f, 0.5f, 0.4f).darkBurst());
        // An all-but-invisible blade: a dark streak with a thread of red in it, leaving three cuts.
        STYLES.put(SpellType.SECTUMSEMPRA, new BeamStyle().colours(0xFFB0B0, 0x9A0010, 0x500008).tail(2.5f)
                .widths(0.02f, 0.05f, 0.16f).alphas(0.25f, 0.04f).jitter(0.0f).writhe(0.0f, 1.0f, 1.0f).flicker(0.05f)
                .darkSheath(0.3f, 0.8f).head(0.3f, 0.12f, 0.3f, true)
                .wake(WAKE_SPARKS, 0.45f, 12.0f, 0.02f, 0.9f, 0.0f).afterglow(3.0f).strike(0.8f, 0.4f, 0.25f)
                .darkBurst().arcs(3, 0.0f).parallelSlashes());
        STYLES.put(SpellType.DOLOHOV_CURSE, new BeamStyle().colours(0xF6C8FF, 0xC020D0, 0x8000A0).tail(7.0f)
                .jitter(0.04f).writhe(0.14f, 2.0f, 3.0f).darkSheath(0.28f, 0.4f).crackle(1, 0.12f)
                .head(0.7f, 0.26f, 0.5f, true).wake(WAKE_MIST, 0.6f, 18.0f, 0.25f, 0.14f, 0.01f)
                .strike(0.9f, 0.8f, 0.5f).darkBurst().arcs(8, 0.22f).screenFlash(0.3f));
        STYLES.put(SpellType.SERPENSORTIA, new BeamStyle().colours(0xB0FFB0, 0x1A8A1A, 0x0A5A0A).tail(8.0f)
                .jitter(0.0f).writhe(0.18f, 1.4f, 2.2f).darkSheath(0.24f, 0.5f).head(0.6f, 0.22f, 0.45f, true)
                .wake(WAKE_SMOKE, 0.8f, 18.0f, 0.18f, 0.2f, 0.02f).strike(0.9f, 0.5f, 0.4f).darkBurst()
                .ring(2.0f, false, true));
        STYLES.put(SpellType.TENEBRIS_FLIGHT, new BeamStyle().colours(0x707070, 0x303030, 0x202020).tail(6.0f)
                .alphas(0.5f, 0.05f).jitter(0.02f).darkSheath(0.5f, 0.7f).head(0.8f, 0.1f, 0.2f, true)
                .wake(WAKE_SMOKE, 0.5f, 26.0f, 0.3f, 0.45f, 0.03f).wakeShape(0.8f, 0.3f).strike(1.0f, 0.2f, 0.1f)
                .darkBurst());
        STYLES.put(SpellType.APPARITION, new BeamStyle().colours(0xF4E8FF, 0x9B5BD6, 0x6A2CB0).tail(4.0f)
                .writhe(0.05f, 1.0f, 1.2f).darkSheath(0.2f, 0.3f).wake(WAKE_MIST, 0.6f, 16.0f, 0.25f, 0.12f, 0.0f)
                .strike(0.9f, 0.6f, 0.5f).mistBurst());

        // ---- fire ----
        STYLES.put(SpellType.CONFRINGO, new BeamStyle().colours(0xFFF2C0, 0xFF8A1A, 0xFF5000).tail(3.5f)
                .widths(0.05f, 0.16f, 0.45f).jitter(0.02f).head(1.1f, 0.4f, 0.55f, false)
                .wake(WAKE_EMBERS, 0.3f, 16.0f, 0.07f, 0.9f, 0.04f).smokeToo().strike(1.1f, 1.2f, 0.6f)
                .darkBurst().ring(3.0f, false, false).debris(18, 0.015f, 0.25f).screenFlash(0.4f));
        STYLES.put(SpellType.INCENDIO, new BeamStyle().colours(0xFFF0A0, 0xFF5A10, 0xFF3300).tail(5.0f)
                .widths(0.04f, 0.12f, 0.38f).jitter(0.06f).writhe(0.05f, 2.2f, 2.5f).flicker(0.3f)
                .head(0.9f, 0.3f, 0.5f, false).wake(WAKE_EMBERS, 0.25f, 14.0f, 0.06f, 0.9f, 0.06f)
                .strike(0.8f, 0.8f, 0.5f).darkBurst().debris(12, -0.01f, 0.15f));
        STYLES.put(SpellType.FIENDFYRE, new BeamStyle().colours(0xFFE6A0, 0xFF6A00, 0xFF2200).tail(9.0f)
                .widths(0.06f, 0.2f, 0.6f).jitter(0.1f).writhe(0.1f, 1.6f, 2.0f).flicker(0.35f)
                .darkSheath(0.5f, 0.35f).twist(2, 0.25f, 1.8f, 2.4f).head(1.3f, 0.45f, 0.55f, false)
                .wake(WAKE_EMBERS, 0.25f, 18.0f, 0.08f, 1.0f, 0.05f).smokeToo().afterglow(8.0f)
                .strike(1.4f, 1.3f, 0.7f).darkBurst().ring(4.0f, false, false).debris(24, 0.015f, 0.3f)
                .screenFlash(0.5f));
        // A dark concussive slug with a burning orange-white rim, two shockwaves and a spray of dust.
        STYLES.put(SpellType.BOMBARDA, new BeamStyle().colours(0xFFE6C8, 0xFF7A30, 0xC84A10).tail(4.0f)
                .widths(0.05f, 0.14f, 0.42f).jitter(0.03f).darkSheath(0.25f, 0.3f).head(1.1f, 0.4f, 0.5f, true)
                .wake(WAKE_SPARKS, 0.35f, 12.0f, 0.02f, 0.9f, 0.0f).strike(1.3f, 1.5f, 0.6f).darkBurst()
                .ring(5.0f, false, false).doubleRing().arcs(4, 0.2f).debris(22, 0.045f, 0.35f).screenFlash(0.7f));

        // ---- combat charms: clean bolts ----
        // A short, fat, punchy stunner: all impact, no crackle.
        STYLES.put(SpellType.STUPEFY, new BeamStyle().colours(0xFFE2E2, 0xFF2020, 0xFF0000).tail(3.0f)
                .widths(0.05f, 0.15f, 0.42f).jitter(0.02f).head(1.0f, 0.36f, 0.55f, false)
                .wake(WAKE_SPARKS, 0.5f, 10.0f, 0.02f, 0.9f, 0.0f).strike(1.0f, 1.0f, 0.5f)
                .ring(3.0f, false, true).debris(8, 0.03f, 0.2f).screenFlash(0.3f));
        // A long scarlet corkscrew.
        STYLES.put(SpellType.EXPELLIARMUS, new BeamStyle().colours(0xFFE8D8, 0xFF4433, 0xFF2A10).tail(8.0f)
                .jitter(0.015f).twist(2, 0.15f, 3.2f, 1.6f)
                .wake(WAKE_SPARKS, 0.5f, 10.0f, 0.02f, 0.8f, 0.0f).strike(0.9f, 0.9f, 0.45f)
                .ring(3.5f, false, false).debris(10, 0.03f, 0.25f));
        STYLES.put(SpellType.PETRIFICUS_TOTALUS, new BeamStyle().colours(0xFFFFFF, 0xC8C8C8, 0x9AA0A8).tail(6.0f)
                .jitter(0.0f).writhe(0.0f, 1.0f, 1.0f).flicker(0.03f).twist(2, 0.1f, 0.8f, 0.3f)
                .wake(WAKE_MIST, 0.6f, 18.0f, 0.2f, 0.1f, -0.005f).strike(0.9f, 0.6f, 0.4f).ring(3.0f, false, true)
                .debris(10, 0.03f, 0.2f));
        STYLES.put(SpellType.DIFFINDO, new BeamStyle().colours(0xFFFFF4, 0xFFF4A0, 0xFFE060).tail(2.5f)
                .widths(0.02f, 0.045f, 0.14f).jitter(0.0f).writhe(0.0f, 1.0f, 1.0f).head(0.3f, 0.15f, 0.5f, false)
                .wake(WAKE_SPARKS, 0.6f, 8.0f, 0.02f, 0.8f, 0.0f).afterglow(3.0f).strike(0.7f, 0.6f, 0.3f)
                .arcs(5, 0.0f));
        STYLES.put(SpellType.DEPULSO, new BeamStyle().colours(0xFFFFFF, 0xE6E6E6, 0xBFC8D0).tail(5.0f)
                .alphas(0.7f, 0.18f).jitter(0.0f).shedRings(0.9f, 0.7f, 9.0f)
                .wake(WAKE_MIST, 0.9f, 12.0f, 0.3f, 0.08f, 0.0f).strike(1.0f, 0.6f, 0.3f).ring(5.5f, false, true));
        // A jinx: violet with a faint dark edge, lifting.
        STYLES.put(SpellType.LEVICORPUS, new BeamStyle().colours(0xEEDCFF, 0x8844FF, 0x5A20E0).tail(6.0f)
                .writhe(0.05f, 1.0f, 1.0f).darkSheath(0.2f, 0.25f).beads(3, 0.5f, 0.12f)
                .wake(WAKE_MOTES, 0.4f, 24.0f, 0.06f, 0.9f, 0.05f)
                .strike(0.9f, 0.6f, 0.45f).ring(2.0f, false, false).pillar().debris(8, -0.02f, 0.12f));
        STYLES.put(SpellType.INCARCEROUS, new BeamStyle().colours(0xFFF0D0, 0xD9B26A, 0xA8823C).tail(6.0f)
                .alphas(0.7f, 0.12f).jitter(0.0f).writhe(0.02f, 1.0f, 1.0f).twist(3, 0.07f, 6.0f, 1.5f)
                .strike(0.7f, 0.4f, 0.3f).ring(1.5f, true, false));
        // Amber and lilac, wobbling.
        STYLES.put(SpellType.CONFUNDO, new BeamStyle().colours(0xFFF4E8, 0xC8A0FF, 0xFFB050).tail(6.0f)
                .jitter(0.0f).writhe(0.1f, 1.7f, 1.9f).twist(3, 0.16f, 4.0f, 2.5f)
                .wake(WAKE_MIST, 0.7f, 22.0f, 0.28f, 0.13f, 0.0f).strike(0.9f, 0.5f, 0.5f).ring(2.5f, false, false)
                .mistBurst().debris(8, 0.0f, 0.1f));
        // Silver-white, slow, drawing the memory in.
        STYLES.put(SpellType.OBLIVIATE, new BeamStyle().colours(0xFFFFFF, 0xE8EEF6, 0xC0CCDA).tail(8.0f)
                .jitter(0.0f).writhe(0.07f, 0.6f, 0.5f).twist(2, 0.18f, 1.0f, 0.3f)
                .wake(WAKE_MIST, 0.7f, 30.0f, 0.3f, 0.16f, 0.01f).strike(1.0f, 0.9f, 0.6f).ring(3.5f, true, false)
                .mistBurst());
        STYLES.put(SpellType.ARRESTO_MOMENTUM, new BeamStyle().colours(0xFFFFFF, 0xCFE8FF, 0xA0C8FF).tail(6.0f)
                .jitter(0.0f).beads(4, 0.2f, 0.13f).shedRings(1.2f, 0.6f, 16.0f)
                .wake(WAKE_MOTES, 0.5f, 22.0f, 0.06f, 0.8f, 0.0f).strike(0.9f, 0.6f, 0.4f).ring(3.5f, true, true));

        // ---- cold ----
        STYLES.put(SpellType.GLACIUS, new BeamStyle().colours(0xFFFFFF, 0x9FF4FF, 0x59D8FF).tail(8.0f)
                .jitter(0.0f).writhe(0.01f, 1.0f, 1.0f).flicker(0.05f).lightSheath(0.26f, 0.5f)
                .wake(WAKE_MOTES, 0.35f, 30.0f, 0.06f, 0.9f, -0.01f).strike(0.9f, 0.7f, 0.5f)
                .ring(2.5f, false, true).debris(14, 0.02f, 0.22f));

        // ---- utility and light ----
        STYLES.put(SpellType.LUMOS, new BeamStyle().colours(0xFFFFF0, 0xFFF6B0, 0xFFE070).tail(2.0f)
                .widths(0.04f, 0.08f, 0.3f).jitter(0.0f).head(1.0f, 0.35f, 0.55f, false)
                .wake(WAKE_MOTES, 0.3f, 30.0f, 0.06f, 0.9f, 0.01f).strike(1.0f, 1.2f, 0.8f)
                .debris(8, -0.005f, 0.1f));
        STYLES.put(SpellType.ACCIO, new BeamStyle().colours(0xE8F2FF, 0x5C9CFF, 0x3070FF).tail(JET)
                .jitter(0.01f).twist(1, 0.16f, 2.6f, -1.2f).beads(5, -0.5f, 0.14f).afterglow(6.0f)
                .strike(0.8f, 0.5f, 0.45f).ring(2.5f, true, false));
        // Pale lilac, drifting slowly as a feather.
        STYLES.put(SpellType.WINGARDIUM_LEVIOSA, new BeamStyle().colours(0xFAF4FF, 0xD8B8FF, 0xB890FF).tail(7.0f)
                .jitter(0.0f).writhe(0.06f, 0.9f, 0.3f).twist(2, 0.14f, 1.6f, 0.4f)
                .wake(WAKE_MOTES, 0.4f, 28.0f, 0.06f, 0.9f, 0.03f).strike(0.8f, 0.6f, 0.45f)
                .ring(2.0f, false, false).debris(10, -0.01f, 0.12f));
        STYLES.put(SpellType.ALOHOMORA, new BeamStyle().colours(0xFFFBE0, 0xFFD84A, 0xFFB020).tail(3.5f)
                .jitter(0.0f).beads(3, 0.8f, 0.12f).wake(WAKE_MOTES, 0.5f, 18.0f, 0.05f, 0.8f, 0.0f)
                .strike(0.7f, 0.7f, 0.4f).ring(1.6f, false, false).debris(6, 0.02f, 0.15f));
        STYLES.put(SpellType.REVELIO, new BeamStyle().colours(0xE8FFFF, 0x50FFFF, 0x20C8FF).tail(8.0f)
                .jitter(0.0f).beads(3, 1.0f, 0.13f).wake(WAKE_MOTES, 0.5f, 22.0f, 0.05f, 0.8f, 0.0f)
                .strike(1.0f, 0.6f, 0.5f).ring(6.0f, false, true));
        // Warm yellow-green in a gold glow.
        STYLES.put(SpellType.HEAL, new BeamStyle().colours(0xFFFDE8, 0xC8F070, 0xFFD870).tail(5.0f)
                .jitter(0.0f).writhe(0.03f, 1.0f, 0.8f).twist(2, 0.12f, 2.0f, 0.8f)
                .wake(WAKE_MOTES, 0.4f, 26.0f, 0.06f, 0.9f, 0.03f).strike(0.9f, 0.6f, 0.6f).pillar()
                .debris(10, -0.01f, 0.12f));
        STYLES.put(SpellType.EXPECTO_PATRONUM, new BeamStyle().colours(0xFFFFFF, 0xC8F4FF, 0x8EE6FF).tail(JET)
                .widths(0.05f, 0.14f, 0.5f).alphas(0.8f, 0.3f).jitter(0.0f).writhe(0.02f, 0.8f, 0.6f)
                .flicker(0.05f).lightSheath(0.6f, 0.5f).twist(2, 0.22f, 1.2f, 0.6f).head(1.1f, 0.4f, 0.55f, false)
                .wake(WAKE_MOTES, 0.25f, 34.0f, 0.07f, 0.9f, 0.01f).afterglow(12.0f).strike(1.3f, 1.2f, 0.7f)
                .ring(4.5f, false, false).pillar().debris(12, -0.008f, 0.15f).screenFlash(0.5f));
        // A short shield-bolt in a soft blue aura.
        STYLES.put(SpellType.PROTEGO, new BeamStyle().colours(0xFFFFFF, 0x9FD8FF, 0x7AB8FF).tail(3.0f)
                .jitter(0.0f).lightSheath(0.3f, 0.4f).head(0.9f, 0.3f, 0.5f, false).strike(0.8f, 0.6f, 0.4f)
                .ring(3.0f, false, false));
        // The great ward: a broad pale beam in a wide aura, landing as two flat rings.
        STYLES.put(SpellType.PROTEGO_MAXIMA, new BeamStyle().colours(0xFFFFFF, 0xC8F6FF, 0x9BE7FF).tail(5.0f)
                .widths(0.05f, 0.14f, 0.45f).jitter(0.0f).lightSheath(0.5f, 0.5f).head(1.2f, 0.4f, 0.5f, false)
                .strike(1.0f, 0.7f, 0.5f).ring(4.5f, false, true).doubleRing());
        STYLES.put(SpellType.ASCENDIO, new BeamStyle().colours(0xFFFFFF, 0xF4F8FF, 0xD8E8FF).tail(5.0f)
                .jitter(0.0f).wake(WAKE_MIST, 0.5f, 20.0f, 0.25f, 0.14f, 0.08f).strike(0.9f, 0.6f, 0.4f).pillar()
                .ring(2.5f, false, true));
        STYLES.put(SpellType.CAVE_INIMICUM, new BeamStyle().colours(0xF0FFFC, 0x7AF0E0, 0x40C8B8).tail(6.0f)
                .jitter(0.0f).beads(2, 0.6f, 0.12f).wake(WAKE_MOTES, 0.5f, 24.0f, 0.05f, 0.8f, 0.0f)
                .strike(0.9f, 0.6f, 0.4f).ring(4.0f, false, true));
        STYLES.put(SpellType.ALATUS, new BeamStyle().colours(0xFFFBF0, 0xD4C4A8, 0xB8A888).tail(5.0f)
                .jitter(0.0f).writhe(0.06f, 0.8f, 1.0f).twist(2, 0.2f, 0.9f, 1.2f)
                .wake(WAKE_MIST, 0.6f, 18.0f, 0.3f, 0.1f, 0.02f).strike(0.9f, 0.5f, 0.35f).ring(3.0f, false, false)
                .mistBurst());
        STYLES.put(SpellType.REPARO, new BeamStyle().colours(0xF0FFFA, 0x8CF3D7, 0x50D8B0).tail(5.0f)
                .jitter(0.0f).twist(2, 0.12f, 2.0f, 1.0f).beads(4, -0.6f, 0.12f)
                .wake(WAKE_MOTES, 0.5f, 22.0f, 0.05f, 0.8f, 0.0f).strike(0.8f, 0.6f, 0.45f).ring(2.5f, true, false)
                .debris(8, -0.005f, 0.1f));
        STYLES.put(SpellType.PIERTOTUM_LOCOMOTOR, new BeamStyle().colours(0xFFFFFF, 0xB8BCC0, 0x8A9096).tail(6.0f)
                .jitter(0.0f).beads(3, 0.6f, 0.14f).twist(2, 0.1f, 0.6f, 0.4f)
                .wake(WAKE_MIST, 0.6f, 20.0f, 0.22f, 0.1f, -0.005f).strike(1.0f, 0.7f, 0.45f)
                .ring(3.5f, false, true).pillar().debris(12, 0.03f, 0.22f));
        STYLES.put(SpellType.COLLOPORTUS, new BeamStyle().colours(0xFFF4E0, 0xB08A4A, 0x8A6D3B).tail(3.5f)
                .jitter(0.0f).twist(1, 0.1f, 2.5f, 1.0f).strike(0.7f, 0.5f, 0.35f).ring(1.6f, true, false));
        // A bouncing rainbow that ends in confetti.
        STYLES.put(SpellType.RIDDIKULUS, new BeamStyle().colours(0xFFFFFF, 0xFF8AD1, 0xFF5AB8).tail(5.0f)
                .jitter(0.0f).writhe(0.12f, 2.4f, 3.5f).beads(4, 0.9f, 0.13f).bodyRainbow()
                .wake(WAKE_MOTES, 0.3f, 24.0f, 0.07f, 1.0f, 0.03f).rainbow().strike(0.9f, 0.6f, 0.45f)
                .debris(16, 0.02f, 0.25f));
    }

    private BeamStyles() {
    }

    /** The spell's beam style, or null for a spell whose beam is never drawn (Aqua Eructo's water lash). */
    public static BeamStyle of(SpellType spell) {
        if (spell == SpellType.AQUA_ERUCTO) {
            return null;
        }
        BeamStyle style = STYLES.get(spell);
        if (style == null) {
            // A spell added later: a clean bolt in its own colour until it gets a style of its own.
            int rgb = spell.getColor();
            int core = lighten(rgb, 0.75f);
            style = new BeamStyle().colours(core, rgb, rgb).tail(5.0f).twist(1, 0.12f, 2.0f, 1.0f)
                    .wake(WAKE_MOTES, 0.5f, 20.0f, 0.05f, 0.8f, 0.0f).strike(0.8f, 0.6f, 0.4f)
                    .ring(2.0f, false, false);
            STYLES.put(spell, style);
        }
        return style;
    }

    private static int lighten(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) Mth.lerp(amount, r, 255);
        g = (int) Mth.lerp(amount, g, 255);
        b = (int) Mth.lerp(amount, b, 255);
        return (r << 16) | (g << 8) | b;
    }
}
