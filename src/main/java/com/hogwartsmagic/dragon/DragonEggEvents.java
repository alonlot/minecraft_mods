package com.hogwartsmagic.dragon;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DragonNestEggBlock;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Taking a dragon egg: every Horntail or Ironbelly within sixty-four blocks turns on the thief, and
 * the player's action bar warns them a dragon has their scent. Creative and spectator players take
 * eggs without a grudge. Wingardium and Diffindo post a real break event, so they count as theft.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class DragonEggEvents {
    private DragonEggEvents() {
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onEggBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !event.getState().is(DragonNestEggBlock.DRAGON_EGGS)
                || !(event.getPlayer() instanceof ServerPlayer thief)
                || !DragonEggRules.raisesGrudge(thief.isCreative(), thief.isSpectator())) {
            return;
        }
        if (alertDragons(level, event.getPos(), thief) > 0) {
            thief.displayClientMessage(Component.translatable("block.hogwartsmagic.dragon_egg.stolen")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Every living dragon (either species, chained or wild) that notices the theft swears vengeance. */
    static int alertDragons(ServerLevel level, BlockPos egg, ServerPlayer thief) {
        Vec3 centre = Vec3.atCenterOf(egg);
        int alerted = 0;
        for (HungarianHorntailEntity dragon : level.getEntitiesOfClass(HungarianHorntailEntity.class,
                new AABB(egg).inflate(DragonEggRules.THEFT_RADIUS))) {
            if (dragon.isAlive() && DragonEggRules.notices(dragon.distanceToSqr(centre))) {
                dragon.swearVengeance(thief);
                alerted++;
            }
        }
        return alerted;
    }
}
