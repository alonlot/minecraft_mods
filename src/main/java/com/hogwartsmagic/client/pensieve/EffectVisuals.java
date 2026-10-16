package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.entity.VoldemortSummoningEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    /** A lightning strike every this many ticks while a ritual runs (the real ritual strikes every 10). */
    private static final int RITUAL_STRIKE_EVERY = 20;
    private static final int MARK_RISE_TICKS = 50;
    private static final double MARK_RISE_BLOCKS = 18.0;
    /** The smallest visual scale a DarkMarkEntity accepts. */
    private static final float MARK_SCALE = 0.5f;

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<DarkMarkEntity> MARKS = new ArrayList<>();
    private static final List<LightningBolt> BOLTS = new ArrayList<>();

    private EffectVisuals() {
    }

    /** Start {@code effect} at {@code at}; {@code duration} is in ticks (0 = the effect's own default). */
    public static void play(String effect, Vec3 at, int duration) {
        play(effect, at, duration, 0);
    }

    /** As {@link #play(String, Vec3, int)}, joining an effect that began {@code elapsed} ticks ago. */
    public static void play(String effect, Vec3 at, int duration, int elapsed) {
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

    /** Stop everything and remove every entity an effect added. */
    static void clear() {
        RUNNING.clear();
        for (DarkMarkEntity m : MARKS) m.discard();
        MARKS.clear();
        for (LightningBolt b : BOLTS) b.discard();
        BOLTS.clear();
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
