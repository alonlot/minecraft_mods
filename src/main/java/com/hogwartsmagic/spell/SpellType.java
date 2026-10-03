package com.hogwartsmagic.spell;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.sorting.HousePerks;
import net.minecraft.server.level.ServerPlayer;

/**
 * Defines spell types with colors, particles, and hit effects.
 */
public enum SpellType {
    EXPELLIARMUS("expelliarmus", 0xFF4444, 0.9f, 0.2f, 0.2f, ParticleTypes.FLAME, ParticleTypes.LARGE_SMOKE, 40, 20, SpellLootRarity.UNCOMMON),
    GLACIUS("glacius", 0x88FFFF, 0.5f, 1.0f, 1.0f, ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, 60, 25, SpellLootRarity.UNCOMMON),
    CONFRINGO("confringo", 0xFF8800, 1.0f, 0.5f, 0.0f, ParticleTypes.LAVA, ParticleTypes.EXPLOSION, 80, 35, SpellLootRarity.UNCOMMON),
    PROTEGO("protego", 0x88CCFF, 0.5f, 0.8f, 1.0f, ParticleTypes.END_ROD, ParticleTypes.FLASH, 100, 40, SpellLootRarity.UNCOMMON),
    PROTEGO_MAXIMA("protego_maxima", 0xB3F2FF, 0.70f, 0.95f, 1.0f, ParticleTypes.END_ROD, ParticleTypes.FLASH, 180, 65, SpellLootRarity.RARE),
    LUMOS("lumos", 0xFFFFAA, 1.0f, 1.0f, 0.67f, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, 20, 5, SpellLootRarity.COMMON),
    ACCIO("accio", 0x4488FF, 0.27f, 0.53f, 1.0f, ParticleTypes.CLOUD, ParticleTypes.SWEEP_ATTACK, 40, 15, SpellLootRarity.COMMON),
    WINGARDIUM_LEVIOSA("wingardium_leviosa", 0xAA44FF, 0.67f, 0.27f, 1.0f, ParticleTypes.ENCHANT, ParticleTypes.WITCH, 60, 15, SpellLootRarity.UNCOMMON),
    ALOHOMORA("alohomora", 0xFFDD44, 1.0f, 0.87f, 0.27f, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.GLOW, 40, 10, SpellLootRarity.COMMON),
    INCENDIO("incendio", 0xFF4400, 1.0f, 0.27f, 0.0f, ParticleTypes.FLAME, ParticleTypes.LAVA, 40, 20, SpellLootRarity.UNCOMMON),
    STUPEFY("stupefy", 0xFF0000, 1.0f, 0.0f, 0.0f, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.FLASH, 60, 30, SpellLootRarity.UNCOMMON),
    REVELIO("revelio", 0x44FFFF, 0.27f, 1.0f, 1.0f, ParticleTypes.ENCHANT, ParticleTypes.INSTANT_EFFECT, 100, 25, SpellLootRarity.UNCOMMON),
    AVADA_KEDAVRA("avada_kedavra", 0x00FF00, 0.0f, 1.0f, 0.0f, ParticleTypes.WITCH, ParticleTypes.FLASH, 12000, 100, SpellLootRarity.EPIC),
    CRUCIO("crucio", 0xFF0000, 1.0f, 0.0f, 0.0f, ParticleTypes.CRIT, ParticleTypes.DAMAGE_INDICATOR, 100, 60, SpellLootRarity.EPIC),
    IMPERIO("imperio", 0x88FF88, 0.53f, 1.0f, 0.53f, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.GLOW, 200, 80, SpellLootRarity.EPIC),
    FIENDFYRE("fiendfyre", 0xFF8800, 1.0f, 0.53f, 0.0f, ParticleTypes.FLAME, ParticleTypes.DRIPPING_LAVA, 300, 85, SpellLootRarity.EPIC),
    SECTUMSEMPRA("sectumsempra", 0x440000, 0.27f, 0.0f, 0.0f, ParticleTypes.SWEEP_ATTACK, ParticleTypes.CRIT, 60, 45, SpellLootRarity.RARE),
    MORSMORDRE("morsmordre", 0x004400, 0.0f, 0.27f, 0.0f, ParticleTypes.SQUID_INK, ParticleTypes.LARGE_SMOKE, 1200, 90, SpellLootRarity.EPIC),
    DOLOHOV_CURSE("dolohov_curse", 0xAA00AA, 0.67f, 0.0f, 0.67f, ParticleTypes.DRAGON_BREATH, ParticleTypes.FLASH, 100, 50, SpellLootRarity.RARE),
    INCARCEROUS("incarcerous", 0x444444, 0.27f, 0.27f, 0.27f, ParticleTypes.SQUID_INK, ParticleTypes.ITEM_SLIME, 80, 35, SpellLootRarity.UNCOMMON),
    HEAL("heal", 0x55FF55, 0.4f, 1.0f, 0.4f, ParticleTypes.HEART, ParticleTypes.ENCHANT, 100, 40, SpellLootRarity.UNCOMMON),
    TENEBRIS_FLIGHT("tenebris_flight", 0x111111, 0.1f, 0.1f, 0.1f, ParticleTypes.SQUID_INK, ParticleTypes.LARGE_SMOKE, 400, 50, SpellLootRarity.EPIC),
    EXPECTO_PATRONUM("expecto_patronum", 0x44FFFF, 0.27f, 1.0f, 1.0f, ParticleTypes.END_ROD, ParticleTypes.FLASH, 1200, 90, SpellLootRarity.EPIC),
    BOMBARDA("bombarda", 0xFF0055, 1.0f, 0.0f, 0.33f, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, 100, 50, SpellLootRarity.RARE),
    DEPULSO("depulso", 0xDDDDDD, 0.86f, 0.86f, 0.86f, ParticleTypes.CLOUD, ParticleTypes.SWEEP_ATTACK, 40, 20, SpellLootRarity.COMMON),
    LEVICORPUS("levicorpus", 0x8844FF, 0.53f, 0.27f, 1.0f, ParticleTypes.SQUID_INK, ParticleTypes.ENCHANT, 80, 25, SpellLootRarity.UNCOMMON),
    DIFFINDO("diffindo", 0xFFFFAA, 1.0f, 1.0f, 0.67f, ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, 40, 25, SpellLootRarity.COMMON),
    PETRIFICUS_TOTALUS("petrificus_totalus", 0xAAAAAA, 0.67f, 0.67f, 0.67f, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.FLASH, 100, 45, SpellLootRarity.UNCOMMON),
    AQUA_ERUCTO("aqua_eructo", 0x0088FF, 0.0f, 0.53f, 1.0f, ParticleTypes.SPLASH, ParticleTypes.BUBBLE, 40, 20, SpellLootRarity.UNCOMMON),
    OBLIVIATE("obliviate", 0xAAFFAA, 0.67f, 1.0f, 0.67f, ParticleTypes.ENCHANT, ParticleTypes.FLASH, 100, 60, SpellLootRarity.RARE),
    ASCENDIO("ascendio", 0xFFFFFF, 1.0f, 1.0f, 1.0f, ParticleTypes.CLOUD, ParticleTypes.CAMPFIRE_COSY_SMOKE, 60, 20, SpellLootRarity.COMMON),
    CONFUNDO("confundo", 0xFFAAAA, 1.0f, 0.67f, 0.67f, ParticleTypes.SMOKE, ParticleTypes.NAUTILUS, 80, 40, SpellLootRarity.UNCOMMON),
    CAVE_INIMICUM("cave_inimicum", 0x7AF0E0, 0.48f, 0.94f, 0.88f, ParticleTypes.END_ROD, ParticleTypes.ENCHANT, 200, 60, SpellLootRarity.RARE),
    /** Awakens flight magic in a broom frame on the Broom Workbench (requires correct ingredients first). */
    ALATUS("alatus", 0xD4C4A8, 0.83f, 0.75f, 0.53f, ParticleTypes.CLOUD, ParticleTypes.ENCHANT, 55, 24, SpellLootRarity.UNCOMMON),
    SERPENSORTIA("serpensortia", 0x005500, 0.0f, 0.33f, 0.0f, ParticleTypes.SQUID_INK, ParticleTypes.WITCH, 200, 60, SpellLootRarity.RARE),
    ARRESTO_MOMENTUM("arresto_momentum", 0xCFE8FF, 0.81f, 0.91f, 1.0f, ParticleTypes.CLOUD, ParticleTypes.GLOW, 50, 18, SpellLootRarity.UNCOMMON),
    REPARO("reparo", 0x8CF3D7, 0.55f, 0.95f, 0.84f, ParticleTypes.ENCHANT, ParticleTypes.WAX_ON, 80, 22, SpellLootRarity.UNCOMMON),
    /** Channeled self-teleport to a known location picked on a minimap. Handled entirely in the cast/channel
     *  pipeline (see ModEvents.startApparitionChannel); it never hits a target, so it has no beam or hit effect. */
    APPARITION("apparition", 0x9B5BD6, 0.61f, 0.36f, 0.84f, ParticleTypes.PORTAL, ParticleTypes.REVERSE_PORTAL, 1200, 133, SpellLootRarity.EPIC),
    /** Raises a man-shaped stone build with a Statue Heart for a head into a sentry bound to the caster.
     *  Everything happens on a block hit (see {@link com.hogwartsmagic.util.StatueMultiblock}), so it has
     *  no entity hit effect. The trail is plain smoke rather than a stone {@code BlockParticleOption}:
     *  these constants are built during this enum's class-init, and nothing in here should depend on the
     *  block registry having been populated first. Real stone comes off the blocks the rite eats. */
    PIERTOTUM_LOCOMOTOR("piertotum_locomotor", 0x9BA0A4, 0.61f, 0.63f, 0.64f, ParticleTypes.SMOKE, ParticleTypes.CAMPFIRE_COSY_SMOKE, 600, 90, SpellLootRarity.RARE);

