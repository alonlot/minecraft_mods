package com.hogwartsmagic.item;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Clankers: the noisemakers the goblins use to keep an Ironbelly back.
 *
 * <p>Ringing them cows any Ironbelly within twenty blocks, and it is not on a cooldown - it is on a
 * cost. <b>The same noise brings every goblin guard within thirty-two</b>, because you are standing
 * in a bank making a colossal racket, and that is a far better limiter than an arbitrary timer:
 * camp the button and the whole undercroft comes to you.
 *
 * <p>Ringing them at a Hungarian Horntail enrages it. It was never trained by goblins; the noise
 * just makes it furious.
 */
public class ClankersItem extends Item {

    /** How far the cowing carries. */
    private static final double COWER_RANGE = 20.0D;
    /** How far the noise carries to everything else. Further, on purpose. */
    private static final double NOISE_RANGE = 32.0D;
    private static final int COOLDOWN = 60;

    public ClankersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        server.playSound(null, player.blockPosition(), ModSounds.CLANKERS_RING.get(),
                SoundSource.PLAYERS, 2.4F, 1.0F);

        if (UkrainianIronbellyEntity.cowerNearby(server, player.position(), COWER_RANGE) > 0
                && player instanceof net.minecraft.server.level.ServerPlayer ringer) {
            com.hogwartsmagic.util.ModAdvancements.award(ringer, "ironbelly_cowed");
        }

        // Everything that is not an Ironbelly simply hears a great deal of noise.
        AABB earshot = new AABB(player.blockPosition()).inflate(NOISE_RANGE);
        for (UkrainianIronbellyEntity dragon
                : server.getEntitiesOfClass(UkrainianIronbellyEntity.class, earshot)) {
            if (player.distanceTo(dragon) > COWER_RANGE) {
                // Too far to cow, near enough to interest. Ringing at the wrong range calls it.
                dragon.heard(player.position());
            }
        }
        for (HungarianHorntailEntity other
                : server.getEntitiesOfClass(HungarianHorntailEntity.class, earshot)) {
            if (!(other instanceof UkrainianIronbellyEntity)) {
                other.setTarget(player);
            }
        }
        List<GoblinGuardEntity> guards =
                server.getEntitiesOfClass(GoblinGuardEntity.class, earshot);
        for (GoblinGuardEntity guard : guards) {
            guard.setTarget(player);
        }
        if (!guards.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.hogwartsmagic.clankers.heard")
                            .withStyle(ChatFormatting.RED), true);
        }

        stack.hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("item.hogwartsmagic.clankers.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
