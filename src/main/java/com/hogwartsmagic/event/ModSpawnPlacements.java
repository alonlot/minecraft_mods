package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.AcromantulaEntity;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.hogwartsmagic.entity.MinatorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.entity.HippogriffEntity;
import com.hogwartsmagic.entity.TrollEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModSpawnPlacements {

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                ModEntities.ACROMANTULA.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AcromantulaEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.MINATOR.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MinatorEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.BASILISK.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BasiliskEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.TROLL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                TrollEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.LYCANTHROPE_VILLAGER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LycanthropeVillagerEntity::checkSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.OWL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                OwlEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.HIPPOGRIFF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HippogriffEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.GOBLIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.hogwartsmagic.entity.GoblinEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.HOUSE_ELF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.hogwartsmagic.entity.HouseElfEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        // Giants keep no hours, so this deliberately skips the darkness check that
        // the other monsters use -- they want headroom, not night.
        event.register(
                ModEntities.GIANT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.hogwartsmagic.entity.GiantEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        // A Dementor flies, but it still needs solid ground to be placed on -- it drifts up from
        // wherever the spawner drops it. Registered so the Dementor Fen can spawn them naturally
        // instead of the insomnia event being the only source in the game.
        event.register(
                ModEntities.DEMENTOR.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DementorEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        // Merpeople are placed in the water column itself rather than on the
        // lakebed, and their own rule keeps them out of the shallows.
        event.register(
                ModEntities.MERPERSON.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.hogwartsmagic.entity.MerpersonEntity::checkMerpersonSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(
                ModEntities.HUNGARIAN_HORNTAIL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HungarianHorntailEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
