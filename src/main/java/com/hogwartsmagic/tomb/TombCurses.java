package com.hogwartsmagic.tomb;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ConcealedRockBlock;
import com.hogwartsmagic.block.CursedGlyphBlock;
import com.hogwartsmagic.block.entity.TombMarkerBlockEntity;
import com.hogwartsmagic.entity.InferiusEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.util.ModAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What a desert tomb's curses do, how Revelio shows them, and how all of it looks and sounds.
 *
 * <p>The design rule: every curse can be seen before it goes off, and a player who looks for it can
 * lift it. Revelio is the curse-breaker's tool, as everywhere else in the mod. A curse a player had
 * no way to see coming would be a coin toss, not a puzzle.
 *
 * <p>Each curse plays in three beats, so it reads as magic and not as a trap door:
 * <ol>
 *   <li><b>It wakes</b> ({@link FxKinds#CURSE_TRIGGER}): a circle of burning hieroglyphs opens on the
 *       floor in the curse's colour, sigils spiral up round the player, the view jolts.</li>
 *   <li><b>It gathers</b> (the wind-up, about a second and a half): the ceiling groans and dust
 *       trickles from it; or sand whirls up where the dead will rise; or curse-smoke coils in on the
 *       player. A quick player can still step out from under the ceiling.</li>
 *   <li><b>It lands</b> ({@link FxKinds#CURSE_LAND}): the sand drops in a burst of dust, the dead
 *       break out of the floor, or the Eye of Horus burns over the player's head.</li>
 * </ol>
 *
 * <p>Server only. The looks are in {@code client.fx.CurseFx}; this sends the moments and the
 * particles everyone sees.
 */
public final class TombCurses {

    /**
     * How far Revelio reaches into a tomb. Short on purpose: one cast shows one room, so a tomb is
     * cleared as you go and not from the front door. (The Locket cave reveals 48 blocks, because
     * its hidden way through is a single passage.)
     */
    public static final int REVEAL_RADIUS = 8;
    /** One minute shown, the same as the Locket cave. */
    public static final int REVEAL_TICKS = 1200;
    /** How fast Revelio's wave runs across the floor, blocks a tick: it crosses a room in about half a second. */
    public static final float WAVE_SPEED = 0.7F;
    /** Ticks from a curse waking to it landing. */
    public static final int COLLAPSE_WINDUP = 30;
    public static final int RISEN_WINDUP = 30;
    public static final int WITHERING_WINDUP = 22;
    /** How long the Withering's Wither lasts, and so its aura and the darkened view. */
    public static final int WITHERING_TICKS = 200;

    private TombCurses() {
    }

    /** Creative and spectator players walk the tomb without setting anything off. */
    public static boolean canTrigger(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }

    // ------------------------------------------------------------------------------------------------
    // Revealing
    // ------------------------------------------------------------------------------------------------

    /**
     * Revelio's reveal: a wave of red light runs out across the floor from {@code center}, and each
     * hidden tomb block within {@link #REVEAL_RADIUS} shows itself as the wave reaches it (cursed
     * glyphs burn in their curse's colour, concealed sandstone opens). Each closes again on its own
     * scheduled tick.
     *
     * @return how many blocks the wave will reveal
     */
    public static int reveal(ServerLevel level, Vec3 center) {
        Fx.send(level, FxKinds.CURSE_WAVE, center, 48.0, -1, REVEAL_RADIUS, WAVE_SPEED);
        return reveal(level, center, REVEAL_RADIUS, REVEAL_TICKS, WAVE_SPEED);
    }

    /**
     * As {@link #reveal(ServerLevel, Vec3)}, with its own reach and time and no wave: the Sphinx's
     * gift shows the whole tomb at once.
     */
    public static int reveal(ServerLevel level, Vec3 center, int radius, int ticks) {
        return reveal(level, center, radius, ticks, 0.0F);
    }

    private static int reveal(ServerLevel level, Vec3 center, int radius, int ticks, float waveSpeed) {
        List<BlockPos> found = hiddenWithin(level, center, radius);
        for (BlockPos p : found) {
            int delay = waveSpeed > 0.0F ? Mth.ceil(Math.sqrt(p.distToCenterSqr(center)) / waveSpeed) : 0;
            if (delay == 0) {
                show(level, p, ticks);
            } else {
                CurseScheduler.after(delay, () -> show(level, p, ticks));
            }
        }
        if (!found.isEmpty()) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.BLOCKS, 1.2F, 0.6F);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.BLOCKS, 0.7F, 0.6F);
        }
        return found.size();
    }

    /** Every hidden, not yet shown, tomb block within {@code radius} of {@code center}. */
    private static List<BlockPos> hiddenWithin(ServerLevel level, Vec3 center, int radius) {
        double r2 = (double) radius * radius;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(center.x - radius);
             cx <= SectionPos.blockToSectionCoord(center.x + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.z - radius);
                 cz <= SectionPos.blockToSectionCoord(center.z + radius); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(be instanceof TombMarkerBlockEntity)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    BlockState s = level.getBlockState(p);
                    if (p.distToCenterSqr(center) <= r2 && s.hasProperty(ConcealedRockBlock.REVEALED)
                            && !s.getValue(ConcealedRockBlock.REVEALED)) {
                        found.add(p.immutable());
                    }
                }
            }
        }
        return found;
    }

    /** One block shows itself: the state changes and its sigil rises out of it. */
    private static void show(ServerLevel level, BlockPos p, int ticks) {
        BlockState s = level.getBlockState(p);
        if (!s.hasProperty(ConcealedRockBlock.REVEALED) || s.getValue(ConcealedRockBlock.REVEALED)) {
            return;     // already shown, or broken while the wave was on its way
        }
        level.setBlock(p, s.setValue(ConcealedRockBlock.REVEALED, true), Block.UPDATE_ALL);
        level.scheduleTick(p, s.getBlock(), ticks);
        int curse = s.hasProperty(CursedGlyphBlock.CURSE) ? s.getValue(CursedGlyphBlock.CURSE).ordinal() : -1;
        Fx.send(level, FxKinds.CURSE_REVEAL, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 48.0, -1, curse);
        level.playSound(null, p, SoundEvents.AMETHYST_CLUSTER_STEP, SoundSource.BLOCKS, 0.9F,
                curse < 0 ? 0.7F : 0.9F + 0.1F * curse);
    }

    // ------------------------------------------------------------------------------------------------
    // A curse goes off
    // ------------------------------------------------------------------------------------------------

    /**
     * Sets a curse off on {@code player}. Called with the glyph still in place; a glyph that was
     * stepped on is turned back to plain sandstone here, so the same curse cannot go off twice. (A
     * glyph that was mined is about to break anyway.) The curse lands after its wind-up.
     */
    public static void trigger(ServerLevel level, BlockPos glyph, TombCurse curse, ServerPlayer player) {
        if (level.getBlockState(glyph).is(ExampleMod.CURSED_GLYPH.get())) {
            level.setBlock(glyph, ExampleMod.HIEROGLYPH_SANDSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        Vec3 tile = new Vec3(glyph.getX() + 0.5, glyph.getY() + 1.0, glyph.getZ() + 0.5);
        int windup = switch (curse) {
            case COLLAPSE -> COLLAPSE_WINDUP;
            case RISEN -> RISEN_WINDUP;
            case WITHERING -> WITHERING_WINDUP;
        };
        Fx.send(level, FxKinds.CURSE_TRIGGER, tile, 48.0, player.getId(), curse.ordinal(), windup);
        Fx.shake(level, tile, 0.7F, 10.0F, 10);
        level.playSound(null, glyph, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.9F, 0.7F);
        level.playSound(null, glyph, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.2F, 0.5F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.tomb_curse." + curse.getSerializedName()), true);
        switch (curse) {
            case COLLAPSE -> windupCollapse(level, player.blockPosition());
            case RISEN -> windupRisen(level, player);
            case WITHERING -> windupWithering(level, player);
        }
    }

    // --- Collapse ---

    /**
     * The ceiling groans and dust trickles down over the spot the player was standing on; then that
     * spot's ceiling comes down. Where it falls is fixed when the curse wakes, which is what makes the
     * wind-up a warning and not decoration: step out from under the dust and the sand misses you.
     */
    private static void windupCollapse(ServerLevel level, BlockPos spot) {
        level.playSound(null, spot, SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.4F, 0.55F);
        level.playSound(null, spot, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6F, 0.4F);
        CurseScheduler.every(0, COLLAPSE_WINDUP, t -> {
            // The rumble builds.
            if (t % 6 == 0) {
                Fx.shake(level, Vec3.atCenterOf(spot), 0.25F + 0.04F * t, 8.0F, 8);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.random.nextFloat() > 0.25F + t / (float) COLLAPSE_WINDUP * 0.5F) {
                        continue;
                    }
                    BlockPos column = spot.offset(dx, 0, dz);
                    int ceiling = ceilingAbove(level, column);
                    if (ceiling > 0) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                                column.getX() + level.random.nextDouble(), column.getY() + ceiling - 0.05,
                                column.getZ() + level.random.nextDouble(), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            return true;
        });
        CurseScheduler.after(COLLAPSE_WINDUP, () -> {
            int dropped = collapse(level, spot);
            Vec3 at = Vec3.atBottomCenterOf(spot);
            Fx.send(level, FxKinds.CURSE_LAND, at, 48.0, -1, TombCurse.COLLAPSE.ordinal(), 0);
            if (dropped > 0) {
                Fx.send(level, FxKinds.SLAM, at, 48.0, -1, 3.0F, 0.9F);
                Fx.shake(level, at, 1.3F, 10.0F, 14);
            }
        });
    }

    /** The ceiling's height over a column, from its floor, or -1 under open sky or a low roof. */
    private static int ceilingAbove(ServerLevel level, BlockPos column) {
        for (int dy = 2; dy <= 7; dy++) {
            if (!level.getBlockState(column.above(dy)).isAir()) {
                return dy >= 3 ? dy : -1;
            }
        }
        return -1;
    }

    /** Collapse on the player's own spot, at once: the old entry point, kept for the game tests. */
    public static int collapse(ServerLevel level, ServerPlayer player) {
        return collapse(level, player.blockPosition());
    }

    /**
     * Two layers of sand over the three-by-three round {@code spot}, from just under the ceiling. It
     * is enough to bury a standing player to the head, and sand digs out by hand in a few seconds:
     * the danger is staying put, not the sand.
     */
    public static int collapse(ServerLevel level, BlockPos spot) {
        int dropped = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = spot.offset(dx, 0, dz);
                int ceiling = ceilingAbove(level, column);
                if (ceiling < 0) {
                    continue;
                }
                for (int layer = 1; layer <= 2; layer++) {
                    BlockPos at = column.above(ceiling - layer);
                    if (level.getBlockState(at).isAir()) {
                        FallingBlockEntity.fall(level, at, Blocks.SAND.defaultBlockState());
                        dropped++;
                    }
                }
            }
        }
        level.playSound(null, spot, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, spot, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
        level.playSound(null, spot, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.5F, 0.45F);
        return dropped;
    }

    // --- Risen ---

    /**
     * Sand whirls up in a column of green soul fire at each spot the dead will rise from, then they
     * burst out of the floor. The spots are chosen when the curse wakes, so the whirlwinds show
     * exactly where to look.
     */
    private static void windupRisen(ServerLevel level, ServerPlayer player) {
        List<BlockPos> spots = standingSpots(level, player.blockPosition());
        int green = TombCurse.RISEN.rgb();
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.8F, 1.4F);
        CurseScheduler.every(0, RISEN_WINDUP, t -> {
            float grow = (t + 1) / (float) RISEN_WINDUP;
            for (int i = 0; i < spots.size(); i++) {
                BlockPos s = spots.get(i);
                for (int k = 0; k < 3; k++) {
                    double a = (t * 0.55 + k * 2.094 + i) % (Math.PI * 2.0);
                    double h = level.random.nextDouble() * (0.6 + 1.6 * grow);
                    double r = 0.25 + 0.45 * (1.0 - h / 2.4);
                    level.sendParticles(new SpellVfxOptions(k == 0 ? SpellVfxOptions.Kind.SMOKE : SpellVfxOptions.Kind.MOTE,
                                    k == 0 ? 0xD8C08A : 0xF0D488, k == 0 ? 0.35F : 0.1F, 18),
                            s.getX() + 0.5 + Math.cos(a) * r, s.getY() + h, s.getZ() + 0.5 + Math.sin(a) * r,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
                if (t % 2 == 0) {
                    level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.FLAME, green, 0.22F, 16),
                            s.getX() + 0.5, s.getY() + 0.1, s.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.02);
                }
            }
            return true;
        });
        CurseScheduler.after(RISEN_WINDUP, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            List<Mob> risen = risen(level, player, spots);
            for (Mob mob : risen) {
                Fx.send(level, FxKinds.SLAM, mob.position(), 48.0, -1, 2.0F, 0.6F);
                Fx.send(level, FxKinds.CURSE_LAND, mob.position(), 48.0, mob.getId(), TombCurse.RISEN.ordinal(), 0);
            }
            Fx.shake(level, player.position(), 0.8F, 10.0F, 10);
        });
    }

    /** Two husks and an Inferius climb out of the floor round the player, at once: for the game tests. */
    public static List<Mob> risen(ServerLevel level, ServerPlayer player) {
        return risen(level, player, standingSpots(level, player.blockPosition()));
    }

    private static List<Mob> risen(ServerLevel level, ServerPlayer player, List<BlockPos> spots) {
        List<Mob> risen = new ArrayList<>();
        EntityType<?>[] kinds = {EntityType.HUSK, EntityType.HUSK, ModEntities.INFERIUS.get()};
        for (int i = 0; i < kinds.length && i < spots.size(); i++) {
            BlockPos at = spots.get(i);
            if (!(kinds[i].create(level) instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            if (mob instanceof InferiusEntity) {
                mob.setPersistenceRequired();
            }
            mob.setTarget(player);
            level.addFreshEntity(mob);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                    at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 40, 0.4, 0.2, 0.4, 0.1);
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.FLAME, TombCurse.RISEN.rgb(), 0.3F, 22),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 14, 0.3, 0.5, 0.3, 0.04);
            risen.add(mob);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.6F, 0.5F);
        return risen;
    }

    /** Floor cells two to four blocks from {@code centre} with room for a mob to stand, spread round it. */
    private static List<BlockPos> standingSpots(ServerLevel level, BlockPos centre) {
        List<BlockPos> spots = new ArrayList<>();
        for (int r = 2; r <= 4 && spots.size() < 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                                && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                            spots.add(at);
                            break;
                        }
                    }
                }
            }
        }
        // Spread them round the player rather than three in a row on one side.
        List<BlockPos> picked = new ArrayList<>();
        for (int i = 0; i < spots.size() && picked.size() < 3; i += Math.max(1, spots.size() / 3)) {
            picked.add(spots.get(i));
        }
        return picked;
    }

    // --- Withering ---

    /**
     * Three arms of violet curse-smoke coil in on the player from the edge of the rune circle; when
     * they meet, the Eye of Horus burns over the player's head and the curse takes hold. While it
     * lasts, dark motes rise off the player for everyone to see.
     */
    private static void windupWithering(ServerLevel level, ServerPlayer player) {
        int violet = TombCurse.WITHERING.rgb();
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.5F, 0.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.5F, 0.5F);
        CurseScheduler.every(0, WITHERING_WINDUP, t -> {
            if (!player.isAlive() || player.level() != level) {
                return false;
            }
            float left = 1.0F - t / (float) WITHERING_WINDUP;
            for (int arm = 0; arm < 3; arm++) {
                for (int k = 0; k < 2; k++) {
                    float along = left + k * 0.05F;
                    double a = arm * 2.094 + along * 4.5;
                    double r = 0.4 + 2.6 * along;
                    double y = player.getY() + 0.9 + Math.sin(along * 6.0 + arm) * 0.5;
                    level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SMOKE, 0x2A1238, 0.4F, 14),
                            player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                    level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, violet, 0.16F, 10),
                            player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            return true;
        });
        CurseScheduler.after(WITHERING_WINDUP, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            withering(player);
            Fx.send(level, FxKinds.CURSE_LAND, player.position(), 48.0, player.getId(),
                    TombCurse.WITHERING.ordinal(), WITHERING_TICKS);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 0.6F, 0.5F);
            CurseScheduler.every(1, WITHERING_TICKS, t -> {
                if (!player.isAlive() || player.level() != level || !player.hasEffect(MobEffects.WITHER)) {
                    return false;
                }
                if (t % 4 == 0) {
                    level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SMOKE, 0x1E0E28, 0.3F, 22),
                            player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.01);
                    level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, violet, 0.08F, 24),
                            player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.35, 0.5, 0.35, 0.01);
                }
                return true;
            });
        });
    }

    /** Wither to hurt, Mining Fatigue so the way out is slow to dig, Hunger so it lasts. */
    public static void withering(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHERING_TICKS, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 1));
    }

    // ------------------------------------------------------------------------------------------------
    // Lifting
    // ------------------------------------------------------------------------------------------------

    /** A revealed curse is lifted: the sigil is torn out of the tile and breaks into gold. */
    public static void disarm(ServerLevel level, BlockPos glyph, TombCurse curse, ServerPlayer player) {
        Vec3 tile = new Vec3(glyph.getX() + 0.5, glyph.getY() + 1.0, glyph.getZ() + 0.5);
        Fx.send(level, FxKinds.CURSE_LIFT, tile, 48.0, player.getId(), curse.ordinal());
        level.playSound(null, glyph, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, glyph, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 1.6F);
        level.playSound(null, glyph, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        ExperienceOrb.award(level, Vec3.atCenterOf(glyph), 6);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.tomb_curse.lifted"), true);
        ModAdvancements.award(player, "curse_breaker");
    }
}
