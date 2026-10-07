package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.dark.DarkMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds what a Sneakoscope detects and plays its whistle. Server only. Each probe pairs a SneakoscopeRules.Source
 * with an entity class; the area check is getEntitiesOfClass, which reads loaded entity sections and never loads a
 * chunk. PROBES holds four probes; the dark-player probe takes ServerPlayer, filtered by the dark-player test, and is
 * skipped when SneakoscopeRules.playersDetectable is false.
 */
public final class SneakoscopeSensor {
    private record Probe(SneakoscopeRules.Source source, Class<? extends Entity> type, Predicate<Entity> accepts) {
    }

    private static final Predicate<Entity> ANY = e -> true;

    /**
     * VoldemortEntity extends WizardEntity, so the Death Eater probe finds him too. Part B: Corrupted and Lost players,
     * whatever they look like (the Cloak and Polyjuice do not hide intent); never creative or spectator players.
     */
    private static final List<Probe> PROBES = List.of(
            new Probe(SneakoscopeRules.Source.DEATH_EATER, WizardEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DEMENTOR, DementorEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DARK_MARK, DarkMarkEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DARK_PLAYER, ServerPlayer.class, SneakoscopeSensor::isDarkPlayer));

    private SneakoscopeSensor() {
    }

    /** Signal strength 0..15 at centre. The holder (null for a placed Sneakoscope) is never detected. */
    public static int strength(ServerLevel level, Vec3 centre, @Nullable Entity holder) {
        int range = Config.getSneakoscopeRange();
        int darkMarkRange = Config.getSneakoscopeDarkMarkRange();
        List<SneakoscopeRules.Hit> hits = new ArrayList<>();
        boolean playersOn = SneakoscopeRules.playersDetectable(Config.isDarkMagicEnabled(),
                Config.isSneakoscopeDetectsPlayers());
        for (Probe probe : PROBES) {
            if (probe.source() == SneakoscopeRules.Source.DARK_PLAYER && !playersOn) {
                continue;
            }
            int probeRange = SneakoscopeRules.range(probe.source(), range, darkMarkRange);
            collect(level, probe.type(), probe.source(), probe.accepts(), centre, probeRange, holder, hits);
        }
        return SneakoscopeRules.strength(hits, range, darkMarkRange);
    }

    private static <T extends Entity> void collect(ServerLevel level, Class<T> type, SneakoscopeRules.Source source,
                                                   Predicate<Entity> accepts, Vec3 centre, int range,
                                                   @Nullable Entity holder, List<SneakoscopeRules.Hit> hits) {
        if (range <= 0) {
            return;
        }
        double size = range * 2.0;
        AABB box = AABB.ofSize(centre, size, size, size);
        for (T entity : level.getEntitiesOfClass(type, box, e -> e != holder && e.isAlive() && accepts.test(e))) {
            hits.add(new SneakoscopeRules.Hit(source, Math.sqrt(entity.distanceToSqr(centre))));
        }
    }

    /** Part B: a Corrupted or Lost player in survival or adventure (the toggles are checked before the scan). */
    private static boolean isDarkPlayer(Entity entity) {
        return entity instanceof ServerPlayer player
                && SneakoscopeRules.detectsPlayer(Config.isDarkMagicEnabled(), Config.isSneakoscopeDetectsPlayers(),
                        player.isCreative() || player.isSpectator(), DarkMagicData.getTier(player));
    }

    /** The carried whistle: only this player hears it. */
    public static void whistleTo(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.6f, 2.0f);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
    }

    /** The placed whistle: everyone within 16 blocks hears it. */
    public static void whistleAt(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.4;
        double z = pos.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.BLOCKS, 1.0f, 2.0f);
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.8f);
    }
}
