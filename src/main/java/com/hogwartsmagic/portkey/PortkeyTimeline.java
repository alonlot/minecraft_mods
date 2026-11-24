package com.hogwartsmagic.portkey;

/**
 * Every number on a Portkey journey's timeline, as pure curves, so a tuning pass touches one file. Client code reads
 * these each frame; nothing here touches Minecraft.
 *
 * <p>What the traveller sees, in order. The charge (three seconds): the boot rises into the middle of the view and
 * trembles harder and faster, the view narrows, the frame starts to swirl very slightly towards the boot and the
 * edges pick up a cold blue-white glow. The hook: a white flash, the view flung wide as if yanked from behind the
 * navel, then collapsing inwards as the world is wrung into a spinning tunnel of coloured streaks. The tunnel: it
 * spins faster, its centre wanders and the camera rolls, the boot held still in the middle of it all. The exit: the
 * tunnel lets go, the new world unwinds out of the twist, and the landing thumps, rolls and staggers.
 *
 * <p>Notation: {@code t} ticks since the charge began, {@code h} ticks since the hook, {@code x} ticks since the exit
 * began (negative: that phase has not begun). Roll and pitch are degrees, twist radians, fov a multiplier, spin the
 * tunnel's turn in radians.
 */
public final class PortkeyTimeline {

    private PortkeyTimeline() {
    }

    /** One frame of the traveller's own screen. Centre is where the tunnel's middle sits on screen (0..1). */
    public record Frame(float mix, float twist, float pull, float blur, float chroma, float glow, float fov,
                       float roll, float pitch, float spin, float centreX, float centreY) {
    }

    // ---- palette (shared by the shader, the HUD fallback and the world effects) ------------------------------

    /** The Portkey's core light, #D6ECFF. */
    public static final float CORE_R = 0.84f, CORE_G = 0.93f, CORE_B = 1.0f;
    /** Its glow, #BFE0FF. */
    public static final float GLOW_R = 0.75f, GLOW_G = 0.88f, GLOW_B = 1.0f;
    /** Portkey gold, #FFD98A. */
    public static final float GOLD_R = 1.0f, GOLD_G = 0.85f, GOLD_B = 0.54f;
    /** Cyan, #8FE3FF. */
    public static final float CYAN_R = 0.56f, CYAN_G = 0.89f, CYAN_B = 1.0f;

    // ---- the charge -------------------------------------------------------------------------------------------

    /** The charge ease is (t / CHARGE_TICKS) to this power: slow to start, urgent at the end. */
    public static final float CHARGE_EASE_POWER = 1.6f;
    /** The view narrows by this share at the end of the charge. */
    public static final float CHARGE_FOV_NARROW = 0.12f;
    /** The frame is pulled towards the boot by this much (shader Pull) at the end of the charge. */
    public static final float CHARGE_PULL = 0.035f;
    /** And swirled by this many radians (shader Twist). */
    public static final float CHARGE_TWIST = 0.06f;
    /** Colour fringes (px) creep in over the last third of the charge. */
    public static final float CHARGE_CHROMA = 1.5f;
    public static final float CHARGE_CHROMA_FROM = 40.0f;
    /** The edges lift towards blue-white by this much at the end of the charge. */
    public static final float CHARGE_EDGE_GLOW = 0.25f;
    /** The view's own tremble (degrees of angle noise) at the start and the added amount at the end. */
    public static final float CHARGE_NOISE_DEG = 0.05f, CHARGE_NOISE_GAIN_DEG = 0.55f;
    /** The boot rises into the centre of the view over this many ticks. */
    public static final float BOOT_RISE_TICKS = 10.0f;
    /** The boot's tremble (blocks) at the start and the added amount at the end... */
    public static final float BOOT_TREMBLE = 0.004f, BOOT_TREMBLE_GAIN = 0.03f;
    /** ...and how fast it trembles (Hz) at the start and the end. */
    public static final float BOOT_TREMBLE_HZ_FROM = 1.7f, BOOT_TREMBLE_HZ_TO = 6.0f;
    /** The soft glow card behind the boot: alpha at the start and the added amount at the end. */
    public static final float BOOT_GLOW = 0.15f, BOOT_GLOW_GAIN = 0.6f;
    /** The HUD's blue-white edge vignette at full charge, and its heartbeat period (ticks) from slow to fast. */
    public static final float VIGNETTE_ALPHA = 0.25f;
    public static final float VIGNETTE_BEAT_FROM = 18.0f, VIGNETTE_BEAT_TO = 5.0f;
    /** The charge hum: volume at the start and the added amount at the end; pitch likewise. */
    public static final float CHARGE_VOLUME = 0.35f, CHARGE_VOLUME_GAIN = 0.65f;
    public static final float CHARGE_PITCH = 0.9f, CHARGE_PITCH_GAIN = 0.2f;
    /** A cancelled charge decays over this many ticks, the hum sliding down to this share of its pitch. */
    public static final float CANCEL_TICKS = 8.0f;
    public static final float CANCEL_PITCH = 0.6f;

