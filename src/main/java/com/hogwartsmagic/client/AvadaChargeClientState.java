package com.hogwartsmagic.client;

import com.hogwartsmagic.client.sounds.AvadaChargeSoundInstance;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mind.AvadaChargeFx;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is winding up the Killing Curse as far as this client knows, and how it ended. Fed by
 * {@code avadaCharge} packets. A charge whose end never arrives (the caster walked out of tracking range, a boss
 * that dropped its target) lapses a little after it should have finished and fades out like a dropped one.
 */
@Mod.EventBusSubscriber(modid = com.hogwartsmagic.ExampleMod.MODID, value = Dist.CLIENT)
public final class AvadaChargeClientState {
    /** How long the aftermath of each ending plays, in ticks. */
    public static final float FIRED_TICKS = 10.0f;
    public static final float BROKEN_TICKS = 14.0f;
    public static final float DROPPED_TICKS = 6.0f;
    /** A charge with no end packet this long past its duration counts as dropped. */
    private static final int GRACE_TICKS = 10;

    private static final Map<Integer, Charge> CHARGES = new ConcurrentHashMap<>();

    private AvadaChargeClientState() {
    }

    public static final class Charge {
        public final int entityId;
        public final double start;
        public final int duration;
        public final int seed;
        /** A per-charge angle in [0, 2pi), for offsetting oscillations (the raw seed is far too big for sin). */
        public final float phase;
        /** -1 while gathering, otherwise the {@link AvadaChargeFx} phase it ended with. */
        private byte endPhase = -1;
        private double endTime;
        private float chargeAtEnd;

        Charge(int entityId, double start, int duration) {
            this.entityId = entityId;
            this.start = start;
            this.duration = Math.max(1, duration);
            this.seed = (int) (entityId * 0x9E3779B1L + (long) start * 0x85EBCA6BL);
            this.phase = ((seed >>> 8) & 0xFFFF) / 65536.0f * Mth.TWO_PI;
        }

        /** Ticks since the windup began. */
        public double elapsed(double now) {
            return now - start;
        }

        /** 0 at the start of the windup, 1 at the moment it fires; frozen where it was once it ends. */
        public float charge(double now) {
            if (endPhase >= 0) {
                return chargeAtEnd;
            }
            return Mth.clamp((float) ((now - start) / duration), 0.0f, 1.0f);
        }

        public boolean ended() {
            return endPhase >= 0;
        }

        public byte endPhase() {
            return endPhase;
        }

        /** Ticks since it ended (0 while still gathering). */
        public double sinceEnd(double now) {
            return endPhase >= 0 ? now - endTime : 0.0;
        }

        /** 0 to 1 through the aftermath of the ending. */
        public float aftermath(double now) {
            if (endPhase < 0) {
                return 0.0f;
            }
            return Mth.clamp((float) (sinceEnd(now) / aftermathTicks(endPhase)), 0.0f, 1.0f);
        }

        void end(byte phase, double now) {
            if (endPhase >= 0) {
                return;
            }
            // A fired curse was full by definition, however far this client's clock had got.
            chargeAtEnd = phase == AvadaChargeFx.FIRED ? 1.0f : charge(now);
            endPhase = phase;
            endTime = now;
        }

        boolean expired(double now) {
            if (endPhase < 0) {
                if (now - start > duration + GRACE_TICKS) {
                    end(AvadaChargeFx.DROPPED, now);
                }
                return false;
            }
            return now - endTime > aftermathTicks(endPhase);
        }
    }

    private static float aftermathTicks(byte phase) {
        if (phase == AvadaChargeFx.FIRED) {
            return FIRED_TICKS;
        }
        return phase == AvadaChargeFx.BROKEN ? BROKEN_TICKS : DROPPED_TICKS;
    }

    public static void accept(SpellPackets.AvadaChargePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        if (packet.phase() == AvadaChargeFx.BEGIN) {
            Charge charge = new Charge(packet.casterId(), now, packet.durationTicks());
            CHARGES.put(packet.casterId(), charge);
            Entity entity = minecraft.level.getEntity(packet.casterId());
            if (entity instanceof LivingEntity living) {
                minecraft.getSoundManager().play(new AvadaChargeSoundInstance(living, charge));
            }
            return;
        }
        Charge charge = CHARGES.get(packet.casterId());
        if (charge != null) {
            charge.end(packet.phase(), now);
        }
    }

    /** The caster's charge, gathering or in its aftermath, or null. */
    public static Charge get(int entityId) {
        return CHARGES.get(entityId);
    }

    public static boolean isEmpty() {
        return CHARGES.isEmpty();
    }

    public static Iterable<Charge> all() {
        return CHARGES.values();
    }

    /** Drop charges whose aftermath is over. Called once a frame by the renderer. */
    public static void prune(double now) {
        CHARGES.values().removeIf(charge -> charge.expired(now));
    }

    /** The green the gathering curse throws on what is round the caster, and its flare on the cast (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Charge charge : CHARGES.values()) {
            if (!(minecraft.level.getEntity(charge.entityId) instanceof LivingEntity caster)) {
                continue;
            }
            float c = charge.charge(now);
            float strength = 0.65f * c;
            if (charge.ended()) {
                float after = charge.aftermath(now);
                strength = charge.endPhase() == AvadaChargeFx.FIRED ? 1.0f * (1.0f - after) : strength * (1.0f - after);
            }
            Vec3 tip = AvadaChargePose.wandTip(caster, charge, partial, AvadaChargePose.isOwnFirstPerson(caster));
            sink.add(tip, 0.22f, 1.0f, 0.34f, 2.0f + 3.5f * c, strength);
        }
    }

    /** Client game time with the partial tick, the clock every charge is measured on. */
    public static double now(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + partialTick;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CHARGES.clear();
    }
}
