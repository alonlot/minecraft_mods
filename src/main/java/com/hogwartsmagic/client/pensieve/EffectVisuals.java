package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.entity.MemoryLetterEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.VoldemortSummoningEntity;
import com.hogwartsmagic.pensieve.scene.Eyelids;
import com.hogwartsmagic.pensieve.scene.LetterFlood;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The scripted effects a scene's {@code effect} event may name ({@code SceneNames.EFFECTS}). Everything here is
 * drawn on the viewer's own client level: the entities are added with {@code ClientLevel#addEntity} under a
 * negative id and never exist on the server. {@link #tick} drives the effects that last, and {@link #clear}
 * removes everything when the memory ends.
 */
public final class EffectVisuals {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A ritual with no {@code duration} in the scene runs this long. */
    static final int DEFAULT_RITUAL_TICKS = 100;
    /** A dark mark with no {@code duration} in the scene hangs this long. */
    static final int DEFAULT_MARK_TICKS = 100;
    /** Grey edges with no {@code duration} in the scene last this long. */
    static final int DEFAULT_GREY_TICKS = 60;
    /** The grey closes in over the real gaze's full charge (BasiliskEntity.GAZE_FULL_CHARGE, 2.5 s)... */
    private static final int GREY_RISE_TICKS = 50;
    /** ...and drains over the effect's last ticks, as the real charge decays when the victim looks away. */
    private static final int GREY_FADE_TICKS = 24;
    /** Eyelids with no {@code duration} in the scene take this long (2 s). */
    static final int DEFAULT_EYES_TICKS = 40;
    /** A lightning strike every this many ticks while a ritual runs (the real ritual strikes every 10). */
    private static final int RITUAL_STRIKE_EVERY = 20;
    /** A patronus light with no {@code duration} in the scene lasts this long (4 s). */
    static final int DEFAULT_PATRONUS_TICKS = 80;
    /** The patronus light dome reaches this radius (blocks)... */
    static final double PATRONUS_RADIUS = 22.0;
    /** ...over this fraction of the duration, then fades out over the rest. */
    static final double PATRONUS_GROW_FRACTION = 0.6;
    /** The screen wash peaks at this alpha after PATRONUS_WASH_PEAK_TICKS, then fades over the next ticks. */
    static final float PATRONUS_WASH_ALPHA = 0.35f;
    static final int PATRONUS_WASH_PEAK_TICKS = 8;
    static final int PATRONUS_WASH_FADE_TICKS = 32;
    /** Only a viewer this close to the light sees the wash. */
    static final double PATRONUS_WASH_RANGE = 40.0;
    /** Ticks a ring point lives (plus 0-2): long enough to read as a band, short enough not to fill the disc. */
    static final int RING_POINT_LIFE = 4;
    /** Fiendfyre rings with no {@code duration} in the scene are born for this long (4 s)... */
    static final int DEFAULT_FIENDFYRE_TICKS = 80;
    /** ...one wave every this many ticks, from age 0, while the age is under the duration... */
    static final int FIRE_WAVE_EVERY = 16;
    /** ...each starting this far out, rolling outward this many blocks a tick, and gone at the max radius. */
    static final double FIRE_RING_START = 1.5;
    static final double FIRE_RING_SPEED = 0.25;
    static final double FIRE_RING_MAX = 22.0;
    /** Ticks one wave lives: from {@link #FIRE_RING_START} to {@link #FIRE_RING_MAX}. */
    static final int FIRE_WAVE_TICKS = (int) Math.ceil((FIRE_RING_MAX - FIRE_RING_START) / FIRE_RING_SPEED);
    /** Defaults for {@code letter_flood}: letters, emission ticks and the room half-size. */
    static final int DEFAULT_FLOOD_LETTERS = 160;
    static final int DEFAULT_FLOOD_TICKS = 120;
    static final float[] DEFAULT_FLOOD_SPREAD = {3f, 2.5f, 3f};
    /** A page-turn rustle every this many ticks while letters fly. */
    private static final int FLOOD_RUSTLE_EVERY = 4;
    private static final int MARK_RISE_TICKS = 50;
    private static final double MARK_RISE_BLOCKS = 18.0;
    /** The smallest visual scale a DarkMarkEntity accepts. */
    private static final float MARK_SCALE = 0.5f;

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<DarkMarkEntity> MARKS = new ArrayList<>();
    private static final List<LightningBolt> BOLTS = new ArrayList<>();
    /** Every client-only letter a flood spawned; {@link #clear} discards them so none outlives the memory. */
    private static final List<MemoryLetterEntity> LETTERS = new ArrayList<>();
    /** An eyelid effect has started since the last {@link #clear} (the scene's own eyes have taken over). */
    private static boolean eyesStarted;

    private EffectVisuals() {
    }

    /** Start {@code effect} at {@code at}; {@code duration} is in ticks (0 = the effect's own default). */
    public static void play(String effect, Vec3 at, int duration) {
        play(effect, at, duration, 0);
    }

    /** As {@link #play(String, Vec3, int)}, joining an effect that began {@code elapsed} ticks ago. */
    public static void play(String effect, Vec3 at, int duration, int elapsed) {
        play(effect, at, duration, elapsed, 0, null, Float.NaN);
    }

    public static void play(String effect, Vec3 at, int duration, int elapsed, int count, float[] spread, float yaw) {
        play(effect, at, duration, elapsed, count, spread, yaw, 0f);
    }

    /**
     * The full form: count, spread and yaw are the event values (letter_flood only; 0 or less, null and NaN when
     * absent), and {@code t} is the event scene time, mixed into the swarm seed so two floods in one scene differ.
     */
    public static void play(String effect, Vec3 at, int duration, int elapsed, int count, float[] spread, float yaw,
                            float t) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || effect == null || at == null) return;
        if ("summoning_ritual".equals(effect)) {
            int total = duration > 0 ? duration : DEFAULT_RITUAL_TICKS;
            if (elapsed < total) RUNNING.add(new Ritual(at, elapsed, total));
        } else if ("lightning".equals(effect)) {
            lightning(level, at);
        } else if ("dark_mark".equals(effect)) {
            int total = duration > 0 ? duration : DEFAULT_MARK_TICKS;
            if (elapsed < total) darkMark(level, at, total - elapsed);
        } else if ("grey_edges".equals(effect)) {
            int total = duration > 0 ? duration : DEFAULT_GREY_TICKS;
            if (elapsed < total) RUNNING.add(new GreyEdges(elapsed, total));
        } else if ("eyes_open".equals(effect) || "eyes_close".equals(effect)) {
            boolean opening = "eyes_open".equals(effect);
            int total = duration > 0 ? duration : DEFAULT_EYES_TICKS;
            // An opening is done at its duration; a closing stays shut until clear() ends the scene.
            if (!opening || elapsed < total) RUNNING.add(new Eyes(opening, elapsed, total));
            eyesStarted = true;
        } else if ("patronus_light".equals(effect)) {
            int total = duration > 0 ? duration : DEFAULT_PATRONUS_TICKS;
            if (elapsed < total) {
                // The sound belongs to the moment of the burst: a late joiner does not hear it again.
                if (elapsed == 0) {
                    MemorySounds.play(ModSounds.SPELL_EXPECTO_PATRONUM.get(), at, 1.2f, 1.0f);
                }
                RUNNING.add(new PatronusLight(at, elapsed, total));
            }
        } else if ("fiendfyre_rings".equals(effect)) {
            int births = duration > 0 ? duration : DEFAULT_FIENDFYRE_TICKS;
            if (elapsed < births + FIRE_WAVE_TICKS) {
                if (elapsed == 0) {
                    MemorySounds.play(ModSounds.SPELL_FIENDFYRE.get(), at, 1.6f, 0.8f);
                    MemorySounds.play(SoundEvents.GHAST_SHOOT, at, 1.2f, 0.5f);
                }
                RUNNING.add(new FiendfyreRings(at, elapsed, births));
            }
        } else if ("letter_flood".equals(effect)) {
            if (Float.isNaN(yaw)) {
                LOGGER.warn("Pensieve: letter_flood has no yaw");
                return;
            }
            int letters = count > 0 ? Math.min(count, 400) : DEFAULT_FLOOD_LETTERS;
            int total = duration > 0 ? duration : DEFAULT_FLOOD_TICKS;
            float[] room = spread != null && spread.length >= 3 ? spread : DEFAULT_FLOOD_SPREAD;
            if (elapsed == 0) {
                // The sounds belong to the moment of the burst: a late joiner does not hear them again.
                MemorySounds.play("minecraft:block.fire.extinguish", at, 1.2f, 1.0f);
                MemorySounds.play("minecraft:entity.breeze.wind_burst", at, 1.0f, 1.0f);
            }
            RUNNING.add(new Flood(level, at, yaw, room, letters, total, elapsed, floodSeed(at, t)));
        } else {
            LOGGER.warn("Pensieve: unknown effect '{}' in a memory", effect);
        }
    }

    /** A client-only lightning strike: the bolt and its thunder, with no fire, damage or server. */
    static void lightning(ClientLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.setVisualOnly(true);
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setId(MemoryEvents.nextEntityId());
        level.addEntity(bolt);
        BOLTS.add(bolt);
    }

    /**
     * A client-only Dark Mark rising from {@code from} and fading over its last 80 ticks. Shared by the
     * {@code dark_mark} effect and the morsmordre cast.
     */
    static void darkMark(ClientLevel level, Vec3 from, int lifeTicks) {
        DarkMarkEntity mark = new DarkMarkEntity(level, from.x, from.y, from.z, lifeTicks, MARK_SCALE);
        mark.setId(MemoryEvents.nextEntityId());
        mark.setOldPosAndRot();   // no first-frame streak from the world origin
        level.addEntity(mark);
        MARKS.add(mark);
        RUNNING.add(new Mark(mark, from, lifeTicks));
    }

    /** The swarm seed: the mouth position and the event time, so the same scene always flies the same way. */
    private static long floodSeed(Vec3 at, float t) {
        long h = Float.floatToIntBits((float) at.x);
        h = h * 31 + Float.floatToIntBits((float) at.y);
        h = h * 31 + Float.floatToIntBits((float) at.z);
        return h * 31 + Float.floatToIntBits(t);
    }

    /** How many letters a flood has spawned that are still alive (for the clear-on-exit check). */
    static int liveFloodLetters() {
        int n = 0;
        for (MemoryLetterEntity l : LETTERS) {
            if (!l.isRemoved()) n++;
        }
        return n;
    }

    /** Advance every running effect one client tick. */
    static void tick(ClientLevel level) {
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            if (!it.next().tick(level)) it.remove();
        }
        MARKS.removeIf(DarkMarkEntity::isRemoved);
        BOLTS.removeIf(LightningBolt::isRemoved);
    }

    /**
     * How far the {@code grey_edges} effect has closed in right now, 0 (none running) to 1, for
     * {@code PensieveOverlay}: the strongest of any that are running.
     */
    static float greyEdges(float partial) {
        float most = 0f;
        for (Running r : RUNNING) {
            if (r instanceof GreyEdges grey) most = Math.max(most, grey.intensity(partial));
        }
        return most;
    }

    /** Whether an {@code eyes_open} or {@code eyes_close} has started since the scene's visuals were last cleared. */
    static boolean eyesStarted() {
        return eyesStarted;
    }

    /** How shut the viewer's eyes are right now, 0 (open, or no lid effect) to 1: the most-closed running lid wins. */
    static float eyelids(float partial) {
        float most = 0f;
        for (Running r : RUNNING) {
            if (r instanceof Eyes eyes) most = Math.max(most, eyes.closed(partial));
        }
        return most;
    }

    /**
     * The soft white wash of a running {@code patronus_light}, 0 (none) to {@link #PATRONUS_WASH_ALPHA}, for
     * {@code PensieveOverlay}: the strongest among those within {@link #PATRONUS_WASH_RANGE} of the viewer.
     */
    static float patronusWash(float partial) {
        if (RUNNING.isEmpty()) return 0f;
        Minecraft mc = Minecraft.getInstance();
        // The directed camera, not the pinned body: the viewer sees from wherever the memory's camera is.
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float most = 0f;
        for (Running r : RUNNING) {
            if (r instanceof PatronusLight light && light.centre.distanceTo(eye) <= PATRONUS_WASH_RANGE) {
                most = Math.max(most, light.wash(partial));
            }
        }
        return most;
    }

    /** Stop everything and remove every entity an effect added. */
    static void clear() {
        RUNNING.clear();
        eyesStarted = false;
        for (DarkMarkEntity m : MARKS) m.discard();
        MARKS.clear();
        for (LightningBolt b : BOLTS) b.discard();
        BOLTS.clear();
        for (MemoryLetterEntity l : LETTERS) l.discard();
        LETTERS.clear();
    }

    private interface Running {
        /** @return false once finished */
        boolean tick(ClientLevel level);
    }

    /** {@code summoning_ritual}: the three beam columns and swirling smoke of the real ritual, with strikes. */
    private static final class Ritual implements Running {
        private final Vec3 centre;
        private final int total;
        private int age;

        Ritual(Vec3 centre, int elapsed, int total) {
            this.centre = centre;
            this.age = elapsed;
            this.total = total;
        }

        @Override
        public boolean tick(ClientLevel level) {
            VoldemortSummoningEntity.emitRitualEffects(level, centre, age);
            if (age % RITUAL_STRIKE_EVERY == 0) {
                lightning(level, centre.add((level.random.nextDouble() - 0.5) * 12.0, 0, (level.random.nextDouble() - 0.5) * 12.0));
            }
            return ++age < total;
        }
    }

    /**
     * {@code grey_edges}: the basilisk's stare seen from inside, drawn by {@code PensieveOverlay} with the real gaze
     * overlay's look. The charge climbs for the whole effect but its last {@link #GREY_FADE_TICKS}, when it drains
     * back to nothing: the moment the victim looks away.
     */
    private static final class GreyEdges implements Running {
        private final int total;
        private int age;

        GreyEdges(int elapsed, int total) {
            this.age = elapsed;
            this.total = total;
        }

        @Override
        public boolean tick(ClientLevel level) {
            return ++age < total;
        }

        /** The charge, 0 to 1, at {@code age + partial}. */
        float charge(float partial) {
            float t = Math.min(age + partial, total);
            int fadeAt = Math.max(0, total - GREY_FADE_TICKS);
            float peak = Math.min(fadeAt, GREY_RISE_TICKS) / (float) GREY_RISE_TICKS;
            if (t <= fadeAt) return Math.min(t, GREY_RISE_TICKS) / (float) GREY_RISE_TICKS;
            return peak * Math.max(0f, 1f - (t - fadeAt) / (float) Math.max(1, total - fadeAt));
        }

        /** Like the real overlay: a faint grey the moment it starts, deepening with the charge. */
        float intensity(float partial) {
            float c = Mth.clamp(charge(partial), 0f, 1f);
            if (c <= 0f) return 0f;
            return (0.2f + 0.8f * c) * Math.min(1f, c * 5f);
        }
    }

    /**
     * {@code eyes_open} / {@code eyes_close}: the eyelids, drawn by {@code PensieveOverlay} from {@link Eyelids}. An
     * opening runs out at its duration; a closing never expires, it holds shut until {@link #clear} ends the scene.
     */
    private static final class Eyes implements Running {
        private final boolean opening;
        private final int total;
        private int age;

        Eyes(boolean opening, int elapsed, int total) {
            this.opening = opening;
            this.age = elapsed;
            this.total = total;
        }

        @Override
        public boolean tick(ClientLevel level) {
            age++;
            return !opening || age < total;
        }

        float closed(float partial) {
            return Eyelids.closed(opening, age + partial, total);
        }
    }

    /**
     * {@code patronus_light}: silver-white light bursting outward. A ring on the ground and a faint sphere of
     * glowing particles grow from radius 0 to {@link #PATRONUS_RADIUS} over the first
     * {@link #PATRONUS_GROW_FRACTION} of the duration (ease-out), then thin out over the rest; a dense spray of
     * white particles at the centre. Everything is a function of the age, so a late joiner picks up at the right radius.
     */
    private static final class PatronusLight implements Running {
        private final Vec3 centre;
        private final int total;
        private int age;

        PatronusLight(Vec3 centre, int elapsed, int total) {
            this.centre = centre;
            this.age = elapsed;
            this.total = total;
        }

        /** The dome radius, in blocks, at {@code t} ticks. */
        static double radius(double t, int total) {
            double k = Mth.clamp(t / (total * PATRONUS_GROW_FRACTION), 0.0, 1.0);
            return PATRONUS_RADIUS * (1.0 - (1.0 - k) * (1.0 - k));
        }

        /** How much of the glow is left at {@code t} ticks: 1 while growing, then down to 0 at the end. */
        static double glow(double t, int total) {
            double grow = total * PATRONUS_GROW_FRACTION;
            if (t <= grow) return 1.0;
            return Mth.clamp(1.0 - (t - grow) / Math.max(1.0, total - grow), 0.0, 1.0);
        }

        float wash(float partial) {
            float t = age + partial;
            if (t < PATRONUS_WASH_PEAK_TICKS) return PATRONUS_WASH_ALPHA * t / PATRONUS_WASH_PEAK_TICKS;
            // Fade out by the end of a short light too, so the wash never pops off mid-fade.
            float fade = Math.max(1, Math.min(PATRONUS_WASH_FADE_TICKS, total - PATRONUS_WASH_PEAK_TICKS));
            return PATRONUS_WASH_ALPHA * Math.max(0f, 1f - (t - PATRONUS_WASH_PEAK_TICKS) / fade);
        }

        @Override
        public boolean tick(ClientLevel level) {
            double r = radius(age, total);
            double glow = glow(age, total);
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, centre.x, centre.y + 1.0, centre.z, 0, 0, 0);
            }
            // The horizontal ring on the ground plane: the wavefront, bright, with a fainter trailing ring. The points
            // live only a few ticks, so the ring stays a ring instead of leaving a filled disc (and the particle cap).
            ParticleEngine engine = Minecraft.getInstance().particleEngine;
            int ring = (int) Math.ceil((24 + r * 5) * glow);
            for (int i = 0; i < ring; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double rr = i % 4 == 0 ? r * (0.82 + level.random.nextDouble() * 0.12) : r;
                Particle p = engine.createParticle(ParticleTypes.END_ROD, centre.x + Math.cos(a) * rr,
                        centre.y + 0.15 + level.random.nextDouble() * 0.2, centre.z + Math.sin(a) * rr, 0, 0.02, 0);
                if (p != null) p.setLifetime(RING_POINT_LIFE + level.random.nextInt(3));
            }
            // The faint sphere.
            int sphere = (int) Math.ceil(28 * glow);
            for (int i = 0; i < sphere; i++) {
                double y = level.random.nextDouble() * 2 - 1;
                double a = level.random.nextDouble() * Math.PI * 2;
                double s = Math.sqrt(1 - y * y);
                level.addParticle(ParticleTypes.GLOW, centre.x + s * Math.cos(a) * r, centre.y + Math.abs(y) * r * 0.9,
                        centre.z + s * Math.sin(a) * r, 0, 0, 0);
            }
            // Dense white spray at the centre, thinning with the glow.
            int spray = (int) Math.ceil(26 * glow);
            for (int i = 0; i < spray; i++) {
                double dx = level.random.nextDouble() * 2 - 1;
                double dy = level.random.nextDouble() * 1.4;
                double dz = level.random.nextDouble() * 2 - 1;
                double speed = 0.08 + level.random.nextDouble() * 0.25;
                level.addParticle(i % 3 == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD,
                        centre.x, centre.y + 0.6, centre.z, dx * speed, dy * speed, dz * speed);
            }
            return ++age < total;
        }
    }

    /**
     * {@code fiendfyre_rings}: Fiendfyre filling the place in circles. While the age is under {@code births} a new
     * wave is born every {@link #FIRE_WAVE_EVERY} ticks; each is a writhing wall of flame on the ground plane that
     * rolls outward from {@link #FIRE_RING_START} at {@link #FIRE_RING_SPEED} blocks a tick and burns out at
     * {@link #FIRE_RING_MAX}, thinning over its last third. A tight ring of fire wraps the centre while waves are
     * being born. A wave of radius r reaches distance d at {@code birth + (d - START) / SPEED} ticks: a scene times
     * its burning actors by that. Everything follows the age, so a late joiner sees the waves where they are.
     */
    private static final class FiendfyreRings implements Running {
        private final Vec3 centre;
        private final int births;
        private int age;

        FiendfyreRings(Vec3 centre, int elapsed, int births) {
            this.centre = centre;
            this.age = elapsed;
            this.births = births;
        }

        @Override
        public boolean tick(ClientLevel level) {
            ParticleEngine engine = Minecraft.getInstance().particleEngine;
            if (age == 0) {
                level.addParticle(ParticleTypes.FLASH, centre.x, centre.y + 1.0, centre.z, 0, 0, 0);
            }
            if (age < births) {
                if (age % FIRE_WAVE_EVERY == 0 && age > 0) {
                    MemorySounds.play(SoundEvents.BLAZE_SHOOT, centre, 1.4f, 0.5f + level.random.nextFloat() * 0.2f);
                }
                // The fire wrapped round the caster.
                for (int i = 0; i < 14; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = 1.0 + level.random.nextDouble() * 0.5;
                    level.addParticle(ParticleTypes.FLAME, centre.x + Math.cos(a) * r, centre.y + 1.0 + level.random.nextDouble() * 2.0,
                            centre.z + Math.sin(a) * r, -Math.sin(a) * 0.08, 0.05, Math.cos(a) * 0.08);
                }
            }
            boolean any = false;
            int last = Math.min(age, births - 1);
            for (int birth = 0; birth <= last; birth += FIRE_WAVE_EVERY) {
                int life = age - birth;
                if (life >= FIRE_WAVE_TICKS) continue;
                any = true;
                wave(level, engine, life, birth / FIRE_WAVE_EVERY);
            }
            age++;
            return any || age < births;
        }

        /** One wave, {@code life} ticks old; {@code n} is its number, so the waves writhe out of step. */
        private void wave(ClientLevel level, ParticleEngine engine, int life, int n) {
            double r = FIRE_RING_START + FIRE_RING_SPEED * life;
            double k = life / (double) FIRE_WAVE_TICKS;
            double strength = k < 0.66 ? 1.0 : Math.max(0.0, 1.0 - (k - 0.66) / 0.34);
            double height = (n == 0 ? 3.2 : 2.4) * (0.6 + 0.4 * strength);
            int points = (int) Math.ceil(Math.min(170, 12 + r * 2 * Math.PI * 0.85) * strength);
            for (int i = 0; i < points; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                // The flames lick in and out: tongues of fire running round the ring.
                double rr = r + Math.sin(a * 7 + age * 0.35 + n * 1.9) * 0.45 + (level.random.nextDouble() - 0.5) * 0.5;
                double x = centre.x + Math.cos(a) * rr;
                double z = centre.z + Math.sin(a) * rr;
                double y = centre.y + level.random.nextDouble() * height * (0.4 + 0.6 * level.random.nextDouble());
                Particle p = engine.createParticle(i % 9 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z,
                        Math.cos(a) * 0.06, 0.04 + level.random.nextDouble() * 0.06, Math.sin(a) * 0.06);
                if (p != null) p.setLifetime(6 + level.random.nextInt(5));
            }
            int smoke = (int) Math.ceil(points / 14.0);
            for (int i = 0; i < smoke; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double rr = r - 0.6;
                level.addParticle(ParticleTypes.LARGE_SMOKE, centre.x + Math.cos(a) * rr, centre.y + height,
                        centre.z + Math.sin(a) * rr, 0, 0.08, 0);
            }
            int sparks = (int) Math.ceil(points / 18.0);
            for (int i = 0; i < sparks; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                level.addParticle(ParticleTypes.LAVA, centre.x + Math.cos(a) * r, centre.y + 0.4, centre.z + Math.sin(a) * r, 0, 0, 0);
            }
        }
    }

    /**
     * {@code letter_flood}: runs a {@link LetterFlood} and shows each letter as a client-only
     * {@link MemoryLetterEntity}, spawned the tick it leaves the fireplace. Never finishes: the letters lie on the
     * floor until {@link #clear} ends the scene. A late joiner fast-forwards the swarm first, so it is in the state
     * everyone else sees. The letters are not memory actors: this class places them, tick by tick.
     */
    private static final class Flood implements Running {
        private final ClientLevel level;
        private final Vec3 at;
        private final LetterFlood sim;
        private final MemoryLetterEntity[] letters;
        private final long seed;

        Flood(ClientLevel level, Vec3 at, float yaw, float[] room, int count, int duration, int elapsed, long seed) {
            this.level = level;
            this.at = at;
            this.seed = seed;
            this.sim = new LetterFlood(at.x, at.y, at.z, yaw, room[0], room[1], room[2], count, duration, seed);
            this.letters = new MemoryLetterEntity[count];
            sim.fastForward(elapsed);
            sync();
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (sim.age() % FLOOD_RUSTLE_EVERY == 0 && sim.anyFlying()) {
                MemorySounds.play("minecraft:item.book.page_turn", at, 0.3f + level.random.nextFloat() * 0.3f, 1.0f);
            }
            sim.step();
            sync();
            return true;
        }

        /** Spawn the newly emitted letters and move the rest to where the simulation has them. */
        private void sync() {
            for (int i = 0; i < letters.length; i++) {
                if (!sim.emitted(i)) continue;
                MemoryLetterEntity e = letters[i];
                if (e == null) {
                    e = new MemoryLetterEntity(ModEntities.MEMORY_LETTER.get(), level);
                    e.setId(MemoryEvents.nextEntityId());
                    e.flutterSeed = (int) (seed + i);
                    place(e, i);
                    e.setOldPosAndRot();   // no first-frame streak from the world origin
                    level.addEntity(e);
                    LETTERS.add(e);
                    letters[i] = e;
                } else if (!e.isRemoved()) {
                    // The last tick is the interpolation start: record it before moving.
                    e.xo = e.getX();
                    e.yo = e.getY();
                    e.zo = e.getZ();
                    e.yRotO = e.getYRot();
                    e.xRotO = e.getXRot();
                    place(e, i);
                }
            }
        }

        private void place(MemoryLetterEntity e, int i) {
            e.setPos(sim.x(i), sim.y(i), sim.z(i));
            e.setYRot(sim.yaw(i));
            e.setXRot(sim.pitch(i));
        }
    }

    /** Lifts a mark from its start point and counts its fade down. */
    private static final class Mark implements Running {
        private final DarkMarkEntity mark;
        private final Vec3 from;
        private final int life;
        private int age;

        Mark(DarkMarkEntity mark, Vec3 from, int life) {
            this.mark = mark;
            this.from = from;
            this.life = life;
        }

        @Override
        public boolean tick(ClientLevel level) {
            if (mark.isRemoved()) return false;
            age++;
            if (age >= life) {
                mark.discard();
                return false;
            }
            float rise = Mth.clamp(age / (float) MARK_RISE_TICKS, 0f, 1f);
            float eased = 1f - (1f - rise) * (1f - rise);
            mark.setPos(from.x, from.y + MARK_RISE_BLOCKS * eased, from.z);
            mark.setRemainingTicks(life - age);
            return true;
        }
    }
}
