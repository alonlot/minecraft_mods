package com.hogwartsmagic.event;

import com.hogwartsmagic.util.MagicParticles;

import net.minecraft.ChatFormatting;
import com.hogwartsmagic.MagicParticleOptions;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.HousePerks;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What happens the first time somebody buys a wand in Diagon Alley.
 *
 * <p>Twenty-six seconds, nine beats, and the player is not driving any of it. The camera comes off
 * their shoulders and orbits the two of them, the shop goes quiet, and Ollivander does the talking.
 *
 * <ol>
 *   <li><b>The shop stills.</b> The camera detaches and drifts back. Dust in the air.</li>
 *   <li><b>He remembers.</b> Every wand he has ever sold, and whose hand it went to.</li>
 *   <li><b>The tape.</b> It measures the customer by itself, in a rising spiral, while he keeps
 *       talking and does not look at it once.</li>
 *   <li><b>Three wands that will not do.</b> Each one arrives, is waved, and does something worse
 *       than the last: paper everywhere, then a bang, then a wretched fizzle. Snatched back before
 *       the customer can apologise.</li>
 *   <li><b>The one that will.</b> Holly, phoenix feather, eleven inches. Warm light gathers in and
 *       the wind gets up inside the shop. The room holds its breath, then the wand chooses: a
 *       flash, sparks, rings of motes thrown out across the floor and glints left hanging.</li>
 *   <li><b>Curious.</b> The quiet beat: one other feather, one brother wand, and where it left its
 *       mark. This is the beat the whole scene is built to arrive at, so once the afterglow of the
 *       choosing has died it has no effects in it at all -- just his voice and a very still camera.</li>
 *   <li><b>Release.</b></li>
 * </ol>
 *
 * <h2>How the camera works</h2>
 *
 * <p>An invisible marker entity is spawned and handed to {@link ServerPlayer#setCamera}; every tick
 * it is moved along an easing orbit around the midpoint between customer and wandmaker, and turned
 * to face it. At the end the camera is given back. All of it is server side -- the client is never
 * told anything except where a camera entity is, which is a thing vanilla already knows how to do.
 *
 * <p>The player is held still with a heavy slowness rather than by being teleported back each tick;
 * teleporting fights the client's own prediction and produces a rubber-band, which is the last thing
 * a cinematic wants.
 *
 * <h2>Once</h2>
 *
 * <p>Only the first wand. A twenty-six second cutscene on every purchase would be a punishment, so
 * the flag lives in the player's Forge persistent data, which survives death and dimension changes.
 * {@code /ollivanders} replays it on demand.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.hogwartsmagic.ExampleMod.MODID)
public final class OllivandersCeremony {

    // --- the beats, in ticks ------------------------------------------------------------------
    private static final int STILL = 40;
    private static final int REMEMBER = 70;
    private static final int MEASURE = 60;
    private static final int TRY = 55;              // each of three
    private static final int CHOSEN = 80;
    private static final int CURIOUS = 95;
    private static final int RELEASE = 25;

    private static final int T_STILL = STILL;
    private static final int T_REMEMBER = T_STILL + REMEMBER;
    private static final int T_MEASURE = T_REMEMBER + MEASURE;
    private static final int T_TRY1 = T_MEASURE + TRY;
    private static final int T_TRY2 = T_TRY1 + TRY;
    private static final int T_TRY3 = T_TRY2 + TRY;
    private static final int T_CHOSEN = T_TRY3 + CHOSEN;
    private static final int T_CURIOUS = T_CHOSEN + CURIOUS;
    private static final int TOTAL = T_CURIOUS + RELEASE;

    /** Dust hanging in the shop light: tiny, dim, warm. */
    private static final MagicParticleOptions DUST_MOTE = MagicParticleOptions.mote(1.0F, 0.93F, 0.75F, 0.45F);
    /** The held-breath tell: one big slow glint over the wand. */
    private static final MagicParticleOptions GLINT_BREATH = MagicParticleOptions.glint(1.0F, 0.95F, 0.8F, 2.2F);
    /** Phoenix gold, the wand's own colour. */
    private static final int PHOENIX_GOLD = 0xFFC94A;
    /** Phoenix flame, used when the customer has no house yet. */
    private static final int PHOENIX_FLAME = 0xFF6A2A;
    /** How long the room holds its breath between the light arriving and the burst. */
    private static final int BREATH = 12;

