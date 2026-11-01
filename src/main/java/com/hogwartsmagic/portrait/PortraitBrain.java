package com.hogwartsmagic.portrait;

import com.hogwartsmagic.portrait.PortraitLines.Line;
import com.hogwartsmagic.portrait.PortraitLines.Reaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server side of a living portrait: decides when it speaks and which line. Runs every 10 ticks from its entity
 * and idles when no player is within 24 blocks. One line at a time ({@code busyUntil}); only CREEPER, PUNCHED,
 * EXPLOSION and THUNDER may cut a line short, and not each other; a reaction never repeats its previous line;
 * the reactions that own a video line use it only when that clip has not played for two minutes.
 *
 * <p>Common code: it only sets the entity's synced values, the client does the rest.
 */
public final class PortraitBrain {
    private static final Reaction[] REACTIONS = Reaction.values();

    /** Cooldown per reaction in ticks. Reactions in PER_PLAYER are cooled down per player instead of globally. */
    private static final int[] COOLDOWN = new int[REACTIONS.length];
    private static final boolean[] PER_PLAYER = new boolean[REACTIONS.length];
    private static final boolean[] INTERRUPTS = new boolean[REACTIONS.length];

    static {
        cooldown(Reaction.GREET, 60, true);
        cooldown(Reaction.ROBES_GRYFFINDOR, 60, true);
        cooldown(Reaction.ROBES_SLYTHERIN, 60, true);
        cooldown(Reaction.ROBES_RAVENCLAW, 60, true);
        cooldown(Reaction.ROBES_HUFFLEPUFF, 60, true);
        cooldown(Reaction.LEAVE, 60, true);
        cooldown(Reaction.DARK, 90, false);
        cooldown(Reaction.LIGHT_BACK, 0, false);
        cooldown(Reaction.CREEPER, 20, false);
        cooldown(Reaction.HOSTILE, 25, false);
        cooldown(Reaction.PUNCHED, 3, false);
        cooldown(Reaction.EXPLOSION, 8, false);
        cooldown(Reaction.RAIN, 600, false);
        cooldown(Reaction.THUNDER, 20, false);
        cooldown(Reaction.PLAYER_HURT, 60, true);
        cooldown(Reaction.WAND, 40, true);
        cooldown(Reaction.SWORD, 40, true);
        cooldown(Reaction.SING, 180, false);
        cooldown(Reaction.MORNING, 0, false);
        cooldown(Reaction.NIGHTFALL, 0, false);
        INTERRUPTS[Reaction.CREEPER.ordinal()] = true;
        INTERRUPTS[Reaction.PUNCHED.ordinal()] = true;
        INTERRUPTS[Reaction.EXPLOSION.ordinal()] = true;
        INTERRUPTS[Reaction.THUNDER.ordinal()] = true;
    }

    private static void cooldown(Reaction r, int seconds, boolean perPlayer) {
        COOLDOWN[r.ordinal()] = seconds * 20;
        PER_PLAYER[r.ordinal()] = perPlayer;
    }


    private static final long CLIP_COOLDOWN = 2400;      // 2 minutes
    private static final long LINE_PAD = 30;             // ticks of silence after a line
    private static final long LIGHT_BACK_WINDOW = 6000;  // 5 minutes after she complained about the dark
    private static final long PENDING_LEAVE_WINDOW = 240; // a farewell still makes sense 12 s after they left
    private static final int SING_NEAR_TICKS = 600;      // a player near for 30 s
    private static final long SING_QUIET = 300;          // and she has been quiet for 15 s

    private final PortraitEntity portrait;
    private final List<Line> lines;
    /** The indices into the kind's lines that belong to each reaction (empty: this portrait never reacts to it). */
    private final int[][] linesOf = new int[REACTIONS.length][];

    private long busyUntil;
    @Nullable
    private Reaction current;
    private final long[] globalCooldown = new long[REACTIONS.length];
    private final Map<UUID, long[]> playerCooldown = new HashMap<>();
    private final int[] lastLine = new int[REACTIONS.length];
    private final Map<String, Long> clipPlayed = new HashMap<>();
    private final Set<UUID> greeted = new HashSet<>();
    private final Map<UUID, Integer> nearTicks = new HashMap<>();
    private boolean darkPending;
    private long darkAt;
    /** A greeted player who walked off while she was mid-line: the farewell waits for her to finish. */
    private UUID pendingLeaver;
    private long pendingLeaveUntil;
    private boolean worldKnown;
    private boolean wasRaining;
    private boolean wasNight;

