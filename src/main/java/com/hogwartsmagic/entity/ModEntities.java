package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<EntityType<SpellBeamEntity>> SPELL_BEAM =
            ENTITY_TYPES.register("spell_beam",
                    () -> EntityType.Builder.<SpellBeamEntity>of(SpellBeamEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("spell_beam"));

    public static final RegistryObject<EntityType<BroomEntity>> BROOM =
            ENTITY_TYPES.register("broom",
                    () -> EntityType.Builder.<BroomEntity>of(BroomEntity::new, MobCategory.MISC)
                            .sized(0.7f, 0.7f)
                            .clientTrackingRange(10)
                            .updateInterval(1) // send updates every tick for smoother client-side interpolation
                            .build("broom"));

    /**
     * The Gringotts cart. Tracked every tick like the broom, because at 31 m/s down a banked
     * spiral anything less is visibly steppy.
     */
    /**
     * The Ironbelly. Bigger than the Horntail it subclasses, and it never leaves the ground until
     * somebody frees it.
     */
    public static final RegistryObject<EntityType<UkrainianIronbellyEntity>> UKRAINIAN_IRONBELLY =
            ENTITY_TYPES.register("ukrainian_ironbelly",
                    () -> EntityType.Builder.<UkrainianIronbellyEntity>of(
                                    UkrainianIronbellyEntity::new, MobCategory.MONSTER)
                            .sized(3.6f, 3.8f)
                            .clientTrackingRange(16)
                            .fireImmune()
                            .build("ukrainian_ironbelly"));

    public static final RegistryObject<EntityType<GringottsCartEntity>> GRINGOTTS_CART =
            ENTITY_TYPES.register("gringotts_cart",
                    () -> EntityType.Builder.<GringottsCartEntity>of(GringottsCartEntity::new, MobCategory.MISC)
                            .sized(1.2f, 0.9f)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .fireImmune()
                            .build("gringotts_cart"));

    public static final RegistryObject<EntityType<FiendfyreEntity>> FIENDFYRE =
            ENTITY_TYPES.register("fiendfyre",
                    () -> EntityType.Builder.<FiendfyreEntity>of(FiendfyreEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("fiendfyre"));

    public static final RegistryObject<EntityType<FiendfyreSerpentEntity>> FIENDFYRE_SERPENT =
            ENTITY_TYPES.register("fiendfyre_serpent",
                    () -> EntityType.Builder.<FiendfyreSerpentEntity>of(FiendfyreSerpentEntity::new, MobCategory.MISC)
                            .sized(0.7f, 0.7f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("fiendfyre_serpent"));

    public static final RegistryObject<EntityType<WizardEntity>> WIZARD =
            ENTITY_TYPES.register("wizard",
                    () -> EntityType.Builder.<WizardEntity>of(WizardEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(8)
                            .build("wizard"));

    public static final RegistryObject<EntityType<VoldemortEntity>> VOLDEMORT =
            ENTITY_TYPES.register("voldemort",
                    () -> EntityType.Builder.<VoldemortEntity>of(VoldemortEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("voldemort"));

    public static final RegistryObject<EntityType<StudentEntity>> STUDENT =
            ENTITY_TYPES.register("student",
                    () -> EntityType.Builder.<StudentEntity>of(StudentEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(8)
                            .build("student"));

    public static final RegistryObject<EntityType<DarkMarkEntity>> DARK_MARK =
            ENTITY_TYPES.register("dark_mark",
                    () -> EntityType.Builder.<DarkMarkEntity>of(DarkMarkEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(96)
                            .build("dark_mark"));

    public static final RegistryObject<EntityType<VoldemortSummoningEntity>> VOLDEMORT_SUMMONING =
            ENTITY_TYPES.register("voldemort_summoning",
                    () -> EntityType.Builder.<VoldemortSummoningEntity>of(VoldemortSummoningEntity::new, MobCategory.MISC)
                            .sized(0.2f, 0.2f)
                            .clientTrackingRange(64)
                            .build("voldemort_summoning"));

    public static final RegistryObject<EntityType<OwlEntity>> OWL =
            ENTITY_TYPES.register("owl",
                    () -> EntityType.Builder.<OwlEntity>of(OwlEntity::new, MobCategory.CREATURE)
                            .sized(0.5f, 0.9f)
                            .clientTrackingRange(10)
                            .build("owl"));

    public static final RegistryObject<EntityType<HippogriffEntity>> HIPPOGRIFF =
            ENTITY_TYPES.register("hippogriff",
                    () -> EntityType.Builder.<HippogriffEntity>of(HippogriffEntity::new, MobCategory.CREATURE)
                            // The rebuilt model is film-scale: taller at the head than the old one.
                            .sized(1.7f, 2.2f)
                            .clientTrackingRange(10)
                            .build("hippogriff"));

    public static final RegistryObject<EntityType<FlyingMobEntity>> FLYING_MOB =
            ENTITY_TYPES.register("flying_mob",
                    () -> EntityType.Builder.<FlyingMobEntity>of(FlyingMobEntity::new, MobCategory.CREATURE)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .build("flying_mob"));
    public static final RegistryObject<EntityType<AcromantulaEntity>> ACROMANTULA =
            ENTITY_TYPES.register("acromantula",
                    () -> EntityType.Builder.<AcromantulaEntity>of(AcromantulaEntity::new, MobCategory.MONSTER)
                            // Base dims for a scale-1.0 spider. The legs splay much wider than
                            // the body, but only the body should block player melee, so we keep
                            // the hitbox tight to the body. AcromantulaEntity#getDimensions
                            // multiplies this by the per-instance spider scale so tiny spiders
                            // have a tiny hitbox and huge ones grow appropriately.
                            .sized(1.6f, 1.2f)
                            .clientTrackingRange(8)
                            .build("acromantula"));

    public static final RegistryObject<EntityType<DementorEntity>> DEMENTOR =
            ENTITY_TYPES.register("dementor",
                    () -> EntityType.Builder.<DementorEntity>of(DementorEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 3.0F) // Roughly three metres, per the books
                            .clientTrackingRange(64) // Render from very far away
                            .build("dementor"));

    public static final RegistryObject<EntityType<PatronusEntity>> PATRONUS =
            ENTITY_TYPES.register("patronus",
                    () -> EntityType.Builder.<PatronusEntity>of(PatronusEntity::new, MobCategory.MISC)
                            .sized(1.2F, 1.5F) // Stag size
                            .clientTrackingRange(32)
                            .build("patronus"));

    // A block lifted by Wingardium Leviosa (Spell Mastery Phase 7b). The server steers it every tick,
    // so it sends every tick too; it is not living and has no attributes.
    public static final RegistryObject<EntityType<CarriedBlockEntity>> CARRIED_BLOCK =
            ENTITY_TYPES.register("carried_block",
                    () -> EntityType.Builder.<CarriedBlockEntity>of(CarriedBlockEntity::new, MobCategory.MISC)
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("carried_block"));

    public static final RegistryObject<EntityType<MinatorEntity>> MINATOR =
            ENTITY_TYPES.register("minator",
                    () -> EntityType.Builder.<MinatorEntity>of(MinatorEntity::new, MobCategory.MONSTER)
                            .sized(1.45F, 2.65F)
                            .clientTrackingRange(10)
                            .build("minator"));

    public static final RegistryObject<EntityType<BasiliskEntity>> BASILISK =
            ENTITY_TYPES.register("basilisk",
                    () -> EntityType.Builder.<BasiliskEntity>of(BasiliskEntity::new, MobCategory.MONSTER)
                            .sized(3.5F, 3.0F)
                            .clientTrackingRange(16)
                            .build("basilisk"));

    public static final RegistryObject<EntityType<BasiliskVenomEntity>> BASILISK_VENOM =
            ENTITY_TYPES.register("basilisk_venom",
                    () -> EntityType.Builder.<BasiliskVenomEntity>of(BasiliskVenomEntity::new, MobCategory.MISC)
                            .sized(0.4F, 0.4F)
                            .clientTrackingRange(6)
                            .updateInterval(10)
                            .build("basilisk_venom"));

    public static final RegistryObject<EntityType<TrollEntity>> TROLL =
            ENTITY_TYPES.register("troll",
                    () -> EntityType.Builder.<TrollEntity>of(TrollEntity::new, MobCategory.MONSTER)
                            .sized(1.75F, 3.25F)
                            .clientTrackingRange(12)
                            .build("troll"));

    // Height matches the model rendered at GiantRenderer.MODEL_SCALE. The hitbox
    // is kept tight to the torso; the arms overhang it on both sides.
    public static final RegistryObject<EntityType<GiantEntity>> GIANT =
            ENTITY_TYPES.register("giant",
                    () -> EntityType.Builder.<GiantEntity>of(GiantEntity::new, MobCategory.MONSTER)
                            .sized(2.2F, 5.5F)
                            .clientTrackingRange(16)
                            .build("giant"));

    public static final RegistryObject<EntityType<HungarianHorntailEntity>> HUNGARIAN_HORNTAIL =
            ENTITY_TYPES.register("hungarian_horntail",
                    () -> EntityType.Builder.<HungarianHorntailEntity>of(HungarianHorntailEntity::new, MobCategory.MONSTER)
                            .sized(3.0F, 3.2F)
                            .clientTrackingRange(16)
                            .fireImmune()
                            .build("hungarian_horntail"));

    public static final RegistryObject<EntityType<PetrifiedRemainsEntity>> PETRIFIED_REMAINS =
            ENTITY_TYPES.register("petrified_remains",
                    () -> EntityType.Builder.<PetrifiedRemainsEntity>of(PetrifiedRemainsEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("petrified_remains"));

    public static final RegistryObject<EntityType<ThrownBrewEntity>> THROWN_BREW =
            ENTITY_TYPES.register("thrown_brew",
                    () -> EntityType.Builder.<ThrownBrewEntity>of(ThrownBrewEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_brew"));

    public static final RegistryObject<EntityType<HorcruxSoulEntity>> HORCRUX_SOUL =
            ENTITY_TYPES.register("horcrux_soul",
                    () -> EntityType.Builder.<HorcruxSoulEntity>of(HorcruxSoulEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(96)
                            .updateInterval(1)
                            .build("horcrux_soul"));

    public static final RegistryObject<EntityType<GoldenSnitchEntity>> GOLDEN_SNITCH =
            ENTITY_TYPES.register("golden_snitch",
                    () -> EntityType.Builder.<GoldenSnitchEntity>of(GoldenSnitchEntity::new, MobCategory.MISC)
                            .sized(0.35F, 0.35F)
                            .clientTrackingRange(12)
                            .updateInterval(1) // darts fast; interpolate every tick
                            .build("golden_snitch"));

    public static final RegistryObject<EntityType<LycanthropeVillagerEntity>> LYCANTHROPE_VILLAGER =
            ENTITY_TYPES.register("lycanthrope_villager",
                    () -> EntityType.Builder.<LycanthropeVillagerEntity>of(LycanthropeVillagerEntity::new, MobCategory.CREATURE)
                            .sized(0.65F, 2.05F)
                            .clientTrackingRange(10)
                            .build("lycanthrope_villager"));

    // Height matches the goblin model rendered at GoblinRenderer.MODEL_SCALE.
    public static final RegistryObject<EntityType<GoblinEntity>> GOBLIN =
            ENTITY_TYPES.register("goblin",
                    () -> EntityType.Builder.<GoblinEntity>of(GoblinEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.25F)
                            .clientTrackingRange(10)
                            .build("goblin"));

    // Height matches the model rendered at HouseElfRenderer.MODEL_SCALE.
    public static final RegistryObject<EntityType<HouseElfEntity>> HOUSE_ELF =
            ENTITY_TYPES.register("house_elf",
                    () -> EntityType.Builder.<HouseElfEntity>of(HouseElfEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 0.85F)
                            .clientTrackingRange(10)
                            .build("house_elf"));

    // Roughly three blocks nose to fluke, but the tail curls under when it is
    // treading water, so the box is kept to the head, torso and upper tail.
    public static final RegistryObject<EntityType<MerpersonEntity>> MERPERSON =
            ENTITY_TYPES.register("merperson",
                    () -> EntityType.Builder.<MerpersonEntity>of(MerpersonEntity::new, MobCategory.WATER_CREATURE)
                            .sized(0.9F, 2.2F)
                            .clientTrackingRange(10)
                            .build("merperson"));

    // Small enough to hide in a weed bed. The model renders at 0.7.
    public static final RegistryObject<EntityType<GrindylowEntity>> GRINDYLOW =
            ENTITY_TYPES.register("grindylow",
                    () -> EntityType.Builder.<GrindylowEntity>of(GrindylowEntity::new, MobCategory.MONSTER)
                            .sized(0.7F, 1.0F)
                            .clientTrackingRange(8)
                            .build("grindylow"));

    // Player-sized box whatever shape it wears; the renderer draws the borrowed form, the box stays put.
    public static final RegistryObject<EntityType<com.hogwartsmagic.entity.boggart.BoggartEntity>> BOGGART =
            ENTITY_TYPES.register("boggart",
                    () -> EntityType.Builder.<com.hogwartsmagic.entity.boggart.BoggartEntity>of(
                                    com.hogwartsmagic.entity.boggart.BoggartEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("boggart"));

    public static final RegistryObject<EntityType<GoblinGuardEntity>> GOBLIN_GUARD =
            ENTITY_TYPES.register("goblin_guard",
                    () -> EntityType.Builder.<GoblinGuardEntity>of(GoblinGuardEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.25F)
                            .clientTrackingRange(10)
                            .build("goblin_guard"));

    // The box is the trunk only. The branches reach seven blocks past it and are handled by the
    // attack arcs, not by the hitbox -- see WhompingWillowEntity.BRANCH_RANGE. Tracking range is
    // generous because it is a landmark you are meant to see before you are in reach of it.
    public static final RegistryObject<EntityType<WhompingWillowEntity>> WHOMPING_WILLOW =
            ENTITY_TYPES.register("whomping_willow",
                    () -> EntityType.Builder.<WhompingWillowEntity>of(WhompingWillowEntity::new, MobCategory.MONSTER)
                            .sized(3.0F, 9.0F)
                            .clientTrackingRange(16)
                            .build("whomping_willow"));

    // The box is the armour: 1.2 wide across the pauldrons, 2.9 tall to the crest. The halberd
    // stands about half a block proud of that and is deliberately left outside the box -- widening
    // it to cover the weapon would mean arrows that pass a foot over the helm still register as
    // hits. Tracking range matches the troll's: it has to be rendered before you are inside
    // WAKE_RADIUS, or the rise happens off-screen and the tell is wasted.
    public static final RegistryObject<EntityType<LivingStatueEntity>> LIVING_STATUE =
            ENTITY_TYPES.register("living_statue",
                    () -> EntityType.Builder.<LivingStatueEntity>of(LivingStatueEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 2.9F)
                            .clientTrackingRange(12)
                            // Stone does not burn, and a guardian standing over a ruin's hearth or
                            // in a lava-lit chamber would otherwise cook itself to death unattended.
                            .fireImmune()
                            .build("living_statue"));
}
