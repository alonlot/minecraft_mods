package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Where a spell is drawn, as against where it really is. The real beam flies dead straight from the caster's
 * eye and is what hits; the drawn spell leaves from the wand tip, off to the side, curves in onto that line
 * over the first blocks, and wobbles a little around it all the way, so no two casts fly the same.
 * Visual only: hits, aim and the server are unchanged. The renderer, the sparkle trail and the ribbon all use
 * this, and each beam keeps a short history of drawn points so its tail follows the curve.
 */
public final class SpellPath {

    /** Distance over which the spell curves from the wand tip onto its real line, in blocks. */
    private static final double CURVE_DISTANCE = 7.0;
    /** Spells that wobble harder (the duelling hexes in the video). */
    private static final Set<SpellType> WILD = EnumSet.of(SpellType.STUPEFY, SpellType.EXPELLIARMUS,
            SpellType.CONFRINGO, SpellType.INCENDIO, SpellType.DIFFINDO, SpellType.PETRIFICUS_TOTALUS,
            SpellType.LEVICORPUS, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE);
    /** Spells that keep a straight line: a ribbon already bends, and Aqua Eructo has no orb. */
    private static final Set<SpellType> STRAIGHT = EnumSet.of(SpellType.AQUA_ERUCTO, SpellType.GLACIUS,
            SpellType.REPARO);

    private static final int HISTORY = 16;

    private SpellPath() {
    }

    /** The drawn orb position in the world (the middle of the orb). */
    public static Vec3 orb(SpellBeamEntity beam, float partialTick) {
        return beam.getPosition(partialTick).add(0.0, SpellBeamRenderer.ORB_HEIGHT, 0.0).add(offset(beam, partialTick));
    }

    /** The drawn position minus the real one. */
    public static Vec3 offset(SpellBeamEntity beam, float partialTick) {
        SpellType spell = beam.getSpellType();
        if (STRAIGHT.contains(spell)) {
            return Vec3.ZERO;
        }
        Vec3 start = startOffset(beam);
        Vec3 vel = beam.getDeltaMovement();
        double speed = vel.length();
        float age = beam.tickCount + partialTick;
        double flown = age * speed;
        Vec3 out = Vec3.ZERO;
        if (start != null) {
            double k = Mth.clamp(flown / CURVE_DISTANCE, 0.0, 1.0);
            double left = (1.0 - k) * (1.0 - k);
            out = start.scale(left);
        }
        if (speed > 1.0E-4) {
            Vec3 dir = vel.scale(1.0 / speed);
            Vec3 upRef = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 right = dir.cross(upRef).normalize();
            Vec3 up = right.cross(dir).normalize();
            float seed = (beam.getId() * 0.6180339F) % 1.0F * Mth.TWO_PI;
            double amp = (WILD.contains(spell) ? 0.45 : 0.2) * Mth.clamp(flown / 2.0, 0.0, 1.0);
            double sx = Mth.sin(age * 0.55F + seed) + 0.5 * Mth.sin(age * 1.3F + seed * 2.0F);
            double sy = Mth.sin(age * 0.7F + seed * 3.0F) + 0.4 * Mth.sin(age * 1.7F + seed);
            out = out.add(right.scale(sx * amp * 0.75)).add(up.scale(sy * amp * 0.5));
        }
        return out;
    }

    /**
     * The gap between the caster's wand tip and where the beam appeared, taken the first time the beam is
     * drawn. A beam first seen well into its flight (it came into range) starts on its line.
     */
    private static Vec3 startOffset(SpellBeamEntity beam) {
        if (beam.vfxStartTaken) {
            return beam.vfxStartOffset;
        }
        beam.vfxStartTaken = true;
        if (beam.tickCount > 2) {
            return null;
        }
        Vec3 tip = SpellRibbon.handPosition(beam.getOwner(), 1.0F, Minecraft.getInstance().gameRenderer.getMainCamera());
        if (tip == null) {
            return null;
        }
        Vec3 spawn = beam.position().add(0.0, SpellBeamRenderer.ORB_HEIGHT, 0.0)
                .subtract(beam.getDeltaMovement().scale(beam.tickCount));
        Vec3 gap = tip.subtract(spawn);
        beam.vfxStartOffset = gap.lengthSqr() < 4.0 ? gap : null; // never pull it from far away
        return beam.vfxStartOffset;
    }

    /** Remembers the drawn position for the tail; call once per frame from the renderer. */
    static void record(SpellBeamEntity beam, Vec3 drawn) {
        ArrayDeque<Vec3> history = beam.vfxHistory;
        Vec3 last = history.peekLast();
        if (last != null && last.distanceToSqr(drawn) < 0.06 * 0.06) {
            return;
        }
        history.addLast(drawn);
        while (history.size() > HISTORY) {
            history.pollFirst();
        }
    }

    /** Recent drawn points, newest first, up to {@code length} blocks of path behind {@code head}. */
    static Vec3[] tail(SpellBeamEntity beam, Vec3 head, double length) {
        java.util.List<Vec3> out = new java.util.ArrayList<>();
        out.add(head);
        double total = 0.0;
        Vec3 prev = head;
        for (Iterator<Vec3> it = beam.vfxHistory.descendingIterator(); it.hasNext(); ) {
            Vec3 p = it.next();
            double d = p.distanceTo(prev);
            if (d < 1.0E-4) {
                continue;
            }
            if (total + d > length) {
                out.add(prev.add(p.subtract(prev).scale((length - total) / d)));
                break;
            }
            total += d;
            out.add(p);
            prev = p;
        }
        return out.toArray(new Vec3[0]);
    }
}