    PortraitBrain(PortraitEntity portrait) {
        this.portrait = portrait;
        this.lines = portrait.kind().lines();
        for (Reaction r : REACTIONS) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).reaction() == r) {
                    list.add(i);
                }
            }
            linesOf[r.ordinal()] = list.stream().mapToInt(Integer::intValue).toArray();
        }
        java.util.Arrays.fill(lastLine, -1);
    }

    // ---------------------------------------------------------------- the 10-tick brain

    void tick(ServerLevel level) {
        long now = level.getGameTime();

        // Rain and the clock are watched even when nobody is around, so a transition that happened while she was
        // idle is not mistaken for a new one the moment someone walks up.
        boolean sky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        boolean raining = sky && level.isRaining();
        boolean timed = sky && !level.dimensionType().hasFixedTime();
        boolean night = timed && level.getDayTime() % 24000L >= 13000L;
        boolean rainStarted = worldKnown && raining && !wasRaining;
        boolean nightfall = worldKnown && timed && night && !wasNight;
        boolean morning = worldKnown && timed && !night && wasNight;
        wasRaining = raining;
        wasNight = night;
        worldKnown = true;

        Vec3 center = portrait.position();
        List<ServerPlayer> players = level.getPlayers(p -> !p.isSpectator() && p.isAlive()
                && p.distanceToSqr(center) <= 24.0 * 24.0);
        if (players.isEmpty()) {
            portrait.setLook(-1);
            greeted.clear();
            nearTicks.clear();
            return;
        }

        // who is near, who she looks at
        List<ServerPlayer> near = new ArrayList<>();
        ServerPlayer nearest = null;
        double nearestDist = Double.MAX_VALUE;
        ServerPlayer lookAt = null;
        double lookDist = Double.MAX_VALUE;
        Vec3 front = portrait.frontVector();
        for (ServerPlayer p : players) {
            double dist = distanceToCanvas(p);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
            if (dist <= 16.0 && p.position().subtract(center).dot(front) > 0.0 && dist < lookDist) {
                lookDist = dist;
                lookAt = p;
            }
            if (isNear(p, level)) {
                near.add(p);
                nearTicks.merge(p.getUUID(), 10, Integer::sum);
            } else {
                nearTicks.remove(p.getUUID());
            }
        }
        nearTicks.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));
        portrait.setLook(lookAt == null ? -1 : lookAt.getId());

        // LEAVE bookkeeping: a greeted player who walks beyond 10 blocks (or logs out) is no longer greeted
        ServerPlayer leaver = null;
        for (Iterator<UUID> it = greeted.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null || p.level() != level || p.isSpectator()) {
                it.remove();
            } else if (distanceToCanvas(p) > 10.0) {
                it.remove();
                if (leaver == null) {
                    leaver = p;
                }
            }
        }

        BlockPos frontPos = BlockPos.containing(center.x + front.x * 0.6, center.y, center.z + front.z * 0.6);

        // 1. things that may cut in
        if (hasCreeperNear(level)) {
            if (say(Reaction.CREEPER, nearest, level)) {
                return;
            }
        }
        // 2. everything else, in priority order; the first that speaks ends the brain tick
        if (hasHostileNear(level) && say(Reaction.HOSTILE, nearest, level)) {
            return;
        }
        if (nearestDist <= 8.0 && level.getMaxLocalRawBrightness(frontPos) <= 4
                && say(Reaction.DARK, nearest, level)) {
            darkPending = true;
            darkAt = now;
            return;
        }
        if (darkPending) {
            if (now - darkAt > LIGHT_BACK_WINDOW) {
                darkPending = false;
            } else if (!near.isEmpty() && level.getBrightness(LightLayer.BLOCK, frontPos) >= 7
                    && say(Reaction.LIGHT_BACK, near.get(0), level)) {
                darkPending = false;
                return;
            }
        }
        for (ServerPlayer p : near) {
            if (p.getHealth() <= 6.0f && say(Reaction.PLAYER_HURT, p, level)) {
                return;
            }
        }
        for (ServerPlayer p : near) {
            if (holdsWand(p) && looksAtPortrait(p) && say(Reaction.WAND, p, level)) {
                return;
            }
        }
        for (ServerPlayer p : near) {
            if (holdsSword(p) && looksAtPortrait(p) && say(Reaction.SWORD, p, level)) {
                return;
            }
        }
        for (ServerPlayer p : near) {
            if (greeted.contains(p.getUUID())) {
                continue;
            }
            long[] cds = playerCooldown(p);
            Reaction greeting = robesReaction(p);
            if (linesOf[greeting.ordinal()].length == 0) {
                greeting = Reaction.GREET; // no house greeting recorded for this portrait
            }
            if (now < cds[Reaction.GREET.ordinal()]) {
                greeted.add(p.getUUID()); // greeted a moment ago: recognise her, say nothing
                continue;
            }
            if (say(greeting, p, level)) {
                greeted.add(p.getUUID());
                cds[Reaction.GREET.ordinal()] = now + COOLDOWN[Reaction.GREET.ordinal()];
                return;
            }
        }
        if (leaver != null && pendingLeaver == null) {
            pendingLeaver = leaver.getUUID();
            pendingLeaveUntil = now + PENDING_LEAVE_WINDOW;
        }
        if (pendingLeaver != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(pendingLeaver);
            if (p == null || now > pendingLeaveUntil || greeted.contains(pendingLeaver)) {
                pendingLeaver = null; // gone, too late, or came back
            } else if (say(Reaction.LEAVE, p, level)) {
                pendingLeaver = null;
                return;
            }
        }
        ServerPlayer within16 = null;
        for (ServerPlayer p : players) {
            if (distanceToCanvas(p) <= 16.0) {
                within16 = p;
                break;
            }
        }
        if (within16 != null) {
            if (rainStarted && say(Reaction.RAIN, within16, level)) {
                return;
            }
            if (nightfall && say(Reaction.NIGHTFALL, within16, level)) {
                return;
            }
            if (morning && say(Reaction.MORNING, within16, level)) {
                return;
            }
        }
        if (now >= busyUntil + SING_QUIET) {
            for (ServerPlayer p : near) {
                if (nearTicks.getOrDefault(p.getUUID(), 0) >= SING_NEAR_TICKS && say(Reaction.SING, p, level)) {
                    return;
                }
            }
        }
    }

    /** Entry point for the explosion, lightning and punch hooks: speaks now if the rules allow it. */
    void trigger(Reaction reaction, @Nullable ServerPlayer who) {
        if (!(portrait.level() instanceof ServerLevel level)) {
            return;
        }
        if (who == null) {
            who = (ServerPlayer) level.getNearestPlayer(portrait, 24.0);
        }
        say(reaction, who, level);
    }

    // ---------------------------------------------------------------- speaking

    /**
     * Tries to start a line for the reaction. Fails (and changes nothing) when she is mid-line and the reaction may
     * not cut in, when it is cooling down, when nobody is around to hear, or when the reaction has no line.
     */
    private boolean say(Reaction reaction, @Nullable ServerPlayer who, ServerLevel level) {
        long now = level.getGameTime();
        int ri = reaction.ordinal();
        if (now < busyUntil) {
            boolean mayCut = INTERRUPTS[ri] && (current == null || !INTERRUPTS[current.ordinal()]);
            if (!mayCut) {
                return false;
            }
        }
        if (PER_PLAYER[ri]) {
            if (who == null || now < playerCooldown(who)[ri]) {
                return false;
            }
        } else if (now < globalCooldown[ri]) {
            return false;
        }
        if (level.getNearestPlayer(portrait, 24.0) == null) {
            return false;
        }
        int index = pick(reaction, now, portrait.getRandom());
        if (index < 0) {
            return false;
        }
        Line line = lines.get(index);

        if (PER_PLAYER[ri]) {
            playerCooldown(who)[ri] = now + COOLDOWN[ri];
        } else {
            globalCooldown[ri] = now + COOLDOWN[ri];
        }
        lastLine[ri] = index;
        if (line.isVideo()) {
            clipPlayed.put(line.clip(), now);
        }
        current = reaction;
        busyUntil = now + (long) Math.ceil(line.seconds() * 20.0) + LINE_PAD;
        if (who != null) {
            portrait.setLook(who.getId());
        }
        portrait.startLine(index);
        return true;
    }

    /** Chooses a line: the video if its clip is rested, otherwise a puppet line, never the previous one again. */
    private int pick(Reaction reaction, long now, net.minecraft.util.RandomSource random) {
        int[] all = linesOf[reaction.ordinal()];
        if (all.length == 0) {
            return -1;
        }
        int video = -1;
        List<Integer> puppet = new ArrayList<>();
        for (int i : all) {
            Line line = lines.get(i);
            if (line.isVideo()) {
                video = i;
            } else {
                puppet.add(i);
            }
        }
        if (video >= 0) {
            Long last = clipPlayed.get(lines.get(video).clip());
            if (last == null || now - last >= CLIP_COOLDOWN || puppet.isEmpty()) {
                return video;
            }
        }
        int previous = lastLine[reaction.ordinal()];
        if (puppet.size() > 1) {
            puppet.remove(Integer.valueOf(previous));
        }
        return puppet.get(random.nextInt(puppet.size()));
    }

    private long[] playerCooldown(ServerPlayer p) {
        return playerCooldown.computeIfAbsent(p.getUUID(), id -> new long[REACTIONS.length]);
    }

    // ---------------------------------------------------------------- perception

    /** Distance from the player's eyes to the canvas box (0 when inside it). */
    private double distanceToCanvas(ServerPlayer p) {
        AABB box = portrait.getBoundingBox();
        Vec3 eye = p.getEyePosition();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** "Near": within 6 blocks, in front of the canvas, with a clear line to it. */
    private boolean isNear(ServerPlayer p, ServerLevel level) {
        if (distanceToCanvas(p) > 6.0) {
            return false;
        }
        Vec3 center = portrait.position();
        Vec3 front = portrait.frontVector();
        Vec3 eye = p.getEyePosition();
        if (eye.subtract(center).dot(front) <= 0.0) {
            return false;
        }
        double half = portrait.kind().heightBlocks() / 2.0 - 0.1; // aim at the canvas, never the block under it
        double y = Math.max(center.y - half, Math.min(center.y + half, eye.y));
        Vec3 target = new Vec3(center.x + front.x * 0.2, y, center.z + front.z * 0.2);
        return level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                .getType() == HitResult.Type.MISS;
    }

    private boolean hasCreeperNear(ServerLevel level) {
        AABB area = portrait.getBoundingBox().inflate(7.0, 4.0, 7.0);
        return !level.getEntitiesOfClass(Creeper.class, area, c -> c.isAlive()).isEmpty();
    }

    private boolean hasHostileNear(ServerLevel level) {
        AABB area = portrait.getBoundingBox().inflate(8.0, 4.0, 8.0);
        return !level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof Enemy && !(e instanceof Creeper)
                        && !(e instanceof com.hogwartsmagic.entity.LivingStatueEntity) // a player's ally, not a threat
                        && e.isAlive()).isEmpty();
    }

    private static boolean holdsWand(ServerPlayer p) {
        return isWand(p.getMainHandItem()) || isWand(p.getOffhandItem());
    }

    private static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("wand");
    }

    private static boolean holdsSword(ServerPlayer p) {
        return isSword(p.getMainHandItem()) || isSword(p.getOffhandItem());
    }

    private static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof SwordItem
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("sword"));
    }

    /** Looking at her: the view direction points at the canvas (or within about 14 degrees of its centre). */
    private boolean looksAtPortrait(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 toCenter = portrait.position().subtract(eye);
        if (toCenter.lengthSqr() > 1.0e-6 && look.dot(toCenter.normalize()) > 0.97) {
            return true;
        }
        return portrait.getBoundingBox().inflate(0.25).clip(eye, eye.add(look.scale(8.0))).isPresent();
    }

    /** GREET, or the house greeting for someone wearing house robes on the chest. */
    private static Reaction robesReaction(ServerPlayer p) {
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) {
            String path = BuiltInRegistries.ITEM.getKey(chest.getItem()).getPath();
            if (path.contains("robes")) {
                if (path.contains("gryffindor")) {
                    return Reaction.ROBES_GRYFFINDOR;
                }
                if (path.contains("slytherin")) {
                    return Reaction.ROBES_SLYTHERIN;
                }
                if (path.contains("ravenclaw")) {
                    return Reaction.ROBES_RAVENCLAW;
                }
                if (path.contains("hufflepuff")) {
                    return Reaction.ROBES_HUFFLEPUFF;
                }
            }
        }
        return Reaction.GREET;
    }
}
