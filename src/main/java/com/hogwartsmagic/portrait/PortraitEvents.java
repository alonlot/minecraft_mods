package com.hogwartsmagic.portrait;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells nearby portraits about the loud things that happen to the world: explosions (within 20 blocks) and
 * lightning (within 96). Common code, server side only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortraitEvents {
    private static final double EXPLOSION_RANGE = 20.0;
    private static final double LIGHTNING_RANGE = 96.0;

    private PortraitEvents() {
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            notifyPortraits(level, event.getExplosion().center(), EXPLOSION_RANGE, FatLadyLines.Reaction.EXPLOSION);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt bolt && event.getLevel() instanceof ServerLevel level) {
            notifyPortraits(level, bolt.position(), LIGHTNING_RANGE, FatLadyLines.Reaction.THUNDER);
        }
    }

    private static void notifyPortraits(ServerLevel level, Vec3 at, double range, FatLadyLines.Reaction reaction) {
        AABB area = AABB.ofSize(at, range * 2.0, range * 2.0, range * 2.0);
        for (FatLadyPortraitEntity portrait : level.getEntitiesOfClass(FatLadyPortraitEntity.class, area,
                p -> p.position().distanceToSqr(at) <= range * range)) {
            portrait.trigger(reaction, null);
        }
    }
}
