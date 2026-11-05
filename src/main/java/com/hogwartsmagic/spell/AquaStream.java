package com.hogwartsmagic.spell;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

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
 * Aqua Eructo held: a continuous jet of water from the wand for as long as the cast key is held (and the mana
 * lasts). The jet is real water: every tick the wand throws out a fresh {@link Drop} along the caster's aim, and
 * from then on that water flies on its own, straight while it has pressure, then slowing and falling under its own
 * weight, until it lands on a block or a living thing. Swing the wand and the stream bends behind it like a hose;
 * let go and the water already in the air still lands where it was going. Whatever the water strikes is driven
 * along it (a heavy shove on first contact, a steady push while it stays in the stream), doused, slowed, and if it
 * was burning, wreathed in steam; blazes and magma cubes take damage; fire and lit campfires it passes or lands on
 * go out. No water block is ever placed: the clients run the same drops from the caster's wand
 * ({@code AquaStreamClient}) and draw them, so the water you see is the water that pushes.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AquaStream {
    public static final byte START = 0;
    public static final byte KEEP = 1;
    public static final byte END = 2;

    /** How wide the stream is, for what it hits. */
    private static final double RADIUS = 0.55;
    private static final int HEARTBEAT_TIMEOUT = 5;
    private static final int UPKEEP_INTERVAL = 10;
    /** A tap still pours this long. */
    private static final int MIN_TICKS = 10;

    /** How fast the water leaves the wand, blocks a tick. */
    public static final double SPEED = 3.0;
    /** How far the water flies straight before its pressure is spent and it falls. */
    public static final double FLIGHT = 36.0;
    /** How far it may fall (down a cliff, into the void) before it is given up on. */
    private static final double MAX_FALL = 64.0;
    /** Once spent: how hard it falls, how fast it loses its forward speed, and how fast it can fall. */
    private static final double GRAVITY = 0.16;
    private static final double DRAG = 0.82;
    private static final double TERMINAL = 2.6;
    private static final int MAX_AGE = 80;

    /**
     * One tick's worth of water, flying on its own once it leaves the wand. Shared by the server (what it hits)
     * and the clients (what they draw), stepped once a tick on each.
     */
    public static final class Drop {
        public final long id;
        /** Where it was at the start of this tick and where it is now, for drawing between ticks. */
        public Vec3 prev;
        public Vec3 pos;
        public Vec3 vel;
        /** Blocks flown from the wand. */
        public double travelled;
        private double fallen;
        private int age;
        /** It has landed this tick (it sits where it landed for one tick, then is gone). */
        public boolean landed;
        public boolean gone;

        public Drop(long id, Vec3 from, Vec3 dir) {
            this.id = id;
            this.prev = from;
            this.pos = from;
            Vec3 unit = dir.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
            this.vel = unit.scale(SPEED);
        }

        /** Where the water landed: on a face of a block, or on a living thing. */
        public record Hit(Vec3 at, Vec3 dir, @Nullable Direction face, @Nullable LivingEntity target, double travelled) {
        }

        /** Fly one tick. Returns where it landed, if it landed this tick. */
        @Nullable
        public Hit step(Level level, Entity caster) {
            prev = pos;
            if (landed) {
                gone = true;
                return null;
            }
            if (++age > MAX_AGE || fallen > MAX_FALL || pos.y < level.getMinBuildHeight() - 16) {
                gone = true;
                return null;
            }
            Vec3 next = pos.add(vel);
            BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
            LivingEntity first = null;
            Vec3 entry = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, stop).inflate(RADIUS + 0.5),
                    e -> e != caster && e.isAlive() && !e.isSpectator())) {
                AABB box = e.getBoundingBox().inflate(RADIUS);
                Vec3 hit = box.clip(pos, stop).orElse(box.contains(pos) ? pos : null);
                if (hit != null && pos.distanceToSqr(hit) < best) {
                    best = pos.distanceToSqr(hit);
                    first = e;
                    entry = hit;
                }
            }
            Vec3 dir = vel.normalize();
            if (first != null) {
                travelled += pos.distanceTo(entry);
                pos = entry;
                landed = true;
                return new Hit(entry, dir, null, first, travelled);
            }
            if (block.getType() != HitResult.Type.MISS) {
                travelled += pos.distanceTo(stop);
                pos = stop;
                landed = true;
                return new Hit(stop, dir, block.getDirection(), null, travelled);
            }
            travelled += vel.length();
            if (travelled > FLIGHT) {
                fallen += Math.max(0.0, pos.y - next.y);
            }
            pos = next;
            // Straight while it has pressure; spent, it eases over into a fall (no kink where it gives out).
            double spent = Mth.clamp((travelled - (FLIGHT - 4.0)) / 8.0, 0.0, 1.0);
            if (spent > 0.0) {
                double drag = 1.0 - (1.0 - DRAG) * spent;
                double down = Math.max(-TERMINAL, vel.y - GRAVITY * spent);
                vel = new Vec3(vel.x * drag, down, vel.z * drag);
            }
            return null;
        }
    }

    private static final class Stream {
        final UUID caster;
        final long started;
        long lastSeen;
        long lastDrain;
        long lastSplashSound = Long.MIN_VALUE;
        long lastScald = Long.MIN_VALUE;
        /** Let go: no more water comes out, but what is in the air still lands. */
        boolean ended;
        @Nullable ServerLevel level;
        long nextDrop;
        final List<Drop> drops = new ArrayList<>();
        final Set<UUID> struck = new HashSet<>();

        Stream(UUID caster, long now) {
            this.caster = caster;
            this.started = now;
            this.lastSeen = now;
            this.lastDrain = now;
        }
    }

    private static final Map<UUID, Stream> STREAMS = new HashMap<>();

    private AquaStream() {
    }

    public static boolean isStreaming(ServerPlayer player) {
        Stream stream = STREAMS.get(player.getUUID());
        return stream != null && !stream.ended;
    }

    /** The cast key is down: start the stream (paying the cast) or keep it pouring. */
    public static void heartbeat(ServerPlayer player, ItemStack wand) {
        long now = player.level().getGameTime();
        Stream old = STREAMS.get(player.getUUID());
        if (old != null && !old.ended) {
            old.lastSeen = now;
            return;
        }
        boolean creative = player.isCreative();
        if (!creative && MagicWandItem.isOnCooldown(wand, SpellType.AQUA_ERUCTO, now)) {
            return;
        }
        if (!creative && !pay(player, SpellCosts.manaCost(player, wand, SpellType.AQUA_ERUCTO))) {
            return;
        }
        Stream stream = new Stream(player.getUUID(), now);
        if (old != null) {
            // The last stream's water is still landing.
            stream.drops.addAll(old.drops);
            stream.nextDrop = old.nextDrop + 1000;
            stream.level = old.level;
        }
        STREAMS.put(player.getUUID(), stream);
        com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SPELL_AQUA.get(),
                SoundSource.PLAYERS, 1.1f, 0.95f);
        send(player, START);
    }

    /** The cast key was let go. A tap still pours a moment; the tick handler ends it then. */
    public static void release(ServerPlayer player) {
        Stream stream = STREAMS.get(player.getUUID());
        if (stream != null && !stream.ended) {
            stream.lastSeen = Math.min(stream.lastSeen, player.level().getGameTime() - HEARTBEAT_TIMEOUT);
        }
    }

    private static boolean pay(ServerPlayer player, float cost) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        float maxMana = PlayerMagicData.getMaxMana(player);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        if (mana < cost) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return false;
        }
        mana -= cost;
        modData.putFloat("Mana", mana);
        PlayerMagicData.saveModData(player, modData);
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STREAMS.isEmpty()) {
            return;
        }
        Iterator<Stream> it = STREAMS.values().iterator();
        while (it.hasNext()) {
            Stream stream = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(stream.caster);
            if (player == null) {
                it.remove();
                continue;
            }
            long now = player.level().getGameTime();
            if (!stream.ended) {
                ItemStack wand = player.getMainHandItem();
                boolean held = now - stream.lastSeen <= HEARTBEAT_TIMEOUT || now - stream.started < MIN_TICKS;
                boolean upkept = true;
                if (held && !player.isCreative() && now - stream.lastDrain >= UPKEEP_INTERVAL) {
                    stream.lastDrain = now;
                    upkept = pay(player, Math.max(1.0f,
                            SpellCosts.manaCost(player, wand, SpellType.AQUA_ERUCTO) * 0.15f));
                }
                if (!held || !upkept || !player.isAlive() || !MagicWandItem.isAnyWand(wand)
                        || MagicWandItem.getSelectedSpell(wand) != SpellType.AQUA_ERUCTO
                        || com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    stop(player, wand, now);
                    stream.ended = true;
                } else if ((now - stream.started) % 5 == 0) {
                    send(player, KEEP); // the clients let a stream go if they stop hearing of it
                }
            }
            if (!(player.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (stream.level != level) {
                stream.drops.clear(); // changed dimension: that water is in another world now
                stream.level = level;
            }
            if (!stream.ended) {
                Vec3 look = player.getLookAngle();
                // Out of the wand, held out at about where the clients draw its tip.
                Vec3 from = player.getEyePosition().add(look.scale(0.8)).add(0.0, -0.25, 0.0);
                stream.drops.add(new Drop(stream.nextDrop++, from, look));
            }
            flow(level, player, stream, now);
            if (stream.ended && stream.drops.isEmpty()) {
                it.remove();
            }
        }
    }

    private static void stop(ServerPlayer player, ItemStack wand, long now) {
        send(player, END);
        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellCosts.startCooldown(player, wand, SpellType.AQUA_ERUCTO, now);
        }
    }

    /** Every drop in the air flies a tick: fire it passes goes out, and what it lands on feels it. */
    private static void flow(ServerLevel level, ServerPlayer player, Stream stream, long now) {
        for (Drop drop : stream.drops) {
            Drop.Hit hit = drop.step(level, player);
            if (hit != null) {
                land(level, player, stream, hit, now);
            } else if (!drop.gone) {
                douse(level, BlockPos.containing(drop.pos));
                douse(level, BlockPos.containing(drop.prev.lerp(drop.pos, 0.5)));
            }
        }
        stream.drops.removeIf(d -> d.gone);
    }

    /** One drop's worth of water landing. */
    private static void land(ServerLevel level, ServerPlayer player, Stream stream, Drop.Hit hit, long now) {
        LivingEntity target = hit.target();
        Vec3 dir = hit.dir();
        if (target != null) {
            float falloff = (float) Math.max(0.35, 1.0 - hit.travelled() / (FLIGHT * 1.2));
            if (stream.struck.add(target.getUUID())) {
                // The first water to reach it: a heavy shove, and everything the bolt did (douse, steam, slow, XP).
                SpellType.AQUA_ERUCTO.applyHitEffect(target, player, dir, true);
            } else {
                // Held in the stream: driven steadily along it.
                double along = target.getDeltaMovement().dot(dir);
                if (along < 1.4) {
                    target.push(dir.x * 0.34 * falloff, 0.06 + dir.y * 0.2 * falloff, dir.z * 0.34 * falloff);
                }
                target.clearFire();
                if (now - stream.lastScald >= 10 && (target instanceof net.minecraft.world.entity.monster.Blaze
                        || target instanceof net.minecraft.world.entity.monster.MagmaCube)) {
                    stream.lastScald = now;
                    target.hurt(target.damageSources().drown(), 3.0f);
                }
            }
            target.hurtMarked = true; // a player only feels it once the motion is sent
        }
        BlockPos at = BlockPos.containing(hit.at());
        douse(level, at);
        douse(level, at.above());
        if (hit.face() != null) {
            douse(level, at.relative(hit.face()));
        }
        if (now % 4 == 0) {
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                douse(level, pos);
            }
        }
        if (now - stream.lastSplashSound >= 12) {
            stream.lastSplashSound = now;
            level.playSound(null, hit.at().x, hit.at().y, hit.at().z, ModSounds.AQUA_SPLASH.get(), SoundSource.PLAYERS,
                    0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }
    }

    private static void douse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BaseFireBlock) {
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
        } else if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
            CampfireBlock.dowse(null, level, pos, state);
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
        }
    }

    private static void send(ServerPlayer player, byte state) {
        SpellPackets.CHANNEL.send(new SpellPackets.AquaStreamPacket(player.getId(), state),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STREAMS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STREAMS.clear();
    }
}
