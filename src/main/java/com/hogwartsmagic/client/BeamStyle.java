package com.hogwartsmagic.client;

/**
 * How one spell's beam looks in flight and where it lands. Every spell gets its own in {@link BeamStyles}; the
 * renderer ({@link SpellBeamFxRenderer}) reads nothing else. Built with chained setters, never changed after.
 *
 * <p>Distances are in blocks, times in ticks, speeds in blocks a tick, alphas 0 to 1.
 */
public final class BeamStyle implements Cloneable {
    /** Nothing left behind. */
    public static final int WAKE_NONE = 0;
    /** Near-black smoke that rises and spreads (drawn darkening, not glowing). */
    public static final int WAKE_SMOKE = 1;
    /** Soft clouds of the beam's glow colour. */
    public static final int WAKE_MIST = 2;
    /** Hot flickering motes that rise like cinders. */
    public static final int WAKE_EMBERS = 3;
    /** Tiny streaks thrown off and falling. */
    public static final int WAKE_SPARKS = 4;
    /** Points of light hanging in the air, twinkling. */
    public static final int WAKE_MOTES = 5;

    /** A continuous jet from the wand to the head, however far it has gone. */
    public static final float JET = 1.0E9f;

    // Colours: the white-hot centre, the body of the beam, and the wide glow round it.
    float coreR = 1.0f, coreG = 1.0f, coreB = 1.0f;
    float bodyR = 1.0f, bodyG = 1.0f, bodyB = 1.0f;
    float glowR = 1.0f, glowG = 1.0f, glowB = 1.0f;

    // Form: a jet, or a bolt trailing a tail this long.
    float tail = 5.0f;
    float coreWidth = 0.035f;
    float bodyWidth = 0.10f;
    float glowWidth = 0.30f;
    float bodyAlpha = 0.80f;
    float glowAlpha = 0.26f;
    /** Random sideways jag, re-struck every other tick: 0 is a laser, 0.1 a crackling bolt. */
    float jitter = 0.03f;
    /** A smooth travelling wave along the beam: size, waves per block, speed. */
    float writhe = 0.03f;
    float writheFreq = 1.2f;
    float writheSpeed = 0.8f;
    /** How hard the body flickers between ticks. */
    float flicker = 0.14f;

    // Sheath: a wide band round the beam, dark (it darkens the world) or a soft light of the glow colour.
    float sheathWidth = 0.0f;
    float sheathAlpha = 0.5f;
    boolean sheathDark = true;

    // Strands coiling round the beam: crackling (jagged, with gaps that jump) or smooth twisting ribbons.
    int strands = 0;
    float strandRadius = 0.12f;
    float strandTwist = 2.3f;
    float strandSpin = 0.9f;
    boolean strandsCrackle = true;

    // Beads of light running along the beam (negative speed: back toward the wand).
    int beads = 0;
    float beadSpeed = 0.6f;
    float beadSize = 0.14f;

    // Rings shed into the air behind the head, every so often, expanding as they fade.
    float ringEvery = 0.0f;
    float ringRadius = 0.6f;
    float ringLife = 10.0f;

    // The head.
    float headRadius = 0.7f;
    float headCore = 0.26f;
    float headAlpha = 0.45f;
    boolean headDark = false;

    // What the air the beam passed through gives off.
    int wake = WAKE_NONE;
    float wakeSpacing = 0.7f;
    float wakeLife = 22.0f;
    float wakeSize = 0.22f;
    float wakeGrow = 0.55f;
    float wakeAlpha = 0.32f;
    float wakeRise = 0.035f;
    float wakeSpread = 0.25f;
    boolean wakeRainbow = false;
    /** Fire leaves smoke as well as cinders. */
    boolean smokeToo = false;

    /** How long the beam stays burned into the air after it ends. */
    float afterglow = 6.0f;

    // The strike.
    float impactScale = 1.0f;
    float impactFlash = 0.8f;
    float impactGlow = 0.5f;
    boolean impactDark = false;
    float impactRing = 0.0f;
    boolean ringInward = false;
    boolean flatRing = false;
    int impactArcs = 0;
    float arcJag = 0.18f;
    int debris = 0;
    /** Blocks a tick squared; negative floats upward. */
    float debrisGravity = 0.02f;
    float debrisSpeed = 0.22f;
    boolean pillar = false;
    boolean mistBurst = false;
    /** How hard a strike close by flashes the edges of the view. */
    float screenFlash = 0.0f;
    /**
     * The colour of the straight-alpha layers (sheath, dark head, smoke wake, dark burst): near-black for the
     * curses, translucent blue for water.
     */
    float shadeR = 0.006f, shadeG = 0.030f, shadeB = 0.012f;
    /** A second wake laid down as well as the first (water sprays and leaves mist), or null. */
    BeamStyle second;
    /** The strike rolls a wave front out across the ground in the direction the beam was going. */
    boolean waveFront = false;
    /**
     * Drawn as water, not light: a churning translucent body with foam and a highlight, real falling droplets and
     * mist, and a splash of water rather than a flash (SpellBeamFxRenderer's liquid drawing).
     */
    boolean liquid = false;
    /** A second shockwave chasing the first (a concussive blast). */
    boolean doubleRing = false;
    /** The body and glow run through the colours of the rainbow (Riddikulus). */
    boolean bodyRainbow = false;
    /** The strike's arcs are parallel straight cuts across the beam's line (a blade). */
    boolean parallelSlashes = false;

    BeamStyle colours(int core, int body, int glow) {
        coreR = r(core);
        coreG = g(core);
        coreB = b(core);
        bodyR = r(body);
        bodyG = g(body);
        bodyB = b(body);
        glowR = r(glow);
        glowG = g(glow);
        glowB = b(glow);
        return this;
    }