    // ---- the hook ---------------------------------------------------------------------------------------------

    /** The view is flung wide to this at h = 1... */
    public static final float HOOK_FOV_PUNCH = 1.30f;
    /** ...then collapses to this by h = HOOK_COLLAPSE_TICKS. */
    public static final float HOOK_FOV_COLLAPSE = 0.72f;
    public static final float HOOK_COLLAPSE_TICKS = 4.0f;
    /** The pitch kick (degrees, forward) and its decay time constant (ticks). */
    public static final float HOOK_PITCH_KICK = 7.0f, HOOK_PITCH_TAU = 3.0f;
    /** The shader ramps (ease-in) to these over HOOK_RAMP_TICKS: wrung, pulled, fringed and smeared. */
    public static final float HOOK_RAMP_TICKS = 4.0f;
    public static final float HOOK_PULL = 0.32f, HOOK_TWIST = 1.8f, HOOK_CHROMA = 7.0f, HOOK_BLUR = 0.8f;
    /** The tunnel fades in between these ticks after the hook. */
    public static final float HOOK_MIX_FROM = 1.0f, HOOK_MIX_TO = 3.5f;
    /** Edge glow at the end of the hook (it also brightens the tunnel's vanishing point). */
    public static final float HOOK_GLOW = 0.6f;
    /** The hook's flash (rgb, strength, ticks) and its local shake (strength, ticks, frequency). */
    public static final float HOOK_FLASH = 0.75f;
    public static final int HOOK_FLASH_TICKS = 6;
    public static final float HOOK_SHAKE = 1.1f, HOOK_SHAKE_TICKS = 8.0f, HOOK_SHAKE_FREQ = 2.2f;
    /** The boot flares to this scale at the hook and jerks this far forward (blocks). */
    public static final float HOOK_BOOT_FLARE = 2.0f, HOOK_BOOT_JERK = 0.15f;

    // ---- the tunnel -------------------------------------------------------------------------------------------

    /** Tunnel spin speed (radians a second) at the hook and HOOK_SPIN_RAMP ticks later. */
    public static final float SPIN_FROM = 0.9f, SPIN_TO = 2.5f, SPIN_RAMP_TICKS = 10.0f;
    /** The tunnel centre's wander: amplitudes (screen share) and rates (radians a tick). */
    public static final float TUMBLE_X = 0.06f, TUMBLE_X_RATE = 0.21f;
    public static final float TUMBLE_Y = 0.045f, TUMBLE_Y_RATE = 0.33f;
    /** The camera roll in the tunnel: two sines (degrees, radians a tick), faded in over ROLL_FADE_TICKS. */
    public static final float ROLL_A = 22.0f, ROLL_A_RATE = 0.19f, ROLL_B = 9.0f, ROLL_B_RATE = 0.47f;
    public static final float ROLL_FADE_TICKS = 6.0f;
    /** The tunnel's buffeting: a local shake (strength, ticks, frequency) refreshed every TRANSIT_SHAKE_EVERY. */
    public static final float TRANSIT_SHAKE = 0.35f, TRANSIT_SHAKE_TICKS = 6.0f, TRANSIT_SHAKE_FREQ = 1.6f;
    public static final int TRANSIT_SHAKE_EVERY = 5;
    /** The view widens from the collapse back to this by TRANSIT_FOV_TICKS after the hook. */
    public static final float TRANSIT_FOV = 0.85f, TRANSIT_FOV_TICKS = 12.0f;
    /** Letterbox bars come in at this many ticks after the hook, held for TRANSIT_LETTERBOX_HOLD. */
    public static final int TRANSIT_LETTERBOX_AT = 3, TRANSIT_LETTERBOX_HOLD = 24;
    /** The wind loop fades in over this many ticks, and out over WIND_OUT_TICKS of the exit. */
    public static final float WIND_IN_TICKS = 4.0f, WIND_OUT_TICKS = 6.0f;

