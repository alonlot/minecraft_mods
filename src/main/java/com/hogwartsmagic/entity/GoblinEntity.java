package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * A Gringotts teller. Neutral, sells and buys against Galleons, and will defend
 * itself - loudly - if a customer turns out to be a thief.
 *
 * <p>Extends {@link AbstractVillager} purely for the merchant plumbing (trade
 * offers and the trading screen); none of the villager profession, gossip or
 * breeding behaviour applies.
 */
public class GoblinEntity extends AbstractVillager implements GoblinAnimated {

    /** Radius in which a struck goblin summons the vault guards. */
    public static final double GUARD_ALERT_RADIUS = 24.0D;

    /** Entity event byte broadcast to clients to kick off the swing animation. */
    private static final byte EVENT_ATTACK = 61;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState busyAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();

    public GoblinEntity(EntityType<? extends AbstractVillager> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.4D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // Neutral: no target goal of its own, it only ever retaliates.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ---------------------------------------------------------------- trading

    @Override
    protected void updateTrades() {
        MerchantOffers offers = this.getOffers();
        offers.clear();

        // The bank's spread: it pays two Galleons for an ingot and charges three
        // to buy one back.
        offers.add(new MerchantOffer(
                new ItemCost(Items.GOLD_INGOT, 1), new ItemStack(ExampleMod.GALLEON.get(), 2), 24, 0, 0.05F));
        offers.add(new MerchantOffer(
                new ItemCost(ExampleMod.GALLEON.get(), 3), new ItemStack(Items.GOLD_INGOT, 1), 24, 0, 0.05F));
        offers.add(new MerchantOffer(
                new ItemCost(Items.DIAMOND, 1), new ItemStack(ExampleMod.GALLEON.get(), 6), 12, 0, 0.05F));
        offers.add(new MerchantOffer(
                new ItemCost(ExampleMod.GALLEON.get(), 14),
                new ItemStack(ExampleMod.FINE_WOOD_STICK.get(), 1), 8, 0, 0.05F));
        offers.add(new MerchantOffer(
                new ItemCost(ExampleMod.GALLEON.get(), 26), new ItemStack(ExampleMod.BROOM.get(), 1), 3, 0, 0.05F));
        offers.add(new MerchantOffer(
                new ItemCost(ExampleMod.GALLEON.get(), 34),
                new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get(), 1), 2, 0, 0.05F));
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        // Goblins do not level up or gossip; the coin is the whole reward.
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || this.isBaby() || this.isTrading()) {
            return super.mobInteract(player, hand);
        }
        // A goblin that is currently furious will not do business.
        if (this.getTarget() != null) {
            this.playSound(ModSounds.GOBLIN_NO.get(), 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.level().isClientSide) {
            if (this.getOffers().isEmpty()) {
                this.updateTrades();
            }
            this.setTradingPlayer(player);
            this.openTradingScreen(player, this.getDisplayName(), 1);
            this.playSound(ModSounds.GOBLIN_TRADE.get(), 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);
        this.playSound(ModSounds.GOBLIN_TRADE.get(), 1.0F, 1.0F);
    }

    // ------------------------------------------------------------- behaviour

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        this.updateTrades();
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        // Raising a hand to a teller brings the whole vault down on you.
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            GoblinGuardEntity.alertGuards(this, attacker, GUARD_ALERT_RADIUS);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_ATTACK);
        }
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK) {
            this.attackAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            // The ledger-and-coins routine only plays while standing still.
            if (this.isStandingStill()) {
                this.busyAnimationState.startIfStopped(this.tickCount);
            } else {
                this.busyAnimationState.stop();
            }
        }
    }

    /** True when the goblin is not walking and has nothing to fight. */
    private boolean isStandingStill() {
        return this.getTarget() == null
                && this.getDeltaMovement().horizontalDistanceSqr() < 1.0E-5D;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * Wild goblins keep to the deep - the vaults run miles under London, and the
     * ones you meet outside Gringotts are down in the dark on bank business.
     * Deliberately not using the vanilla animal rules, which demand sky light.
     */
    public static boolean canSpawn(EntityType<GoblinEntity> type, ServerLevelAccessor level,
                                   MobSpawnType reason, net.minecraft.core.BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        if (reason == MobSpawnType.SPAWNER || reason == MobSpawnType.SPAWN_EGG
                || reason == MobSpawnType.STRUCTURE || reason == MobSpawnType.COMMAND) {
            return true;
        }
        if (pos.getY() > 40) {
            return false;
        }
        return level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && level.getFluidState(pos).isEmpty();
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    // ----------------------------------------------------------------- sound

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (this.getTarget() != null) {
            return ModSounds.GOBLIN_ANGRY.get();
        }
        return switch (this.random.nextInt(3)) {
            case 0 -> ModSounds.GOBLIN_AMBIENT1.get();
            case 1 -> ModSounds.GOBLIN_AMBIENT2.get();
            default -> ModSounds.GOBLIN_AMBIENT3.get();
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GOBLIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOBLIN_DEATH.get();
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean success) {
        return success ? ModSounds.GOBLIN_TRADE.get() : ModSounds.GOBLIN_NO.get();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return ModSounds.GOBLIN_TRADE.get();
    }

    /** Convenience used by worldgen so the Gringotts teller reads as staff. */
    public void makeTeller(String name) {
        this.setCustomName(Component.literal(name));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
    }

    // ------------------------------------------------------------ animations

    @Override
    public AnimationState goblinIdleAnimationState() {
        return this.idleAnimationState;
    }

    @Override
    public AnimationState goblinBusyAnimationState() {
        return this.busyAnimationState;
    }

    @Override
    public AnimationState goblinAttackAnimationState() {
        return this.attackAnimationState;
    }

    @Override
    public boolean goblinCountsCoins() {
        return true;
    }

    @Override
    public boolean goblinHoldsWeapon() {
        return false;
    }
}
