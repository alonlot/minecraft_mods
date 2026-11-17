package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a spell is drawn, as against where it really is. The real beam flies dead straight from the caster's
 * eye and is what hits; the drawn spell leaves from the wand tip, off to the side, curves in onto that line
 * over the first blocks, and (a bolt) wobbles a little around it all the way, so no two casts fly the same.
 * Visual only: hits, aim and the server are unchanged. {@link SpellBeamClientState} records each beam's head
 * through this, so {@link SpellBeamFxRenderer} draws every beam style, its wake and its tail along the curve.
 */
public final class SpellPath {

    /** Distance over which the spell curves from the wand tip onto its real line, in blocks. */
    private static final double CURVE_DISTANCE = 7.0;
    /** Spells that wobble harder (the duelling hexes). */
    private static final Set<SpellType> WILD = EnumSet.of(SpellType.STUPEFY, SpellType.EXPELLIARMUS,
            SpellType.CONFRINGO, SpellType.INCENDIO, SpellType.DIFFINDO, SpellType.PETRIFICUS_TOTALUS,
            SpellType.LEVICORPUS, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE);
    /** Spells that keep a straight line: a ribbon already bends (SpellRibbon), and Aqua Eructo is a stream. */
    private static final Set<SpellType> STRAIGHT = EnumSet.of(SpellType.AQUA_ERUCTO, SpellType.GLACIUS,
            SpellType.REPARO);

    /**
     * The server launches the beam as the swing starts, but the first-person gesture lets the spell go a few
     * ticks later. Until then the head is held on the wand tip and moves with it, for at most this many ticks of
     * beam age, so a fast beam has not hit before it leaves.
     */
    private static final float MAX_HOLD = 4.5F;
    /** Once let go, the time a held head takes to catch up with the real beam, in ticks. */
    private static final float RELEASE_TICKS = 2.0F;

    private SpellPath() {
    }

    /**
     * The gap from where the beam appeared to the wand tip it left from, taken once when the client first sees
     * it. A beam first seen well into its flight (it came into range), or far from the tip, starts on its line.
     */
    public static void takeStart(SpellBeamEntity beam, Vec3 head, Vec3 tip) {
        if (beam.vfxStartTaken) {
            return;
        }
        beam.vfxStartTaken = true;
        if (beam.tickCount > 2 || STRAIGHT.contains(beam.getSpellType())) {
            return;
        }
        Vec3 spawn = head.subtract(beam.getDeltaMovement().scale(beam.tickCount));
        Vec3 gap = tip.subtract(spawn);
        beam.vfxStartOffset = gap.lengthSqr() < 4.0 ? gap : null; // never pull it from far away
    }

    /**
     * The drawn position minus the real one, {@code base} being the real centre this tick. A jet keeps the
     * curve out of the wand but no wobble: it is drawn whole, so a wobble would freeze into kinks.
     */
    public static Vec3 offset(SpellBeamEntity beam, Vec3 base, boolean jet) {
        SpellType spell = beam.getSpellType();
        if (STRAIGHT.contains(spell)) {
            return Vec3.ZERO;
        }
        float age = beam.tickCount;
        Vec3 held = heldTip(beam, age, base);
        if (held != null) {
            return held.subtract(base);
        }
        Vec3 start = beam.vfxStartOffset;
        Vec3 vel = beam.getDeltaMovement();
        double speed = vel.length();
        double flown = age * speed;
        Vec3 out = Vec3.ZERO;
        // A held head lunges from the tip onto the line; any other bends in over the first blocks of flight.
        double k = beam.vfxHeld ? Mth.clamp((age - beam.vfxReleaseAge) / RELEASE_TICKS, 0.0, 1.0)
                : Mth.clamp(flown / CURVE_DISTANCE, 0.0, 1.0);
        if (start != null) {
            double left = (1.0 - k) * (1.0 - k);
            out = start.scale(left);
        }
        if (!jet && speed > 1.0E-4) {
            Vec3 dir = vel.scale(1.0 / speed);
            Vec3 upRef = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 right = dir.cross(upRef).normalize();
            Vec3 up = right.cross(dir).normalize();
            float seed = (beam.getId() * 0.6180339F) % 1.0F * Mth.TWO_PI;
            double amp = (WILD.contains(spell) ? 0.45 : 0.2) * (beam.vfxHeld ? k : Mth.clamp(flown / 2.0, 0.0, 1.0));
            double sx = Mth.sin(age * 0.55F + seed) + 0.5 * Mth.sin(age * 1.3F + seed * 2.0F);
            double sy = Mth.sin(age * 0.7F + seed * 3.0F) + 0.4 * Mth.sin(age * 1.7F + seed);
            out = out.add(right.scale(sx * amp * 0.75)).add(up.scale(sy * amp * 0.5));
        }
        return out;
    }

    /**
     * While the caster's gesture has not yet let the spell go, the wand tip the head is held on; null once it has
     * (or was never held). For every gesture drawn by {@link CastGestures}: your first-person arm, players in
     * third person and the wand-carrying NPCs. The gap to the real beam at the last held tick is where the lunge
     * starts.
     */
    @javax.annotation.Nullable
    private static Vec3 heldTip(SpellBeamEntity beam, float age, Vec3 base) {
        if (beam.vfxReleaseAge >= 0.0F) {
            return null;
        }
        Vec3 tip = null;
        if (age < MAX_HOLD && beam.getOwner() instanceof LivingEntity caster && heldByGesture(caster)) {
            CastGestures.Cast cast = CastGestures.castOf(caster);
            float t = cast == null ? -1.0F : cast.progress(caster.tickCount);
            if (t >= 0.0F && t < cast.gesture().release) {
                Minecraft minecraft = Minecraft.getInstance();
                tip = caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
                        ? WandCastAnimation.localTipInWorld(minecraft.gameRenderer.getMainCamera())
                        : WandSwipeLayer.recentTip(caster); // never a guess: no drawn tip, no hold
            }
        }
        if (tip != null && tip.distanceToSqr(base) < 64.0) {
            beam.vfxHeld = true;
            beam.vfxStartTaken = true;
            beam.vfxStartOffset = tip.subtract(base);
            return tip;
        }
        beam.vfxReleaseAge = age;
        return null;
    }

    private static boolean heldByGesture(LivingEntity caster) {
        return CastGestures.posesModel(caster);
    }
}