    // ---- the exit and landing ---------------------------------------------------------------------------------

    /** The tunnel lets go over this many ticks (ease-out). */
    public static final float EXIT_MIX_TICKS = 5.0f;
    /** The new world unwinds out of the twist over this many ticks. */
    public static final float EXIT_TWIST_TICKS = 10.0f;
    public static final float EXIT_BLUR_TICKS = 8.0f, EXIT_CHROMA_TICKS = 6.0f;
    /** The frame is pushed outwards (negative pull) as it opens, settling by EXIT_PULL_TICKS. */
    public static final float EXIT_PUSH = -0.12f, EXIT_PUSH_AT = 1.5f, EXIT_PULL_TICKS = 6.0f;
    /** When the feet hit the ground, ticks into the exit. */
    public static final float LANDING_AT = 2.0f;
    /** FOV at the landing, settling to normal by LANDING_FOV_TICKS into the exit. */
    public static final float LANDING_FOV = 0.93f, LANDING_FOV_TICKS = 10.0f;
    /** Landing roll: amplitude (degrees, in the direction the tunnel was rolling), decay (ticks), wobble rate. */
    public static final float LANDING_ROLL = 10.0f, LANDING_ROLL_TAU = 4.0f, LANDING_ROLL_RATE = 0.9f;
    /** The stagger: pitch dips this many degrees with this decay time constant (ticks). */
    public static final float LANDING_PITCH = 9.0f, LANDING_PITCH_TAU = 5.0f;
    /** The landing's own thump: local shake (strength, ticks, frequency) and a white flash (strength, ticks). */
    public static final float LANDING_SHAKE = 1.6f, LANDING_SHAKE_TICKS = 12.0f, LANDING_SHAKE_FREQ = 1.4f;
    public static final float LANDING_FLASH = 0.25f;
    public static final int LANDING_FLASH_TICKS = 4;
    /** An Old Portkey's boot sinks and shrinks away from this many ticks into the exit, over CRUMBLE_TICKS. */
    public static final float CRUMBLE_AT = 12.0f, CRUMBLE_TICKS = 10.0f;
    /** After arriving, the exit waits for the feet's chunk section to be drawn, but no longer than this. */
    public static final float SECTION_WAIT_MAX = 40.0f;

    // ---- the world (what everyone near sees) -------------------------------------------------------------------

