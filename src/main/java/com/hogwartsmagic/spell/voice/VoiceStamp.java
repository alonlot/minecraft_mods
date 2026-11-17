package com.hogwartsmagic.spell.voice;

/**
 * Proof, held on the server, that the player just said a spell (the {@code voiceOnlySpells} gamerule). Pure Java,
 * unit tested.
 *
 * <p>The client sends a stamp right before the select and cast packets of a voice cast. While the gamerule is on,
 * a cast is accepted only with a fresh stamp for the selected spell:</p>
 * <ul>
 *   <li>one cast (beam or self-cast, as stamped) per stamp;</li>
 *   <li>channel heartbeats for that spell (holds that the voice cast started, or that right-click takes over in
 *   the 1.5 s window) are accepted and keep the stamp fresh, so a long hold never times out;</li>
 *   <li>after {@link #WINDOW_TICKS} with neither, the stamp is spent. A release always passes and ends it.</li>
 * </ul>
 * <p>This is a game rule, not anti-cheat: a modified client can send stamps of its own. It costs such a client
 * nothing it could not already do with the cast packets.</p>
 */
public final class VoiceStamp {
    /** 3 s: the 1.5 s hold window, plus network time, plus a margin. */
    public static final int WINDOW_TICKS = 60;

    private final String spellId;
    private final boolean selfCast;
    private long lastTick;
    private boolean castUsed;

    public VoiceStamp(String spellId, boolean selfCast, long now) {
        this.spellId = spellId;
        this.selfCast = selfCast;
        this.lastTick = now;
    }

    public String spellId() {
        return spellId;
    }

    private boolean fresh(String selectedId, long now) {
        return spellId.equals(selectedId) && now - lastTick <= WINDOW_TICKS;
    }

    /**
     * Whether a cast packet for {@code selectedId} may pass, and spends or refreshes the stamp to match.
     * {@code heartbeat} is a channel heartbeat; {@code selfCastPacket} is a cast on the caster.
     */
    public boolean allowsCast(String selectedId, boolean heartbeat, boolean selfCastPacket, long now) {
        if (!fresh(selectedId, now)) {
            return false;
        }
        if (heartbeat) {
            if (selfCastPacket) {
                return false; // no heartbeat is ever a self-cast
            }
            lastTick = now;
            return true;
        }
        if (castUsed || selfCastPacket != selfCast) {
            return false;
        }
        castUsed = true;
        lastTick = now;
        return true;
    }

    /** An Imperio Master hold start (its own packet): needs a fresh stamp for the spell, refreshes it. */
    public boolean allowsHoldStart(String selectedId, long now) {
        if (!fresh(selectedId, now)) {
            return false;
        }
        lastTick = now;
        return true;
    }
}
