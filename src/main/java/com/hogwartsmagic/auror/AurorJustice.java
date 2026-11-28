package com.hogwartsmagic.auror;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.AurorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.dark.DarkCosts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The law side of the Aurors, all server-side: who is wanted, what an Auror who sees a dark cast does, the hunting
 * squads sent after Corrupted and Lost players, and Apparition in and out.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class AurorJustice {
    private static final String KEY_WITNESSED = "chat.hogwartsmagic.auror.witnessed";
    private static final String KEY_SQUAD = "chat.hogwartsmagic.auror.squad";

    private AurorJustice() {
    }

    // ---- wanted -----------------------------------------------------------------------------------------------

    /** True when Aurors attack this player on sight. Creative and spectator players never are. */
    public static boolean isHunted(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        long wantedUntil = player.getPersistentData().getLong(AurorRules.WANTED_UNTIL_KEY);
        return AurorRules.hunts(DarkCosts.tierOf(player), player.level().getGameTime(), wantedUntil);
    }

    public static void markWanted(ServerPlayer player) {
        long until = player.level().getGameTime() + AurorRules.WANTED_TICKS;
        if (player.getPersistentData().getLong(AurorRules.WANTED_UNTIL_KEY) < until) {
            player.getPersistentData().putLong(AurorRules.WANTED_UNTIL_KEY, until);
        }
    }

    /**
     * From DarkMagicData.onDarkCastPaid: every Auror within sight of the caster turns on them, and the caster is
     * wanted for a while, so the rest of the post joins in too.
     */
    public static void onDarkCast(ServerPlayer caster, SpellType spell) {
        if (caster.isCreative() || caster.isSpectator()) {
            return;
        }
        List<AurorEntity> witnesses = caster.serverLevel().getEntitiesOfClass(AurorEntity.class,
                caster.getBoundingBox().inflate(AurorRules.WITNESS_RADIUS),
                a -> a.isAlive() && a.hasLineOfSight(caster));
        if (witnesses.isEmpty()) {
            return;
        }
        boolean wasWanted = caster.level().getGameTime() < caster.getPersistentData().getLong(AurorRules.WANTED_UNTIL_KEY);
        markWanted(caster);
        for (AurorEntity auror : witnesses) {
            auror.setTarget(caster);
        }
        if (!wasWanted) {
            caster.sendSystemMessage(Component.translatable(KEY_WITNESSED).withStyle(ChatFormatting.RED));
        }
    }

    /** Aurors near a breakout come running, and the liberator (if any) is wanted. */
    public static void raiseAlarm(ServerLevel level, BlockPos where, LivingEntity escapee, @Nullable ServerPlayer liberator) {
        if (liberator != null && !liberator.isCreative() && !liberator.isSpectator()) {
            markWanted(liberator);
        }
        for (AurorEntity auror : level.getEntitiesOfClass(AurorEntity.class,
                new net.minecraft.world.phys.AABB(where).inflate(AurorRules.ALARM_RADIUS))) {
            if (auror.getTarget() == null || !auror.getTarget().isAlive()) {
                auror.setTarget(escapee);
            }
        }
    }

    // ---- hunting squads ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || (player.tickCount + player.getId()) % AurorRules.SQUAD_CHECK_TICKS != 0
                || player.isCreative() || player.isSpectator() || !player.isAlive()
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        int tier = DarkCosts.tierOf(player);
        float chance = AurorRules.squadChance(tier);
        long now = player.level().getGameTime();
        if (chance <= 0.0f
                || !AurorRules.squadOffCooldown(now, player.getPersistentData().getLong(AurorRules.LAST_SQUAD_KEY))
                || player.getRandom().nextFloat() >= chance) {
            return;
        }
        if (sendSquad(player.serverLevel(), player) > 0) {
            player.getPersistentData().putLong(AurorRules.LAST_SQUAD_KEY, now);
        }
    }

    /** Apparates a squad around the player. Returns how many came. */
    public static int sendSquad(ServerLevel level, ServerPlayer target) {
        int size = AurorRules.squadSize(target.getRandom().nextInt(AurorRules.SQUAD_MAX_SIZE - AurorRules.SQUAD_MIN_SIZE + 1));
        int came = 0;
        for (int i = 0; i < size; i++) {
            BlockPos pos = landingNear(level, target.blockPosition(), AurorRules.SQUAD_SPAWN_MIN_DISTANCE,
                    AurorRules.SQUAD_SPAWN_MAX_DISTANCE);
            if (pos == null) {
                continue;
            }
            AurorEntity auror = ModEntities.AUROR.get().create(level);
            if (auror == null) {
                continue;
            }
            // They come in as the films have them: streaks of silver-white smoke out of the sky, landing around the
            // player. The spawn point is a standable spot well out, lifted into the air for the run in.
            auror.moveTo(pos.getX() + 0.5D, pos.getY() + AurorRules.SQUAD_ARRIVE_HEIGHT, pos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            auror.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            auror.setSquad(true);
            auror.setTarget(target);
            if (level.addFreshEntity(auror)) {
                auror.startLightFlight(target.position(), true);
                came++;
            }
        }
        if (came > 0) {
            target.sendSystemMessage(Component.translatable(KEY_SQUAD).withStyle(ChatFormatting.RED));
        }
        return came;
    }

    // ---- Apparition -------------------------------------------------------------------------------------------

    public static void disapparate(AurorEntity auror) {
        if (auror.level() instanceof ServerLevel level) {
            crack(level, auror);
        }
        auror.discard();
    }

    private static void crack(ServerLevel level, LivingEntity who) {
        level.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.CLOUD), who.getX(), who.getY() + 1.0D,
                who.getZ(), 16, 0.3D, 0.6D, 0.3D, 0.03D);
        level.playSound(null, who.getX(), who.getY(), who.getZ(), ModSounds.APPARATE_CRACK.get(), SoundSource.NEUTRAL,
                1.0F, 1.0F);
    }

    /**
     * A standable surface spot min..max blocks from the centre, at most 6 blocks above or below it, or null after a
     * few tries. Only columns in loaded chunks are looked at.
     */
    @Nullable
    public static BlockPos landingNear(ServerLevel level, BlockPos centre, double min, double max) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = min + level.random.nextDouble() * (max - min);
            BlockPos column = BlockPos.containing(centre.getX() + Math.cos(angle) * distance, centre.getY(),
                    centre.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos spot = standableNear(level, column, centre.getY());
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Scans from 6 above to 6 below the reference height for sturdy, lava-free ground with two free blocks over it. */
    @Nullable
    private static BlockPos standableNear(ServerLevel level, BlockPos column, int refY) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        int top = Math.min(refY + 6, surface);
        for (int y = top; y >= refY - 6; y--) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            BlockState below = level.getBlockState(feet.below());
            if (below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                    && below.getFluidState().isEmpty()
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()) {
                return feet;
            }
        }
        return null;
    }
}
