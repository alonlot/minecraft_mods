package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.hogwartsmagic.item.SplashBrewItem;
import com.hogwartsmagic.petrification.Petrification;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown cauldron brew. Like the vanilla splash potion it breaks on whatever it hits and reaches 4 blocks, but its
 * effect is the mod's own: the Mandrake Draught frees stone players and statues, Living Death stuns through
 * CrowdControl (diminishing returns, the boss rule, the PvP rule: this entity's owner is the caster).
 */
public class ThrownBrewEntity extends ThrowableItemProjectile {

    public ThrownBrewEntity(EntityType<? extends ThrownBrewEntity> type, Level level) {
        super(type, level);
    }

    public ThrownBrewEntity(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_BREW.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ExampleMod.DRAUGHT_OF_LIVING_DEATH.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        SplashBrewItem.Kind kind = this.getItem().getItem() instanceof SplashBrewItem brew
                ? brew.kind() : SplashBrewItem.Kind.LIVING_DEATH;
        AABB area = this.getBoundingBox().inflate(PotionRules.SPLASH_RADIUS, 2.0D, PotionRules.SPLASH_RADIUS);
        int grade = this.getItem().getOrDefault(ExampleMod.BREW_QUALITY.get(), 0);
        if (kind == SplashBrewItem.Kind.MANDRAKE) {
            Petrification.splashMandrake(level, this, area, this.getOwner(),
                    PotionRules.qualityTicks(PotionRules.MANDRAKE_GAZE_WARD_TICKS, grade));
        } else {
            splashLivingDeath(level, area, grade);
        }
        level.levelEvent(LevelEvent.PARTICLES_SPELL_POTION_SPLASH, this.blockPosition(), kind.color());
        this.discard();
    }

    private void splashLivingDeath(ServerLevel level, AABB area, int grade) {
        Holder<MobEffect> stunned = ExampleMod.STUNNED.getHolder().orElse(null);
        if (stunned == null) {
            return;
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && !Petrification.isPetrified(e) && PotionRules.inSplash(this.distanceToSqr(e)))) {
            CrowdControl.apply(target, stunned,
                    Math.round(PotionRules.qualityAmount(PotionRules.LIVING_DEATH_STUN_TICKS, grade)), this);
        }
    }
}
