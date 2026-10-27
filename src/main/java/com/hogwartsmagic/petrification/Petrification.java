package com.hogwartsmagic.petrification;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Canon petrification in the world. A player the Basilisk's full gaze meets is turned to stone: an infinite
 * basilisk_petrification effect, backed by Petrified and PetrifiedSince under PlayerPersisted/hogwartsmagic (the
 * keys are the truth; tick() keeps the effect in line with them). Stone counts as stunned (CombatEffects.isStunned),
 * which locks casting, use, attack and movement; PetrificationEvents keeps damage and targeting off it. A Mandrake
 * Restorative Draught cures it; giving in kills the player with the petrification damage type. Common code: no
 * client classes.
 */
public final class Petrification {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "petrification"));

    /** Hot path (CombatEffects.isStunned runs for every living entity every tick): resolve the holder once. */
    private static Holder<MobEffect> stoneHolder;

    private Petrification() {
    }

    @Nullable
    public static Holder<MobEffect> stone() {
        Holder<MobEffect> holder = stoneHolder;
        if (holder == null) {
            holder = stoneHolder = ExampleMod.BASILISK_PETRIFICATION.getHolder().orElse(null);
        }
        return holder;
    }

    /**
     * Both sides: lasting stone, an infinite basilisk_petrification effect. The 60-tick effect of a mob the gaze is
     * killing does not count.
     */
    public static boolean isPetrified(LivingEntity entity) {
        Holder<MobEffect> holder = stone();
        if (holder == null) {
            return false;
        }
        MobEffectInstance instance = entity.getEffect(holder);
        return instance != null && instance.isInfiniteDuration();
    }

    /** Server: the persisted key, the source of truth for players. */
    public static boolean hasPetrifiedFlag(ServerPlayer player) {
        return PlayerMagicData.getModData(player).getBoolean(PetrificationRules.KEY_PETRIFIED);
    }

    /** The Basilisk's full gaze meets a survival or adventure player. */
    public static void petrify(ServerPlayer player) {
        CompoundTag data = PlayerMagicData.getModData(player);
        data.putBoolean(PetrificationRules.KEY_PETRIFIED, true);
        data.putLong(PetrificationRules.KEY_SINCE, now(player));
        PlayerMagicData.saveModData(player, data);
        com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
        com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
        player.stopUsingItem();
        player.stopRiding(); // stone is never carried by a vehicle
        applyStone(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_PLACE,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.petrified")
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** Every server tick for every player (PetrificationEvents). */
    public static void tick(ServerPlayer player) {
        if (!hasPetrifiedFlag(player)) {
            Holder<MobEffect> holder = stone();
            if (holder != null && isPetrified(player)) {
                player.removeEffect(holder); // an infinite stone effect the keys do not back
            }
            return;
        }
        if (!player.isAlive()) {
            return;
        }
        if (!isPetrified(player)) {
            applyStone(player); // removed by something that bypassed the stun lock
        }
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setExhaustion(0.0F);
        long now = now(player);
        if (PetrificationRules.dueCheck(now, player.getId())) {
            clearHunters(player);
            CompoundTag stoneData = PlayerMagicData.getModData(player);
            long since;
            if (stoneData.contains(PetrificationRules.KEY_SINCE)) {
                since = stoneData.getLong(PetrificationRules.KEY_SINCE);
            } else {
                since = now; // a missing start is written once, so the time limit can begin
                stoneData.putLong(PetrificationRules.KEY_SINCE, now);
                PlayerMagicData.saveModData(player, stoneData);
            }
            if (PetrificationRules.limitReached(since, now, Config.getPetrificationMaxSeconds())) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.time_up")
                        .withStyle(ChatFormatting.GRAY), false);
                giveIn(player);
            }
        }
    }

    /**
     * Ends a player's stone state (a Mandrake Draught). rescuer: who brought them back, or null. Returns false when
     * the player was not stone.
     */
    public static boolean cure(ServerPlayer player, @Nullable Entity rescuer) {
        if (!hasPetrifiedFlag(player) && !isPetrified(player)) {
            return false;
        }
        clearFlag(player);
        Holder<MobEffect> holder = stone();
        if (holder != null) {
            player.removeEffect(holder);
        }
        player.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PotionRules.CURE_SLOWNESS_TICKS, 0));
        stoneBurst(player.serverLevel(), player);
        if (rescuer instanceof ServerPlayer saver && saver != player) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.saved_by",
                    saver.getDisplayName()), false);
            saver.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.you_saved",
                    player.getDisplayName()), false);
        } else {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.freed"), false);
        }
        wardGaze(player); // a rescued player gets the same 8 s ward as a drinker
        return true;
    }

    /**
     * The player gives in (GiveInPacket, or the time limit). Checked here, never trusted: only a stone, living
     * player. The damage is dealt while the player is still stone (the damage filter lets this type through); the
     * keys are cleared by the LOWEST death handler (PetrificationEvents), so this is a normal death (Horcrux, drops,
     * keepInventory). If the player is still alive afterwards (another mod cancelled the hurt), they stay stone and
     * PetrifiedSince is re-armed to now, so a time limit starts over instead of repeating the time-up line.
     */
    public static void giveIn(ServerPlayer player) {
        if (!PetrificationRules.giveInAccepted(hasPetrifiedFlag(player), player.isAlive())) {
            return;
        }
        player.hurt(damage(player.level()), Float.MAX_VALUE);
        if (PetrificationRules.giveInFailed(hasPetrifiedFlag(player), player.isAlive())) {
            CompoundTag data = PlayerMagicData.getModData(player);
            data.putLong(PetrificationRules.KEY_SINCE, now(player));
            PlayerMagicData.saveModData(player, data);
        }
    }

    public static void clearFlag(ServerPlayer player) {
        CompoundTag data = PlayerMagicData.getModData(player);
        data.remove(PetrificationRules.KEY_PETRIFIED);
        data.remove(PetrificationRules.KEY_SINCE);
        PlayerMagicData.saveModData(player, data);
    }

    /** "gave in to the stone": bypasses armour, effects, enchantments, resistance, invulnerability and totems. */
    public static DamageSource damage(Level level) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        return new DamageSource(type);
    }

    /** A drink of Mandrake Draught: the gaze's hold is gone, and for 8 seconds it cannot build. */
    public static void wardGaze(LivingEntity entity) {
        wardGaze(entity, PotionRules.MANDRAKE_GAZE_WARD_TICKS);
    }

    /** The same ward for a chosen length: a better brew lasts longer. */
    public static void wardGaze(LivingEntity entity, int ticks) {
        entity.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        entity.getPersistentData().putLong(PetrificationRules.WARD_KEY, entity.level().getGameTime() + ticks);
    }

    public static boolean isGazeWarded(LivingEntity entity) {
        return PetrificationRules.warded(entity.getPersistentData().getLong(PetrificationRules.WARD_KEY),
                entity.level().getGameTime());
    }

    /** Stone cracking away: 30 stone particles and a stone break. */
    public static void stoneBurst(ServerLevel level, Entity at) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                at.getX(), at.getY(0.5D), at.getZ(), 30, 0.3D, 0.5D, 0.3D, 0.1D);
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void applyStone(ServerPlayer player) {
        Holder<MobEffect> holder = stone();
        if (holder != null) {
            player.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, 0, false, true, true));
        }
    }

    /** Mobs that already had the stone player as their target let go (new targets are refused in PetrificationEvents). */
    private static void clearHunters(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(PetrificationRules.TARGET_CLEAR_RADIUS), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    // ---- The Mandrake splash ---------------------------------------------------------------------

    /**
     * A splash of Mandrake Draught at {@code center}: stone players in the sphere are cured (by the thrower), every
     * other living thing gets the drink's ward, then statues in the sphere come back.
     */
    public static void splashMandrake(ServerLevel level, Entity center, net.minecraft.world.phys.AABB area,
                                      @Nullable Entity thrower) {
        splashMandrake(level, center, area, thrower, PotionRules.MANDRAKE_GAZE_WARD_TICKS);
    }

    /** As above, with the ward length of the thrown bottle's quality; the cure itself is the same. */
    public static void splashMandrake(ServerLevel level, Entity center, net.minecraft.world.phys.AABB area,
                                      @Nullable Entity thrower, int wardTicks) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (!PotionRules.inSplash(center.distanceToSqr(entity))) {
                continue;
            }
            if (entity instanceof ServerPlayer player && (hasPetrifiedFlag(player) || isPetrified(player))) {
                cure(player, thrower);
            } else if (!isPetrified(entity)) {
                wardGaze(entity, wardTicks);
            }
        }
        for (com.hogwartsmagic.entity.PetrifiedRemainsEntity statue
                : level.getEntitiesOfClass(com.hogwartsmagic.entity.PetrifiedRemainsEntity.class, area)) {
            if (PotionRules.inSplash(center.distanceToSqr(statue))) {
                reviveStatue(statue, thrower);
            }
        }
    }

    // ---- Statues ---------------------------------------------------------------------------------

    /**
     * The mob's save without the death fields, for its statue. A fox drops its mouth item on death outside
     * captureDrops, so the stored main hand is emptied. A villager's POI memories go too: vanilla released those
     * tickets when it died.
     */
    public static CompoundTag captureMob(LivingEntity victim) {
        CompoundTag tag = victim.saveWithoutId(new CompoundTag());
        for (String key : PetrificationRules.STRIPPED_MOB_KEYS) {
            tag.remove(key);
        }
        if (victim instanceof net.minecraft.world.entity.animal.Fox
                && tag.contains("HandItems", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag hands = tag.getList("HandItems", net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (!hands.isEmpty()) {
                hands.set(0, new CompoundTag());
            }
        }
        if (tag.contains("Brain", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag brain = tag.getCompound("Brain");
            if (brain.contains("memories", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag memories = brain.getCompound("memories");
                for (String memory : PetrificationRules.RELEASED_MEMORIES) {
                    memories.remove(memory);
                }
            }
        }
        return tag;
    }

    /** A non-player, non-boss that dies under the gaze's petrification: it became a statue, so no loot and no XP. */
    public static boolean isStatueDeath(LivingEntity entity) {
        Holder<MobEffect> holder = stone();
        return holder != null && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !com.hogwartsmagic.spell.combat.CombatTags.isBoss(entity) && entity.hasEffect(holder);
    }

    /**
     * Brings a statue's mob back where the statue stands, with full health. A statue with no stored mob (an older
     * world) brings back a fresh mob of the type. Bosses and unknown types stay stone, and so does a statue whose mob
     * the level refuses to add. Returns true when it came back.
     */
    public static boolean reviveStatue(com.hogwartsmagic.entity.PetrifiedRemainsEntity statue, @Nullable Entity rescuer) {
        if (statue.isRemoved() || !(statue.level() instanceof ServerLevel level)) {
            return false;
        }
        net.minecraft.world.entity.EntityType<?> type =
                net.minecraft.world.entity.EntityType.byString(statue.getMimicTypeId()).orElse(null);
        Entity created = type == null ? null : type.create(level);
        boolean boss = type != null && type.is(com.hogwartsmagic.spell.combat.CombatTags.BOSSES);
        if (!PetrificationRules.canRevive(type != null, created instanceof LivingEntity, boss)
                || !(created instanceof LivingEntity mob)) {
            if (created != null) {
                created.discard();
            }
            return false;
        }
        CompoundTag stored = statue.getStoredMob();
        if (!stored.isEmpty()) {
            mob.load(stored);
        }
        mob.removeAllEffects(); // vanilla also takes off the attribute modifiers the stored effects added
        if (level.getEntity(mob.getUUID()) != null) {
            mob.setUUID(net.minecraft.util.Mth.createInsecureUUID(level.getRandom())); // the corpse is still here
        }
        mob.moveTo(statue.getX(), statue.getY(), statue.getZ(), statue.getYRot(), 0.0F);
        mob.setYBodyRot(statue.getStoredBodyYaw());
        mob.setYHeadRot(statue.getStoredHeadYaw());
        mob.deathTime = 0;
        mob.setHealth(mob.getMaxHealth());
        if (!level.addFreshEntity(mob)) {
            return false; // refused (another mod): the statue stays, no message, no dose used
        }
        statue.discard();
        stoneBurst(level, mob);
        if (rescuer instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.statue_revived",
                    mob.getDisplayName()), true);
        }
        return true;
    }
}
