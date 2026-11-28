package com.hogwartsmagic.spell.enchant;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.IncarcerousTether;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The fighting and hiding spell enchantments, server side.
 * <ul>
 * <li><b>Cave Inimicum</b> I-II (any armour): mobs notice the wearer from less far (the body also shows faintly
 * see-through, drawn client side).</li>
 * <li><b>Depulso</b> I-III (shield): a blocked melee blow throws the attacker back.</li>
 * <li><b>Protego Maxima</b> (shield): blocked arrows fly back at their shooter, and a blocked spell sends a third of
 * its force back at the caster.</li>
 * <li><b>Incarcerous</b> I-II (crossbow): a bolt that hits binds the target in chains for 2 / 3.5 s.</li>
 * </ul>
 * Revelio (glowing hostiles) is client side only: {@code RevelioGlowMixin}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellEnchantmentCombat {
    private static final int[] INCARCEROUS_TICKS = {40, 70};
    public static final float PROTEGO_MAXIMA_RETURN = 1.0f / 3.0f;

    private SpellEnchantmentCombat() {
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        int levels = SpellEnchantments.armourLevels(event.getEntity(), SpellEnchantments.CAVE_INIMICUM);
        if (levels > 0 && caveInimicumPaid(event.getEntity())) {
            event.modifyVisibility(SpellEnchantments.caveInimicumVisibility(levels));
        }
    }

    /** Cave Inimicum costs a little a second while something is looking; paid once a second at most. */
    private static boolean caveInimicumPaid(LivingEntity wearer) {
        long now = wearer.level().getGameTime();
        var data = wearer.getPersistentData();
        if (now - data.getLong("hogwartsmagic:cave_paid") < 20) {
            return data.getBoolean("hogwartsmagic:cave_ok");
        }
        boolean ok = SpellEnchantMana.pay(wearer, SpellEnchantMana.CAVE_INIMICUM);
        data.putLong("hogwartsmagic:cave_paid", now);
        data.putBoolean("hogwartsmagic:cave_ok", ok);
        return ok;
    }

    /** The shield in use, or empty. */
    private static ItemStack shield(LivingEntity blocker) {
        ItemStack using = blocker.getUseItem();
        return using.is(Items.SHIELD) ? using : ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!(blocker.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack shield = shield(blocker);
        if (shield.isEmpty()) {
            return;
        }
        DamageSource source = event.getDamageSource();
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();

        int depulso = SpellEnchantments.level(level, SpellEnchantments.DEPULSO, shield);
        if (depulso > 0 && direct instanceof LivingEntity melee && direct == attacker && melee != blocker
                && SpellEnchantMana.pay(blocker, SpellEnchantMana.DEPULSO)) {
            Vec3 away = melee.position().subtract(blocker.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize();
                melee.knockback(0.5 + 0.35 * Math.min(3, depulso), -away.x, -away.z);
                melee.hurtMarked = true;
                Vec3 at = blocker.getEyePosition().add(away.scale(0.8));
                level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, 0x9FD8FF, 0.9f, 10),
                        at.x, at.y - 0.4, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.playSound(null, blocker.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                        SoundSource.PLAYERS, 0.6F, 1.3F);
            }
        }

        if (SpellEnchantments.level(level, SpellEnchantments.PROTEGO_MAXIMA, shield) <= 0
                || !SpellEnchantMana.pay(blocker, SpellEnchantMana.PROTEGO_MAXIMA)) {
            return;
        }
        if (direct instanceof AbstractArrow arrow && attacker instanceof LivingEntity shooter && shooter != blocker) {
            // The arrow is still resolving its hit: turn it round once that is done.
            level.getServer().execute(() -> reflect(level, arrow, blocker, shooter));
        } else if (source.is(DamageSupport.SPELL) && attacker instanceof LivingEntity caster && caster != blocker
                && caster.isAlive()) {
            float back = event.getBlockedDamage() * PROTEGO_MAXIMA_RETURN;
            if (back > 0.0f) {
                caster.hurt(DamageSupport.spell(level, null, blocker), back);
                Vec3 at = caster.getEyePosition();
                level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xBFE6FF, 0.07f, 14),
                        at.x, at.y, at.z, 14, 0.25, 0.3, 0.25, 0.15);
            }
        }
        Vec3 front = blocker.getEyePosition().add(blocker.getLookAngle().scale(0.7));
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, 0xBFE6FF, 0.4f, 8),
                front.x, front.y - 0.3, front.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, blocker.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void reflect(ServerLevel level, AbstractArrow arrow, LivingEntity blocker, LivingEntity shooter) {
        if (arrow.isRemoved() || !shooter.isAlive() || shooter.level() != level) {
            return;
        }
        Vec3 from = arrow.position();
        Vec3 to = shooter.getEyePosition().subtract(0.0, 0.3, 0.0);
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        arrow.setOwner(blocker);
        arrow.setDeltaMovement(dir.normalize().scale(1.8));
        arrow.hasImpulse = true;
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0xBFE6FF, 0.05f, 10),
                from.x, from.y, from.z, 8, 0.1, 0.1, 0.1, 0.1);
    }

    /** Patronus: the wearer shrugs off Darkness and Wither. */
    @SubscribeEvent
    public static void onEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if ((effect.is(net.minecraft.world.effect.MobEffects.DARKNESS) || effect.is(net.minecraft.world.effect.MobEffects.WITHER))
                && SpellEnchantments.level(event.getEntity(), SpellEnchantments.PATRONUS,
                net.minecraft.world.entity.EquipmentSlot.CHEST) > 0
                && SpellEnchantMana.pay(event.getEntity(), SpellEnchantMana.PATRONUS)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            LivingEntity wearer = event.getEntity();
            long now = wearer.level().getGameTime();
            if (wearer.level() instanceof ServerLevel level
                    && now - wearer.getPersistentData().getLong("hogwartsmagic:patronus_flash") > 20) {
                // A silver flare as the guardian turns the darkness away (at most once a second).
                wearer.getPersistentData().putLong("hogwartsmagic:patronus_flash", now);
                level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, 0xDDEEFF, 1.2f, 14),
                        wearer.getX(), wearer.getY(0.5), wearer.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, 0xE8F4FF, 0.05f, 26),
                        wearer.getX(), wearer.getY(0.5), wearer.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
                level.playSound(null, wearer.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                        0.8F, 1.6F);
            }
        }
    }

    /** Glacius: a melee hit or an arrow from a Glacius weapon chills the target. */
    @SubscribeEvent
    public static void onGlaciusHit(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getAmount() <= 0.0f) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        ItemStack weapon = ItemStack.EMPTY;
        if (direct instanceof AbstractArrow arrow && arrow.getWeaponItem() != null) {
            weapon = arrow.getWeaponItem();
        } else if (direct instanceof LivingEntity attacker && direct == event.getSource().getEntity()) {
            weapon = attacker.getMainHandItem();
        }
        int glacius = SpellEnchantments.level(level, SpellEnchantments.GLACIUS, weapon);
        if (glacius <= 0 || target == event.getSource().getEntity()
                || !SpellEnchantMana.pay(event.getSource().getEntity(), SpellEnchantMana.GLACIUS)) {
            return;
        }
        glacius = Math.min(3, glacius);
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                SpellEnchantments.glaciusTicks(glacius), glacius - 1), event.getSource().getEntity());
        // Frost creeping over the screen and the skin (kept short of the cold's own damage at 140).
        if (target.canFreeze()) {
            target.setTicksFrozen(Math.min(130, target.getTicksFrozen() + 40 * glacius));
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6), target.getZ(),
                8 + 4 * glacius, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, 0xBFE9FF, 0.04f, 20), target.getX(), target.getY(0.5),
                target.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
        level.playSound(null, target.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    @SubscribeEvent
    public static void onBoltHit(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
                || event.getEntity().level().isClientSide()) {
            return;
        }
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || !weapon.is(Items.CROSSBOW)) {
            return;
        }
        int level = SpellEnchantments.level(event.getEntity().level(), SpellEnchantments.INCARCEROUS, weapon);
        if (level > 0 && SpellEnchantMana.pay(arrow.getOwner(), SpellEnchantMana.INCARCEROUS)) {
            IncarcerousTether.applyBolt(event.getEntity(), arrow.getOwner(), INCARCEROUS_TICKS[Math.min(2, level) - 1]);
        }
    }
}
