package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.AcromantulaEntity;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.FlyingMobEntity;
import com.hogwartsmagic.entity.HippogriffEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MinatorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.entity.TrollEntity;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.OWL.get(), OwlEntity.createAttributes().build());
        event.put(ModEntities.HIPPOGRIFF.get(), HippogriffEntity.createAttributes().build());
        event.put(ModEntities.UNICORN.get(), com.hogwartsmagic.entity.UnicornEntity.createAttributes().build());
        event.put(ModEntities.WIZARD.get(), WizardEntity.createAttributes().build());
        event.put(ModEntities.VOLDEMORT.get(), VoldemortEntity.createAttributes().build());
        event.put(ModEntities.STUDENT.get(), StudentEntity.createAttributes().build());
        event.put(ModEntities.FLYING_MOB.get(), FlyingMobEntity.createAttributes().build());
        event.put(ModEntities.ACROMANTULA.get(), AcromantulaEntity.createAttributes().build());
        event.put(ModEntities.DEMENTOR.get(), DementorEntity.createAttributes().build());
        event.put(ModEntities.PATRONUS.get(), PatronusEntity.createAttributes().build());
        event.put(ModEntities.MINATOR.get(), MinatorEntity.createAttributes().build());
        event.put(ModEntities.BASILISK.get(), BasiliskEntity.createAttributes().build());
        event.put(ModEntities.TROLL.get(), TrollEntity.createAttributes().build());
        event.put(ModEntities.GIANT.get(), com.hogwartsmagic.entity.GiantEntity.createAttributes().build());
        event.put(ModEntities.HUNGARIAN_HORNTAIL.get(), HungarianHorntailEntity.createAttributes().build());
        event.put(ModEntities.MEMORY_HORNTAIL.get(), HungarianHorntailEntity.createAttributes().build());
        event.put(ModEntities.UKRAINIAN_IRONBELLY.get(),
                UkrainianIronbellyEntity.createIronbellyAttributes().build());
        event.put(ModEntities.LYCANTHROPE_VILLAGER.get(), LycanthropeVillagerEntity.createAttributes().build());
        event.put(ModEntities.GOLDEN_SNITCH.get(), com.hogwartsmagic.entity.GoldenSnitchEntity.createAttributes().build());
        event.put(ModEntities.GOBLIN.get(), com.hogwartsmagic.entity.GoblinEntity.createAttributes().build());
        event.put(ModEntities.GOBLIN_GUARD.get(), com.hogwartsmagic.entity.GoblinGuardEntity.createAttributes().build());
        event.put(ModEntities.HOUSE_ELF.get(), com.hogwartsmagic.entity.HouseElfEntity.createAttributes().build());
        event.put(ModEntities.MERPERSON.get(), com.hogwartsmagic.entity.MerpersonEntity.createAttributes().build());
        event.put(ModEntities.GRINDYLOW.get(), com.hogwartsmagic.entity.GrindylowEntity.createAttributes().build());
        event.put(ModEntities.INFERIUS.get(), com.hogwartsmagic.entity.InferiusEntity.createAttributes().build());
        event.put(ModEntities.BOGGART.get(), com.hogwartsmagic.entity.boggart.BoggartEntity.createAttributes().build());
        event.put(ModEntities.WHOMPING_WILLOW.get(), com.hogwartsmagic.entity.WhompingWillowEntity.createAttributes().build());
        event.put(ModEntities.LIVING_STATUE.get(), com.hogwartsmagic.entity.LivingStatueEntity.createAttributes().build());
        event.put(ModEntities.SNAKE.get(), com.hogwartsmagic.entity.SnakeEntity.createAttributes().build());
        event.put(ModEntities.PENSIEVE_BODY.get(), com.hogwartsmagic.pensieve.PensieveBodyEntity.createAttributes().build());
    }
}
