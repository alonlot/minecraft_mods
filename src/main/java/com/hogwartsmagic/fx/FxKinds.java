package com.hogwartsmagic.fx;

/**
 * The cinematic effect kinds {@link Fx} can send. Each feature adds its own kinds here with a one-line note on the
 * numbers that ride with it (params[0], params[1], ...). Strings, not an enum: a client that doesn't know a kind
 * ignores it, and switch-on-enum has failed to load in this mod's render code before.
 */
public final class FxKinds {

    private FxKinds() {
    }

    /** The view shakes. params: strength, radius (blocks), duration (ticks), frequency (1 = normal). */
    public static final String SHAKE = "shake";

    /** A soot scorch on the faces round (x, y, z). params: radius (blocks), seconds. Handled by client.fx.Marks. */
    public static final String MARK_SCORCH = "mark_scorch";
    /** Glacius frost on the faces round (x, y, z). params: radius (blocks), seconds. Handled by client.fx.Marks. */
    public static final String MARK_FROST = "mark_frost";

    /**
     * A big moment at (x, y, z) about entityId (a killing blow, a boss falling). params: weight (0..1: how big; 1
     * is a boss dying or a Killing Curse landing). Handled by client.fx.Cinematic's owner.
     */
    public static final String MOMENT = "moment";
    // Lingering marks (client.fx.Marks): MARK_SCORCH takes an optional third param, a delay in ticks before it appears
    // (a Bombarda blast waits for the blocks it breaks).

    // ---- Boss first sighting ----

    /**
     * Sent to ONE player the first time they see a great beast: the black bars, the slow push-in and the title card.
     * Position is the beast's, entityId its id. params: beast index (into fx.BossBeasts.IDS). Handled by
     * client.fx.intro.BossIntroClient.
     */
    public static final String BOSS_INTRO = "boss_intro";

    // --- Creatures: the big ones hit like the spells do (builder A) ---

    /**
     * A creature hits the ground at (x, y, z): an expanding ring of dust rolls outwards and settles, chunks of the
     * ground there are flung up, and a faint shockwave ring crosses the ground. params: radius (blocks the ring
     * reaches), strength (0.3 a footfall .. 1 a Giant's kick .. 1.5 a dragon landing). Handled by
     * client.fx.creature.SlamFx.
     */
    public static final String SLAM = "slam";

    // --- Tomb curses (Cursebreaker's Desert). Handled by client.fx.CurseFx. The curse id is TombCurse.ordinal(). ---

    /**
     * A cursed tile goes off at (x, y, z), the top centre of the tile: a rotating circle of glowing hieroglyphs opens
     * on the floor and sigils spiral up. params: curse id, wind-up (ticks until the curse lands).
     */
    public static final String CURSE_TRIGGER = "curse_trigger";
    /** The curse lands on entityId at (x, y, z). params: curse id, how long it lasts (ticks; Withering's overlay). */
    public static final String CURSE_LAND = "curse_land";
    /** Revelio's wave runs out across the floor from (x, y, z). params: radius (blocks), speed (blocks a tick). */
    public static final String CURSE_WAVE = "curse_wave";
    /** The wave reaches a hidden tile at (x, y, z) and shows it: a sigil rises. params: curse id, or -1 for a wall. */
    public static final String CURSE_REVEAL = "curse_reveal";
    /** A shown curse is lifted out of its tile at (x, y, z) and breaks into gold. params: curse id. */
    public static final String CURSE_LIFT = "curse_lift";

    // --- The Horcrux rite. Position is the vessel's heart (block centre, ~1 block up); entityId the soul's owner.
    // Handled by client.fx.HorcruxFx. ---

    /** A Killing Curse kill over the altar wakes it: a crimson shock and the rite circle opens. No params. */
    public static final String HORCRUX_RITE = "horcrux_rite";
    /** One pulse of the rite: a tether of soul is torn from the owner into the heart. params: pulse (1..), pulses. */
    public static final String HORCRUX_FEED = "horcrux_feed";
    /** The soul fragment is sealed in: implosion, a pillar of green light, a ground-wide shockwave. No params. */
    public static final String HORCRUX_BOUND = "horcrux_bound";
    /** The rite breaks (the owner strayed or died): the tether snaps back. No params. */
    public static final String HORCRUX_ABORT = "horcrux_abort";
    /** The vessel is destroyed: it sucks in, bursts, and the trapped soul shrieks out of it. params: violent (0/1). */
    public static final String HORCRUX_SHATTER = "horcrux_shatter";
    /** The owner's body reforms at the vessel: black smoke spirals into them. Position is their feet. No params. */
    public static final String HORCRUX_REBIRTH = "horcrux_rebirth";
}
