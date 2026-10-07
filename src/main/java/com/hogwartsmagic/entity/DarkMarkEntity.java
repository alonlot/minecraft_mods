package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DarkMarkEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(DarkMarkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_VISUAL_SCALE = SynchedEntityData.defineId(DarkMarkEntity.class, EntityDataSerializers.FLOAT);
    private static final int STORM_DURATION_TICKS = 20 * 90;
    private static final int STORM_REFRESH_INTERVAL_TICKS = 20;
    private static final double FULL_SCALE_TICKS = 80.0D;
    private static final double MAX_SCALE = 5.0D;

    public DarkMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public DarkMarkEntity(Level level, double x, double y, double z, int duration) {
        this(level, x, y, z, duration, 1.0f);
    }

    public DarkMarkEntity(Level level, double x, double y, double z, int duration, float visualScale) {
        this(ModEntities.DARK_MARK.get(), level);
        this.setPos(x, y, z);
        this.entityData.set(DATA_DURATION, duration);
        this.entityData.set(DATA_VISUAL_SCALE, Math.max(0.5f, visualScale));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DURATION, 1200);
        builder.define(DATA_VISUAL_SCALE, 1.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_DURATION, tag.getInt("Duration"));
        if (tag.contains("VisualScale")) {
            this.entityData.set(DATA_VISUAL_SCALE, tag.getFloat("VisualScale"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Duration", this.entityData.get(DATA_DURATION));
        tag.putFloat("VisualScale", this.entityData.get(DATA_VISUAL_SCALE));
    }

    public float getVisualScale() {
        return this.entityData.get(DATA_VISUAL_SCALE);
    }

    @Override
    public void tick() {
        super.tick();
        int duration = this.entityData.get(DATA_DURATION);
        if (duration <= 0) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }
        this.entityData.set(DATA_DURATION, duration - 1);

        if (this.level().isClientSide) {
            spawnParticles();
        } else {
            if (this.level() instanceof ServerLevel serverLevel && (this.tickCount == 1 || this.tickCount % STORM_REFRESH_INTERVAL_TICKS == 0)) {
                startStorm(serverLevel);
            }
            // Build up drama: only start effects after 40 ticks
            if (this.tickCount > 40) {
                if (this.tickCount % 60 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    spawnStormLightning(serverLevel);
                }
                if (this.tickCount % 100 == 0) {
                    applyBuffs();
                }
                if (this.tickCount % 200 == 0) {
                    summonMonsters();
                }
            }
        }
    }

    private void startStorm(ServerLevel serverLevel) {
        int remainingDuration = Math.max(this.entityData.get(DATA_DURATION), STORM_DURATION_TICKS);
        serverLevel.setWeatherParameters(0, remainingDuration, true, true);
    }

    private void spawnStormLightning(ServerLevel serverLevel) {
        net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning == null) {
            return;
        }

        double offsetX = (this.random.nextDouble() - 0.5D) * 30.0D;
        double offsetZ = (this.random.nextDouble() - 0.5D) * 30.0D;
        net.minecraft.core.BlockPos strikePos = serverLevel.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.core.BlockPos.containing(this.getX() + offsetX, 0.0D, this.getZ() + offsetZ)
        );
        lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
        lightning.setVisualOnly(true);
        serverLevel.addFreshEntity(lightning);
    }

    // ---- visuals ---------------------------------------------------------------------
    // The mark is built as a flat skull-and-snake silhouette in a vertical plane that
    // faces the nearest player. Outline particles are SOUL_FIRE_FLAME (greenish, emits
    // light); shadow accents are SQUID_INK. Eye sockets, nasal cavity and the mouth are
    // *carved out* — left empty so the dark sky reads through them, which is what gives
    // the film mark its skull-like silhouette instead of a sparkly green blob.

    private static final double SKULL_HALF_WIDTH = 3.6D;   // horizontal extent at scale=1
    private static final double SKULL_HALF_HEIGHT = 4.5D;  // vertical extent at scale=1
    private static final double SKULL_Y_OFFSET = 2.0D;     // lift skull above entity Y

    private void spawnParticles() {
        double growth = Math.min(1.0D, this.tickCount / FULL_SCALE_TICKS);
        double fade = Math.min(1.0D, this.entityData.get(DATA_DURATION) / 80.0D);
        double scale = MAX_SCALE * growth * Math.max(0.45D, fade);

        // Orient the silhouette toward whichever player is closest, so it always reads.
        net.minecraft.world.entity.player.Player nearest = this.level().getNearestPlayer(this, 256.0D);
        double fx = 0.0D, fz = 1.0D;
        if (nearest != null) {
            double dx = nearest.getX() - this.getX();
            double dz = nearest.getZ() - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.01D) { fx = dx / len; fz = dz / len; }
        }
        // "Right" axis of the skull plane — perpendicular to face in XZ.
        double rx = -fz, rz = fx;

        spawnAmbientCloud(scale);
        spawnSkullSilhouette(scale, rx, rz);
        spawnSnake(scale, rx, rz);
    }

    private void spawnAmbientCloud(double scale) {
        // Dark smoky halo well outside the skull silhouette.
        for (int i = 0; i < 6; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = (5.0D + this.random.nextDouble() * 3.0D) * scale;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + (this.random.nextDouble() * 9.0D - 2.0D) * scale;
            double z = this.getZ() + Math.sin(angle) * radius;
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0D, 0.004D, 0.0D);
        }
    }

    private void spawnSkullSilhouette(double scale, double rx, double rz) {
        // Sample random points in the [-1, 1]^2 skull-local box. Accept only those
        // inside the skull mask and outside the feature carve-outs. Edge points get
        // the bright SOUL_FIRE_FLAME glow; interior gets sparse smoky fill.
        int samples = 220;
        for (int i = 0; i < samples; i++) {
            double u = this.random.nextDouble() * 2.0D - 1.0D;
            double v = this.random.nextDouble() * 2.0D - 1.0D;
            if (!isInsideSkull(u, v)) continue;
            if (isCarveOut(u, v)) {
                // Sparse dark accent inside carve-outs to deepen the shadow.
                if (this.random.nextFloat() < 0.08f) {
                    spawnInPlane(u, v, scale, rx, rz, ParticleTypes.SQUID_INK);
                }
                continue;
            }
            if (isNearEdge(u, v)) {
                spawnInPlane(u, v, scale, rx, rz, ParticleTypes.SOUL_FIRE_FLAME);
            } else if (this.random.nextFloat() < 0.35f) {
                spawnInPlane(u, v, scale, rx, rz, ParticleTypes.CAMPFIRE_COSY_SMOKE);
            }
        }
        // Teeth: thin vertical bars bridging the mouth opening, top and bottom rows.
        for (int i = -3; i <= 3; i++) {
            double u = i * 0.075D;
            for (double dv = 0.0D; dv < 0.04D; dv += 0.014D) {
                spawnInPlane(u, -0.205D - dv, scale, rx, rz, ParticleTypes.SOUL_FIRE_FLAME);
                spawnInPlane(u, -0.295D + dv, scale, rx, rz, ParticleTypes.SOUL_FIRE_FLAME);
            }
        }
    }

    private boolean isInsideSkull(double u, double v) {
        // Cranium: ellipse centered (0, 0.35), radii (0.85, 0.6).
        double cu = u / 0.85D;
        double cv = (v - 0.35D) / 0.6D;
        if (cu * cu + cv * cv <= 1.0D) return true;
        // Jaw: trapezoid tapering from width 1.1 at y=-0.05 to width 0.6 at y=-0.55.
        if (v >= -0.55D && v <= -0.05D) {
            double t = (v + 0.05D) / -0.5D;
            double halfWidth = 0.55D - t * 0.25D;
            if (Math.abs(u) <= halfWidth) return true;
        }
        return false;
    }

    private boolean isCarveOut(double u, double v) {
        // Eye sockets — two large dark ellipses high on the face.
        double ex = u + 0.30D, ey = v - 0.45D;
        if (ex * ex / 0.04D + ey * ey / 0.025D <= 1.0D) return true;
        ex = u - 0.30D;
        if (ex * ex / 0.04D + ey * ey / 0.025D <= 1.0D) return true;
        // Nasal cavity — small inverted triangle below the brow.
        if (v >= 0.05D && v <= 0.25D && Math.abs(u) <= 0.04D + (v - 0.05D) * 0.25D) return true;
        // Mouth opening — wide low ellipse.
        double mu = u / 0.30D;
        double mv = (v + 0.25D) / 0.07D;
        return mu * mu + mv * mv <= 1.0D;
    }

    private boolean isNearEdge(double u, double v) {
        // 4-neighbour silhouette test: if any neighbour is outside the skull or
        // inside a carve-out, we're on a visible edge.
        double s = 0.04D;
        return !isInsideSkull(u + s, v) || isCarveOut(u + s, v)
            || !isInsideSkull(u - s, v) || isCarveOut(u - s, v)
            || !isInsideSkull(u, v + s) || isCarveOut(u, v + s)
            || !isInsideSkull(u, v - s) || isCarveOut(u, v - s);
    }

    private void spawnInPlane(double u, double v, double scale, double rx, double rz,
                              net.minecraft.core.particles.ParticleOptions particle) {
        double x = this.getX() + rx * u * SKULL_HALF_WIDTH * scale;
        double y = this.getY() + v * SKULL_HALF_HEIGHT * scale + SKULL_Y_OFFSET * scale;
        double z = this.getZ() + rz * u * SKULL_HALF_WIDTH * scale;
        this.level().addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private void spawnSnake(double scale, double rx, double rz) {
        // S-curve emerging from the mouth (u=0, v=-0.25), descending below the skull
        // with a sideways sway. Body tapers from thicker near the head to a thin tail.
        int segments = 56;
        for (int i = 0; i < segments; i++) {
            double t = i / (double) segments;
            double v = -0.30D - t * 2.2D;
            double u = Math.sin(t * Math.PI * 1.8D) * 0.55D * (0.35D + t * 0.65D);
            double bodyHalf = (1.0D - t) * 0.09D + 0.035D;
            for (double w = -bodyHalf; w <= bodyHalf; w += 0.035D) {
                spawnInPlane(u + w, v, scale, rx, rz, ParticleTypes.SOUL_FIRE_FLAME);
            }
            if (i % 5 == 0) {
                spawnInPlane(u, v - 0.045D, scale, rx, rz, ParticleTypes.SQUID_INK);
            }
        }
        // Snake head — a small bulbous cluster at the tail-end of the curve.
        double headU = Math.sin(Math.PI * 1.8D) * 0.55D * 1.0D;
        double headV = -0.30D - 2.2D - 0.06D;
        for (int i = 0; i < 14; i++) {
            double a = (Math.PI * 2.0D * i) / 14.0D;
            spawnInPlane(headU + Math.cos(a) * 0.10D, headV + Math.sin(a) * 0.07D,
                    scale, rx, rz, ParticleTypes.SOUL_FIRE_FLAME);
        }
        // Two slit eyes on the snake head.
        spawnInPlane(headU - 0.035D, headV + 0.012D, scale, rx, rz, ParticleTypes.WARPED_SPORE);
        spawnInPlane(headU + 0.035D, headV + 0.012D, scale, rx, rz, ParticleTypes.WARPED_SPORE);
    }

    private void applyBuffs() {
        AABB area = new AABB(this.getX() - 50, this.getY() - 100, this.getZ() - 50, this.getX() + 50, this.getY() + 20, this.getZ() + 50);
        List<WizardEntity> wizards = this.level().getEntitiesOfClass(WizardEntity.class, area);
        for (WizardEntity wizard : wizards) {
            giveMarkBuffs(wizard);
        }
        // Dark Magic: the Mark answers a Corrupted or Lost wizard as it answers a Death Eater.
        List<net.minecraft.world.entity.player.Player> darkPlayers = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, area,
                player -> !player.isSpectator() && com.hogwartsmagic.spell.dark.DarkRewards.darkMarkBuffs(player));
        for (net.minecraft.world.entity.player.Player player : darkPlayers) {
            giveMarkBuffs(player);
        }
    }

    /** Strength II and Regeneration I for 10 seconds, refreshed every 5. */
    private static void giveMarkBuffs(LivingEntity entity) {
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1));
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
    }

    private void summonMonsters() {
        if (this.level() instanceof ServerLevel serverLevel) {
            AABB area = new AABB(this.getX() - 30, this.getY() - 100, this.getZ() - 30, this.getX() + 30, this.getY() + 20, this.getZ() + 30);
            long monsterCount = serverLevel.getEntitiesOfClass(Mob.class, area, m -> m.getType().getCategory() == MobCategory.MONSTER).size();
            
            if (monsterCount < 15) {
                double rx = this.getX() + (this.random.nextDouble() - 0.5) * 40.0;
                double rz = this.getZ() + (this.random.nextDouble() - 0.5) * 40.0;
                double ry = this.getY() - 50; // Try to spawn near ground level below the mark
                
                // Find ground
                net.minecraft.core.BlockPos spawnPos = new net.minecraft.core.BlockPos((int)rx, (int)ry, (int)rz);
                while (spawnPos.getY() > 0 && serverLevel.isEmptyBlock(spawnPos)) {
                    spawnPos = spawnPos.below();
                }
                spawnPos = spawnPos.above();

                EntityType<?>[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
                EntityType<?> type = types[this.random.nextInt(types.length)];
                Entity monster = type.create(serverLevel);
                if (monster != null) {
                    monster.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                    serverLevel.addFreshEntity(monster);
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, monster.getX(), monster.getY(), monster.getZ(), 20, 0.5, 1.0, 0.5, 0.05);
                }
            }
        }
    }
}
