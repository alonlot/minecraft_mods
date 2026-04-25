package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.AcromantulaEntity;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.hogwartsmagic.entity.MinatorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.entity.HippogriffEntity;
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
    }
}
