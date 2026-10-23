package com.hogwartsmagic.fire;

import com.hogwartsmagic.pensieve.scene.DragonFire;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The scorch of the wild Horntails fire, the bookkeeping. A memory soot stays until its scene ends, but the open
 * world must not pile up: here every spot fades out ({@link #fade}), spots are capped per dragon and in all (the oldest
 * goes first), a spot whose block is gone is dropped, and a dragon spots all go when it does. Times are the client own
 * clock in ticks. Pure: no Minecraft classes.
 */
public final class WildScorch {
    /** Spots a dragon may have, and all dragons together, at most. */
    public static final int PER_DRAGON = 20, TOTAL = 60;
    /** The soot holds this long after the last hit, then fades out over {@link #FADE_TICKS} (20 s and 30 s). */
    public static final double HOLD_TICKS = 400, FADE_TICKS = 600;
    /** Hits a spot remembers: more than the 120 that blacken it fully, so it stays full yet never grows without end. */
    public static final int MAX_HITS = 240;

    /** A spot and the dragon (entity id) that burned it. */
    public record Entry(int owner, DragonFire.Spot spot) {
    }

    private final List<Entry> entries = new ArrayList<>();

    /** The soot opacity share at {@code now} for a spot last hit at {@code lastHit}: 1 while held, 0 once gone. */
    public static double fade(double now, double lastHit) {
        if (Double.isInfinite(lastHit) || now <= lastHit + HOLD_TICKS) return 1;
        double x = (now - lastHit - HOLD_TICKS) / FADE_TICKS;
        if (x >= 1) return 0;
        return 1 - x * x * (3 - 2 * x);
    }

    /** Whether the spot is faded out at {@code now}. */
    public static boolean faded(DragonFire.Spot s, double now) {
        return fade(now, s.lastHit(now)) <= 0;
    }

    /**
     * Records a hit of dragon {@code owner} at (x, y, z) on a face of normal {@code n} at time {@code t}: it joins the
     * dragon own spot within {@link DragonFire#SPOT_JOIN}, else makes a new one, evicting the dragon oldest at
     * {@link #PER_DRAGON} and the oldest of all at {@link #TOTAL}. Returns the spots evicted (to forget their drawing).
     */
    public List<DragonFire.Spot> hit(int owner, double x, double y, double z, double[] n, double t) {
        List<DragonFire.Spot> evicted = new ArrayList<>();
        DragonFire.Spot best = null;
        double bd = DragonFire.SPOT_JOIN;
        for (Entry e : entries) {
            if (e.owner != owner) continue;
            double d = e.spot.distance(x, y, z);
            if (d <= bd) {
                bd = d;
                best = e.spot;
            }
        }
        if (best == null) {
            if (count(owner) >= PER_DRAGON) evictOldest(owner, evicted);
            if (entries.size() >= TOTAL) evictOldest(-1, evicted);
            best = new DragonFire.Spot(x, y, z, n[0], n[1], n[2]);
            entries.add(new Entry(owner, best));
        }
        best.hitCapped(t, MAX_HITS);
        return evicted;
    }

    private void evictOldest(int owner, List<DragonFire.Spot> out) {
        Entry oldest = null;
        double ot = Double.POSITIVE_INFINITY;
        for (Entry e : entries) {
            if (owner >= 0 && e.owner != owner) continue;
            double l = e.spot.lastHit(Double.POSITIVE_INFINITY);
            if (oldest == null || l < ot) {
                oldest = e;
                ot = l;
            }
        }
        if (oldest != null) {
            entries.remove(oldest);
            out.add(oldest.spot);
        }
    }

    /** Removes the spots faded out at {@code now}, or for which {@code gone} holds (their block has gone); returns them. */
    public List<DragonFire.Spot> prune(double now, Predicate<DragonFire.Spot> gone) {
        List<DragonFire.Spot> out = new ArrayList<>();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (faded(e.spot, now) || gone.test(e.spot)) {
                out.add(e.spot);
                it.remove();
            }
        }
        return out;
    }

    /** Removes one spot (its block went); true if it was there. */
    public boolean remove(DragonFire.Spot s) {
        return entries.removeIf(e -> e.spot == s);
    }

    /** Removes every spot of a dragon that left the world; returns them. */
    public List<DragonFire.Spot> removeOwner(int owner) {
        List<DragonFire.Spot> out = new ArrayList<>();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.owner == owner) {
                out.add(e.spot);
                it.remove();
            }
        }
        return out;
    }

    /** Removes every spot whose owner is not in {@code live}; returns them. */
    public List<DragonFire.Spot> retainOwners(Set<Integer> live) {
        List<DragonFire.Spot> out = new ArrayList<>();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (!live.contains(e.owner)) {
                out.add(e.spot);
                it.remove();
            }
        }
        return out;
    }

    public void clear() {
        entries.clear();
    }

    public int count(int owner) {
        int n = 0;
        for (Entry e : entries) if (e.owner == owner) n++;
        return n;
    }

    public int size() {
        return entries.size();
    }

    /** The dragon spots, in the order they were made (a copy). */
    public List<DragonFire.Spot> spots(int owner) {
        List<DragonFire.Spot> out = new ArrayList<>();
        for (Entry e : entries) if (e.owner == owner) out.add(e.spot);
        return out;
    }

    /** Every spot (a copy). */
    public List<DragonFire.Spot> all() {
        List<DragonFire.Spot> out = new ArrayList<>(entries.size());
        for (Entry e : entries) out.add(e.spot);
        return out;
    }
}
