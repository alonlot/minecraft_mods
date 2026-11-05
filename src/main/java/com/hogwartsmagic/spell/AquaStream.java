package com.hogwartsmagic.spell;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aqua Eructo held: a continuous jet of water from the wand for as long as the cast key is held (and the mana
 * lasts). Each tick the stream arcs from the wand along the caster's aim, falling under its own weight, until it
 * meets a block, a living thing or 24 blocks out (see {@link #trace}); whatever it strikes is driven hard along it (a heavy shove on first contact, a steady push while it stays
 * in the stream), doused, slowed, and if it was burning, wreathed in steam; blazes and magma cubes take damage;
 * fire and lit campfires where it pours go out. No water block is ever placed: the clients draw the water
 * ({@code AquaStreamClient}) from the start and end packets, tracing the stream from the caster's own aim.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AquaStream {
    public static final byte START = 0;
    public static final byte KEEP = 1;
    public static final byte END = 2;

    public static final double RANGE = 24.0;
    /** How wide the stream is, for what it hits. */
    private static final double RADIUS = 0.55;
    private static final int HEARTBEAT_TIMEOUT = 5;
    private static final int UPKEEP_INTERVAL = 10;
    /** A tap still pours this long. */
    private static final int MIN_TICKS = 10;

    private static final class Stream {
        final UUID caster;
        final long started;
        long lastSeen;
        long lastDrain;
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
        return STREAMS.containsKey(player.getUUID());
    }

    /** The cast key is down: start the stream (paying the cast) or keep it pouring. */
    public static void heartbeat(ServerPlayer player, ItemStack wand) {
        long now = player.level().getGameTime();
        Stream stream = STREAMS.get(player.getUUID());
        if (stream != null) {
            stream.lastSeen = now;
            return;
        }
        boolean creative = player.isCreative();
        if (!creative && MagicWandItem.isOnCooldown(wand, SpellType.AQUA_ERUCTO, now)) {
            return;
        }
        if (!creative && !pay(player, SpellCosts.manaCost(player, wand, SpellType.AQUA_ERUCTO))) {
            return;
        }
        STREAMS.put(player.getUUID(), new Stream(player.getUUID(), now));
        com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SPELL_AQUA.get(),
                SoundSource.PLAYERS, 1.1f, 0.95f);
        send(player, START);
    }

    /** The cast key was let go. A tap still pours a moment; the tick handler ends it then. */
    public static void release(ServerPlayer player) {
        Stream stream = STREAMS.get(player.getUUID());
        if (stream != null) {
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
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            long now = player.level().getGameTime();
            ItemStack wand = player.getMainHandItem();
            boolean held = now - stream.lastSeen <= HEARTBEAT_TIMEOUT || now - stream.started < MIN_TICKS;
            if (!held || !MagicWandItem.isAnyWand(wand)
                    || MagicWandItem.getSelectedSpell(wand) != SpellType.AQUA_ERUCTO
                    || com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                stop(player, wand, now);
                it.remove();
                continue;
            }
            if (!player.isCreative() && now - stream.lastDrain >= UPKEEP_INTERVAL) {
                stream.lastDrain = now;
                float upkeep = Math.max(1.0f, SpellCosts.manaCost(player, wand, SpellType.AQUA_ERUCTO) * 0.15f);
                if (!pay(player, upkeep)) {
                    stop(player, wand, now);
                    it.remove();
                    continue;
                }
            }
            pour(player, stream, now);
            if ((now - stream.started) % 5 == 0) {
                send(player, KEEP); // the clients let a stream go if they stop hearing of it
            }
        }
    }

    private static void stop(ServerPlayer player, ItemStack wand, long now) {
        send(player, END);
        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellCosts.startCooldown(player, wand, SpellType.AQUA_ERUCTO, now);
        }
    }

    /**
     * The stream's path: a jet leaving {@code from} along {@code dir} and falling under its own weight, traced a
     * block at a time until it meets a block, a living thing (the first one stops it: what stands behind is
     * shielded), or has run {@link #RANGE} blocks. Shared by the server (what it hits) and the clients (what they
     * draw), so the water you see is the water that pushes.
     */
    public record Pour(java.util.List<Vec3> points, Vec3 end, @javax.annotation.Nullable net.minecraft.core.Direction face,
                       @javax.annotation.Nullable LivingEntity target) {
        public boolean hitSomething() {
            return face != null || target != null;
        }
    }

    /** Blocks the jet covers per step, and how much it falls per step per step. */
    private static final double STEP = 1.0;
    private static final double GRAVITY = 0.014;
    private static final int MAX_STEPS = 40;

    public static Pour trace(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity caster, Vec3 from,
                             Vec3 dir) {
        java.util.List<Vec3> points = new java.util.ArrayList<>();
        points.add(from);
        Vec3 at = from;
        Vec3 velocity = dir.normalize().scale(STEP);
        double travelled = 0.0;
        for (int i = 0; i < MAX_STEPS && travelled < RANGE; i++) {
            Vec3 next = at.add(velocity);
            BlockHitResult block = level.clip(new ClipContext(at, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
            LivingEntity first = null;
            Vec3 entry = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, stop).inflate(RADIUS + 0.5),
                    e -> e != caster && e.isAlive() && !e.isSpectator())) {
                AABB box = e.getBoundingBox().inflate(RADIUS);
                java.util.Optional<Vec3> clip = box.clip(at, stop);
                Vec3 hit = clip.orElse(box.contains(at) ? at : null);
                if (hit != null && at.distanceToSqr(hit) < best) {
                    best = at.distanceToSqr(hit);
                    first = e;
                    entry = hit;
                }
            }
            if (first != null) {
                points.add(entry);
                return new Pour(points, entry, null, first);
            }
            if (block.getType() != HitResult.Type.MISS) {
                points.add(stop);
                return new Pour(points, stop, block.getDirection(), null);
            }
            points.add(next);
            travelled += velocity.length();
            at = next;
            velocity = velocity.add(0.0, -GRAVITY, 0.0);
        }
        return new Pour(points, at, null, null);
    }

    /** Where the stream runs this tick, and what it does to what it meets. */
    private static void pour(ServerPlayer player, Stream stream, long now) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Pour pour = trace(level, player, player.getEyePosition(), player.getLookAngle());
        Vec3 to = pour.end();
        LivingEntity target = pour.target();
        if (target != null) {
            java.util.List<Vec3> points = pour.points();
            Vec3 dir = to.subtract(points.get(Math.max(0, points.size() - 2)));
            dir = dir.lengthSqr() < 1.0E-6 ? player.getLookAngle() : dir.normalize();
            double distance = player.getEyePosition().distanceTo(to);
            float falloff = (float) Math.max(0.35, 1.0 - distance / (RANGE * 1.2));
            if (stream.struck.add(target.getUUID())) {
                // The first hit of the jet: a heavy shove, and everything the bolt did (douse, steam, slow, XP).
                SpellType.AQUA_ERUCTO.applyHitEffect(target, player, dir, true);
            } else {
                // Held in the stream: driven steadily along it.
                double along = target.getDeltaMovement().dot(dir);
                if (along < 1.4) {
                    target.push(dir.x * 0.34 * falloff, 0.06 + dir.y * 0.2 * falloff, dir.z * 0.34 * falloff);
                }
                target.clearFire();
                if ((now - stream.started) % 10 == 0 && (target instanceof net.minecraft.world.entity.monster.Blaze
                        || target instanceof net.minecraft.world.entity.monster.MagmaCube)) {
                    target.hurt(target.damageSources().drown(), 3.0f);
                }
            }
            target.hurtMarked = true; // a player only feels it once the motion is sent
        }

        // Fire where it pours, and along the way every few ticks.
        douse(level, BlockPos.containing(to));
        douse(level, BlockPos.containing(to).above());
        if ((now - stream.started) % 4 == 0) {
            for (Vec3 point : pour.points()) {
                douse(level, BlockPos.containing(point));
            }
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(to).offset(-1, -1, -1),
                    BlockPos.containing(to).offset(1, 1, 1))) {
                douse(level, pos);
            }
        }
        if ((now - stream.started) % 12 == 0 && pour.hitSomething()) {
            level.playSound(null, to.x, to.y, to.z, ModSounds.AQUA_SPLASH.get(), SoundSource.PLAYERS, 0.7f,
                    0.9f + level.getRandom().nextFloat() * 0.2f);
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