    private final String id;
    private final int color;
    private final float r;
    private final float g;
    private final float b;
    private final ParticleOptions trailParticle;
    private final ParticleOptions impactParticle;
    private final int cooldownTicks;
    private final int manaCost;
    private final SpellLootRarity lootRarity;

    SpellType(String id, int color, float r, float g, float b,
              ParticleOptions trailParticle, ParticleOptions impactParticle, int cooldownTicks, int manaCost,
              SpellLootRarity lootRarity) {
        this.id = id;
        this.color = color;
        this.r = r;
        this.g = g;
        this.b = b;
        this.trailParticle = trailParticle;
        this.impactParticle = impactParticle;
        this.cooldownTicks = cooldownTicks;
        this.manaCost = manaCost;
        this.lootRarity = lootRarity;
    }

    public SpellLootRarity getLootRarity() {
        return lootRarity;
    }

    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public ParticleOptions getTrailParticle() {
        return trailParticle;
    }

    public ParticleOptions getImpactParticle() {
        return impactParticle;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getManaCost() {
        // Global tuning: reduce every spell's mana cost by 25% (use 75% of base cost).
        return Math.max(1, Math.round(manaCost * 0.75f));
    }

    /** Colored dust particle for beam trail - creates actual colored beam effect */
    public DustParticleOptions getDustParticle() {
        return new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
    }

    public void applyHitEffect(LivingEntity target, Entity caster) {
        applyHitEffect(target, caster, null);
    }

    /** @param beamDir the beam's travel direction at impact (used by Stupefy knockback), or null */
    public void applyHitEffect(LivingEntity target, Entity caster, @javax.annotation.Nullable net.minecraft.world.phys.Vec3 beamDir) {
        applyHitEffect(target, caster, beamDir, true);
    }

    /** @param masteryEligible false for a Protego-reflected beam: a Bombarda blast it queues earns no block XP */
    public void applyHitEffect(LivingEntity target, Entity caster, @javax.annotation.Nullable net.minecraft.world.phys.Vec3 beamDir,
                               boolean masteryEligible) {
        com.hogwartsmagic.spell.mind.MindHooks.beforeSpellHit(this, target, caster);
        switch (this) {
            case EXPELLIARMUS -> com.hogwartsmagic.spell.support.ExpelliarmusSpell.apply(target, caster, masteryEligible);
            case GLACIUS -> {
                // Steam combo first: put out the fire, or it would thaw the freeze on the next tick.
                if (target.isOnFire()) {
                    triggerSteamCombo(target);
                    target.clearFire();
                }
                com.hogwartsmagic.spell.disable.GlaciusSpell.apply(target, caster);
            }
            case CONFRINGO -> {
                com.hogwartsmagic.spell.damage.ConfringoSpell.apply(target, caster);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    triggerSteamCombo(target);
                    target.setTicksFrozen(Math.max(0, target.getTicksFrozen() - 120));
                }
            }
            case PROTEGO -> com.hogwartsmagic.spell.support.ProtegoSpell.raise(target, caster);
            case LUMOS -> com.hogwartsmagic.spell.support.LumosSpell.onHit(target, caster);
            case ACCIO -> com.hogwartsmagic.spell.support.AccioSpell.onLivingHit(target, caster, masteryEligible);
            case WINGARDIUM_LEVIOSA -> {
            }
            case ALOHOMORA -> {
                // No longer just haste, but potentially other effects if we want
            }
            case INCENDIO -> {
                com.hogwartsmagic.spell.damage.IncendioSpell.apply(target, caster);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    triggerSteamCombo(target);
                    target.setTicksFrozen(Math.max(0, target.getTicksFrozen() - 80));
                }
            }
            case STUPEFY -> com.hogwartsmagic.spell.disable.StupefySpell.apply(target, caster, beamDir);
            case REVELIO -> {
                // Revelio logic handled in SpellBeamEntity for blocks, 
                // but let's reveal invisible mobs here.
                if (target.isInvisible()) {
                    target.setInvisible(false);
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
                }
            }
            case AVADA_KEDAVRA -> {
                target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
                if (caster instanceof LivingEntity livingCaster) {
                    livingCaster.hurt(livingCaster.damageSources().magic(), scaledSpellDamage(caster, 2.0f)); // Dark magic cost
                }
            }
            case CRUCIO -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            }
            case IMPERIO -> {
                interruptActiveCasting(target);
                if (target instanceof com.hogwartsmagic.entity.HouseElfEntity elf) {
                    // An elf under the Imperius Curse cannot will itself away, which
                    // is the whole point: this is the dark road to binding one.
                    elf.markSubdued(200);
                }
                if (caster instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    net.minecraft.nbt.CompoundTag playerData = serverPlayer.getPersistentData();
                    playerData.putUUID("hogwartsmagic:imperio_recent_hit_target", target.getUUID());
                    playerData.putLong("hogwartsmagic:imperio_recent_hit_expiry", serverPlayer.level().getGameTime() + 60L);
                }
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    // Turn mob into ally by making it attack other hostile mobs
                    net.minecraft.world.phys.AABB area = mob.getBoundingBox().inflate(16.0);
                    java.util.List<net.minecraft.world.entity.Mob> nearby = mob.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, area, 
                        e -> e != mob && e != caster && (e instanceof net.minecraft.world.entity.monster.Enemy));
                    
