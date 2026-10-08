package com.hogwartsmagic.item;

import com.hogwartsmagic.entity.ThrownBrewEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A cauldron brew made to be thrown, as the vanilla splash potion: the Mandrake Draught with gunpowder, and Living Death. */
public class SplashBrewItem extends Item {
    public enum Kind {
        MANDRAKE("splash_mandrake_draught", 0x5FD3C0),
        LIVING_DEATH("draught_of_living_death", 0xD8E2EA);

        private final String id;
        private final int color;

        Kind(String id, int color) {
            this.id = id;
            this.color = color;
        }

        /** The item registry path. */
        public String id() {
            return id;
        }

        /** RGB tint of the bottle and of the splash particles. */
        public int color() {
            return color;
        }
    }

    private final Kind kind;

    public SplashBrewItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownBrewEntity thrown = new ThrownBrewEntity(level, player);
            thrown.setItem(stack);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(thrown);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic." + kind.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