    /** Glow at the hand: radius at the start of the charge and the added amount at the end. */
    public static final float WORLD_GLOW_RADIUS = 0.18f, WORLD_GLOW_RADIUS_GAIN = 0.42f;
    /** The three spiral ribbons: segments, start radius, spin (radians a tick) from and to, alpha from and gain. */
    public static final int SPIRAL_RIBBONS = 3, SPIRAL_SEGMENTS = 24;
    public static final float SPIRAL_RADIUS = 2.0f, SPIRAL_SPIN_FROM = 0.05f, SPIRAL_SPIN_TO = 0.35f;
    public static final float SPIRAL_ALPHA = 0.2f, SPIRAL_ALPHA_GAIN = 0.8f;
    /** A rune ripple runs out along the ground every RIPPLE_EVERY ticks, to RIPPLE_RADIUS over RIPPLE_TICKS. */
    public static final int RIPPLE_EVERY = 15;
    public static final float RIPPLE_RADIUS = 2.6f, RIPPLE_TICKS = 20.0f;
    /** Runes sit on a ring this wide and glint as the ripple passes. */
    public static final int RUNE_COUNT = 12;
    public static final float RUNE_RING = 1.4f;
    /** Motes drawn in from this radius, from one to four a tick. */
    public static final float MOTE_RADIUS = 2.2f;
    public static final int MOTES_FROM = 1, MOTES_TO = 4;
    /** The light the charge throws on its surroundings: radius and intensity, from and gain. */
    public static final float LIGHT_RADIUS = 3.0f, LIGHT_RADIUS_GAIN = 3.0f;
    public static final float LIGHT_INTENSITY = 0.2f, LIGHT_INTENSITY_GAIN = 0.6f;
    /** The implosion: a sphere of light collapsing from this radius over IMPLOSION_TICKS. */
    public static final float IMPLOSION_RADIUS = 1.8f, IMPLOSION_TICKS = 5.0f, IMPLOSION_ALPHA = 0.9f;
    /** Five vortex ribbons spinning up and in over VORTEX_TICKS, to a point at VORTEX_TOP above the feet. */
    public static final int VORTEX_RIBBONS = 5, VORTEX_SEGMENTS = 16;
    public static final float VORTEX_TICKS = 8.0f, VORTEX_TOP = 1.2f;
    /** The white streak (width x height, blocks) standing in the implosion between these ticks. */
    public static final float STREAK_WIDTH = 0.08f, STREAK_HEIGHT = 3.0f, STREAK_FROM = 1.0f, STREAK_TO = 4.0f;
    /** The ground shock ring at the departure and at the arrival. */
    public static final float DEPART_RING = 3.5f, DEPART_RING_TICKS = 8.0f;
    public static final float ARRIVE_RING = 4.0f, ARRIVE_RING_TICKS = 10.0f;
    /** The arrival's sphere bursting out to this radius over ARRIVE_SPHERE_TICKS. */
    public static final float ARRIVE_SPHERE = 2.2f, ARRIVE_SPHERE_TICKS = 5.0f;
    /** A light flash at each end: radius, fading over FLASH_LIGHT_TICKS. */
    public static final float FLASH_LIGHT_RADIUS = 6.0f, FLASH_LIGHT_TICKS = 8.0f;
    /** Bystanders' shake at the departure and the arrival (strength, radius, ticks): sent by the server. */
    public static final float DEPART_SHAKE = 0.4f, DEPART_SHAKE_RADIUS = 8.0f;
    public static final int DEPART_SHAKE_TICKS = 8;
    public static final float ARRIVE_SHAKE = 0.8f, ARRIVE_SHAKE_RADIUS = 10.0f;
    public static final int ARRIVE_SHAKE_TICKS = 10;
    /** The landing decal: scuff fading over this many ticks, rune ring over that many. At most MAX_DECALS. */
    public static final int DECAL_SCUFF_TICKS = 600, DECAL_GLOW_TICKS = 40, MAX_DECALS = 8;
    public static final float DECAL_CULL = 64.0f;
    /** The bind: a rune ring at the feet flashing in and out over BIND_TICKS. */
    public static final float BIND_RING = 1.0f, BIND_TICKS = 20.0f;
    /** World effects past this distance are not drawn. */
    public static final float WORLD_CULL = 48.0f;

    // ---- curves -----------------------------------------------------------------------------------------------

    /** The charge's ease, 0 at the start, 1 at the hook. */
    public static float chargeEase(float t) {
        float u = clamp01(t / PortkeyRules.CHARGE_TICKS);
        return (float) Math.pow(u, CHARGE_EASE_POWER);
    }

    public static float chargeVolume(float e) {
        return CHARGE_VOLUME + CHARGE_VOLUME_GAIN * clamp01(e);
    }

    public static float chargePitch(float e) {
        return CHARGE_PITCH + CHARGE_PITCH_GAIN * clamp01(e);
    }

    /** The view's tremble (degrees) at charge ease {@code e}. */
    public static float chargeNoiseDegrees(float e) {
        return CHARGE_NOISE_DEG + CHARGE_NOISE_GAIN_DEG * clamp01(e);
    }

