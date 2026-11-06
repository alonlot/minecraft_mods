package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VoldemortSummoningEntity extends Entity {
    private static final DustParticleOptions CURSED_BEAM = new DustParticleOptions(new Vector3f(0.15f, 0.95f, 0.2f), 1.25f);
    private static final int SUMMON_DELAY_TICKS = 160;

    private BlockPos wardA = BlockPos.ZERO;
    private BlockPos wardB = BlockPos.ZERO;
    private BlockPos wardC = BlockPos.ZERO;
    private boolean markSpawned;

    public VoldemortSummoningEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public VoldemortSummoningEntity(ServerLevel level, BlockPos wardA, BlockPos wardB, BlockPos wardC) {
        this(ModEntities.VOLDEMORT_SUMMONING.get(), level);
        this.wardA = wardA.immutable();
        this.wardB = wardB.immutable();
        this.wardC = wardC.immutable();
        Vec3 center = getTriangleCenter();
        this.setPos(center.x, center.y, center.z);
    }

    public static boolean tryStart(ServerLevel level, BlockPos hitWardPos, LivingEntity caster) {
        if (!level.getBlockState(hitWardPos).is(ExampleMod.WARD.get())) {
            return false;
        }

        AABB ritualArea = new AABB(hitWardPos).inflate(10.0D);
        if (!level.getEntitiesOfClass(VoldemortSummoningEntity.class, ritualArea).isEmpty()) {
            return true;
        }
        if (!level.getEntitiesOfClass(VoldemortEntity.class, ritualArea).isEmpty()) {
            return true;
        }

        List<BlockPos> wards = new ArrayList<>();
        BlockPos.betweenClosedStream(hitWardPos.offset(-4, 0, -4), hitWardPos.offset(4, 0, 4))
                .filter(pos -> pos.getY() == hitWardPos.getY() && level.getBlockState(pos).is(ExampleMod.WARD.get()))
                .forEach(pos -> wards.add(pos.immutable()));

        if (wards.size() < 3) {
            return false;
        }

        for (int i = 0; i < wards.size(); i++) {
            for (int j = i + 1; j < wards.size(); j++) {
                for (int k = j + 1; k < wards.size(); k++) {
                    List<BlockPos> trio = List.of(wards.get(i), wards.get(j), wards.get(k));
                    if (!trio.contains(hitWardPos)) {
                        continue;
                    }
                    if (!formsDiagonalTriangle(trio)) {
                        continue;
                    }

                    VoldemortSummoningEntity ritual = new VoldemortSummoningEntity(level, trio.get(0), trio.get(1), trio.get(2));
                    level.addFreshEntity(ritual);
                    level.playSound(null, hitWardPos, ModSounds.SPELL_MORSMORDRE.get(), SoundSource.HOSTILE, 1.5f, 0.55f);
                    level.sendParticles(ParticleTypes.SQUID_INK, hitWardPos.getX() + 0.5D, hitWardPos.getY() + 1.0D, hitWardPos.getZ() + 0.5D, 40, 0.35D, 0.6D, 0.35D, 0.06D);
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean formsDiagonalTriangle(List<BlockPos> trio) {
        List<Integer> distances = new ArrayList<>();
        distances.add(horizontalDistanceSq(trio.get(0), trio.get(1)));
        distances.add(horizontalDistanceSq(trio.get(0), trio.get(2)));
        distances.add(horizontalDistanceSq(trio.get(1), trio.get(2)));
        distances.sort(Comparator.naturalOrder());
        return distances.get(0) == 8 && distances.get(1) == 8 && distances.get(2) == 16;
    }

    private static int horizontalDistanceSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.wardA = BlockPos.of(tag.getLong("WardA"));
        this.wardB = BlockPos.of(tag.getLong("WardB"));
        this.wardC = BlockPos.of(tag.getLong("WardC"));
        this.markSpawned = tag.getBoolean("MarkSpawned");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("WardA", this.wardA.asLong());
        tag.putLong("WardB", this.wardB.asLong());
        tag.putLong("WardC", this.wardC.asLong());
        tag.putBoolean("MarkSpawned", this.markSpawned);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.setWeatherParameters(0, 20 * 40, true, true);
        spawnRitualEffects(serverLevel);

        if (!this.markSpawned && this.tickCount >= 110) {
            this.markSpawned = true;
            BlockPos center = BlockPos.containing(this.getX(), this.getY(), this.getZ());
            DarkMarkEntity mark = new DarkMarkEntity(serverLevel, center.getX() + 0.5D, center.getY() + 26.0D, center.getZ() + 0.5D, 20 * 180, 1.9f);
            serverLevel.addFreshEntity(mark);
            serverLevel.playSound(null, center, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.2f, 0.55f);
        }

        if (this.tickCount >= SUMMON_DELAY_TICKS) {
            summonVoldemort(serverLevel);
            this.discard();
        }
    }

    private void spawnRitualEffects(ServerLevel serverLevel) {
        Vec3 center = getTriangleCenter();
        emitRitual(serverLevel, center, this.tickCount, List.of(Vec3.atCenterOf(this.wardA),
                Vec3.atCenterOf(this.wardB), Vec3.atCenterOf(this.wardC)));

        if (this.tickCount % 10 == 0) {
            BlockPos strikePos = serverLevel.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(
                            center.x + (this.random.nextDouble() - 0.5D) * 12.0D,
                            center.y,
                            center.z + (this.random.nextDouble() - 0.5D) * 12.0D
                    )
            );
            var lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }
        }
    }

    /**
     * One tick of the ritual's particles and beam columns around {@code centre}, for the standard ward triangle
     * (legs of 2 and 2 blocks diagonally, the right angle on the {@code -x} side). On a server level this sends
     * the particles to every tracking client; on a client level (a Pensieve memory) it draws them locally. The
     * lightning strikes are not part of this: each side raises its own.
     */
    public static void emitRitualEffects(Level level, Vec3 centre, int age) {
        emitRitual(level, centre, age, List.of(
                centre.add(-4.0D / 3.0D, 0.0D, 0.0D), centre.add(2.0D / 3.0D, 0.0D, 2.0D), centre.add(2.0D / 3.0D, 0.0D, -2.0D)));
    }

    private static void emitRitual(Level level, Vec3 center, int age, List<Vec3> wardCentres) {
        double beamTopY = center.y + 22.0D;

        for (Vec3 ward : wardCentres) {
            ritualParticles(level, ParticleTypes.SQUID_INK, ward.x, ward.y + 0.55D, ward.z, 6, 0.15D, 0.15D, 0.15D, 0.02D);
            ritualParticles(level, ParticleTypes.SMOKE, ward.x, ward.y + 0.5D, ward.z, 8, 0.22D, 0.2D, 0.22D, 0.02D);
            spawnBeamColumn(level, age, ward.add(0.0D, 0.6D, 0.0D), beamTopY);
        }

        if (age % 6 == 0) {
            ritualParticles(level, ParticleTypes.WITCH, center.x, center.y + 1.0D, center.z, 10, 1.2D, 0.4D, 1.2D, 0.02D);
            ritualParticles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 0.6D, center.z, 14, 1.4D, 0.2D, 1.4D, 0.015D);
        }
    }

    private static void spawnBeamColumn(Level level, int age, Vec3 start, double topY) {
        int segments = 20;
        for (int i = 0; i <= segments; i++) {
            double progress = i / (double) segments;
            double y = Mth.lerp(progress, start.y, topY);
            double swirl = age * 0.12D + progress * Math.PI * 2.0D;
            double x = start.x + Math.cos(swirl) * 0.18D;
            double z = start.z + Math.sin(swirl) * 0.18D;
            ritualParticles(level, CURSED_BEAM, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 3 == 0) {
                ritualParticles(level, ParticleTypes.END_ROD, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }
    }

    /** {@code ServerLevel#sendParticles} on a server; the same gaussian scatter drawn locally on a client. */
    private static void ritualParticles(Level level, net.minecraft.core.particles.ParticleOptions particle, double x, double y,
                                        double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
        } else if (level.isClientSide) {
            for (int i = 0; i < count; i++) {
                level.addParticle(particle, x + level.random.nextGaussian() * dx, y + level.random.nextGaussian() * dy,
                        z + level.random.nextGaussian() * dz, level.random.nextGaussian() * speed,
                        level.random.nextGaussian() * speed, level.random.nextGaussian() * speed);
            }
        }
    }

    private void summonVoldemort(ServerLevel serverLevel) {
        BlockPos spawnPos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(this.getX(), this.getY(), this.getZ()));
        VoldemortEntity voldemort = ModEntities.VOLDEMORT.get().create(serverLevel);
        if (voldemort == null) {
            return;
        }

        voldemort.setPos(spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D);
        voldemort.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null);
        voldemort.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
        voldemort.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 24, 0));
        serverLevel.addFreshEntity(voldemort);
        voldemort.beginEntrance();

        var lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.moveTo(Vec3.atBottomCenterOf(spawnPos));
            lightning.setVisualOnly(true);
            serverLevel.addFreshEntity(lightning);
        }

        serverLevel.sendParticles(ParticleTypes.FLASH, voldemort.getX(), voldemort.getY() + 1.2D, voldemort.getZ(), 6, 0.3D, 0.4D, 0.3D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.SQUID_INK, voldemort.getX(), voldemort.getY() + 1.2D, voldemort.getZ(), 70, 0.45D, 0.9D, 0.45D, 0.06D);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, voldemort.getX(), voldemort.getY() + 1.0D, voldemort.getZ(), 90, 0.6D, 1.0D, 0.6D, 0.04D);
        serverLevel.playSound(null, spawnPos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.7f);
        summonDeathEaters(serverLevel, spawnPos, voldemort);
    }

    private void summonDeathEaters(ServerLevel serverLevel, BlockPos center, VoldemortEntity voldemort) {
        int summonCount = 4 + this.random.nextInt(3);
        for (int i = 0; i < summonCount; i++) {
            double rx = center.getX() + (this.random.nextDouble() - 0.5D) * 18.0D;
            double rz = center.getZ() + (this.random.nextDouble() - 0.5D) * 18.0D;
            BlockPos spawnPos = serverLevel.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(rx, center.getY(), rz)
            );
            WizardEntity wizard = ModEntities.WIZARD.get().create(serverLevel);
            if (wizard == null) {
                continue;
            }

            wizard.setPos(spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D);
            wizard.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null);
            wizard.setTarget(voldemort.getTarget());
            wizard.addTag(WizardEntity.VOLDEMORT_GUARD_TAG); // Dark Magic: his guards never turn neutral to a Lost player
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 30, 0));
            serverLevel.addFreshEntity(wizard);

            var lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(Vec3.atBottomCenterOf(spawnPos));
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }

            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, wizard.getX(), wizard.getY() + 1.0D, wizard.getZ(), 35, 0.45D, 0.9D, 0.45D, 0.04D);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, wizard.getX(), wizard.getY() + 1.1D, wizard.getZ(), 20, 0.3D, 0.8D, 0.3D, 0.03D);
        }
        serverLevel.playSound(null, center, ModSounds.SPELL_MORSMORDRE.get(), SoundSource.HOSTILE, 1.25f, 0.65f);
    }

    private Vec3 getTriangleCenter() {
        return new Vec3(
                (this.wardA.getX() + this.wardB.getX() + this.wardC.getX()) / 3.0D + 0.5D,
                Math.max(Math.max(this.wardA.getY(), this.wardB.getY()), this.wardC.getY()) + 0.5D,
                (this.wardA.getZ() + this.wardB.getZ() + this.wardC.getZ()) / 3.0D + 0.5D
        );
    }
}
