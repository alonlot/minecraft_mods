package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every Protego shield (a player's ranked Protego, the NPC layer shields, Protego Maxima's bubble) as
 * mod state rather than a vanilla MobEffect: no potion icon, no effect swirl, untouched by milk. The
 * server keeps it in the holder's persistent data and tells every watching client, which draws the
 * bubble ({@code ProtegoShieldRenderer}). {@code layers} is the old effect amplifier: the number of extra
 * hits a layer shield eats before it shatters; 3 and up is Maxima strength.
 *
 * <p>Common code. The client half ({@link #accept}, {@link #view}) is plain data written by the client's
 * packet handler; on a dedicated server it simply stays empty.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtegoShield {
    private static final String TAG_UNTIL = ExampleMod.MODID + ":shield_until";
    private static final String TAG_LAYERS = ExampleMod.MODID + ":shield_layers";
    private static final String TAG_DOME = ExampleMod.MODID + ":shield_dome";
    private static final String TAG_SYNCED_UNTIL = ExampleMod.MODID + ":shield_synced_until";

    /**
     * A refresh that only extends a held shield (Maxima re-raises its bubble every tick) is sent on once it
     * has run this far past what the watchers were last told, not every time.
     */
    private static final int RESYNC_MARGIN = 6;

    /** What a sync packet reports, beyond the shield's state. */
    public static final byte STATE = 0;
    public static final byte RAISED = 1;
    public static final byte HIT = 2;
    public static final byte PARRIED = 3;
    public static final byte SHATTERED = 4;
    public static final byte DROPPED = 5;

    private ProtegoShield() {
    }

    public static boolean has(LivingEntity entity) {
        return layers(entity) >= 0;
    }

    /** The shield's layers, or -1 without one. On a client, what the server last reported. */
    public static int layers(LivingEntity entity) {
        long now = entity.level().getGameTime();
        if (entity.level().isClientSide()) {
            View view = view(entity.getId(), now);
            return view != null && view.up(now) ? view.layers() : -1;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG_UNTIL) || now >= data.getLong(TAG_UNTIL)) {
            return -1;
        }
        return data.getInt(TAG_LAYERS);
    }

    /** Raise a shield, the bubble drawn at Maxima size from 3 layers up. */
    public static void raise(LivingEntity entity, int ticks, int layers) {
        raise(entity, ticks, layers, layers >= 3);
    }

    /**
     * Raise (or refresh) a shield for {@code ticks}. Like the effect it replaces, a weaker shield never
     * overrides a stronger one that is still up, and an equal one only ever extends it. Server only.
     */
    public static void raise(LivingEntity entity, int ticks, int layers, boolean dome) {
        if (entity.level().isClientSide()) {
            return;
        }
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();
        int current = layers(entity);
        if (current > layers) {
            return;
        }
        long until = now + ticks;
        boolean wasDome = current >= 0 && data.getBoolean(TAG_DOME);
        if (current == layers) {
            until = Math.max(until, data.getLong(TAG_UNTIL));
            dome |= wasDome;
        }
        data.putLong(TAG_UNTIL, until);
        data.putInt(TAG_LAYERS, layers);
        data.putBoolean(TAG_DOME, dome);
        boolean changed = current != layers || wasDome != dome;
        if (current < 0) {
            sync(entity, RAISED);
        } else if (changed || until - data.getLong(TAG_SYNCED_UNTIL) >= RESYNC_MARGIN) {
            sync(entity, STATE);
        }
    }

    /**
     * A layer shield takes a blow: one layer comes off, or the last one shatters. Returns true when it
     * shattered. Server only; a no-op without a shield.
     */
    public static boolean absorbHit(LivingEntity entity) {
        int current = layers(entity);
        if (current < 0 || entity.level().isClientSide()) {
            return false;
        }
        if (current == 0) {
            remove(entity, SHATTERED);
            return true;
        }
        entity.getPersistentData().putInt(TAG_LAYERS, current - 1);
        sync(entity, HIT);
        return false;
    }

    /** The shield held: tell watchers so the bubble flares, without changing it. */
    public static void flash(LivingEntity entity, byte kind) {
        if (!entity.level().isClientSide() && has(entity)) {
            sync(entity, kind);
        }
    }

    /** Take the shield down: {@link #SHATTERED} bursts the bubble, {@link #DROPPED} lets it fade. */
    public static void remove(LivingEntity entity, byte kind) {
        if (entity.level().isClientSide()) {
            return;
        }
        boolean had = has(entity);
        CompoundTag data = entity.getPersistentData();
        data.remove(TAG_UNTIL);
        data.remove(TAG_LAYERS);
        data.remove(TAG_DOME);
        data.remove(TAG_SYNCED_UNTIL);
        if (had) {
            send(entity, new SpellPackets.ProtegoShieldPacket(entity.getId(), -1, 0, false, false, kind));
        }
    }

    private static void sync(LivingEntity entity, byte kind) {
        CompoundTag data = entity.getPersistentData();
        data.putLong(TAG_SYNCED_UNTIL, data.getLong(TAG_UNTIL));
        send(entity, packet(entity, kind));
    }

    @Nullable
    private static SpellPackets.ProtegoShieldPacket packet(LivingEntity entity, byte kind) {
        int layers = layers(entity);
        if (layers < 0) {
            return null;
        }
        CompoundTag data = entity.getPersistentData();
        int remaining = (int) Math.min(Integer.MAX_VALUE, data.getLong(TAG_UNTIL) - entity.level().getGameTime());
        return new SpellPackets.ProtegoShieldPacket(entity.getId(), layers, remaining, data.getBoolean(TAG_DOME),
                ProtegoSpell.isRankedShield(entity), kind);
    }

    private static void send(LivingEntity entity, @Nullable SpellPackets.ProtegoShieldPacket packet) {
        if (packet != null) {
            SpellPackets.CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
        }
    }

    private static void sendTo(ServerPlayer player, LivingEntity holder) {
        SpellPackets.ProtegoShieldPacket packet = packet(holder, STATE);
        if (packet != null) {
            SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
        }
    }

    /** A player who starts watching someone already shielded has to be told, or they'd see nothing. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof LivingEntity target) {
            sendTo(watcher, target);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player, player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player, player);
        }
    }

    // ---- client view -------------------------------------------------------------------------------

    /**
     * One shield as a client knows it, in that client's game time. {@code event} is the last thing the
     * server reported ({@link #HIT}, {@link #SHATTERED}, ...) and {@code eventAt} when it arrived; a shattered
     * or dropped shield keeps its entry briefly (with {@code until == eventAt}) so the bubble can burst or fade.
     */
    public record View(int layers, boolean dome, boolean ranked, long raisedAt, long until, byte event, long eventAt) {
        public boolean up(long now) {
            return now < until;
        }
    }

    /** How long a taken-down shield's entry lingers for its last animation. */
    public static final int LINGER_TICKS = 18;
    /** How long a bubble takes to swell to full size. */
    public static final int GROW_TICKS = 6;

    private static final Map<Integer, View> VIEWS = new ConcurrentHashMap<>();

    /** The client's packet handler: {@code now} is the client level's game time. */
    public static void accept(int entityId, int layers, int remaining, boolean dome, boolean ranked, byte kind, long now) {
        View previous = view(entityId, now);
        boolean wasUp = previous != null && previous.up(now);
        if (layers < 0) {
            if (wasUp) {
                VIEWS.put(entityId, new View(previous.layers(), previous.dome(), previous.ranked(),
                        previous.raisedAt(), now, kind, now));
            }
            return;
        }
        // Someone already shielded when they came into view appears at full size rather than swelling.
        long raisedAt = kind == RAISED ? now : wasUp ? previous.raisedAt() : now - GROW_TICKS;
        byte event = kind;
        long eventAt = now;
        if (kind == STATE && wasUp) {
            event = previous.event();
            eventAt = previous.eventAt();
        }
        VIEWS.put(entityId, new View(layers, dome, ranked, raisedAt, now + remaining, event, eventAt));
    }

    /** The client's view of {@code entityId}'s shield, including a lingering burst, or null. */
    @Nullable
    public static View view(int entityId, long now) {
        View view = VIEWS.get(entityId);
        if (view != null && now > view.until() + LINGER_TICKS) {
            VIEWS.remove(entityId, view);
            return null;
        }
        return view;
    }

    public static Map<Integer, View> views() {
        return VIEWS;
    }

    /** Leaving a world must not carry stale shields into the next one. */
    public static void clearViews() {
        VIEWS.clear();
    }
}
