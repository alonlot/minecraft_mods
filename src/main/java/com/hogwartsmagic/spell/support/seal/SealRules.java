package com.hogwartsmagic.spell.support.seal;

import java.util.List;
import java.util.UUID;

/**
 * Colloportus: how far a seal reaches and how many a caster keeps at each rank, which blocks a seal covers, and
 * what an Alohomora does against the seals over a lock. Pure numbers.
 */
public final class SealRules {
    public static final int MAX_RADIUS = 4;

    private static final int TOP_RANK = 4;
    private static final int[] RADIUS = {1, 2, 3, 4};
    private static final int[] CAP = {1, 2, 4, 8};

    public enum Verdict { PASS, REFUSE, BREAK }

    /** target: the index, in the covering list, of the seal that refuses or breaks; -1 for PASS. */
    public record Decision(Verdict verdict, int target) {
    }

    private SealRules() {
    }

    public static int radius(int rank) {
        return RADIUS[index(rank)];
    }

    /** Seals one caster may keep at once (across all dimensions). */
    public static int cap(int rank) {
        return CAP[index(rank)];
    }

    /** Block (x, y, z) lies in the seal: its centre is at most the radius from the seal's centre. */
    public static boolean covers(Seal seal, int x, int y, int z) {
        long dx = x - seal.x();
        long dy = y - seal.y();
        long dz = z - seal.z();
        long r = seal.radius();
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /**
     * A strictly higher Alohomora breaks a seal. Alohomora tops out at Master, so a Master Alohomora also breaks a
     * Master seal (otherwise nothing could).
     */
    public static boolean breaks(int alohomoraRank, int sealRank) {
        return alohomoraRank > sealRank || (alohomoraRank == TOP_RANK && sealRank == TOP_RANK);
    }

    /**
     * An Alohomora at rank against the seals covering its lock, oldest first. caster: the casting player, or null
     * for an NPC or a puppet cast (they own no seal). The caster's own seals let it through. Of the others the
     * highest rank decides, the oldest of equals first: an Alohomora that breaks it does so when mayBreak,
     * otherwise the cast is refused.
     */
    public static Decision decide(List<Seal> covering, UUID caster, int rank, boolean mayBreak) {
        int target = -1;
        for (int i = 0; i < covering.size(); i++) {
            Seal seal = covering.get(i);
            if (caster != null && caster.equals(seal.owner())) {
                continue;
            }
            if (target < 0 || seal.rank() > covering.get(target).rank()) {
                target = i;
            }
        }
        if (target < 0) {
            return new Decision(Verdict.PASS, -1);
        }
        if (mayBreak && breaks(rank, covering.get(target).rank())) {
            return new Decision(Verdict.BREAK, target);
        }
        return new Decision(Verdict.REFUSE, target);
    }

    /** How many of a caster's oldest seals must go so one more fits under cap (more than one after a rank drop). */
    public static int toEvict(int existing, int cap) {
        return Math.max(0, existing - Math.max(1, cap) + 1);
    }

    private static int index(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