    private static final String FLAG = "hogwartsmagic:ollivanders_done";

    private static final List<Scene> ACTIVE = new ArrayList<>();

    private OllivandersCeremony() {
    }

    /** Has this player already been through it? */
    public static boolean hasHadCeremony(Player player) {
        return persisted(player).getBoolean(FLAG);
    }

    public static void markDone(Player player) {
        persisted(player).putBoolean(FLAG, true);
    }

    /**
     * The sub-tag Forge carries across death and dimension changes. Same key the rest of the mod
     * uses, spelled out rather than via the constant so it matches ClientRuntimeBridge.
     */
    private static final String PERSISTED = "PlayerPersisted";

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(PERSISTED)) {
            root.put(PERSISTED, new CompoundTag());
        }
        return root.getCompound(PERSISTED);
    }

    public static boolean isBusy(ServerPlayer player) {
        for (Scene s : ACTIVE) {
            if (s.player == player) {
                return true;
            }
        }
        return false;
    }

    /**
     * Start the scene. {@code wandmaker} may be null, in which case a spot two blocks in front of
     * the player stands in for him -- which is what {@code /ollivanders} uses.
     */
    public static boolean begin(ServerPlayer player, @Nullable Entity wandmaker) {
        if (isBusy(player)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 him = wandmaker != null
                ? wandmaker.position()
                : player.position().add(player.getLookAngle().scale(2.0D).multiply(1, 0, 1));

        ArmorStand camera = EntityType.ARMOR_STAND.create(level);
        if (camera == null) {
            return false;
        }
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setInvulnerable(true);
        camera.setSilent(true);
        camera.setNoBasePlate(true);
        // No collision box. ArmorStand#setMarker is private, so it goes in through NBT -- the
        // same field /summon writes. Without this a solid stand spawns at the player's eye
        // height and traps them inside it for the whole scene.
        CompoundTag marker = camera.saveWithoutId(new CompoundTag());
        marker.putBoolean("Marker", true);
        marker.putBoolean("NoGravity", true);
        marker.putBoolean("Invisible", true);
        camera.load(marker);
        camera.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
        level.addFreshEntity(camera);

        Scene scene = new Scene(player, camera, level, player.position(), him);
        ACTIVE.add(scene);
        player.setCamera(camera);
        markDone(player);
        return true;
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Scene> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Scene scene = it.next();
            if (scene.advance()) {
                scene.finish();
                it.remove();
            }
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        endAll();
    }

    /**
     * While the camera is detached, vanilla drags the customer body along with it every tick
     * (ServerPlayer#tick moves the player to its camera), and the orbit passes through the shop
     * walls and hangs in the air. Nothing about the scene should be able to suffocate or drop them.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.isCanceled() || ACTIVE.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if ((event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL))
                && isBusy(player)) {
            event.setCanceled(true);
        }
    }

    /** Give every camera back if the server stops mid-scene, so nobody logs in disembodied. */
    public static void endAll() {
        for (Scene scene : ACTIVE) {
            scene.finish();
        }
        ACTIVE.clear();
    }

    // ---------------------------------------------------------------------------------------

    private static final class Scene {
        private final ServerPlayer player;
        private final ArmorStand camera;
        private final ServerLevel level;      // where the scene started
        private final Vec3 stand;             // where the customer is rooted
        private final Vec3 him;               // where the wandmaker is
        private final Vec3 centre;
        private final float startYaw;
        private int ticks;
        private int lastLine = -1;

        Scene(ServerPlayer player, ArmorStand camera, ServerLevel level, Vec3 stand, Vec3 him) {
            this.player = player;
            this.camera = camera;
            this.level = level;
            this.stand = stand;
            this.him = him;
            this.centre = stand.add(him).scale(0.5D).add(0.0D, 1.3D, 0.0D);
            this.startYaw = player.getYRot();
        }

        /** Returns true when the scene is over. */
        boolean advance() {
            if (!player.isAlive() || player.hasDisconnected()) {
                return true;
            }
            ticks++;
            ServerLevel level = player.serverLevel();

            // Hold the customer still. Slowness rather than a teleport: teleporting every tick
            // fights the client's own movement prediction and rubber-bands.
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, false, false));
            player.setDeltaMovement(Vec3.ZERO);

            moveCamera();
            beat(level);
            return ticks >= TOTAL;
        }

        /**
         * The orbit. Radius and height both ease inward across the scene, so it starts as a wide
         * establishing shot and finishes close on the two of them for the quiet beat.
         */
        private void moveCamera() {
            float p = ticks / (float) TOTAL;
            double ease = p * p * (3.0D - 2.0D * p);
            double radius = 5.0D - 2.6D * ease;
            double height = 1.9D - 0.55D * ease;
            // Just over half a turn across the whole scene: enough that the geometry keeps changing,
            // slow enough that it never draws attention to itself.
            double angle = Math.toRadians(startYaw + 20.0D) + ease * Math.PI * 1.15D;

            double cx = centre.x + Math.sin(angle) * radius;
            double cz = centre.z - Math.cos(angle) * radius;
            double cy = centre.y + height;
            camera.moveTo(cx, cy, cz);

            // Face the middle of the pair.
            double dx = centre.x - cx;
            double dy = (centre.y - 0.2D) - cy;
            double dz = centre.z - cz;
            double flat = Math.sqrt(dx * dx + dz * dz);
            camera.setYRot((float) (Math.toDegrees(Math.atan2(-dx, dz))));
            camera.setXRot((float) (-Math.toDegrees(Math.atan2(dy, flat))));
            camera.setYHeadRot(camera.getYRot());
        }

        private void beat(ServerLevel level) {
            int t = ticks;

            if (t == 1) {
                title(Component.literal("Ollivanders").withStyle(ChatFormatting.GOLD),
                        Component.literal("Makers of Fine Wands since 382 B.C.")
                                .withStyle(ChatFormatting.GRAY), 10, 50, 20);
                sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_SHOP.get(), 0.7F, 1.0F);
            }

            // Dust hanging in the light, all the way through -- the shop has not been swept in
            // several centuries and it is the cheapest way to make the air read as still.
            if (t % 3 == 0) {
                level.sendParticles(DUST_MOTE, centre.x, centre.y, centre.z,
                        2, 1.4D, 1.0D, 1.4D, 0.004D);
            }

            if (t == T_STILL) {
                say(0, "I wondered how long it would be before you came in.");
                sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_DOOR.get(), 0.7F, 1.0F);
            } else if (t == T_STILL + 35) {
                say(1, "Every wand that has left this shop, I remember. Whose hand it went to, and what it did there.");
            } else if (t == T_REMEMBER) {
                say(2, "Now. Let us see what will have you.");
                sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_LEDGER.get(), 1.0F, 1.0F);
            }

            // --- the tape measures him, on its own ------------------------------------------
            if (t > T_REMEMBER && t <= T_MEASURE) {
                double u = (t - T_REMEMBER) / (double) MEASURE;
                double a = u * Math.PI * 6.0D;
                double r = 0.75D;
                level.sendParticles(MagicParticles.MOTE_WHITE,
                        stand.x + Math.cos(a) * r, stand.y + 0.2D + u * 1.9D, stand.z + Math.sin(a) * r,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (t % 12 == 0) {
                    sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_MEASURE.get(), 0.45F, 0.9F + (float) u * 0.7F);
                }
            }
            if (t == T_REMEMBER + 20) {
                say(3, "Wand arm out. No, it does not need holding -- it manages perfectly well by itself.");
            }

            // --- three wands that will not do -------------------------------------------------
            if (t == T_MEASURE + 6) {
                offer(level, "Ash and unicorn. Nine inches, rather springy. Go on -- give it a wave.");
            } else if (t == T_TRY1 - 18) {
                fail(level, 0);
                say(5, "No. No, put it down.");
            } else if (t == T_TRY1 + 6) {
                offer(level, "Maple and dragon heartstring. Seven inches. Quite whippy.");
            } else if (t == T_TRY2 - 18) {
                fail(level, 1);
                say(7, "Certainly not.");
            } else if (t == T_TRY2 + 6) {
                offer(level, "Ebony and unicorn hair. Eight and a half. Pliable.");
            } else if (t == T_TRY3 - 18) {
                fail(level, 2);
                say(9, "You are a tricky customer. Not to worry. We will find the pair of you.");
            }

            // --- the one that will --------------------------------------------------------------
            if (t == T_TRY3 + 4) {
                say(10, "I wonder... yes, I rather think so.");
                sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_BOX.get(), 0.9F, 1.0F);
            } else if (t == T_TRY3 + 26) {
                say(11, "Holly. Phoenix feather. Eleven inches, and very supple indeed.");
            }
            if (t > T_TRY3 + 26 && t <= T_CHOSEN - BREATH) {
                // Light gathers IN rather than bursting out. A burst is a firework; converging
                // light is something choosing you. Every mote is thrown at the wand, so the
                // whole room leans toward it, and the three arms tighten as the voice goes quiet.
                double u = (t - (T_TRY3 + 26)) / (double) (CHOSEN - BREATH - 26);
                double r = 3.2D * (1.0D - u) + 0.5D;
                Vec3 wand = stand.add(0.0D, 1.25D, 0.0D);
                for (int i = 0; i < 3; i++) {
                    double a = (t * 0.35D) + i * (Math.PI * 2.0D / 3.0D);
                    double px = stand.x + Math.cos(a) * r;
                    double py = stand.y + 1.0D + Math.sin(u * Math.PI) * 0.9D;
                    double pz = stand.z + Math.sin(a) * r;
                    // thrown at the wand with a sideways curl, so each mote spirals in
                    double ix = wand.x - px;
                    double iy = wand.y - py;
                    double iz = wand.z - pz;
                    double il = Math.max(0.001D, Math.sqrt(ix * ix + iy * iy + iz * iz));
                    double tx = -Math.sin(a) * 0.5D;
                    double tz = Math.cos(a) * 0.5D;
                    ParticleOptions arm = i == 0 ? MagicParticles.MOTE_WHITE : (i == 1 ? gold(1.0F) : accent(1.0F));
                    level.sendParticles(arm, px, py, pz, 0,
                            ix / il + tx, iy / il, iz / il + tz, 0.05D + 0.06D * u);
                }
                if (t % 6 == 0) {
                    level.sendParticles(gold(0.9F), stand.x, stand.y + 1.2D, stand.z,
                            4, 0.35D, 0.5D, 0.35D, 0.01D);
                }
                // fine sparks racing in along the floor, more of them as it nears
                if (t % 4 == 0 && u > 0.35D) {
                    double a = level.random.nextDouble() * Math.PI * 2.0D;
                    level.sendParticles(MagicParticles.SPARK_GOLD,
                            stand.x + Math.cos(a) * 2.2D, stand.y + 0.6D + level.random.nextDouble() * 1.2D,
                            stand.z + Math.sin(a) * 2.2D, 0, -Math.cos(a), 0.05D, -Math.sin(a), 0.14D);
                }
                if (t == T_TRY3 + 34) {
                    sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_CHOSEN.get(), 1.0F, 1.0F);
                }
            }

            // --- a held breath ---------------------------------------------------------------------
            // Everything stops. One glint hangs over the wand and swells: the tell.
            if (t == T_CHOSEN - BREATH + 1 || t == T_CHOSEN - 5) {
                level.sendParticles(GLINT_BREATH, stand.x, stand.y + 1.25D, stand.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // --- and the wand chooses ----------------------------------------------------------------
            if (t == T_CHOSEN) {
                choose(level);
            } else if (t > T_CHOSEN && t <= T_CHOSEN + 30 && t % 3 == 0) {
                // the afterglow: glints hang where the light was and a few motes settle through them
                level.sendParticles(MagicParticles.GLINT_GOLD, stand.x, stand.y + 1.3D, stand.z,
                        1, 1.5D, 0.7D, 1.5D, 0.0D);
                level.sendParticles(gold(0.7F), stand.x, stand.y + 1.3D, stand.z,
                        2, 1.3D, 0.6D, 1.3D, 0.0D);
            }

            // --- curious ------------------------------------------------------------------------
            // Deliberately empty of effects (the afterglow above has died away). Everything before
            // this beat was noise so that this one can be quiet, and a shower of particles over the
            // best line in the scene would waste it.
            if (t == T_CHOSEN + 10) {
                title(Component.literal("Curious").withStyle(ChatFormatting.GOLD), null, 10, 45, 15);
                say(12, "Curious. Very curious.");
                sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_CURIOUS.get(), 0.6F, 1.0F);
            } else if (t == T_CHOSEN + 40) {
                say(13, "The phoenix whose tail feather sits in your wand gave one other. Only one.");
            } else if (t == T_CHOSEN + 68) {
                say(14, "It is curious that you should be chosen by this wand, when its brother gave you that scar.");
            } else if (t == T_CURIOUS - 6) {
                say(15, "We can expect great things from you. Terrible things, perhaps. But great.");
            }

            if (t == T_CURIOUS + 8) {
                title(Component.empty(), null, 0, 1, 8);
            }
        }

        /** The customer's house colour if they have one, otherwise the phoenix's flame. */
        private int accentRgb() {
            HogwartsHouse house = HousePerks.getHouse(player);
            if (house == HogwartsHouse.GRYFFINDOR) {
                return 0xE0302A;
            } else if (house == HogwartsHouse.RAVENCLAW) {
                return 0x4F7BFF;
            } else if (house == HogwartsHouse.HUFFLEPUFF) {
                return 0xFFD83A;
            } else if (house == HogwartsHouse.SLYTHERIN) {
                return 0x3DDB74;
            }
            return PHOENIX_FLAME;
        }

        private ParticleOptions accent(float scale) {
            return MagicParticleOptions.mote(accentRgb(), scale);
        }

        private ParticleOptions gold(float scale) {
            return MagicParticleOptions.mote(PHOENIX_GOLD, scale);
        }

        /**
         * The wand chooses. A flash, a sphere of gold sparks, rings of motes thrown out across the
         * floor, a column of light, and glints left hanging in the air afterwards. In the wand's
         * gold and the customer's house colour (the phoenix's flame if they have none yet). The
         * ground gives a short, light shudder, no more.
         */
        private void choose(ServerLevel level) {
            Vec3 w = stand.add(0.0D, 1.25D, 0.0D);
            int rgb = accentRgb();
            com.hogwartsmagic.fx.Fx.shake(level, w, 0.5F, 14.0F, 14);
            level.sendParticles(ParticleTypes.FLASH, w.x, w.y, w.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            // sparks in every direction, gold and the house colour
            MagicParticles.sphere(level, MagicParticles.SPARK_GOLD, w.x, w.y, w.z, 34, 0.55D, level.random);
            MagicParticles.sphere(level, MagicParticleOptions.spark(rgb, 1.0F), w.x, w.y, w.z, 22, 0.42D, level.random);
            // rings of motes: fast gold at the height of the wand, a slower house one below, a white one above
            MagicParticles.ring(level, gold(1.5F), w.x, w.y, w.z, 40, 0.34D);
            MagicParticles.ring(level, accent(1.3F), w.x, w.y - 0.15D, w.z, 28, 0.2D);
            MagicParticles.ring(level, MagicParticles.MOTE_WHITE, w.x, w.y + 0.2D, w.z, 24, 0.12D);
            // a column of light climbing out of the wand
            for (int i = 0; i < 14; i++) {
                level.sendParticles(MagicParticles.MOTE_WHITE, w.x, w.y + i * 0.14D, w.z, 0,
                        0.0D, 1.0D, 0.0D, 0.06D + level.random.nextDouble() * 0.05D);
            }
            // glints thrown wide, to hang where they stop
            level.sendParticles(MagicParticles.GLINT_WHITE, w.x, w.y, w.z, 12, 1.4D, 0.8D, 1.4D, 0.0D);
            level.sendParticles(MagicParticles.GLINT_GOLD, w.x, w.y, w.z, 10, 1.9D, 1.0D, 1.9D, 0.0D);
            level.sendParticles(MagicParticleOptions.glint(rgb, 1.2F), w.x, w.y, w.z, 8, 1.7D, 0.9D, 1.7D, 0.0D);
            // a warm puff of light smoke where the light broke
            level.sendParticles(MagicParticles.SMOKE_GOLD, w.x, w.y, w.z, 5, 0.3D, 0.2D, 0.3D, 0.02D);
        }

        /** A wand arrives out of the stacks and hangs in front of the customer. */
        private void offer(ServerLevel level, String line) {
            say(4, line);
            sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_OFFER.get(), 0.8F, 1.0F);
            Vec3 from = him.add(0.0D, 1.4D, 0.0D);
            for (int i = 0; i <= 12; i++) {
                double u = i / 12.0D;
                Vec3 p = from.lerp(stand.add(0.0D, 1.3D, 0.0D), u);
                level.sendParticles(MagicParticles.MOTE_GOLD, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        /** ...and does something it should not. Each failure is worse than the one before. */
        private void fail(ServerLevel level, int which) {
            Vec3 at = stand.add(0.0D, 1.3D, 0.0D);
            switch (which) {
                case 0 -> {         // paper, everywhere
                    level.sendParticles(MagicParticles.MOTE_GOLD, at.x, at.y, at.z, 60, 1.6D, 1.0D, 1.6D, 0.6D);
                    sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_FAIL_PAPER.get(), 1.0F, 1.0F);
                }
                case 1 -> {         // a bang
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.6D, 0.4D, 0.6D, 0.0D);
                    level.sendParticles(MagicParticles.SMOKE_DARK, at.x, at.y, at.z, 40, 1.2D, 0.8D, 1.2D, 0.05D);
                    sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_FAIL_BANG.get(), 1.0F, 1.0F);
                }
                default -> {        // a wretched fizzle, which is worse than the bang
                    level.sendParticles(MagicParticles.SMOKE_GREY, at.x, at.y, at.z, 50, 0.5D, 0.5D, 0.5D, 0.02D);
                    sound(level, com.hogwartsmagic.ModSounds.OLLIVANDERS_FAIL_FIZZLE.get(), 1.0F, 1.0F);
                }
            }
        }

        private void say(int index, String text) {
            if (index == lastLine) {
                return;
            }
            lastLine = index;
            player.sendSystemMessage(Component.literal("Ollivander: ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 60, 12));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal(text).withStyle(ChatFormatting.WHITE)));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        }

        private void title(Component title, @Nullable Component subtitle, int in, int stay, int out) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    subtitle == null ? Component.empty() : subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }

        private void sound(ServerLevel level, SoundEvent event, float volume, float pitch) {
            level.playSound(null, stand.x, stand.y, stand.z, event, SoundSource.PLAYERS, volume, pitch);
        }

        void finish() {
            player.setCamera(player);
            // setCamera(player) leaves the body wherever the camera last was (possibly in a wall or
            // in the air); put the customer back on the spot they were standing on.
            if (!player.hasDisconnected() && player.isAlive() && player.serverLevel() == level) {
                player.teleportTo(level, stand.x, stand.y, stand.z, java.util.Set.of(), startYaw, 0.0F);
                player.fallDistance = 0.0F;
            }
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (camera.isAlive()) {
                camera.discard();
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 1, 6));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        }
    }
}
