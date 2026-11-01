package com.hogwartsmagic.spell.support.ceiling;

import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caelum Fascino, "bewitch the sky": a player makes their own ceiling into the Great Hall's enchanted sky by casting at
 * two opposite corners of it. The first cast marks a corner (a glow on that block, shown to the caster only), the
 * second makes the ceiling over the rectangle between the two blocks, its sky plane at the lower face of the lower
 * corner block. The blocks stay: the ceiling is only drawn, in front of the real one's underside, by the client from
 * the {@link AmbienceRegistry} entry this spell writes. A sneak-cast at a bewitched ceiling removes it, for its
 * maker or an op. Players only, never through a puppet or a reflected beam (the beam entity checks the latter).
 * Server only.
 *
 * <p>The pure part, {@link #refusal}, takes no level so a unit test can run it: the side, count, overlap and dimension
 * limits.
 */
public final class CaelumFascinoSpell {
    /** What a Caelum Fascino beam did to the block it struck. */
    public enum Result {
        /** The first corner was marked (pays no XP). */
        MARKED,
        /** The second corner made a ceiling (pays XP). */
        CREATED,
        /** A sneak-cast took away a ceiling (no XP). */
        REMOVED,
        /** A limit or a permission said no; the caster was told why. */
        REFUSED,
        /** Nothing happened (not a player, a puppet, or a sneak-cast with no ceiling there). */
        NOTHING
    }

    /** One pending first corner: the block, the dimension it is in and the game tick it was marked on. */
    public record Mark(ResourceKey<Level> dim, BlockPos pos, long tick) {
    }

    /** The longest side of a ceiling, in blocks. */
    public static final int MAX_SIDE = 64;
    /** The most two corner blocks may differ in height, in blocks; a ceiling is a flat plane, not a stairwell. */
    public static final int MAX_RISE = 16;
    /** How many ceilings one player may have bewitched at once, counted over every dimension. */
    public static final int MAX_PER_PLAYER = 8;
    /** A first corner is forgotten after this many ticks (60 seconds). */
    public static final int MARK_TTL_TICKS = 1200;
    /** How often the pending mark glows for its caster, in ticks. */
    public static final int GLOW_INTERVAL_TICKS = 10;
    /** A sneak-cast finds a ceiling whose roof block it hit within this many blocks of the plane (see ceilingAt). */
    public static final double REMOVE_BAND = 2.0;
    /**
     * A second ceiling above or below another over the same floor area is allowed from this many blocks of vertical
     * clearance, so the storeys of a tower can each have their own; closer than that counts as overlap.
     */
    public static final int STOREY_GAP = 4;

    private static final String KEY = "message.hogwartsmagic.caelum_fascino.";
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    private CaelumFascinoSpell() {
    }

    /**
     * The lang key of the reason a ceiling between these two corner blocks may not be made, or null when it may.
     * Checked in this order: both corners in one dimension, no side over {@link #MAX_SIDE} and no more than
     * {@link #MAX_RISE} blocks between the corners' heights (both are {@code too_big}), the caster under
     * {@link #MAX_PER_PLAYER} ceilings, and no overlap with an existing ceiling (shared floor area at a plane less
     * than {@link #STOREY_GAP} blocks from its span).
     *
     * @param ownedByCaster how many ceilings the caster already has
     */
    public static @Nullable String refusal(BlockPos a, ResourceKey<Level> dimA, BlockPos b, ResourceKey<Level> dimB,
                                           List<AmbientEffect> existing, int ownedByCaster) {
        if (!dimA.equals(dimB)) {
            return KEY + "other_dimension";
        }
        if (side(a.getX(), b.getX()) > MAX_SIDE || side(a.getZ(), b.getZ()) > MAX_SIDE
                || Math.abs(a.getY() - b.getY()) > MAX_RISE) {
            return KEY + "too_big";
        }
        if (ownedByCaster >= MAX_PER_PLAYER) {
            return KEY + "too_many";
        }
        AmbientEffect made = make(UUID.randomUUID(), a, b, null, 0L);
        for (AmbientEffect e : existing) {
            if (e.kind() == AmbientEffect.KIND_CEILING && made.overlapsXZ(e)
                    && made.y() > e.y() - STOREY_GAP && made.y() < e.y() + Math.max(0f, e.hy()) + STOREY_GAP) {
                return KEY + "overlap";
            }
        }
        return null;
    }

    /** The ceiling between two corner blocks: x and z from the lower to one past the higher, plane at the lower face. */
    public static AmbientEffect make(UUID id, BlockPos a, BlockPos b, @Nullable UUID owner, long createdTick) {
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;
        return new AmbientEffect(id, AmbientEffect.KIND_CEILING, (minX + maxX) / 2.0, Math.min(a.getY(), b.getY()),
                (minZ + maxZ) / 2.0, (maxX - minX) / 2f, 0f, (maxZ - minZ) / 2f, 0f, 0, owner, createdTick);
    }

    private static int side(int a, int b) {
        return Math.abs(a - b) + 1;
    }

    /** The Caelum Fascino beam struck pos (a loaded block). */
    public static Result onBlockHit(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (MindControl.hitPuppeteer() != null || !(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return Result.NOTHING;
        }
        AmbienceRegistry registry = AmbienceRegistry.get(level);
        if (player.isShiftKeyDown()) {
            return removeAt(level, registry, player, pos);
        }
        long now = level.getGameTime();
        Mark mark = MARKS.get(player.getUUID());
        if (mark != null && now - mark.tick() >= MARK_TTL_TICKS) {
            // Stale (the sweep runs once a tick, so this is only a cast on the very tick it lapses): this cast starts over.
            MARKS.remove(player.getUUID());
            tell(player, "expired", ChatFormatting.GRAY);
            mark = null;
        }
        if (mark == null) {
            MARKS.put(player.getUUID(), new Mark(level.dimension(), pos.immutable(), now));
            glow(level, player, pos);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
            tell(player, "marked", ChatFormatting.AQUA);
            return Result.MARKED;
        }
        MARKS.remove(player.getUUID());
        String refusal = refusal(mark.pos(), mark.dim(), pos, level.dimension(), registry.all(),
                ownedAnywhere(level.getServer(), player.getUUID()));
        if (refusal != null) {
            tell(player, refusal.substring(KEY.length()), ChatFormatting.GOLD);
            return Result.REFUSED;
        }
        AmbientEffect ceiling = make(UUID.randomUUID(), mark.pos(), pos, player.getUUID(), now);
        registry.add(level, ceiling);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7f, 1.5f);
        // A flourish along the new sky's four corners, so the caster sees how far it reaches.
        double y = ceiling.y() - 0.1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                level.sendParticles(ParticleTypes.END_ROD, ceiling.x() + sx * ceiling.hx(), y, ceiling.z() + sz * ceiling.hz(),
                        8, 0.3, 0.2, 0.3, 0.02);
            }
        }
        tell(player, "created", ChatFormatting.AQUA);
        return Result.CREATED;
    }

    /** How many ceilings this player has bewitched in every dimension: the limit is per player, not per level. */
    public static int ownedAnywhere(MinecraftServer server, UUID player) {
        int count = 0;
        for (ServerLevel each : server.getAllLevels()) {
            count += AmbienceRegistry.get(each).ceilingsOwnedBy(player).size();
        }
        return count;
    }

    /** A sneak-cast: take away the caster's own ceiling (or any, for an op) whose roof block this is. */
    private static Result removeAt(ServerLevel level, AmbienceRegistry registry, ServerPlayer player, BlockPos pos) {
        AmbientEffect ceiling = registry.ceilingAt(Vec3.atCenterOf(pos), REMOVE_BAND);
        if (ceiling == null) {
            tell(player, "no_ceiling", ChatFormatting.GOLD);
            return Result.NOTHING;
        }
        if (!player.getUUID().equals(ceiling.owner()) && !player.hasPermissions(2)) {
            tell(player, "not_yours", ChatFormatting.GOLD);
            return Result.REFUSED;
        }
        registry.remove(level, ceiling.id());
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.ENCHANT, ceiling.x(), ceiling.y() - 0.2, ceiling.z(), 30,
                Math.min(ceiling.hx(), 6.0), 0.2, Math.min(ceiling.hz(), 6.0), 0.05);
        tell(player, "removed", ChatFormatting.GRAY);
        return Result.REMOVED;
    }

    /** The pending first corner of this player, or null. */
    public static @Nullable Mark markOf(UUID player) {
        return MARKS.get(player);
    }

    /** Forgets this player's pending corner (a logout). */
    public static void clearMark(UUID player) {
        MARKS.remove(player);
    }

    /** Forgets every pending corner (the server stopped). */
    public static void clearAllMarks() {
        MARKS.clear();
    }

    /**
     * Once a tick: drops the corners marked at least {@link #MARK_TTL_TICKS} before {@code now} (telling their casters)
     * and makes the live ones glow every {@link #GLOW_INTERVAL_TICKS}. A corner whose caster is gone is dropped too.
     */
    public static void tickMarks(MinecraftServer server, long now) {
        if (MARKS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Mark> entry = it.next();
            Mark mark = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
            } else if (now - mark.tick() >= MARK_TTL_TICKS) {
                it.remove();
                tell(player, "expired", ChatFormatting.GRAY);
            } else if ((now - mark.tick()) % GLOW_INTERVAL_TICKS == 0) {
                ServerLevel level = server.getLevel(mark.dim());
                if (level != null) {
                    glow(level, player, mark.pos());
                }
            }
        }
    }

    /** The marked block's lower face glows, for the caster only. */
    private static void glow(ServerLevel level, ServerPlayer player, BlockPos pos) {
        level.sendParticles(player, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() - 0.05, pos.getZ() + 0.5,
                5, 0.35, 0.0, 0.35, 0.0);
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
        player.sendSystemMessage(Component.translatable(KEY + key).withStyle(colour));
    }
}
