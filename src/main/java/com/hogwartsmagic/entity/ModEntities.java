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
                            .sized(1.6f, 1.9f)
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
                            .sized(3.5f, 3.0f)
                            .clientTrackingRange(8)
                            .build("acromantula"));

    public static final RegistryObject<EntityType<DementorEntity>> DEMENTOR =
            ENTITY_TYPES.register("dementor",
                    () -> EntityType.Builder.<DementorEntity>of(DementorEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 2.5F) // Tall, terrifying figure
                            .clientTrackingRange(64) // Render from very far away
                            .build("dementor"));

    public static final RegistryObject<EntityType<PatronusEntity>> PATRONUS =
            ENTITY_TYPES.register("patronus",
                    () -> EntityType.Builder.<PatronusEntity>of(PatronusEntity::new, MobCategory.MISC)
                            .sized(1.2F, 1.5F) // Stag size
                            .clientTrackingRange(32)
                            .build("patronus"));

    public static final RegistryObject<EntityType<MinatorEntity>> MINATOR =
            ENTITY_TYPES.register("minator",
                    () -> EntityType.Builder.<MinatorEntity>of(MinatorEntity::new, MobCategory.MONSTER)
                            .sized(1.45F, 2.65F)
                            .clientTrackingRange(10)
                            .build("minator"));

    public static final RegistryObject<EntityType<BasiliskEntity>> BASILISK =
            ENTITY_TYPES.register("basilisk",
                    () -> EntityType.Builder.<BasiliskEntity>of(BasiliskEntity::new, MobCategory.MONSTER)
                            .sized(3.4F, 2.6F)
                            .clientTrackingRange(12)
                            .build("basilisk"));

    public static final RegistryObject<EntityType<PetrifiedRemainsEntity>> PETRIFIED_REMAINS =
            ENTITY_TYPES.register("petrified_remains",
                    () -> EntityType.Builder.<PetrifiedRemainsEntity>of(PetrifiedRemainsEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("petrified_remains"));

    public static final RegistryObject<EntityType<HorcruxSoulEntity>> HORCRUX_SOUL =
            ENTITY_TYPES.register("horcrux_soul",
                    () -> EntityType.Builder.<HorcruxSoulEntity>of(HorcruxSoulEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(96)
                            .updateInterval(1)
                            .build("horcrux_soul"));

    public static final RegistryObject<EntityType<LycanthropeVillagerEntity>> LYCANTHROPE_VILLAGER =
            ENTITY_TYPES.register("lycanthrope_villager",
                    () -> EntityType.Builder.<LycanthropeVillagerEntity>of(LycanthropeVillagerEntity::new, MobCategory.CREATURE)
                            .sized(0.65F, 2.05F)
                            .clientTrackingRange(10)
                            .build("lycanthrope_villager"));
}
