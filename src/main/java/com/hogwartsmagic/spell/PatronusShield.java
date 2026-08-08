package com.hogwartsmagic.spell;

/**
 * Geometry of the non-corporeal Patronus shield, shared by the server logic that decides
 * which Dementors are caught and the client renderer that draws the light. Keeping one
 * source of truth means the cone you see is always the cone that actually pushes.
 */
public final class PatronusShield {

    /** How far the light reaches, in blocks. */
    public static final double RANGE = 20.0D;

    /** Cosine of the cone's half-angle (25 degrees). */
    public static final double COS_HALF_ANGLE = 0.9063D;

    /** Radius of the cone's mouth at {@link #RANGE}. */
    public static final double MOUTH_RADIUS = RANGE * Math.tan(Math.acos(COS_HALF_ANGLE));

    /** Dementors cannot close inside this many blocks while they are in the cone. */
    public static final double STANDOFF = 3.0D;

    private PatronusShield() {
    }
}
