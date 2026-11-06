package com.hogwartsmagic.spell;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Priori Incantatem: two duellists who cast at each other at the same moment don't cancel out, their beams lock.
 * A bead of light sits where the two spells meet; each fresh cast a duellist makes pushes it toward the other
 * (NPCs push at their own strength, the Dark Lord hardest). Whoever's wand the bead reaches takes the other's
 * spell; at the time limit whoever is ahead still takes it, and only a dead heat blows apart between them. Both are held nearly still
 * while it lasts and can cast nothing else.
 *
 * <p>Server side: who is locked, the bead, the pushes and the outcome. The clients draw it from {@code
 * PrioriLockPacket}s ({@code PrioriClient}); the ending is drawn as a clash.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PrioriIncantatem {
    public static final byte START = 0;
    public static final byte UPDATE = 1;
    public static final byte END = 2;

    /** Two beams this close in age count as cast at the same moment. */
    private static final int SAME_MOMENT_TICKS = 8;
    private static final double MAX_DISTANCE = 40.0;
    /** The tug-of-war meter goes to each locked player this often. */
    private static final int METER_EVERY = 3;
    private static final int METER_CELLS = 21;

    private static final List<Lock> LOCKS = new ArrayList<>();
    private static int nextId = 1;

    /**
     * Client side only: this client's own player is locked (set by PrioriClient each tick). The wand-use hook reads
     * it to send a bare push past the mana and hold-spell checks, which would otherwise swallow the press.
     */
    public static volatile boolean clientLocalLocked;

    private PrioriIncantatem() {
    }

    private static final class Lock {
        final int id;
        final ServerLevel level;
        final UUID a;
        final UUID b;
        final int aId;
        final int bId;
        final SpellType spellA;
        final SpellType spellB;
        /** -1 at A's wand, +1 at B's wand. */
        float bead;
        int age;
        int pushesA;
        int pushesB;
        /** The two beams that locked, so the clients end them in the lock rather than striking. */
        int beamA = -1;
        int beamB = -1;

        Lock(int id, ServerLevel level, LivingEntity a, LivingEntity b, SpellType spellA, SpellType spellB) {
            this.id = id;
            this.level = level;
            this.a = a.getUUID();
            this.b = b.getUUID();
            this.aId = a.getId();
            this.bId = b.getId();
            this.spellA = spellA;
            this.spellB = spellB;
        }
    }

    public static boolean isLocked(Entity entity) {
        UUID id = entity.getUUID();
        for (Lock lock : LOCKS) {
            if (lock.a.equals(id) || lock.b.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two beams have met head on: lock them if they are two duellists' spells cast at the same moment. Returns true
     * when it locked (the caller discards both beams and plays no clash).
     */
    public static boolean tryLock(Entity beamA, SpellType spellA, Entity beamB, SpellType spellB, int ageA, int ageB,
                                  Entity ownerA, Entity ownerB) {
        if (!(beamA.level() instanceof ServerLevel level) || !(ownerA instanceof LivingEntity a)
                || !(ownerB instanceof LivingEntity b) || a == b || !a.isAlive() || !b.isAlive()) {
            return false;
        }
        if (!(a instanceof Player) && !(b instanceof Player)) {
            return false; // a duel a player is in
        }
        if (Math.abs(ageA - ageB) > SAME_MOMENT_TICKS || a.level() != b.level() || a.distanceTo(b) > MAX_DISTANCE
                || isLocked(a) || isLocked(b)) {
            return false;
        }
        if (spellA == SpellType.AQUA_ERUCTO || spellB == SpellType.AQUA_ERUCTO) {
            return false;
        }
        Lock lock = new Lock(nextId++, level, a, b, spellA, spellB);
        lock.beamA = beamA.getId();
        lock.beamB = beamB.getId();
        LOCKS.add(lock);
        send(lock, START);
        // Anyone tracking only the beams (midway in a long duel) must not see them strike instead.
        SpellPackets.PrioriLockPacket start = new SpellPackets.PrioriLockPacket(lock.id, lock.aId, lock.bId,
                lock.beamA, lock.beamB, lock.spellA.getId(), lock.spellB.getId(), lock.bead, START);
        SpellPackets.CHANNEL.send(start, PacketDistributor.TRACKING_ENTITY.with(beamA));
        SpellPackets.CHANNEL.send(start, PacketDistributor.TRACKING_ENTITY.with(beamB));
        MusicStings.priori(a);
        MusicStings.priori(b);
        Component prompt = Component.translatable("gui." + ExampleMod.MODID + ".priori.prompt")
                .withStyle(ChatFormatting.GOLD);
        if (a instanceof ServerPlayer pa) {
            pa.displayClientMessage(prompt, true);
        }
        if (b instanceof ServerPlayer pb) {
            pb.displayClientMessage(prompt, true);
        }
        return true;
    }

    /** A locked player's cast key: a push instead of a spell. */
    public static void push(ServerPlayer player) {
        UUID id = player.getUUID();
        for (Lock lock : LOCKS) {
            if (lock.a.equals(id)) {
                lock.pushesA++;
            } else if (lock.b.equals(id)) {
                lock.pushesB++;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LOCKS.isEmpty()) {
            return;
        }
        Iterator<Lock> it = LOCKS.iterator();
        while (it.hasNext()) {
            Lock lock = it.next();
            Entity ea = lock.level.getEntity(lock.a);
            Entity eb = lock.level.getEntity(lock.b);
            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b) || !a.isAlive() || !b.isAlive()
                    || a.distanceTo(b) > MAX_DISTANCE + 8.0) {
                send(lock, END);
                it.remove();
                continue;
            }
            lock.age++;
            hold(a);
            hold(b);
            // At most one press a tick counts, however many packets arrive.
            float pushA = Math.min(lock.pushesA, 1) * PrioriRules.PLAYER_PUSH + npcPush(a, lock.level);
            float pushB = Math.min(lock.pushesB, 1) * PrioriRules.PLAYER_PUSH + npcPush(b, lock.level);
            lock.pushesA = 0;
            lock.pushesB = 0;
            // A's pushes drive the bead toward B (+1), B's toward A (-1), and it never sits quite still.
            lock.bead = PrioriRules.step(lock.bead, pushA, pushB,
                    (lock.level.getRandom().nextFloat() - 0.5f) * 0.01f);
            int outcome = PrioriRules.outcome(lock.bead, lock.age);
            if (outcome == PrioriRules.A_WINS || outcome == PrioriRules.B_WINS) {
                boolean aWins = outcome == PrioriRules.A_WINS;
                finish(lock, aWins ? a : b, aWins ? b : a, aWins ? lock.spellA : lock.spellB);
                it.remove();
                continue;
            }
            if (outcome == PrioriRules.BLOW_APART) {
                blowApart(lock, a, b);
                it.remove();
                continue;
            }
            if (lock.age % 2 == 0) {
                send(lock, UPDATE);
            }
            if (lock.age % METER_EVERY == 0) {
                meter(lock, a, true);
                meter(lock, b, false);
            }
        }
    }

    /** Held nearly still for the lock, and turned to face the duel. */
    private static void hold(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 6, false, false, false));
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.2, 1.0, 0.2));
    }

    private static float npcPush(LivingEntity entity, ServerLevel level) {
        if (entity instanceof Player) {
            return 0.0f;
        }
        float pressure = entity instanceof VoldemortEntity ? PrioriRules.VOLDEMORT_PRESSURE
                : entity instanceof StudentEntity ? PrioriRules.STUDENT_PRESSURE : PrioriRules.WIZARD_PRESSURE;
        return PrioriRules.npcPush(pressure, level.getRandom().nextFloat());
    }

    /**
     * The locked player's tug-of-war, on the action bar: their own stream from the left, the bead, the other's
     * stream from the right. Green while the bead is on the far half, red while it is on theirs.
     */
    private static void meter(Lock lock, LivingEntity entity, boolean isA) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        // Toward the opponent is +1 from this player's side.
        float lead = isA ? lock.bead : -lock.bead;
        int at = Math.round((lead + 1.0f) * 0.5f * (METER_CELLS - 1));
        ChatFormatting tone = lead > 0.15f ? ChatFormatting.GREEN : lead < -0.15f ? ChatFormatting.RED
                : ChatFormatting.GOLD;
        MutableComponent bar = Component.translatable("gui." + ExampleMod.MODID + ".priori.you")
                .withStyle(ChatFormatting.YELLOW).append(" ");
        bar.append(Component.literal("|".repeat(at)).withStyle(tone));
        bar.append(Component.literal("◆").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        bar.append(Component.literal("|".repeat(METER_CELLS - 1 - at)).withStyle(ChatFormatting.DARK_GRAY));
        bar.append(" ").append(Component.translatable("gui." + ExampleMod.MODID + ".priori.push")
                .withStyle(lock.age / 6 % 2 == 0 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        player.displayClientMessage(bar, true);
    }

    private static void tell(LivingEntity entity, String key, ChatFormatting colour) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".priori." + key)
                    .withStyle(colour, ChatFormatting.BOLD), true);
        }
    }

    /** The bead reached the loser's wand: the winner's spell takes them, and the lock bursts at their wand. */
    private static void finish(Lock lock, LivingEntity winner, LivingEntity loser, SpellType spell) {
        send(lock, END);
        Vec3 at = loser.getEyePosition().add(loser.getLookAngle().scale(0.8));
        Vec3 dir = loser.position().subtract(winner.position());
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        byte outcome = winner.getId() == lock.aId ? SpellBeamFx.CLASH_A_WINS : SpellBeamFx.CLASH_B_WINS;
        SpellType spellA = lock.spellA;
        SpellType spellB = lock.spellB;
        LivingEntity a = winner.getId() == lock.aId ? winner : loser;
        LivingEntity b = a == winner ? loser : winner;
        Vec3 aToB = b.position().subtract(a.position());
        SpellBeamFx.clash(a, spellA, b, spellB, at, aToB, aToB.scale(-1.0), outcome);
        spell.applyHitEffect(loser, winner, dir, true);
        SpellBeamFx.react(loser, spell, dir, SpellBeamFx.REACT_HIT);
        // The loser is thrown back off their feet by the light that reached them.
        loser.push(dir.x * 0.8, 0.3, dir.z * 0.8);
        loser.hurtMarked = true;
        tell(winner, "won", ChatFormatting.GREEN);
        tell(loser, "lost", ChatFormatting.RED);
    }

    /** Neither broke: it blows apart between them and throws them both back. */
    private static void blowApart(Lock lock, LivingEntity a, LivingEntity b) {
        send(lock, END);
        Vec3 at = a.getEyePosition().lerp(b.getEyePosition(), (lock.bead + 1.0) * 0.5);
        Vec3 aToB = b.position().subtract(a.position());
        SpellBeamFx.clash(a, lock.spellA, b, lock.spellB, at, aToB, aToB.scale(-1.0), SpellBeamFx.CLASH_MUTUAL);
        for (LivingEntity entity : new LivingEntity[]{a, b}) {
            Vec3 away = entity.position().subtract(at);
            away = new Vec3(away.x, 0.0, away.z);
            away = away.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
            entity.push(away.x * 0.9, 0.35, away.z * 0.9);
            entity.hurtMarked = true;
            tell(entity, "draw", ChatFormatting.GOLD);
        }
    }

    private static void send(Lock lock, byte state) {
        SpellPackets.PrioriLockPacket packet = new SpellPackets.PrioriLockPacket(lock.id, lock.aId, lock.bId,
                lock.beamA, lock.beamB, lock.spellA.getId(), lock.spellB.getId(), lock.bead, state);
        Entity a = lock.level.getEntity(lock.a);
        Entity b = lock.level.getEntity(lock.b);
        // Everyone watching either duellist; the client takes the same packet twice without harm.
        if (a != null) {
            SpellPackets.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(a));
        }
        if (b != null) {
            SpellPackets.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(b));
        }
    }

    /** A locked NPC duellist casts nothing else: its beams never enter the world while the lock holds. */
    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (LOCKS.isEmpty() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof com.hogwartsmagic.entity.SpellBeamEntity beam)) {
            return;
        }
        Entity owner = beam.getOwner();
        if (owner != null && !(owner instanceof Player) && isLocked(owner)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOCKS.clear();
    }
}
