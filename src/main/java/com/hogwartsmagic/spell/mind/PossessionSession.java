package com.hogwartsmagic.spell.mind;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.hogwartsmagic.spell.SpellType;
import java.util.UUID;

/** One possession, server side. Mutated only on the server thread by PossessionManager. */
final class PossessionSession {
    final UUID casterId;
    final UUID victimId;
    final ResourceKey<Level> dimension;
    final long startedAt;
    final int maxTicks;
    final boolean boss;
    final boolean victimIsPlayer;
    /** Started by /imperio possess: no hold needed, no mana, no cooldown, no caster-wand check. */
    final boolean debug;

    final RateWindow inputRate = new RateWindow(PossessionRules.INPUTS_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);
    final RateWindow hotbarRate = new RateWindow(PossessionRules.HOTBAR_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);
    final RateWindow castRate = new RateWindow(PossessionRules.CASTS_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);

    long lastSeen;
    long lastDrainAt;
    long lastStateAt = Long.MIN_VALUE / 2;
    long lastSnapshotAt = Long.MIN_VALUE / 2;
    long lastAttackAt = Long.MIN_VALUE / 2;
    long lastResistReportAt = Long.MIN_VALUE / 2;
    int attackCooldown = PossessionRules.ATTACK_COOLDOWN;

    float forward;
    float strafe;
    float yaw;
    float pitch;
    boolean jump;
    boolean attack;
    /** The last PuppetInputPacket sent for this session: it is re-sent only when the input changes. */
    float sentForward;
    float sentStrafe;
    float sentYaw;
    float sentPitch;
    boolean sentJump;
    boolean sentAny;
    double lastVictimY;

    /** The victim wand's selected spell before the first puppet cast, put back when the session ends (null: untouched). */
    @javax.annotation.Nullable
    SpellType prevSelectedSpell;
    boolean prevSelectedSaved;
    int forcedSlot = -1;
    /** A snapshot goes out next tick (a hotbar pick was rate-refused, so the caster's display needs a re-sync). */
    boolean snapshotDue;
    /** The puppet creeper's fuse was lit by the caster: if it vanishes, it died of it. */
    boolean fuseLit;
    /** The protected-player refusal line was already shown for this attack press. */
    boolean refusalShown;
    int resistWindow = -1;
    int resistPresses;
    boolean resistAnnounced;

    PossessionSession(UUID casterId, UUID victimId, ResourceKey<Level> dimension, long startedAt, int maxTicks,
                      boolean boss, boolean victimIsPlayer, boolean debug) {
        this.casterId = casterId;
        this.victimId = victimId;
        this.dimension = dimension;
        this.startedAt = startedAt;
        this.maxTicks = maxTicks;
        this.boss = boss;
        this.victimIsPlayer = victimIsPlayer;
        this.debug = debug;
        this.lastSeen = startedAt;
        this.lastDrainAt = startedAt;
    }
}
