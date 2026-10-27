package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What the mixins and events call. Each hook is a few field writes after an O(1) check that the position is inside
 * a chunk a player is near; they sit on hot paths ({@code setBlockState}, {@code playSeededSound}) so they do nothing
 * else. A failure here is logged once and never reaches the game.
 */
public final class CaptureHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failed;

    private CaptureHooks() {
    }

    private static void fail(Throwable t) {
        if (!failed) {
            failed = true;
            LOGGER.error("Pensieve capture hook failed; further failures are not logged", t);
        }
    }

    // ---- world ----

    public static void onBlockChanged(Level level, BlockPos pos, BlockState old, BlockState now) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        try {
            LevelLog log = CaptureLogs.of(sl);
            if (log == null || !log.isWatched(pos.getX(), pos.getZ())) {
                return;
            }
            log.addBlockChange(sl.getGameTime(), pos.asLong(), Block.getId(old), Block.getId(now));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onSound(ServerLevel level, double x, double y, double z, Holder<SoundEvent> sound,
                               float volume, float pitch) {
        try {
            LevelLog log = CaptureLogs.of(level);
            if (log == null || !log.isWatched(x, z)) {
                return;
            }
            log.addSound(new LevelLog.SoundRec(level.getGameTime(), sound.value().getLocation(), (float) x, (float) y,
                    (float) z, volume, pitch));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onParticles(ServerLevel level, ParticleOptions options, double x, double y, double z, int count,
                                   double dx, double dy, double dz, double speed) {
        try {
            LevelLog log = CaptureLogs.of(level);
            if (log == null || !log.isWatched(x, z)) {
                return;
            }
            log.addParticle(new LevelLog.ParticleRec(level.getGameTime(), options, x, y, z, count, dx, dy, dz, speed));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onLevelEvent(ServerLevel level, int type, BlockPos pos, int data) {
        try {
            LevelLog log = CaptureLogs.of(level);
            if (log == null || !log.isWatched(pos.getX(), pos.getZ())) {
                return;
            }
            log.addLevelEvent(new LevelLog.LevelEvtRec(level.getGameTime(), type, pos.asLong(), data));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** An explosion is kept as one emitter particle and one explode sound. */
    public static void onExplosion(ServerLevel level, double x, double y, double z, float radius) {
        try {
            LevelLog log = CaptureLogs.of(level);
            if (log == null || !log.isWatched(x, z)) {
                return;
            }
            long tick = level.getGameTime();
            log.addParticle(new LevelLog.ParticleRec(tick, ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0));
            log.addSound(new LevelLog.SoundRec(tick, SoundEvents.GENERIC_EXPLODE.value().getLocation(), (float) x,
                    (float) y, (float) z, 4.0f, 1.0f));
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- entities ----

    public static void onSwing(LivingEntity entity, InteractionHand hand) {
        if (!(entity.level() instanceof ServerLevel sl)) {
            return;
        }
        try {
            LevelLog log = CaptureLogs.of(sl);
            if (log == null) {
                return;
            }
            EntityHistory h = log.entities.get(entity.getUUID());
            if (h != null) {
                h.act(sl.getGameTime(), hand == InteractionHand.MAIN_HAND ? Recording.ACT_SWING_MAIN
                        : Recording.ACT_SWING_OFF);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onHurt(LivingEntity entity) {
        act(entity, Recording.ACT_HURT, false);
    }

    public static void onDeath(LivingEntity entity) {
        act(entity, Recording.ACT_DIE, true);
    }

    private static void act(LivingEntity entity, int kind, boolean killed) {
        if (!(entity.level() instanceof ServerLevel sl)) {
            return;
        }
        try {
            LevelLog log = CaptureLogs.of(sl);
            if (log == null) {
                return;
            }
            EntityHistory h = log.entities.get(entity.getUUID());
            if (h != null) {
                h.act(sl.getGameTime(), kind);
                if (killed) {
                    h.killed = true;
                }
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- spells ----

    /** A cast of a spell that has no beam: it is replayed as a cast action with a target 16 blocks along the look vector. */
    public static void onCast(ServerPlayer player, String spellId) {
        if (!SceneNames.SPELL_VISUALS.contains(spellId) || SceneNames.SPELL_BEAMS.contains(spellId)) {
            return;
        }
        try {
            ServerLevel sl = player.serverLevel();
            LevelLog log = CaptureLogs.of(sl);
            if (log == null) {
                return;
            }
            Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(16.0));
            log.addCast(new LevelLog.CastRec(sl.getGameTime(), player.getUUID(), spellId, (float) target.x,
                    (float) target.y, (float) target.z));
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onBeamOpened(ServerLevel level, SpellBeamEntity beam) {
        try {
            LevelLog log = CaptureLogs.of(level);
            if (log == null || !log.isWatched(beam.getX(), beam.getZ())) {
                return;
            }
            net.minecraft.world.entity.Entity owner = beam.getOwner();
            log.openBeam(beam.getId(), level.getGameTime(), beam.getSpellType().getId(),
                    owner == null ? null : owner.getUUID(), beam.getX(), beam.getY(), beam.getZ());
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onBeamClosed(ServerLevel level, SpellBeamEntity beam) {
        try {
            LevelLog log = CaptureLogs.existing(level);
            if (log != null) {
                log.closeBeam(beam.getId(), level.getGameTime(), beam.getX(), beam.getY(), beam.getZ());
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- chat ----

    /** Whispers (/msg, /tell) and team messages, both directions: never recorded. */
    public static boolean isPrivateChatType(Holder<net.minecraft.network.chat.ChatType> type) {
        return type.is(net.minecraft.network.chat.ChatType.MSG_COMMAND_INCOMING)
                || type.is(net.minecraft.network.chat.ChatType.MSG_COMMAND_OUTGOING)
                || type.is(net.minecraft.network.chat.ChatType.TEAM_MSG_COMMAND_INCOMING)
                || type.is(net.minecraft.network.chat.ChatType.TEAM_MSG_COMMAND_OUTGOING);
    }

    public static void onChat(ServerPlayer receiver, @Nullable String sender, String text) {
        try {
            // CaptureLogs.CHAT is a plain map: a mod sending a message from another thread must not touch it.
            if (!receiver.server.isSameThread()) {
                return;
            }
            if (!com.hogwartsmagic.Config.pensieveCaptureEnabled || PensieveDimension.isMemory(receiver.level())) {
                return;
            }
            CaptureLogs.chat(receiver.getUUID()).add(receiver.level().getGameTime(), sender, text);
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void onChatComponent(ServerPlayer receiver, @Nullable Component sender, Component content) {
        onChat(receiver, sender == null ? null : sender.getString(), content.getString());
    }

    // ---- discontinuities ----

    /** The player jumped, changed dimension, respawned or logged in: a memory must start after now. */
    public static void markDiscontinuity(ServerPlayer player) {
        try {
            if (!player.server.isSameThread()) {
                return;
            }
            CaptureLogs.chat(player.getUUID()).lastDiscontinuity = player.level().getGameTime();
        } catch (Throwable t) {
            fail(t);
        }
    }
}
