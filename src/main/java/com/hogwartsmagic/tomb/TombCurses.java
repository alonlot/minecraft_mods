package com.hogwartsmagic.tomb;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ConcealedRockBlock;
import com.hogwartsmagic.block.entity.TombMarkerBlockEntity;
import com.hogwartsmagic.entity.InferiusEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.util.ModAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * What a desert tomb's curses do, and how Revelio shows them.
 *
 * <p>The design rule: every curse can be seen before it goes off, and a player who looks for it
 * can lift it. Revelio is the curse-breaker's tool, as it is everywhere else in the mod. A curse a
 * player had no way to see coming would be a coin toss, not a puzzle.
 *
 * <p>Server only.
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

    private static final DustParticleOptions CURSE_RED = new DustParticleOptions(new Vector3f(0.85F, 0.12F, 0.08F), 1.6F);

    private TombCurses() {
    }

    /** Creative and spectator players walk the tomb without setting anything off. */
    public static boolean canTrigger(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /**
     * Shows every hidden tomb block within {@link #REVEAL_RADIUS} of {@code center}: cursed glyphs
     * glow red, concealed sandstone opens. Each closes again on its own scheduled tick.
     *
     * @return how many blocks were revealed
     */
    public static int reveal(ServerLevel level, Vec3 center) {
        return reveal(level, center, REVEAL_RADIUS, REVEAL_TICKS);
    }

    /** As {@link #reveal(ServerLevel, Vec3)}, with its own reach and time: the Sphinx's gift is wider. */
    public static int reveal(ServerLevel level, Vec3 center, int radius, int ticks) {
        double r2 = (double) radius * radius;
        int count = 0;
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
                    if (p.distToCenterSqr(center) > r2) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (!s.hasProperty(ConcealedRockBlock.REVEALED) || s.getValue(ConcealedRockBlock.REVEALED)) {
                        continue;
                    }
                    level.setBlock(p, s.setValue(ConcealedRockBlock.REVEALED, true), Block.UPDATE_ALL);
                    level.scheduleTick(p, s.getBlock(), ticks);
                    count++;
                }
            }
        }
        if (count > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.BLOCKS, 1.2F, 0.6F);
        }
        return count;
    }

    /**
     * Sets a curse off on {@code player}. Called with the glyph still in place; a glyph that was
     * stepped on is turned back to plain sandstone here, so the same curse cannot go off twice. (A
     * glyph that was mined is about to break anyway.)
     */
    public static void trigger(ServerLevel level, BlockPos glyph, TombCurse curse, ServerPlayer player) {
        if (level.getBlockState(glyph).is(ExampleMod.CURSED_GLYPH.get())) {
            level.setBlock(glyph, ExampleMod.HIEROGLYPH_SANDSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, glyph, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8F, 0.7F);
        level.sendParticles(CURSE_RED, glyph.getX() + 0.5, glyph.getY() + 1.1, glyph.getZ() + 0.5,
                30, 0.6, 0.3, 0.6, 0.02);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.tomb_curse." + curse.getSerializedName()), true);
        switch (curse) {
            case COLLAPSE -> collapse(level, player);
            case RISEN -> risen(level, player);
            case WITHERING -> withering(player);
        }
    }

    /** A Revealed curse is lifted: a reward, so looking first is worth the mana. */
    public static void disarm(ServerLevel level, BlockPos glyph, ServerPlayer player) {
        level.playSound(null, glyph, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.ENCHANT, glyph.getX() + 0.5, glyph.getY() + 0.8, glyph.getZ() + 0.5,
                40, 0.4, 0.4, 0.4, 0.6);
        ExperienceOrb.award(level, Vec3.atCenterOf(glyph), 6);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.tomb_curse.lifted"), true);
        ModAdvancements.award(player, "curse_breaker");
    }

    /**
     * Two layers of sand over the three-by-three round the player, from just under the ceiling. It
     * is enough to bury a standing player to the head, and sand digs out by hand in a few seconds:
     * the danger is staying put, not the sand.
     */
    public static int collapse(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        int dropped = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos column = feet.offset(dx, 0, dz);
                int ceiling = -1;
                for (int dy = 2; dy <= 7; dy++) {
                    if (!level.getBlockState(column.above(dy)).isAir()) {
                        ceiling = dy;
                        break;
                    }
                }
                if (ceiling < 3) {
                    continue;   // open sky, or no room for the sand to fall from
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
        level.playSound(null, feet, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, feet, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
        return dropped;
    }

    /** Two husks and an Inferius climb out of the floor round the player, already hunting them. */
    public static List<Mob> risen(ServerLevel level, ServerPlayer player) {
        List<Mob> risen = new ArrayList<>();
        List<BlockPos> spots = standingSpots(level, player.blockPosition());
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
            risen.add(mob);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        return risen;
    }

    /** Floor cells two to four blocks from {@code centre} with room for a mob to stand, nearest first. */
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

    /** Wither to hurt, Mining Fatigue so the way out is slow to dig, Hunger so it lasts. */
    public static void withering(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 1));
    }
}
