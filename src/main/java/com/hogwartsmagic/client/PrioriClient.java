package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.PrioriIncantatem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * Priori Incantatem as the clients see it: each duellist's spell streaming out of their wand to a bead of light
 * between them where the two meet, churning in both colours, throwing arcs and sparks; the bead sliding toward
 * whoever is losing; a dome of golden threads rising round the two of them; both wand arms held out and shaking;
 * a roar of sound at the bead and a rumble for anyone close. The ending is a clash ({@link SpellBeamClientState}).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PrioriClient {
    private static final float GOLD_R = 1.0f;
    private static final float GOLD_G = 0.82f;
    private static final float GOLD_B = 0.45f;
    private static final int DOME_MERIDIANS = 10;
    private static final int DOME_PARALLELS = 4;
    private static final int KNOT_ARCS = 10;
    private static final int KNOT_SPARKS = 24;
    private static final double SHAKE_RANGE = 14.0;

    private static final class Lock {
        final int id;
        final int casterA;
        final int casterB;
        final BeamStyle styleA;
        final BeamStyle styleB;
        final double start;
        final int seed;
        float target;
        float shown;
        double lastFrame;
        boolean ended;
        LockSound sound;

        Lock(int id, int casterA, int casterB, BeamStyle styleA, BeamStyle styleB, float bead, double start) {
            this.id = id;
            this.casterA = casterA;
            this.casterB = casterB;
            this.styleA = styleA;
            this.styleB = styleB;
            this.target = bead;
            this.shown = bead;
            this.start = start;
            this.lastFrame = start;
            this.seed = id * 0x2545F491;
        }
    }

    private static final Map<Integer, Lock> LOCKS = new HashMap<>();

    private PrioriClient() {
    }

    public static void accept(SpellPackets.PrioriLockPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Lock lock = LOCKS.get(packet.lockId());
        if (packet.state() == PrioriIncantatem.END) {
            if (lock != null) {
                lock.ended = true;
                LOCKS.remove(packet.lockId());
            }
            return;
        }
        if (lock == null) {
            BeamStyle a = style(packet.spellA());
            BeamStyle b = style(packet.spellB());
            if (a == null || b == null) {
                return;
            }
            lock = new Lock(packet.lockId(), packet.casterA(), packet.casterB(), a.asJet(), b.asJet(), packet.bead(),
                    now);
            LOCKS.put(packet.lockId(), lock);
            SpellBeamClientState.endSilently(packet.beamA(), now);
            SpellBeamClientState.endSilently(packet.beamB(), now);
            lock.sound = new LockSound(lock);
            minecraft.getSoundManager().play(lock.sound);
        }
        lock.target = packet.bead();
    }

    private static BeamStyle style(String spellId) {
        SpellType spell = SpellType.fromId(spellId);
        return spell == null ? null : BeamStyles.of(spell);
    }

    private static boolean involves(int entityId) {
        for (Lock lock : LOCKS.values()) {
            if (lock.casterA == entityId || lock.casterB == entityId) {
                return true;
            }
        }
        return false;
    }

    /** Where a locked duellist's wand tip is: held straight out along their gaze. */
    private static Vec3 tip(LivingEntity caster, float partial) {
        Vec3 look = caster.getViewVector(partial);
        double x = Mth.lerp(partial, caster.xo, caster.getX());
        double y = Mth.lerp(partial, caster.yo, caster.getY());
        double z = Mth.lerp(partial, caster.zo, caster.getZ());
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        if (AvadaChargePose.isOwnFirstPerson(caster)) {
            Vec3 up = right.cross(look).normalize();
            return new Vec3(x, y + caster.getEyeHeight(), z).add(look.scale(0.62)).add(right.scale(0.3))
                    .add(up.scale(-0.2));
        }
        if (caster instanceof Player) {
            float bodyYaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
            double shoulder = (caster.isCrouching() ? 18.8 : 22.0) / 16.0 * 0.9375;
            return new Vec3(x, y + shoulder, z).add(bodyRight.scale(5.0 / 16.0 * 0.9375)).add(look.scale(1.12));
        }
        return new Vec3(x, y + caster.getEyeHeight(), z).add(look.scale(0.7)).add(right.scale(0.3)).add(0.0, -0.25, 0.0);
    }

    /** The light at the bead of a lock, both spells' colours washed toward gold (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (LOCKS.isEmpty() || minecraft.level == null) {
            return;
        }
        for (Lock lock : LOCKS.values()) {
            Entity ea = minecraft.level.getEntity(lock.casterA);
            Entity eb = minecraft.level.getEntity(lock.casterB);
            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b)) {
                continue;
            }
            Vec3 bead = tip(a, partial).lerp(tip(b, partial), (lock.shown + 1.0f) * 0.5f);
            float r = (lock.styleA.glowR + lock.styleB.glowR + GOLD_R) / 3.0f;
            float g = (lock.styleA.glowG + lock.styleB.glowG + GOLD_G) / 3.0f;
            float bl = (lock.styleA.glowB + lock.styleB.glowB + GOLD_B) / 3.0f;
            sink.add(bead, r, g, bl, 6.5f, 0.8f);
        }
    }

    // ---- drawing -----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LOCKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = AvadaChargeClientState.now(partial);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (Lock lock : LOCKS.values()) {
            Scene s = Scene.of(minecraft, lock, partial, now);
            if (s == null) {
                continue;
            }
            SpellBeamFxRenderer.drawHeldDark(dark, m, s.beamA, now, camera);
            SpellBeamFxRenderer.drawHeldDark(dark, m, s.beamB, now, camera);
            if (lock.styleA.sheathDark && lock.styleA.sheathWidth > 0.0f
                    || lock.styleB.sheathDark && lock.styleB.sheathWidth > 0.0f) {
                billboard(dark, m, s.bead.subtract(camera), 1.2f, 0.4f, SMOKE_R, SMOKE_G, SMOKE_B, false);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (Lock lock : LOCKS.values()) {
            Scene s = Scene.of(minecraft, lock, partial, now);
            if (s == null) {
                continue;
            }
            SpellBeamFxRenderer.drawHeldLight(light, m, s.beamA, now, camera);
            SpellBeamFxRenderer.drawHeldLight(light, m, s.beamB, now, camera);
            drawKnot(light, m, lock, s, now, camera);
            drawDome(light, m, lock, s, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** One lock's shape this frame. */
    private static final class Scene {
        Vec3 tipA;
        Vec3 tipB;
        Vec3 bead;
        Vec3 axis;
        Vec3 domeCentre;
        float domeRadius;
        SpellBeamClientState.Beam beamA;
        SpellBeamClientState.Beam beamB;

        static Scene of(Minecraft minecraft, Lock lock, float partial, double now) {
            Entity ea = minecraft.level.getEntity(lock.casterA);
            Entity eb = minecraft.level.getEntity(lock.casterB);
            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b)) {
                return null;
            }
            // The bead eases toward where the server says it is, so it slides rather than steps.
            double dt = Math.max(0.0, now - lock.lastFrame);
            lock.lastFrame = now;
            lock.shown += (lock.target - lock.shown) * (float) Math.min(1.0, dt * 0.35);
            Scene s = new Scene();
            s.tipA = tip(a, partial);
            s.tipB = tip(b, partial);
            Vec3 span = s.tipB.subtract(s.tipA);
            s.axis = span.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : span.normalize();
            Vec3[] across = frame(s.axis);
            float age = (float) (now - lock.start);
            Vec3 wobble = across[0].scale(0.06 * Math.sin(age * 4.7)).add(across[1].scale(0.06 * Math.cos(age * 3.9)));
            s.bead = s.tipA.lerp(s.tipB, (lock.shown + 1.0f) * 0.5f).add(wobble);
            s.beamA = SpellBeamClientState.held(lock.seed, lock.styleA, s.tipA, s.bead, lock.start, now);
            s.beamB = SpellBeamClientState.held(lock.seed + 1, lock.styleB, s.tipB, s.bead, lock.start, now);
            Vec3 feetA = a.getPosition(partial);
            Vec3 feetB = b.getPosition(partial);
            s.domeCentre = feetA.lerp(feetB, 0.5).add(0.0, 1.0, 0.0);
            s.domeRadius = (float) (feetA.distanceTo(feetB) * 0.5 + 1.6);
            return s;
        }
    }

    /** Where the two spells meet: both colours churning round a white core, arcs and sparks thrown off it. */
    private static void drawKnot(VertexConsumer out, Matrix4f m, Lock lock, Scene s, double now, Vec3 camera) {
        float age = (float) (now - lock.start);
        Vec3 pos = s.bead.subtract(camera);
        BeamStyle a = lock.styleA;
        BeamStyle b = lock.styleB;
        float strain = 1.0f + 0.25f * Mth.sin(age * 2.3f) + 0.35f * Math.abs(lock.shown);
        billboard(out, m, pos.add(s.axis.scale(-0.15 * strain)), 0.8f, 0.6f, a.glowR, a.glowG, a.glowB, false);
        billboard(out, m, pos.add(s.axis.scale(0.15 * strain)), 0.8f, 0.6f, b.glowR, b.glowG, b.glowB, false);
        billboard(out, m, pos, 0.32f * strain, 0.95f, 1.0f, 0.97f, 0.9f, true);
        billboard(out, m, pos, 1.6f, 0.18f, GOLD_R, GOLD_G, GOLD_B, false);

        int strike = (int) Math.floor(age);
        int segments = 7;
        Vec3[] path = new Vec3[segments + 1];
        float[] alpha = new float[segments + 1];
        float[] width = new float[segments + 1];
        for (int k = 0; k < KNOT_ARCS; k++) {
            int seed = lock.seed + k * 389 + strike * 6151;
            if (hash(seed, 1, 1) > 0.7f) {
                continue;
            }
            BeamStyle st = (k & 1) == 0 ? a : b;
            Vec3 span = randomDirection(seed + 3).scale(0.4 + 0.9 * hash(seed, 2, 2));
            Vec3[] axes = frame(span.normalize());
            double length = span.length();
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                double jag = (i == 0 || i == segments) ? 0.0 : length * 0.25 * Math.sin(Math.PI * t);
                path[i] = pos.add(span.scale(t)).add(axes[0].scale(jag * (hash(seed, i, 7) * 2.0 - 1.0)))
                        .add(axes[1].scale(jag * (hash(seed, i, 8) * 2.0 - 1.0)));
                alpha[i] = 1.0f - 0.6f * t;
                width[i] = 0.016f - 0.008f * t;
            }
            ribbon(out, m, path, width, alpha, 4.0f, 0.3f, st.glowR, st.glowG, st.glowB);
            ribbon(out, m, path, width, alpha, 1.0f, 1.0f, st.coreR, st.coreG, st.coreB);
        }

        // Sparks ground off it all the while, thrown out across the line between the wands.
        Vec3[] across = frame(s.axis);
        for (int k = 0; k < KNOT_SPARKS; k++) {
            int seed = lock.seed + k * 53;
            float period = 10.0f;
            float phase = (age + hash(seed, 9, 9) * period) % period;
            int round = (int) Math.floor((age + hash(seed, 9, 9) * period) / period);
            int rs = seed + round * 7919;
            double angle = hash(rs, 1, 0) * Math.PI * 2.0;
            Vec3 v = across[0].scale(Math.cos(angle)).add(across[1].scale(Math.sin(angle)))
                    .scale(0.18 + 0.18 * hash(rs, 2, 0));
            Vec3 p1 = pos.add(v.scale(phase)).add(0.0, -0.0075 * phase * phase, 0.0);
            float before = Math.max(0.0f, phase - 1.4f);
            Vec3 p0 = pos.add(v.scale(before)).add(0.0, -0.0075 * before * before, 0.0);
            BeamStyle st = (k & 1) == 0 ? a : b;
            float al = 1.0f - phase / period;
            ribbon(out, m, new Vec3[]{p0, p1}, new float[]{0.006f, 0.015f}, new float[]{al * 0.3f, al}, 1.0f, 1.0f,
                    st.coreR, st.coreG, st.coreB);
        }
    }

    /** Golden threads rising into a dome round the two duellists, turning slowly. */
    private static void drawDome(VertexConsumer out, Matrix4f m, Lock lock, Scene s, double now, Vec3 camera) {
        float age = (float) (now - lock.start);
        float rise = AvadaChargePose.smoothstep(0.0f, 20.0f, age);
        if (rise <= 0.01f) {
            return;
        }
        Vec3 centre = s.domeCentre.subtract(camera);
        float radius = s.domeRadius * (0.6f + 0.4f * rise);
        float alpha = 0.22f * rise;
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        for (int k = 0; k < DOME_MERIDIANS; k++) {
            double phi = k * Math.PI / DOME_MERIDIANS + age * 0.02;
            Vec3 right = new Vec3(Math.cos(phi), 0.0, Math.sin(phi));
            ring(out, m, centre, right, up, radius, 0.05f, alpha, GOLD_R, GOLD_G, GOLD_B, 48);
        }
        for (int k = 1; k <= DOME_PARALLELS; k++) {
            double theta = k * (Math.PI * 0.5) / (DOME_PARALLELS + 1);
            Vec3 at = centre.add(0.0, Math.sin(theta) * radius, 0.0);
            ring(out, m, at, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), (float) (Math.cos(theta) * radius),
                    0.05f, alpha * 0.8f, GOLD_R, GOLD_G, GOLD_B, 48);
        }
    }

    // ---- the duellists -----------------------------------------------------------------------------------------

    /** A locked duellist's wand arm held straight out at the other, shaking with the strain. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (LOCKS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof Player)
                || !involves(entity.getId())) {
            return;
        }
        float t = (float) AvadaChargeClientState.now(partial);
        float shake = 0.04f * (Mth.sin(t * 3.1f) + 0.6f * Mth.sin(t * 7.3f));
        humanoid.rightArm.xRot = humanoid.head.xRot - Mth.HALF_PI + shake;
        humanoid.rightArm.yRot = humanoid.head.yRot - 0.06f + shake * 0.5f;
        humanoid.rightArm.zRot = 0.0f;
        humanoid.leftArm.xRot = humanoid.head.xRot - Mth.HALF_PI + 0.35f + shake;
        humanoid.leftArm.yRot = humanoid.head.yRot + 0.45f;
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
        }
    }

    /** A low rumble for anyone close to a lock. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (LOCKS.isEmpty() || minecraft.level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float shake = 0.0f;
        for (Lock lock : LOCKS.values()) {
            Entity a = minecraft.level.getEntity(lock.casterA);
            if (a == null) {
                continue;
            }
            double distance = a.position().distanceTo(camera);
            if (distance < SHAKE_RANGE) {
                shake = Math.max(shake, 0.35f * (float) (1.0 - distance / SHAKE_RANGE));
            }
        }
        if (shake > 0.001f) {
            float t = (float) (minecraft.level.getGameTime() + event.getPartialTick());
            event.setPitch(event.getPitch() + shake * Mth.sin(t * 3.3f));
            event.setYaw(event.getYaw() + shake * Mth.sin(t * 2.7f + 1.1f));
        }
    }

    /** The roar of the two spells straining, at the bead, for as long as the lock holds. */
    private static final class LockSound extends AbstractTickableSoundInstance {
        private final Lock lock;

        LockSound(Lock lock) {
            super(ModSounds.PRIORI_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.lock = lock;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.2f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (lock.ended || minecraft.level == null) {
                stop();
                return;
            }
            Entity a = minecraft.level.getEntity(lock.casterA);
            Entity b = minecraft.level.getEntity(lock.casterB);
            if (a == null || b == null) {
                stop();
                return;
            }
            Vec3 at = a.getEyePosition().lerp(b.getEyePosition(), (lock.shown + 1.0) * 0.5);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.pitch = 0.9f + 0.25f * Math.abs(lock.shown);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (Lock lock : LOCKS.values()) {
            lock.ended = true;
        }
        LOCKS.clear();
    }
}