    /** The boot's tremble amplitude (blocks) and rate (Hz) at charge ease {@code e}. */
    public static float bootTremble(float e) {
        return BOOT_TREMBLE + BOOT_TREMBLE_GAIN * clamp01(e);
    }

    public static float bootTrembleHz(float e) {
        return lerp(BOOT_TREMBLE_HZ_FROM, BOOT_TREMBLE_HZ_TO, clamp01(e));
    }

    /**
     * The edge vignette's heartbeat over the charge: its phase in beats at tick {@code t}, the period shrinking from
     * slow to fast as the charge builds. Integrated in half-tick steps (at most 120), so it never jumps.
     */
    public static float vignetteBeats(float t) {
        float beats = 0.0f;
        float step = 0.5f;
        float end = Math.max(0.0f, Math.min(t, PortkeyRules.CHARGE_TICKS * 2.0f));
        for (float s = 0.0f; s < end; s += step) {
            float dt = Math.min(step, end - s);
            beats += dt / lerp(VIGNETTE_BEAT_FROM, VIGNETTE_BEAT_TO, chargeEase(s + dt * 0.5f));
        }
        return beats;
    }

    /** The tunnel's turn (radians) h ticks after the hook: the integral of a spin speeding up. */
    public static float spinAngle(float h) {
        if (h <= 0.0f) {
            return 0.0f;
        }
        float ramp = SPIN_RAMP_TICKS;
        float u = clamp01(h / ramp);
        // Integral of smoothstep(0, ramp, s) ds from 0 to min(h, ramp) is ramp * (u^3 - u^4 / 2).
        float eased = ramp * (u * u * u - 0.5f * u * u * u * u) + Math.max(0.0f, h - ramp);
        float radiansPerTick = 1.0f / 20.0f;
        return (SPIN_FROM * h + (SPIN_TO - SPIN_FROM) * eased) * radiansPerTick;
    }

    /** Whether the tunnel may let go: after the minimum, once arrived and the ground is drawn, or at the limit. */
    public static boolean exitReady(float h, boolean arrived, boolean sectionReady, float sinceArrival) {
        if (h >= PortkeyRules.TRANSIT_MAX_TICKS) {
            return true;
        }
        if (h < PortkeyRules.TRANSIT_MIN_TICKS || !arrived) {
            return false;
        }
        return sectionReady || sinceArrival >= SECTION_WAIT_MAX;
    }

    /** The traveller's own frame. See the class notes for t, h and x; {@code rollAtExit} is the roll the exit began with. */
    public static Frame self(float t, float h, float x, float rollAtExit) {
        if (h < 0.0f) {
            return charge(t);
        }
        Frame tunnel = tunnel(h);
        if (x < 0.0f) {
            return tunnel;
        }
        return exit(tunnel, x, rollAtExit);
    }

    private static Frame charge(float t) {
        float e = chargeEase(t);
        float chroma = CHARGE_CHROMA * smoothstep(CHARGE_CHROMA_FROM, PortkeyRules.CHARGE_TICKS, t);
        return new Frame(0.0f, CHARGE_TWIST * e, CHARGE_PULL * e, 0.0f, chroma, CHARGE_EDGE_GLOW * e,
                1.0f - CHARGE_FOV_NARROW * e, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f);
    }

