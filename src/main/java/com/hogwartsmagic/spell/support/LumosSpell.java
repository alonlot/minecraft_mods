package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.LumosLightBlock;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Lumos. The wand light is the {@code lumos} effect (amplifier = brightness, read by the light tick in
 * ModEvents). From Expert the cast is a beam: on a surface it leaves a Lumos Maxima orb (a timed
 * LumosLightBlock, at most three per caster, the oldest goes first), on a creature it hands over the
 * light, and at Master it burns creatures of darkness (Lumos Solem). Server side.
 */
public final class LumosSpell {
    public static final TagKey<EntityType<?>> SOLEM_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "lumos_solem_vulnerable"));
    private static final String TAG_ORBS = ExampleMod.MODID + ":lumos_orbs";

    private LumosSpell() {
    }

    private static Holder<MobEffect> light() {
        return ExampleMod.LUMOS_EFFECT.getHolder().orElseThrow();
    }

    /** Lumos / Nox on {@code target}, at the caster's brightness. */
    public static void toggle(LivingEntity target, @Nullable Entity caster) {
        if (target.hasEffect(light())) {
            target.removeEffect(light());
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LUMOS);
        target.addEffect(new MobEffectInstance(light(), LumosRules.LIGHT_TICKS, LumosRules.amplifierForRank(rank), false, false, true));
    }

    /** applyHitEffect: a self-cast toggles; a beam burns at Master or hands over the light. */
    public static void onHit(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        if (CombatEffects.responsible(caster) == target) {
            toggle(target, caster);
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LUMOS);
        if (LumosRules.solem(rank) && target.getType().is(SOLEM_VULNERABLE)) {
            target.hurt(target.damageSources().magic(), DamageSupport.scaled(caster, LumosRules.SOLEM_DAMAGE));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, LumosRules.SOLEM_BLIND_TICKS, 0));
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
            }
            return;
        }
        target.addEffect(new MobEffectInstance(light(), LumosRules.LIGHT_TICKS, LumosRules.amplifierForRank(rank), false, false, true));
    }

    /** Lumos Maxima: a floating light at {@code pos} for 5 minutes. Players at Expert and up only. */
    public static boolean placeOrb(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        int rank = SpellMasteryData.getRank(player, SpellType.LUMOS);
        if (!LumosRules.placesOrbs(rank) || !level.isLoaded(pos) || !TerrainRules.canPlace(level, pos, player)) {
            return false;
        }
        if (!LumosLightBlock.placeTimedLight(level, pos, LumosRules.ORB_LIGHT, LumosRules.ORB_TICKS)) {
            return false;
        }
        rememberOrb(player, level, pos);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.2, 0.2, 0.2, 0.03);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
        return true;
    }

    private static void rememberOrb(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag data = player.getPersistentData();
        ListTag orbs = data.getList(TAG_ORBS, Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", level.dimension().location().toString());
        entry.putLong("Pos", pos.asLong());
        // A re-cast onto an existing orb refreshes it rather than counting twice (the oldest would
        // otherwise evict a light that is still the newest).
        for (int i = orbs.size() - 1; i >= 0; i--) {
            if (orbs.getCompound(i).equals(entry)) {
                orbs.remove(i);
            }
        }
        orbs.add(entry);
        while (orbs.size() > LumosRules.MAX_ORBS) {
            CompoundTag oldest = orbs.getCompound(0);
            orbs.remove(0);
            ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(oldest.getString("Dim")));
            ServerLevel orbLevel = level.getServer().getLevel(dim);
            BlockPos orbPos = BlockPos.of(oldest.getLong("Pos"));
            if (orbLevel != null && orbLevel.isLoaded(orbPos) && LumosLightBlock.isTimedLight(orbLevel.getBlockState(orbPos))) {
                orbLevel.removeBlock(orbPos, false);
            }
        }
        data.put(TAG_ORBS, orbs);
    }
}
