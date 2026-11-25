package com.hogwartsmagic.cosy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The hearth pulse: while lit, everyone nearby is warmed every {@link WarmthRules#PULSE_TICKS}. */
public class HearthBlockEntity extends BlockEntity {
    private int pulse;

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        super(CosyHome.HEARTH_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HearthBlockEntity hearth) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!state.getValue(CampfireBlock.LIT)) {
            hearth.pulse = 0;
            return;
        }
        if (++hearth.pulse >= WarmthRules.PULSE_TICKS) {
            hearth.pulse = 0;
            warmPlayers(serverLevel, pos);
        }
    }

    /** Gives Warmed to every player within reach of a fire at {@code pos}. Shared with the Floo fireplace. */
    public static void warmPlayers(ServerLevel level, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        double reach = WarmthRules.RADIUS;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(reach))) {
            if (player.isSpectator() || player.distanceToSqr(centre) > reach * reach) {
                continue;
            }
            player.addEffect(new MobEffectInstance(CosyHome.WARMED.getHolder().get(), WarmthRules.WARMED_TICKS, 0,
                    false, false, true));
        }
    }
}
