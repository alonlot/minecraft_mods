package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.PatronusEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static com.hogwartsmagic.client.SpellFxGeometry.billboard;

/**
 * What a Dementor does to the world around you before it ever touches you. As one comes near, frost creeps in from
 * the edges of your view, the colour drains to a cold grey-blue, the fog closes in, every sound grows dull and
 * distant under a low freezing drone with a slow heartbeat in it, and your breath fogs in the air (and the breath
 * of anyone else close to one). A Patronus near you, or your own shield, lifts it all.
 *
 * <p>Purely this client's: it reads Dementors and Patroni from the level and builds its own frost.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DementorDread {
    private static final double RANGE = 24.0;
    private static final double PATRONUS_RANGE = 20.0;
    private static final ResourceLocation FROST =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dynamic/dementor_frost");
    private static final int FROST_W = 480;
    private static final int FROST_H = 270;
    private static final float BREATH_TICKS = 34.0f;

    /** 0 to 1: how deep the dread is, eased so it creeps in and drains out. */
    private static float dread;
    private static float target;
    private static boolean frostBuilt;
    private static DreadSound sound;
    private static final List<Breath> BREATHS = new ArrayList<>();
    private static long lastBreath;

    private record Breath(Vec3 mouth, Vec3 out, double start) {
    }

    private DementorDread() {
    }

    public static float dread() {
        return dread;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.player == null
                || minecraft.isPaused()) {
            return;
        }
        Player player = minecraft.player;
        AABB around = player.getBoundingBox().inflate(RANGE);
        float closest = 0.0f;
        List<DementorEntity> dementors = minecraft.level.getEntitiesOfClass(DementorEntity.class, around,
                Entity::isAlive);
        for (DementorEntity dementor : dementors) {
            double distance = dementor.distanceTo(player);
            closest = Math.max(closest, (float) Math.pow(Math.max(0.0, 1.0 - distance / RANGE), 1.2));
        }
        boolean warded = PatronusShieldClientState.isChanneling(player.getId())
                || !minecraft.level.getEntitiesOfClass(PatronusEntity.class,
                player.getBoundingBox().inflate(PATRONUS_RANGE), Entity::isAlive).isEmpty();
        target = warded ? 0.0f : closest;
        // Creeps in slowly, lets go a little faster.
        dread += (target - dread) * (target > dread ? 0.035f : 0.06f);
        if (dread < 0.002f) {
            dread = 0.0f;
        }

        if (dread > 0.02f && (sound == null || sound.isStopped())) {
            sound = new DreadSound();
            minecraft.getSoundManager().play(sound);
        }

        long now = minecraft.level.getGameTime();
        if (dread > 0.25f && now - lastBreath >= BREATH_TICKS) {
            lastBreath = now;
            breathe(player, now);
            // Anyone else standing close to a Dementor breathes cold too.
            for (Player other : minecraft.level.players()) {
                if (other != player && other.distanceTo(player) < 24.0 && !dementors.isEmpty()
                        && dementors.get(0).distanceTo(other) < RANGE * 0.6) {
                    breathe(other, now);
                }
            }
        }
        BREATHS.removeIf(breath -> now - breath.start > BREATH_TICKS);
    }

    private static void breathe(Player player, long now) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 mouth = player.getEyePosition().add(look.scale(0.3)).add(0.0, -0.12, 0.0);
        BREATHS.add(new Breath(mouth, look, now));
    }

    // ---- the view ----------------------------------------------------------------------------------------------

    /** HUD: the frost, the drained colour and the cold dark at the edges. From the HUD mixin. */
    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (dread <= 0.003f || minecraft.options.hideGui) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // The colour drains: a cold grey-blue laid over everything.
        graphics.fill(0, 0, width, height, argb(0.32f * dread, 0x6C7A8C));
        graphics.fill(0, 0, width, height, argb(0.18f * dread, 0x000308));
        // Frost creeping in from the edges.
        buildFrost(minecraft);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, dread * 1.15f));
        graphics.blit(FROST, 0, 0, width, height, 0.0f, 0.0f, FROST_W, FROST_H, FROST_W, FROST_H);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * The frost texture, grown once: thick at the edges of the view and thinning inward, patchy, with crystal
     * veins branching in from the border.
     */
    private static void buildFrost(Minecraft minecraft) {
        if (frostBuilt) {
            return;
        }
        frostBuilt = true;
        NativeImage image = new NativeImage(FROST_W, FROST_H, true);
        float[] veins = new float[FROST_W * FROST_H];
        RandomSource random = RandomSource.create(0x5EEDF205L);
        for (int k = 0; k < 90; k++) {
            int edge = random.nextInt(4);
            double x = edge == 0 ? 0 : edge == 1 ? FROST_W - 1 : random.nextInt(FROST_W);
            double y = edge == 2 ? 0 : edge == 3 ? FROST_H - 1 : random.nextInt(FROST_H);
            double angle = Math.atan2(FROST_H * 0.5 - y, FROST_W * 0.5 - x) + (random.nextDouble() - 0.5) * 1.4;
            grow(veins, random, x, y, angle, 40 + random.nextInt(90), 0);
        }
        for (int y = 0; y < FROST_H; y++) {
            for (int x = 0; x < FROST_W; x++) {
                double edge = Math.min(Math.min(x, FROST_W - 1 - x) / (FROST_W * 0.5),
                        Math.min(y, FROST_H - 1 - y) / (FROST_H * 0.5));
                double base = Math.pow(Math.max(0.0, 1.0 - edge / 0.55), 1.6);
                double noise = 0.6 * valueNoise(x / 18.0, y / 18.0) + 0.4 * valueNoise(x / 5.0 + 31.0, y / 5.0);
                double alpha = base * (0.3 + 0.7 * noise) + veins[y * FROST_W + x] * Math.sqrt(base);
                int a = Mth.clamp((int) (alpha * 235.0), 0, 235);
                // ABGR: pale ice.
                image.setPixelRGBA(x, y, (a << 24) | (255 << 16) | (238 << 8) | 225);
            }
        }
        minecraft.getTextureManager().register(FROST, new DynamicTexture(image));
    }

    /** One crystal vein: a walk inward that wanders and now and then branches. */
    private static void grow(float[] veins, RandomSource random, double x, double y, double angle, int steps,
                             int depth) {
        for (int i = 0; i < steps; i++) {
            int px = (int) x;
            int py = (int) y;
            if (px < 1 || py < 1 || px >= FROST_W - 1 || py >= FROST_H - 1) {
                if (i > 0) {
                    return;
                }
            } else {
                float strength = 0.9f * (1.0f - i / (float) steps);
                veins[py * FROST_W + px] = Math.max(veins[py * FROST_W + px], strength);
                veins[py * FROST_W + px + 1] = Math.max(veins[py * FROST_W + px + 1], strength * 0.4f);
                veins[(py + 1) * FROST_W + px] = Math.max(veins[(py + 1) * FROST_W + px], strength * 0.4f);
            }
            angle += (random.nextDouble() - 0.5) * 0.35;
            x += Math.cos(angle) * 1.3;
            y += Math.sin(angle) * 1.3;
            if (depth < 3 && random.nextFloat() < 0.035f) {
                grow(veins, random, x, y, angle + (random.nextBoolean() ? 0.9 : -0.9), steps / 2, depth + 1);
            }
        }
    }

    private static double valueNoise(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        fx = fx * fx * (3.0 - 2.0 * fx);
        fy = fy * fy * (3.0 - 2.0 * fy);
        double a = lattice(x0, y0);
        double b = lattice(x0 + 1, y0);
        double c = lattice(x0, y0 + 1);
        double d = lattice(x0 + 1, y0 + 1);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static double lattice(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp(Math.round(alpha * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** The fog closes in and goes cold. After the biome and Undercroft fog, so it only ever tightens theirs. */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (dread <= 0.003f) {
            return;
        }
        float far = event.getFarPlaneDistance();
        event.setFarPlaneDistance(Math.min(far, Mth.lerp(dread, far, 14.0f)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(dread, event.getNearPlaneDistance(),
                0.0f)));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (dread <= 0.003f) {
            return;
        }
        float f = dread * 0.75f;
        event.setRed(Mth.lerp(f, event.getRed(), 0.50f));
        event.setGreen(Mth.lerp(f, event.getGreen(), 0.56f));
        event.setBlue(Mth.lerp(f, event.getBlue(), 0.64f));
    }

    // ---- the sound ---------------------------------------------------------------------------------------------

    /** Every new sound comes out dull and distant (the Dementors' own, and the dread itself, excepted). */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (dread <= 0.15f || original == null || original instanceof TickableSoundInstance
                || original.getLocation().getPath().startsWith("dementor")) {
            return;
        }
        event.setSound(new Muffled(original, 1.0f - 0.6f * dread, 1.0f - 0.07f * dread));
    }

    /** A sound played quieter and a shade lower. */
    private static final class Muffled implements SoundInstance {
        private final SoundInstance inner;
        private final float volume;
        private final float pitch;

        Muffled(SoundInstance inner, float volume, float pitch) {
            this.inner = inner;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public net.minecraft.client.resources.sounds.Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * volume;
        }

        @Override
        public float getPitch() {
            return inner.getPitch() * pitch;
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }
    }

    /** The low freezing drone with its slow heartbeat, as loud as the dread is deep. */
    private static final class DreadSound extends AbstractTickableSoundInstance {
        DreadSound() {
            super(ModSounds.DEMENTOR_DREAD.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.volume = 0.01f;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (dread <= 0.01f || Minecraft.getInstance().level == null) {
                stop();
                return;
            }
            this.volume = 0.9f * dread;
        }
    }

    // ---- breath ------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || BREATHS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (Breath breath : BREATHS) {
            float age = (float) (now - breath.start);
            if (age < 0.0f) {
                continue;
            }
            float f = age / BREATH_TICKS;
            // Out on the exhale, slowing, rising a little and spreading as it fades.
            float travel = 0.55f * (1.0f - (1.0f - f) * (1.0f - f));
            for (int k = 0; k < 3; k++) {
                Vec3 c = breath.mouth.add(breath.out.scale(travel * (0.8 + 0.2 * k))).add(0.0, 0.08 * f + 0.03 * k, 0.0)
                        .subtract(camera);
                float alpha = 0.32f * (1.0f - f) * AvadaChargePose.smoothstep(0.0f, 2.0f, age) * (1.0f - 0.25f * k);
                billboard(out, m, c, 0.12f + 0.3f * f + 0.05f * k, alpha, 0.9f, 0.93f, 1.0f, false, 4, 12);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dread = 0.0f;
        target = 0.0f;
        BREATHS.clear();
        if (sound != null) {
            sound = null;
        }
    }
}
