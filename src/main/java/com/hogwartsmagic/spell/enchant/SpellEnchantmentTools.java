package com.hogwartsmagic.spell.enchant;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spell enchantments on things that are not armour or weapons, server side.
 * <ul>
 * <li><b>Bombarda Maxima</b> I-III (trident): where a thrown trident first lands, a shockwave throws back and hurts
 * what stands round it (not the thrower, never blocks).</li>
 * <li><b>Imperio</b> (lead): leashes any mob but bosses, villagers and monsters included. While held it does not
 * fight, keeps close and is never torn off by distance; when let go the enchanted lead comes back.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellEnchantmentTools {
    private static final String TRIDENT_SPENT = "hogwartsmagic:bombarda_spent";
    private static final String IMPERIO_LEAD = "hogwartsmagic:imperio_lead";
    private static final int IMPERIO_GREEN = 0x88FF88;

    private SpellEnchantmentTools() {
    }

    /** Bombarda Maxima: the shockwave's reach (blocks) and its damage at a level. */
    public static double bombardaRadius(int level) {
        return 1.5 + Math.max(1, Math.min(3, level));
    }

    public static float bombardaDamage(int level) {
        return 2.0f + Math.max(1, Math.min(3, level));
    }

    // ------------------------------------------------------------------------------------------------ bombarda

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownTrident trident) || !(trident.level() instanceof ServerLevel level)
                || trident.getPersistentData().getBoolean(TRIDENT_SPENT)) {
            return;
        }
        ItemStack stack = trident.getPickupItemStackOrigin();
        int bombarda = SpellEnchantments.level(level, SpellEnchantments.BOMBARDA_MAXIMA, stack);
        if (bombarda <= 0) {
            return;
        }
        if (!SpellEnchantMana.pay(trident.getOwner(), SpellEnchantMana.BOMBARDA_MAXIMA)) {
            trident.getPersistentData().putBoolean(TRIDENT_SPENT, true);
            return;
        }
        trident.getPersistentData().putBoolean(TRIDENT_SPENT, true); // once a throw, not again on the way back
        Vec3 at = event.getRayTraceResult().getLocation();
        shockwave(level, at, trident.getOwner(), Math.min(3, bombarda));
    }

    public static void shockwave(ServerLevel level, Vec3 at, Entity owner, int bombarda) {
        double radius = bombardaRadius(bombarda);
        float damage = DamageSupport.scaled(owner, bombardaDamage(bombarda));
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> e != owner && e.isAlive() && !e.isSpectator() && e.distanceToSqr(at) <= radius * radius
                        && !(owner != null && e.isAlliedTo(owner)))) {
            if (victim instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)) {
                continue;
            }
            Vec3 push = victim.position().subtract(at).multiply(1.0, 0.0, 1.0);
            double falloff = 1.0 - Math.sqrt(victim.distanceToSqr(at)) / (radius + 0.5);
            victim.hurt(DamageSupport.spell(level, null, owner), damage * (float) Math.max(0.35, falloff));
            if (push.lengthSqr() > 1.0E-4) {
                push = push.normalize();
                victim.knockback(0.6 + 0.4 * falloff * bombarda, -push.x, -push.z);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.25 + 0.1 * bombarda, 0.0));
                victim.hurtMarked = true;
            }
        }
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.BLAST, 0xFF9A40, 0.6f + 0.2f * bombarda, 16),
                at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.RING, 0xFFD8A0, (float) radius, 14),
                at.x, at.y + 0.1, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.3, 0.1, 0.3, 0.0);
        Fx.shake(level, at, 0.35f + 0.15f * bombarda, 14.0f, 10);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                0.7f + 0.15f * bombarda, 1.2f);
    }

    // ------------------------------------------------------------------------------------------------ imperio

    /** An Imperio lead on a mob vanilla would not leash (or any mob, really): ours does the leashing. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeash(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!held.is(Items.LEAD) || !(event.getTarget() instanceof Mob mob)) {
            return;
        }
        if (SpellEnchantments.level(player.level(), SpellEnchantments.IMPERIO, held) <= 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!(player.level() instanceof ServerLevel level) || mob.isLeashed() || CombatTags.isBoss(mob)) {
            return;
        }
        if (!SpellEnchantMana.pay(player, SpellEnchantMana.IMPERIO_LEASH)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.enchant.no_mana").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        ItemStack one = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        mob.setLeashedTo(player, true);
        mob.setTarget(null);
        mob.getPersistentData().put(IMPERIO_LEAD, one.save(level.registryAccess()));
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, IMPERIO_GREEN, 0.05f, 24),
                mob.getX(), mob.getY(0.6), mob.getZ(), 12, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3,
                mob.getBbWidth() * 0.4, 0.01);
        level.playSound(null, mob.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.4F);
    }

    @SubscribeEvent
    public static void onMobTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || !mob.getPersistentData().contains(IMPERIO_LEAD)) {
            return;
        }
        if (!mob.isLeashed()) {
            returnLead(level, mob);
            return;
        }
        Entity holder = mob.getLeashHolder();
        if (mob.tickCount % 60 == 0 && holder instanceof Player && !SpellEnchantMana.pay(holder, SpellEnchantMana.IMPERIO_HOLD)) {
            mob.dropLeash(true, true); // out of mana: the curse lets go (the lead comes back next tick)
            return;
        }
        mob.setTarget(null); // held under the curse: no fighting
        if (mob instanceof PathfinderMob walker && holder != null) {
            double dist = mob.distanceTo(holder);
            if (dist > 8.0) {
                // Never torn off by distance: it is drawn to its holder's side instead.
                Vec3 to = holder.position().add(holder.getLookAngle().multiply(-1.5, 0.0, -1.5));
                mob.teleportTo(to.x, holder.getY(), to.z);
                mob.getNavigation().stop();
            } else if (dist > 3.0) {
                walker.getNavigation().moveTo(holder, 1.2);
            } else if (dist < 2.0) {
                walker.getNavigation().stop();
            }
        }
        if (mob.tickCount % 30 == 0) {
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, IMPERIO_GREEN, 0.035f, 20),
                    mob.getX(), mob.getEyeY() + 0.2, mob.getZ(), 1, 0.15, 0.05, 0.15, 0.005);
        }
    }

    /** Let go: the plain lead vanilla just dropped (if any) becomes the enchanted one again. */
    private static void returnLead(ServerLevel level, Mob mob) {
        CompoundTag saved = mob.getPersistentData().getCompound(IMPERIO_LEAD);
        mob.getPersistentData().remove(IMPERIO_LEAD);
        ItemStack lead = ItemStack.parseOptional(level.registryAccess(), saved);
        if (lead.isEmpty()) {
            return;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(2.5),
                e -> e.getItem().is(Items.LEAD) && e.getAge() < 10
                        && SpellEnchantments.level(level, SpellEnchantments.IMPERIO, e.getItem()) <= 0)) {
            item.setItem(lead);
            return;
        }
    }
}