    BeamStyle tail(float tail) {
        this.tail = tail;
        return this;
    }

    BeamStyle widths(float core, float body, float glow) {
        this.coreWidth = core;
        this.bodyWidth = body;
        this.glowWidth = glow;
        return this;
    }

    BeamStyle alphas(float body, float glow) {
        this.bodyAlpha = body;
        this.glowAlpha = glow;
        return this;
    }

    BeamStyle jitter(float jitter) {
        this.jitter = jitter;
        return this;
    }

    BeamStyle writhe(float size, float freq, float speed) {
        this.writhe = size;
        this.writheFreq = freq;
        this.writheSpeed = speed;
        return this;
    }

    BeamStyle flicker(float flicker) {
        this.flicker = flicker;
        return this;
    }

    BeamStyle darkSheath(float width, float alpha) {
        this.sheathWidth = width;
        this.sheathAlpha = alpha;
        this.sheathDark = true;
        return this;
    }

    BeamStyle lightSheath(float width, float alpha) {
        this.sheathWidth = width;
        this.sheathAlpha = alpha;
        this.sheathDark = false;
        return this;
    }

    BeamStyle crackle(int count, float radius) {
        this.strands = count;
        this.strandRadius = radius;
        this.strandsCrackle = true;
        return this;
    }

    BeamStyle twist(int count, float radius, float twist, float spin) {
        this.strands = count;
        this.strandRadius = radius;
        this.strandTwist = twist;
        this.strandSpin = spin;
        this.strandsCrackle = false;
        return this;
    }

    BeamStyle beads(int count, float speed, float size) {
        this.beads = count;
        this.beadSpeed = speed;
        this.beadSize = size;
        return this;
    }

    BeamStyle shedRings(float every, float radius, float life) {
        this.ringEvery = every;
        this.ringRadius = radius;
        this.ringLife = life;
        return this;
    }

    BeamStyle head(float radius, float core, float alpha, boolean dark) {
        this.headRadius = radius;
        this.headCore = core;
        this.headAlpha = alpha;
        this.headDark = dark;
        return this;
    }

    BeamStyle wake(int kind, float spacing, float life, float size, float alpha, float rise) {
        this.wake = kind;
        this.wakeSpacing = spacing;
        this.wakeLife = life;
        this.wakeSize = size;
        this.wakeAlpha = alpha;
        this.wakeRise = rise;
        return this;
    }

    BeamStyle wakeShape(float grow, float spread) {
        this.wakeGrow = grow;
        this.wakeSpread = spread;
        return this;
    }

    BeamStyle rainbow() {
        this.wakeRainbow = true;
        return this;
    }

    BeamStyle smokeToo() {
        this.smokeToo = true;
        return this;
    }

    BeamStyle afterglow(float ticks) {
        this.afterglow = ticks;
        return this;
    }

    BeamStyle strike(float scale, float flash, float glow) {
        this.impactScale = scale;
        this.impactFlash = flash;
        this.impactGlow = glow;
        return this;
    }

    BeamStyle darkBurst() {
        this.impactDark = true;
        return this;
    }

    BeamStyle ring(float radius, boolean inward, boolean flat) {
        this.impactRing = radius;
        this.ringInward = inward;
        this.flatRing = flat;
        return this;
    }

    BeamStyle arcs(int count, float jag) {
        this.impactArcs = count;
        this.arcJag = jag;
        return this;
    }

    BeamStyle debris(int count, float gravity, float speed) {
        this.debris = count;
        this.debrisGravity = gravity;
        this.debrisSpeed = speed;
        return this;
    }

    BeamStyle pillar() {
        this.pillar = true;
        return this;
    }

    BeamStyle mistBurst() {
        this.mistBurst = true;
        return this;
    }

    BeamStyle shade(int rgb) {
        this.shadeR = r(rgb);
        this.shadeG = g(rgb);
        this.shadeB = b(rgb);
        return this;
    }

    /** A second wake of {@code kind} with its own spacing, life, size, alpha and rise. */
    BeamStyle wakeToo(int kind, float spacing, float life, float size, float alpha, float rise) {
        BeamStyle copy = asJet();
        copy.tail = tail;
        copy.second = null;
        copy.wake(kind, spacing, life, size, alpha, rise);
        this.second = copy;
        return this;
    }

    BeamStyle liquid() {
        this.liquid = true;
        return this;
    }

    BeamStyle waveFront() {
        this.waveFront = true;
        return this;
    }

    BeamStyle doubleRing() {
        this.doubleRing = true;
        return this;
    }

    BeamStyle bodyRainbow() {
        this.bodyRainbow = true;
        return this;
    }

    BeamStyle parallelSlashes() {
        this.parallelSlashes = true;
        return this;
    }

    BeamStyle screenFlash(float strength) {
        this.screenFlash = strength;
        return this;
    }

    /** This style drawn as a full jet: a beam held between two wands shows all of itself. */
    BeamStyle asJet() {
        try {
            BeamStyle copy = (BeamStyle) super.clone();
            copy.tail = JET;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /** A copy whose body and glow are pulled {@code amount} of the way toward (r, g, b); the core stays. */
    BeamStyle tintedToward(float r, float g, float b, float amount) {
        BeamStyle copy = asJet();
        copy.tail = tail;
        copy.bodyR += (r - bodyR) * amount;
        copy.bodyG += (g - bodyG) * amount;
        copy.bodyB += (b - bodyB) * amount;
        copy.glowR += (r - glowR) * amount;
        copy.glowG += (g - glowG) * amount;
        copy.glowB += (b - glowB) * amount;
        return copy;
    }

    /** True when the beam runs all the way back to the wand. */
    boolean isJet() {
        return tail >= JET;
    }

    private static float r(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    private static float g(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    private static float b(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }
}
