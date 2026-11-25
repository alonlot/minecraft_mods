package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * The little chocolate frog that jumps out of a box. It hops every ten ticks for five seconds, then is gone with a
 * puff. Like the one on the train it makes for a window: when it spawns it looks for glass (a block or a pane) within
 * eight blocks and hops toward it, vanishing in a puff when it gets there. With no glass in reach, or glass it cannot
 * get to (it gives up after a second of no progress), it hops away from the nearest player instead. Hit it or
 * right-click it before then to catch and eat it: food 3 and the chocolate cure. It is never saved, so an unloaded
 * chunk just loses it.
 *
 * <p>A plain {@link Entity}: no health, no attributes, no AI. Both sides run the same gravity and friction;
 * only the server decides to hop, and a hop reaches the client as a motion packet. The model squashes and stretches
 * from {@link #hopStartTick()}, which the client notes when the synced hop counter changes.
 */
public class ChocolateFrogEntity extends Entity {
    /** Five seconds of life. */
    public static final int LIFE_TICKS = 100;
    public static final int HOP_INTERVAL = 10;
    private static final double HOP_SPEED = 0.24;
    private static final double HOP_LIFT = 0.36;
    /** How far from the spawn point the frog looks for glass, in blocks. */
    public static final int WINDOW_SCAN = 8;
    /** The frog is "out of the window" once this close to the glass (horizontally). */
    public static final double WINDOW_REACH = 1.2;
    /** It gives up on a window that has not got closer for this many ticks. */
    public static final int GIVE_UP_TICKS = 60;
    /** Entity event: the frog went out of the window (the client puffs). 61 is safe: 21, 35 and 63 crash the client. */
    public static final byte EVENT_WINDOW_PUFF = 61;

    private static final EntityDataAccessor<Integer> DATA_HOPS =
            SynchedEntityData.defineId(ChocolateFrogEntity.class, EntityDataSerializers.INT);

    /** Client: the entity tick on which the latest hop began, or -1000 before the first one. */
    private int hopStartTick = -1000;
    private boolean caught;
    /** Server: the glass it is making for, or null. */
    @Nullable
    private BlockPos windowTarget;
    private double bestWindowDistance = Double.MAX_VALUE;
    private int windowStalled;
    /**
     * Ticks lived. Counted here, not read from {@code tickCount}: the level bumps {@code tickCount} before it
     * calls {@code tick()}, so a frog ticked by hand (as the tests do) would never age.
     */
    private int life;

    public ChocolateFrogEntity(EntityType<? extends ChocolateFrogEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HOPS, 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && DATA_HOPS.equals(key)) {
            this.hopStartTick = this.tickCount;
        }
    }

    /** Client: the tick on which the latest hop began (for the squash and stretch). */
    public int hopStartTick() {
        return this.hopStartTick;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Vec3 v = this.getDeltaMovement();
        if (!this.isNoGravity()) {
            v = v.add(0.0, -0.08, 0.0);
        }
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, this.getDeltaMovement());
        double friction = this.onGround() ? 0.6 : 0.98;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 0.98, friction));

        if (this.level() instanceof ServerLevel server) {
            if (this.life >= LIFE_TICKS) {
                puff(server);
                this.discard();
                return;
            }
            if (this.life == 1) {
                this.windowTarget = findGlass(server);
            }
            if (this.windowTarget != null && updateWindow(server)) {
                return;
            }
            if (this.life % HOP_INTERVAL == 2 && this.onGround()) {
                hop(server);
            }
        }
    }

    /** One hop: away from the nearest player, with a little sideways wobble. */
    private void hop(ServerLevel server) {
        Player near = server.getNearestPlayer(this, 16.0);
        double dx;
        double dz;
        double wobbleSpan = 0.9;
        if (this.windowTarget != null) {
            dx = this.windowTarget.getX() + 0.5 - this.getX();
            dz = this.windowTarget.getZ() + 0.5 - this.getZ();
            wobbleSpan = 0.2;
        } else if (near != null) {
            dx = this.getX() - near.getX();
            dz = this.getZ() - near.getZ();
        } else {
            dx = this.random.nextDouble() - 0.5;
            dz = this.random.nextDouble() - 0.5;
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            dx = Math.cos(a);
            dz = Math.sin(a);
            len = 1.0;
        }
        double wobble = (this.random.nextDouble() - 0.5) * wobbleSpan;
        double cos = Math.cos(wobble);
        double sin = Math.sin(wobble);
        double hx = (dx * cos - dz * sin) / len;
        double hz = (dx * sin + dz * cos) / len;
        this.setDeltaMovement(hx * HOP_SPEED, HOP_LIFT, hz * HOP_SPEED);
        this.hasImpulse = true;
        this.setYRot((float) (Mth.atan2(-hx, hz) * (180.0 / Math.PI)));
        this.yRotO = this.getYRot();
        this.entityData.set(DATA_HOPS, this.entityData.get(DATA_HOPS) + 1);
        if (this.entityData.get(DATA_HOPS) % 3 == 1) {
            server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FROG_AMBIENT,
                    SoundSource.NEUTRAL, 0.35F, 1.6F + this.random.nextFloat() * 0.3F);
        }
    }

    /** The nearest glass block or pane within {@link #WINDOW_SCAN} blocks sideways and from its own level up 4 (never a glass floor under it), or null. */
    @Nullable
    private BlockPos findGlass(ServerLevel server) {
        BlockPos origin = this.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-WINDOW_SCAN, 0, -WINDOW_SCAN),
                origin.offset(WINDOW_SCAN, 4, WINDOW_SCAN))) {
            if (!server.hasChunkAt(p)) {
                continue;
            }
            BlockState state = server.getBlockState(p);
            if (state.is(Tags.Blocks.GLASS) || state.is(Tags.Blocks.GLASS_PANES)) {
                double sq = p.distToCenterSqr(this.getX(), this.getY(), this.getZ());
                if (sq < bestSq) {
                    bestSq = sq;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    /**
     * Each tick while it has a window: gone with a puff when it is there, forgets the window when it has not got closer
     * for {@link #GIVE_UP_TICKS} (glass behind a wall) or the glass was broken. Returns true when the frog is gone.
     */
    private boolean updateWindow(ServerLevel server) {
        BlockPos target = this.windowTarget;
        BlockState state = server.getBlockState(target);
        if (!state.is(Tags.Blocks.GLASS) && !state.is(Tags.Blocks.GLASS_PANES)) {
            this.windowTarget = null;
            return false;
        }
        double dx = target.getX() + 0.5 - this.getX();
        double dz = target.getZ() + 0.5 - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double dy = Math.abs(target.getY() + 0.5 - this.getY());
        // never before it has lived a hop interval and made one hop: a frog opened beside a window still leaps out
        boolean hopped = this.life > HOP_INTERVAL && this.entityData.get(DATA_HOPS) > 0;
        if (hopped && flat <= WINDOW_REACH && dy <= 2.5) {
            server.broadcastEntityEvent(this, EVENT_WINDOW_PUFF);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CARD_PUFF.get(),
                    SoundSource.NEUTRAL, 0.6F, 1.3F);
            this.discard();
            return true;
        }
        if (flat < this.bestWindowDistance - 0.05) {
            this.bestWindowDistance = flat;
            this.windowStalled = 0;
        } else if (++this.windowStalled >= GIVE_UP_TICKS) {
            this.windowTarget = null; // cannot get there: hop away like any frog
        }
        return false;
    }

    /** The glass the frog is making for, or null (for tests). */
    @Nullable
    public BlockPos windowTarget() {
        return this.windowTarget;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_WINDOW_PUFF) {
            // client: a puff of chocolate-brown smoke where the frog was
            for (int i = 0; i < 10; i++) {
                this.level().addParticle(ParticleTypes.POOF, this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
                        this.getY() + 0.1 + this.random.nextDouble() * 0.2, this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                        (this.random.nextDouble() - 0.5) * 0.06, 0.03, (this.random.nextDouble() - 0.5) * 0.06);
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    private void puff(ServerLevel server) {
        server.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.12, 0.08, 0.12, 0.02);
        server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    /** The player eats it: food 3, the chocolate cure, and it is gone. Server side. */
    public void catchBy(Player player) {
        if (this.caught || this.isRemoved() || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        this.caught = true;
        player.getFoodData().eat(3, 0.4F);
        FoodRules.applyChocolateCure(player);
        server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
                0.8F, 1.0F);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.15, this.getZ(),
                4, 0.1, 0.1, 0.1, 0.0);
        this.discard();
    }

    /** A punch catches it, the same as a right-click. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player) {
            if (!this.level().isClientSide) {
                catchBy(player);
            }
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        catchBy(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