    /** The hook and the tunnel, from where the charge left everything. */
    private static Frame tunnel(float h) {
        float ramp = clamp01(h / HOOK_RAMP_TICKS);
        float in = ramp * ramp; // ease-in: the yank accelerates
        float pull = lerp(CHARGE_PULL, HOOK_PULL, in);
        float twist = lerp(CHARGE_TWIST, HOOK_TWIST, in);
        float chroma = lerp(CHARGE_CHROMA, HOOK_CHROMA, in);
        float blur = HOOK_BLUR * in;
        float glow = lerp(CHARGE_EDGE_GLOW, HOOK_GLOW, smoothstep(0.0f, 3.0f, h));
        float mix = smoothstep(HOOK_MIX_FROM, HOOK_MIX_TO, h);

        float charged = 1.0f - CHARGE_FOV_NARROW;
        float fov;
        if (h < 1.0f) {
            float u = 1.0f - (1.0f - h) * (1.0f - h); // flung out, fast then easing
            fov = lerp(charged, HOOK_FOV_PUNCH, u);
        } else if (h < HOOK_COLLAPSE_TICKS) {
            fov = lerp(HOOK_FOV_PUNCH, HOOK_FOV_COLLAPSE, smoothstep(1.0f, HOOK_COLLAPSE_TICKS, h));
        } else {
            fov = lerp(HOOK_FOV_COLLAPSE, TRANSIT_FOV, smoothstep(HOOK_COLLAPSE_TICKS, TRANSIT_FOV_TICKS, h));
        }

        float rollIn = smoothstep(0.0f, ROLL_FADE_TICKS, h);
        float roll = rollIn * (ROLL_A * (float) Math.sin(ROLL_A_RATE * h) + ROLL_B * (float) Math.sin(ROLL_B_RATE * h));
        float attack = clamp01(h / 0.5f);
        float pitch = HOOK_PITCH_KICK * attack * (float) Math.exp(-h / HOOK_PITCH_TAU);
        float tumble = mix;
        float cx = 0.5f + tumble * TUMBLE_X * (float) Math.sin(TUMBLE_X_RATE * h);
        float cy = 0.5f + tumble * TUMBLE_Y * (float) Math.sin(TUMBLE_Y_RATE * h + 1.0f);
        return new Frame(mix, twist, pull, blur, chroma, glow, fov, roll, pitch, spinAngle(h), cx, cy);
    }

    /** The tunnel letting go and the landing, blended out of the tunnel frame {@code from}. */
    private static Frame exit(Frame from, float x, float rollAtExit) {
        float out = clamp01(x / EXIT_MIX_TICKS);
        float mix = from.mix() * (1.0f - (1.0f - (1.0f - out) * (1.0f - out))); // ease-out
        float twist = from.twist() * (1.0f - smoothstep(0.0f, EXIT_TWIST_TICKS, x));
        float blur = from.blur() * (1.0f - smoothstep(0.0f, EXIT_BLUR_TICKS, x));
        float chroma = from.chroma() * (1.0f - smoothstep(0.0f, EXIT_CHROMA_TICKS, x));
        float pull = x < EXIT_PUSH_AT
                ? lerp(from.pull(), EXIT_PUSH, smoothstep(0.0f, EXIT_PUSH_AT, x))
                : lerp(EXIT_PUSH, 0.0f, smoothstep(EXIT_PUSH_AT, EXIT_PULL_TICKS, x));
        float glow = from.glow() * (1.0f - smoothstep(0.0f, EXIT_CHROMA_TICKS, x));

        float fov = x < LANDING_AT
                ? lerp(from.fov(), LANDING_FOV, smoothstep(0.0f, LANDING_AT, x))
                : lerp(LANDING_FOV, 1.0f, smoothstep(LANDING_AT, LANDING_FOV_TICKS, x));

        float side = rollAtExit < 0.0f ? -1.0f : 1.0f;
        float landRoll = side * LANDING_ROLL;
        float roll;
        if (x < LANDING_AT) {
            roll = lerp(rollAtExit, landRoll, smoothstep(0.0f, LANDING_AT, x));
        } else {
            float y = x - LANDING_AT;
            roll = landRoll * (float) Math.exp(-y / LANDING_ROLL_TAU) * (float) Math.cos(LANDING_ROLL_RATE * y);
        }
        float pitch = from.pitch() * (1.0f - smoothstep(0.0f, LANDING_AT, x));
        if (x >= LANDING_AT) {
            float y = x - LANDING_AT;
            pitch += LANDING_PITCH * clamp01(y) * (float) Math.exp(-y / LANDING_PITCH_TAU);
        }
        float cx = lerp(from.centreX(), 0.5f, out);
        float cy = lerp(from.centreY(), 0.5f, out);
        return new Frame(mix, twist, pull, blur, chroma, glow, fov, roll, pitch, from.spin(), cx, cy);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    public static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }

    public static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    public static float smoothstep(float edge0, float edge1, float v) {
        float u = clamp01((v - edge0) / (edge1 - edge0));
        return u * u * (3.0f - 2.0f * u);
    }
}
