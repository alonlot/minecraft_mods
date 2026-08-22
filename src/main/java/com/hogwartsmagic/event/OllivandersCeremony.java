package com.hogwartsmagic.event;

import net.minecraft.ChatFormatting;
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
import net.minecraft.sounds.SoundEvents;
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
 *       the wind gets up inside the shop.</li>
 *   <li><b>Curious.</b> The quiet beat: one other feather, one brother wand, and where it left its
 *       mark. This is the beat the whole scene is built to arrive at, so it is the only one with no
 *       effects in it at all -- just his voice and a very still camera.</li>
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

        Scene scene = new Scene(player, camera, player.position(), him);
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
        private final Vec3 stand;             // where the customer is rooted
        private final Vec3 him;               // where the wandmaker is
        private final Vec3 centre;
        private final float startYaw;
        private int ticks;
        private int lastLine = -1;

        Scene(ServerPlayer player, ArmorStand camera, Vec3 stand, Vec3 him) {
            this.player = player;
            this.camera = camera;
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
                sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.6F);
            }

            // Dust hanging in the light, all the way through -- the shop has not been swept in
            // several centuries and it is the cheapest way to make the air read as still.
            if (t % 3 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH, centre.x, centre.y, centre.z,
                        2, 1.4D, 1.0D, 1.4D, 0.005D);
            }

            if (t == T_STILL) {
                say(0, "I wondered how long it would be before you came in.");
                sound(level, SoundEvents.WOODEN_DOOR_CLOSE, 0.6F, 0.7F);
            } else if (t == T_STILL + 35) {
                say(1, "Every wand that has left this shop, I remember. Whose hand it went to, and what it did there.");
            } else if (t == T_REMEMBER) {
                say(2, "Now. Let us see what will have you.");
                sound(level, SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.9F);
            }

            // --- the tape measures him, on its own ------------------------------------------
            if (t > T_REMEMBER && t <= T_MEASURE) {
                double u = (t - T_REMEMBER) / (double) MEASURE;
                double a = u * Math.PI * 6.0D;
                double r = 0.75D;
                level.sendParticles(ParticleTypes.END_ROD,
                        stand.x + Math.cos(a) * r, stand.y + 0.2D + u * 1.9D, stand.z + Math.sin(a) * r,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (t % 12 == 0) {
                    sound(level, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.35F, 1.2F + (float) u);
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
                sound(level, SoundEvents.CHEST_OPEN, 0.8F, 0.8F);
            } else if (t == T_TRY3 + 26) {
                say(11, "Holly. Phoenix feather. Eleven inches, and very supple indeed.");
            }
            if (t > T_TRY3 + 26 && t <= T_CHOSEN) {
                // Light gathers IN rather than bursting out. A burst is a firework; converging
                // light is something choosing you.
                double u = (t - (T_TRY3 + 26)) / (double) (CHOSEN - 26);
                double r = 3.2D * (1.0D - u) + 0.4D;
                for (int i = 0; i < 3; i++) {
                    double a = (t * 0.35D) + i * (Math.PI * 2.0D / 3.0D);
                    level.sendParticles(ParticleTypes.END_ROD,
                            stand.x + Math.cos(a) * r, stand.y + 1.0D + Math.sin(u * Math.PI) * 0.9D,
                            stand.z + Math.sin(a) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                if (t % 6 == 0) {
                    level.sendParticles(ParticleTypes.GLOW, stand.x, stand.y + 1.2D, stand.z,
                            4, 0.35D, 0.5D, 0.35D, 0.01D);
                }
                if (t == T_TRY3 + 34) {
                    sound(level, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F);
                    sound(level, SoundEvents.ELYTRA_FLYING, 0.5F, 0.6F);
                }
            }

            // --- curious ------------------------------------------------------------------------
            // Deliberately empty of effects. Everything before this beat was noise so that this one
            // can be quiet, and a shower of particles over the best line in the scene would waste it.
            if (t == T_CHOSEN + 10) {
                title(Component.literal("Curious").withStyle(ChatFormatting.GOLD), null, 10, 45, 15);
                say(12, "Curious. Very curious.");
                sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.5F);
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

        /** A wand arrives out of the stacks and hangs in front of the customer. */
        private void offer(ServerLevel level, String line) {
            say(4, line);
            sound(level, SoundEvents.ITEM_PICKUP, 0.8F, 0.7F);
            Vec3 from = him.add(0.0D, 1.4D, 0.0D);
            for (int i = 0; i <= 12; i++) {
                double u = i / 12.0D;
                Vec3 p = from.lerp(stand.add(0.0D, 1.3D, 0.0D), u);
                level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        /** ...and does something it should not. Each failure is worse than the one before. */
        private void fail(ServerLevel level, int which) {
            Vec3 at = stand.add(0.0D, 1.3D, 0.0D);
            switch (which) {
                case 0 -> {         // paper, everywhere
                    level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 60, 1.6D, 1.0D, 1.6D, 0.6D);
                    sound(level, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F);
                    sound(level, SoundEvents.ITEM_BREAK, 0.8F, 1.2F);
                }
                case 1 -> {         // a bang
                    level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.6D, 0.4D, 0.6D, 0.0D);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 40, 1.2D, 0.8D, 1.2D, 0.05D);
                    sound(level, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.6F);
                    sound(level, SoundEvents.GLASS_BREAK, 1.0F, 0.9F);
                }
                default -> {        // a wretched fizzle, which is worse than the bang
                    level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 50, 0.5D, 0.5D, 0.5D, 0.02D);
                    sound(level, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.4F);
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