                    if (!nearby.isEmpty()) {
                        net.minecraft.world.entity.Mob allyTarget = nearby.get(mob.level().random.nextInt(nearby.size()));
                        mob.setTarget(allyTarget);
                    }

                    // For some mobs, we can set them to be "allies" more explicitly if they support it
                    // but for most, just clearing their current target if it was the caster is good
                    if (mob.getTarget() == caster) {
                        mob.setTarget(null);
                    }
                    
                    mob.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 600, 0));
                    mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1));
                    
                    // Add a glowing effect to allied mobs so the player knows who is on their side
                    mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
                    
                    // Add a tag to mark as allied
                    mob.addTag(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);
                }
            }
            case FIENDFYRE -> {
                target.igniteForSeconds(20);
                float dmg = target.level().isRainingAt(target.blockPosition()) ? 6.0f : 8.0f;
                target.hurt(target.damageSources().onFire(), scaledSpellDamage(caster, dmg));
                // Spawn Fiendfyre management entity at target location
                com.hogwartsmagic.entity.FiendfyreEntity fiendfyre = new com.hogwartsmagic.entity.FiendfyreEntity(com.hogwartsmagic.entity.ModEntities.FIENDFYRE.get(), target.level());
                fiendfyre.moveTo(target.getX(), target.getY(), target.getZ());
                target.level().addFreshEntity(fiendfyre);
            }
            case SECTUMSEMPRA -> com.hogwartsmagic.spell.damage.SectumsempraSpell.apply(target, caster);
            case MORSMORDRE -> {
                if (target.level() instanceof ServerLevel serverLevel) {
                    DarkMarkEntity mark = new DarkMarkEntity(serverLevel, target.getX(), target.getY() + 18, target.getZ(), 2400);
                    serverLevel.addFreshEntity(mark);
                }
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
            case DOLOHOV_CURSE -> com.hogwartsmagic.spell.damage.DolohovSpell.apply(target, caster);
            case INCARCEROUS -> com.hogwartsmagic.spell.disable.IncarcerousTether.apply(target, caster);
            case HEAL -> com.hogwartsmagic.spell.support.HealSpell.apply(target, caster);
            case TENEBRIS_FLIGHT -> {
                // effect lasts only 2 seconds (40 ticks)
                if (caster instanceof LivingEntity livingCaster) {
                    livingCaster.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                    if (livingCaster instanceof com.hogwartsmagic.entity.WizardEntity wz) {
                        wz.setFlying(true); // set flying flag (invisibility via effect)
                    }
                }
                target.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                if (target instanceof com.hogwartsmagic.entity.WizardEntity wz2) {
                    wz2.setFlying(true);
                }
            }
            case BOMBARDA -> {
                com.hogwartsmagic.spell.damage.BombardaSpell.onEntityHit(target, caster, masteryEligible);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    triggerSteamCombo(target);
                }
            }
            case DEPULSO -> com.hogwartsmagic.spell.support.DepulsoSpell.apply(target, caster, masteryEligible);
            case LEVICORPUS -> com.hogwartsmagic.spell.disable.LevicorpusSpell.apply(target, caster);
            case DIFFINDO -> com.hogwartsmagic.spell.damage.DiffindoSpell.apply(target, caster);
            case PETRIFICUS_TOTALUS -> com.hogwartsmagic.spell.disable.PetrificusSpell.apply(target, caster);
            case AQUA_ERUCTO -> {
                boolean wasBurning = target.isOnFire();
                net.minecraft.world.phys.Vec3 push = target.position().vectorTo(
                        caster != null ? caster.position() : target.position().add(0, 0, -5))
                        .normalize().scale(-1.15);
                if (wasBurning) {
                    triggerSteamCombo(target);
                }
                target.clearFire(); // Extinguish target
                target.push(push.x, 0.28, push.z);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                if (target instanceof net.minecraft.world.entity.monster.Blaze || target instanceof net.minecraft.world.entity.monster.MagmaCube) {
                    target.hurt(target.damageSources().drown(), scaledSpellDamage(caster, 15.0f)); // Super effective against fire mobs
                }
                if (target.level().isRainingAt(target.blockPosition())) {
                    target.push(push.x * 0.35, 0.1, push.z * 0.35);
                }
            }
            case OBLIVIATE -> com.hogwartsmagic.spell.mind.ObliviateSpell.apply(target, caster);
            case ASCENDIO -> {
                target.setDeltaMovement(target.getDeltaMovement().add(0, 1.5, 0));
                if (target.level() instanceof ServerLevel server) {
                    net.minecraft.world.phys.AABB area = target.getBoundingBox().inflate(2.4);
                    java.util.List<LivingEntity> launched = server.getEntitiesOfClass(LivingEntity.class, area, e -> e != target);
                    for (LivingEntity nearby : launched) {
                        nearby.setDeltaMovement(nearby.getDeltaMovement().add(0, 1.1, 0));
                        nearby.fallDistance = 0.0f;
                    }
                }
            }
            case CONFUNDO -> com.hogwartsmagic.spell.mind.ConfundoSpell.apply(target, caster);
            case SERPENSORTIA -> {
                if (target.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 3; i++) {
                        net.minecraft.world.entity.monster.CaveSpider snake = new net.minecraft.world.entity.monster.CaveSpider(net.minecraft.world.entity.EntityType.CAVE_SPIDER, serverLevel);
                        snake.moveTo(target.getX() + (serverLevel.random.nextDouble() - 0.5) * 2, target.getY(), target.getZ() + (serverLevel.random.nextDouble() - 0.5) * 2, serverLevel.random.nextFloat() * 360F, 0.0F);
                        if (caster instanceof LivingEntity livingCaster) {
                            snake.setTarget(null);
                            // Set allied to caster by making them target what caster targets or attack who hurts caster (vanilla behavior doesn't support easy allies for spiders, we can add tag and ignore in Wizard logic)
                            snake.addTag(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);
                        }
                        serverLevel.addFreshEntity(snake);
                    }
                }
            }
            case ARRESTO_MOMENTUM -> {
                target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, caster == target ? 100 : 80, 0, false, true, true));
                if (caster != target) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true, true));
                }
                net.minecraft.world.phys.Vec3 motion = target.getDeltaMovement();
                double dampedX = motion.x * (caster == target ? 0.8 : 0.35);
                double dampedZ = motion.z * (caster == target ? 0.8 : 0.35);
                double minY = caster == target ? -0.15 : -0.12;
                target.setDeltaMovement(dampedX, Math.max(motion.y, minY), dampedZ);
                target.fallDistance = 0.0f;
                target.hurtMarked = true;
            }
            case REPARO -> {
            }
            case ALATUS -> {
            }
            case APPARITION -> {
                // No hit effect: Apparition is a self-cast channeled teleport, resolved in the channel pipeline.
            }
            case PIERTOTUM_LOCOMOTOR -> {
                // No hit effect: the charm animates masonry, not creatures. A living target is a miss, and
                // SpellBeamEntity.onHit fizzles the beam for it rather than routing through here.
            }
        }
    }

    public static SpellType fromId(String id) {
        for (SpellType s : values()) {
            if (s.id.equals(id)) return s;
        }
        return WINGARDIUM_LEVIOSA;
    }

    public SoundEvent getSoundEvent() {
        return switch (this) {
            case EXPELLIARMUS -> ModSounds.SPELL_EXPELLIARMUS.get();
            case GLACIUS -> ModSounds.SPELL_GLACIUS.get();
            case CONFRINGO -> ModSounds.SPELL_CONFRINGO.get();
            case PROTEGO, PROTEGO_MAXIMA -> ModSounds.SPELL_PROTEGO.get();
            case LUMOS -> ModSounds.SPELL_LUMOS.get();
            case ACCIO -> ModSounds.SPELL_ACCIO.get();
            case WINGARDIUM_LEVIOSA -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
            case ALOHOMORA -> ModSounds.SPELL_ALOHOMORA.get();
            case INCENDIO -> ModSounds.SPELL_INCENDIO.get();
            case STUPEFY -> ModSounds.SPELL_STUPEFY.get();
            case REVELIO -> ModSounds.SPELL_REVELIO.get();
            case AVADA_KEDAVRA -> ModSounds.SPELL_AVADA_KEDAVRA.get();
            case CRUCIO -> ModSounds.SPELL_CRUCIO.get();
            case IMPERIO -> ModSounds.SPELL_IMPERIO.get();
            case FIENDFYRE -> ModSounds.SPELL_FIENDFYRE.get();
            case SECTUMSEMPRA -> ModSounds.SPELL_SECTUMSEMPRA.get();
            case MORSMORDRE -> ModSounds.SPELL_MORSMORDRE.get();
            case DOLOHOV_CURSE -> ModSounds.SPELL_DOLOHOV_CURSE.get();
            case INCARCEROUS -> ModSounds.SPELL_INCARCEROUS.get();
            case HEAL -> ModSounds.SPELL_LUMOS.get();
            case TENEBRIS_FLIGHT -> ModSounds.SPELL_MORSMORDRE.get();
            case EXPECTO_PATRONUM -> ModSounds.SPELL_EXPECTO_PATRONUM.get();
            case BOMBARDA -> ModSounds.SPELL_CONFRINGO.get(); // Use similar explosive sound
            case DEPULSO -> ModSounds.SPELL_EXPELLIARMUS.get(); // Swift sound
            case LEVICORPUS -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
            case DIFFINDO -> ModSounds.SPELL_SECTUMSEMPRA.get(); // Sharp sound
            case PETRIFICUS_TOTALUS -> ModSounds.SPELL_STUPEFY.get(); // Stun sound
            case AQUA_ERUCTO -> ModSounds.SPELL_GLACIUS.get();
            case OBLIVIATE -> ModSounds.SPELL_REVELIO.get();
            case ASCENDIO -> ModSounds.SPELL_ACCIO.get(); // Whoosh sound
            case CONFUNDO -> ModSounds.SPELL_IMPERIO.get();
            case CAVE_INIMICUM -> ModSounds.SPELL_PROTEGO.get();
            case ALATUS -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
            case SERPENSORTIA -> ModSounds.SPELL_MORSMORDRE.get(); // Dark-ish sounding
            case ARRESTO_MOMENTUM -> ModSounds.SPELL_ACCIO.get();
            case REPARO -> ModSounds.SPELL_REVELIO.get();
            case APPARITION -> ModSounds.SPELL_ACCIO.get(); // placeholder; the real CRACK is played in the channel logic
            // The animating charm shares Wingardium's incantation sound; the grinding stone that sells the
            // effect is played by StatueMultiblock at the statue, not at the wand.
            case PIERTOTUM_LOCOMOTOR -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
        };
    }

    private float scaledSpellDamage(Entity caster, float base) {
        return base * HousePerks.getSpellDamageMultiplier(caster);
    }

    private void triggerSteamCombo(LivingEntity target) {
        target.hurt(target.damageSources().magic(), 2.0f);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        if (target.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.35, 0.25, 0.35, 0.01);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.getX(), target.getY() + 0.8, target.getZ(), 12, 0.25, 0.2, 0.25, 0.01);
        }
    }

    private static void interruptActiveCasting(LivingEntity target) {
        if (target instanceof ServerPlayer serverPlayer) {
            ModEvents.interruptActiveCasting(serverPlayer);
        }
    }
}
