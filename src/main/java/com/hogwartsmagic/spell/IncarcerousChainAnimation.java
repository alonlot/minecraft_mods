package com.hogwartsmagic.spell;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Incarcerous: chains erupt from the ground to the left and right of the target, wrap limbs,
 * and are re-drawn while {@link com.hogwartsmagic.ExampleMod#INCARCEROUS_CHAINS} is active.
 *
 * <p>The intro phases are staggered by a real tick counter drained from the level tick (same pattern as
 * {@link com.hogwartsmagic.spell.damage.BombardaBlasts}). {@code MinecraftServer.tell(TickTask)} is not
 * usable for this: {@code shouldRun} is {@code tick + 3 < tickCount || haveTime()}, so on an idle server
 * every deferred task runs in the same tick and the animation collapses into one burst.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IncarcerousChainAnimation {

    /** Tick offsets, relative to the scheduling tick, of the six intro phases. */
    private static final int[] PHASE_OFFSETS = {0, 7, 14, 22, 29, 36};

    /** Pending intro animations per level, keyed by dimension; drained in the level tick END phase. */
    private static final Map<ResourceKey<Level>, ArrayDeque<Pending>> PENDING = new HashMap<>();

    private static final class Pending {
        final LivingEntity target;
        final int startTick;
        int nextPhase;

        Pending(LivingEntity target, int startTick) {
            this.target = target;
            this.startTick = startTick;
        }
    }

    /** Horizontal distance from entity feet where side anchors sit (blocks). */
    private static final double SIDE_OFFSET = 1.12;

    private static final DustParticleOptions CHAIN_DARK =
            new DustParticleOptions(new Vector3f(0.18f, 0.18f, 0.22f), 1.15f);
    private static final DustParticleOptions CHAIN_MID =
            new DustParticleOptions(new Vector3f(0.32f, 0.32f, 0.38f), 1.28f);
    private static final DustParticleOptions CHAIN_EDGE =
            new DustParticleOptions(new Vector3f(0.48f, 0.48f, 0.55f), 0.92f);

    private IncarcerousChainAnimation() {}

    public static void schedule(ServerLevel level, LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        int t0 = level.getServer().getTickCount();
        PENDING.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>()).add(new Pending(target, t0));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        ArrayDeque<Pending> pending = PENDING.get(level.dimension());
        if (pending == null) {
            return;
        }
        if (pending.isEmpty()) {
            PENDING.remove(level.dimension());
            return;
        }
        int now = level.getServer().getTickCount();
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (!p.target.isAlive() || p.target.isRemoved()) {
                it.remove();
                continue;
            }
            int elapsed = now - p.startTick;
            while (p.nextPhase < PHASE_OFFSETS.length && elapsed >= PHASE_OFFSETS[p.nextPhase]) {
                runPhase(p.nextPhase++, p.target);
            }
            if (p.nextPhase >= PHASE_OFFSETS.length) {
                it.remove();
            }
        }
        if (pending.isEmpty()) {
            PENDING.remove(level.dimension());
        }
    }

    private static void runPhase(int phase, LivingEntity target) {
        switch (phase) {
            case 0 -> phaseFirstLeg(target);
            case 1 -> phaseSecondLeg(target);
            case 2 -> phaseLegShackle(target);
            case 3 -> phaseHandChain(target, true);
            case 4 -> phaseHandChain(target, false);
            case 5 -> phaseFinisher(target);
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /**
     * Re-render bound chains while the effect lasts (called from server living tick).
     */
    public static void renderSustained(ServerLevel level, LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        ChainGeometry g = ChainGeometry.from(target);
        double sagLegs = Mth.clamp(target.getBbWidth() * 0.14, 0.08, 0.16);

        bezierChain(level, g.groundLeft(), g.leftAnkle(), g.inwardControl(g.groundLeft(), g.leftAnkle(), true),
                sustainedDensity(g.groundLeft(), g.leftAnkle()), sagLegs, true);
        bezierChain(level, g.groundRight(), g.rightAnkle(), g.inwardControl(g.groundRight(), g.rightAnkle(), false),
                sustainedDensity(g.groundRight(), g.rightAnkle()), sagLegs, true);
        chainBetween(level, g.leftAnkle(), g.rightAnkle(), Mth.clamp((int) (g.leftAnkle().distanceTo(g.rightAnkle()) * 14), 10, 18), sagLegs * 1.2, true);

        bezierChain(level, g.groundLeftHand(), g.leftHand(), g.inwardControl(g.groundLeftHand(), g.leftHand(), true),
                sustainedDensity(g.groundLeftHand(), g.leftHand()), 0.0, true);
        bezierChain(level, g.groundRightHand(), g.rightHand(), g.inwardControl(g.groundRightHand(), g.rightHand(), false),
                sustainedDensity(g.groundRightHand(), g.rightHand()), 0.0, true);

        if (target.tickCount % 12 == 0) {
            soulAnchor(level, g.groundLeft().x, g.groundLeft().y, g.groundLeft().z);
            soulAnchor(level, g.groundRight().x, g.groundRight().y, g.groundRight().z);
        }
    }

    /** Master Incarcerous: a light chain from one bound target to another, re-drawn while both stay bound. */
    public static void renderLink(ServerLevel level, LivingEntity a, LivingEntity b) {
        if (!a.isAlive() || !b.isAlive()) {
            return;
        }
        Vec3 from = a.position().add(0.0, a.getBbHeight() * 0.5, 0.0);
        Vec3 to = b.position().add(0.0, b.getBbHeight() * 0.5, 0.0);
        int segments = Mth.clamp((int) (from.distanceTo(to) * 10), 8, 30);
        chainBetween(level, from, to, segments, 0.12, true);
    }

    private static int sustainedDensity(Vec3 a, Vec3 b) {
        return Mth.clamp((int) (a.distanceTo(b) * 10), 10, 20);
    }

    private static int introDensity(Vec3 a, Vec3 b) {
        return Mth.clamp((int) (a.distanceTo(b) * 18), 16, 36);
    }

    private static ServerLevel asServer(LivingEntity target) {
        if (!target.isAlive()) {
            return null;
        }
        if (target.level() instanceof ServerLevel sl && !sl.isClientSide()) {
            return sl;
        }
        return null;
    }

    private static Vec3 horizontalForward(LivingEntity e) {
        Vec3 look = e.getLookAngle();
        Vec3 f = new Vec3(look.x, 0.0, look.z);
        if (f.lengthSqr() < 1.0E-4) {
            f = new Vec3(0.0, 0.0, 1.0);
        } else {
            f = f.normalize();
        }
        return f;
    }

    private static Vec3 horizontalRight(LivingEntity e) {
        Vec3 f = horizontalForward(e);
        return new Vec3(-f.z, 0.0, f.x);
    }

    private record ChainGeometry(
            Vec3 base,
            Vec3 right,
            Vec3 forward,
            double side,
            double hh
    ) {
        static ChainGeometry from(LivingEntity e) {
            Vec3 base = e.position();
            Vec3 right = horizontalRight(e);
            Vec3 forward = horizontalForward(e);
            double side = Math.max(SIDE_OFFSET, e.getBbWidth() * 1.35);
            double hh = e.getBbHeight() * 0.92;
            return new ChainGeometry(base, right, forward, side, hh);
        }

        Vec3 groundLeft() {
            return new Vec3(base.x + right.x * (-side), base.y + 0.02, base.z + right.z * (-side));
        }

        Vec3 groundRight() {
            return new Vec3(base.x + right.x * side, base.y + 0.02, base.z + right.z * side);
        }

        /** Slight forward shift so wrist chains read separately from leg chains. */
        Vec3 groundLeftHand() {
            return groundLeft().add(forward.scale(0.14));
        }

        Vec3 groundRightHand() {
            return groundRight().add(forward.scale(0.14));
        }

        Vec3 leftAnkle() {
            return base.add(right.scale(-0.22)).add(0.0, 0.12, 0.0);
        }

        Vec3 rightAnkle() {
            return base.add(right.scale(0.22)).add(0.0, 0.12, 0.0);
        }

        Vec3 leftHand() {
            return base.add(right.scale(-0.38)).add(0.0, hh, 0.0);
        }

        Vec3 rightHand() {
            return base.add(right.scale(0.38)).add(0.0, hh, 0.0);
        }

        Vec3 inwardControl(Vec3 from, Vec3 to, boolean fromLeftSide) {
            Vec3 mid = from.lerp(to, 0.5);
            double pull = fromLeftSide ? 0.28 : -0.28;
            return mid.add(right.scale(pull));
        }
    }

    private static void phaseFirstLeg(LivingEntity target) {
        ServerLevel level = asServer(target);
        if (level == null) {
            return;
        }
        ChainGeometry g = ChainGeometry.from(target);
        bezierChain(level, g.groundLeft(), g.leftAnkle(), g.inwardControl(g.groundLeft(), g.leftAnkle(), true),
                introDensity(g.groundLeft(), g.leftAnkle()), 0.11, false);
        soulAnchor(level, g.groundLeft().x, g.groundLeft().y, g.groundLeft().z);
    }

    private static void phaseSecondLeg(LivingEntity target) {
        ServerLevel level = asServer(target);
        if (level == null) {
            return;
        }
        ChainGeometry g = ChainGeometry.from(target);
        bezierChain(level, g.groundRight(), g.rightAnkle(), g.inwardControl(g.groundRight(), g.rightAnkle(), false),
                introDensity(g.groundRight(), g.rightAnkle()), 0.11, false);
        soulAnchor(level, g.groundRight().x, g.groundRight().y, g.groundRight().z);
        level.playSound(null, g.base.x, g.base.y, g.base.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.38f, 0.82f);
    }

    private static void phaseLegShackle(LivingEntity target) {
        ServerLevel level = asServer(target);
        if (level == null) {
            return;
        }
        ChainGeometry g = ChainGeometry.from(target);
        chainBetween(level, g.leftAnkle(), g.rightAnkle(), 14, 0.14, false);
        level.playSound(null, g.base.x, g.base.y, g.base.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.48f, 0.68f);
    }

    private static void phaseHandChain(LivingEntity target, boolean left) {
        ServerLevel level = asServer(target);
        if (level == null) {
            return;
        }
        ChainGeometry g = ChainGeometry.from(target);
        if (left) {
            bezierChain(level, g.groundLeftHand(), g.leftHand(), g.inwardControl(g.groundLeftHand(), g.leftHand(), true),
                    introDensity(g.groundLeftHand(), g.leftHand()), 0.06, false);
            soulAnchor(level, g.groundLeftHand().x, g.groundLeftHand().y, g.groundLeftHand().z);
        } else {
            bezierChain(level, g.groundRightHand(), g.rightHand(), g.inwardControl(g.groundRightHand(), g.rightHand(), false),
                    introDensity(g.groundRightHand(), g.rightHand()), 0.06, false);
            soulAnchor(level, g.groundRightHand().x, g.groundRightHand().y, g.groundRightHand().z);
        }
    }

    private static void phaseFinisher(LivingEntity target) {
        ServerLevel level = asServer(target);
        if (level == null) {
            return;
        }
        Vec3 c = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.05, 0.05, 0.05, 0.0);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y, c.z, 28, 0.45, 0.75, 0.45, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.62f);
    }

    private static void soulAnchor(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.02, z, 3, 0.05, 0.03, 0.05, 0.0);
    }

    private static Vec3 bezierQuadratic(Vec3 a, Vec3 c, Vec3 b, double t) {
        double u = 1.0 - t;
        return a.scale(u * u).add(c.scale(2.0 * u * t)).add(b.scale(t * t));
    }

    private static void bezierChain(ServerLevel level, Vec3 from, Vec3 to, Vec3 control, int segments, double sagY, boolean light) {
        if (segments < 2) {
            return;
        }
        Vec3 prev = bezierQuadratic(from, control, to, 0.0);
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 p = bezierQuadratic(from, control, to, t);
            if (sagY > 1.0E-4) {
                p = p.add(0.0, -Math.sin(t * Math.PI) * sagY, 0.0);
            }
            Vec3 seg = p.subtract(prev);
            if (seg.lengthSqr() > 1.0E-8) {
                emitLinkSegment(level, prev, seg.normalize(), i, t, light);
            }
            prev = p;
        }
    }

    private static void chainBetween(ServerLevel level, Vec3 a, Vec3 b, int segments, double sag, boolean light) {
        if (a.distanceToSqr(b) < 1.0E-6) {
            return;
        }
        Vec3 prev = shacklePoint(a, b, sag, 0, segments);
        for (int i = 1; i <= segments; i++) {
            Vec3 p = shacklePoint(a, b, sag, i, segments);
            Vec3 seg = p.subtract(prev);
            if (seg.lengthSqr() > 1.0E-8) {
                emitLinkSegment(level, prev, seg.normalize(), i, i / (double) segments, light);
            }
            prev = p;
        }
    }

    private static Vec3 shacklePoint(Vec3 a, Vec3 b, double sag, int i, int segments) {
        double t = i / (double) segments;
        return a.lerp(b, t).add(0.0, -Math.sin(t * Math.PI) * sag, 0.0);
    }

    /**
     * Twisted double-strand "link" along the tangent, with alternating bulges like real chain.
     */
    private static void emitLinkSegment(ServerLevel level, Vec3 pos, Vec3 tangent, int index, double along, boolean light) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 n1 = tangent.cross(up);
        if (n1.lengthSqr() < 1.0E-4) {
            n1 = tangent.cross(new Vec3(1.0, 0.0, 0.0));
        }
        n1 = n1.normalize();
        Vec3 n2 = tangent.cross(n1).normalize();

        double twists = light ? (Math.PI * 5.0) : (Math.PI * 7.0);
        double angle = along * twists + index * 0.4;
        double a = light ? 0.042 : 0.056;
        double b = light ? 0.03 : 0.04;
        double bulge = 0.78 + 0.28 * Math.sin(along * Math.PI * 10 + index);

        Vec3 o1 = n1.scale(Math.cos(angle) * a * bulge).add(n2.scale(Math.sin(angle) * b * bulge));
        Vec3 o2 = n1.scale(-Math.cos(angle + 1.1) * a * 0.62).add(n2.scale(-Math.sin(angle + 1.1) * b * 0.62));

        emitAt(level, pos.add(o1), (index + (int) (along * 4)) % 3 == 0 ? CHAIN_EDGE : CHAIN_DARK, light, 0.018);
        emitAt(level, pos.add(o2), CHAIN_MID, light, 0.022);
        if (!light && index % 2 == 0) {
            emitAt(level, pos.add(o1.scale(0.35)).add(o2.scale(0.35)), CHAIN_EDGE, false, 0.012);
        }
    }

    private static void emitAt(ServerLevel level, Vec3 p, DustParticleOptions dust, boolean light, double spread) {
        int count = light ? 1 : 2;
        level.sendParticles(dust, p.x, p.y, p.z, count, spread, spread * 0.6, spread, 0.0);
    }
}
