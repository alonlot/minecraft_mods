package com.hogwartsmagic.item;

import com.hogwartsmagic.spell.support.HealSpell;
import com.hogwartsmagic.spell.support.SupportMana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A potion brewed in the Wizard Cauldron. Not a vanilla Potion entry: the drink calls the mod's own helpers (the
 * Bleeding cure, mana), which vanilla potion contents cannot do.
 */
public class BrewedPotionItem extends Item {
    public enum Kind {
        WIGGENWELD("wiggenweld_potion", 0x4CC38A),
        PEPPERUP("pepperup_potion", 0xE8322A),
        FAILED("failed_potion", 0x6B5A45);

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

        /** RGB tint of the bottle's liquid. */
        public int color() {
            return color;
        }
    }

    /** Regeneration IV heals 1 HP every 6 ticks: 48 ticks = 8 HP over 2.4 s. */
    public static final int WIGGENWELD_REGEN_TICKS = 48;
    public static final int WIGGENWELD_REGEN_AMPLIFIER = 3;
    public static final float PEPPERUP_MANA = 40.0f;
    public static final int PEPPERUP_FIRE_RESISTANCE_TICKS = 20 * 30;
    public static final int FAILED_NAUSEA_TICKS = 20 * 10;
    public static final int FAILED_POISON_TICKS = 20 * 3;

    private final Kind kind;

    public BrewedPotionItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker) {
        if (!level.isClientSide) {
            applyDrink(level, drinker);
        }
        if (drinker instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    return new ItemStack(Items.GLASS_BOTTLE);
                }
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
                }
            }
        }
        drinker.gameEvent(GameEvent.DRINK);
        return stack;
    }

    private void applyDrink(Level level, LivingEntity drinker) {
        if (kind == Kind.WIGGENWELD) {
            // The only removal path for Bleeding that the drink guard lets through.
            HealSpell.cureBleeding(drinker);
            drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WIGGENWELD_REGEN_TICKS, WIGGENWELD_REGEN_AMPLIFIER));
        } else if (kind == Kind.PEPPERUP) {
            if (drinker instanceof ServerPlayer player) {
                SupportMana.add(player, PEPPERUP_MANA);
            }
            drinker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, PEPPERUP_FIRE_RESISTANCE_TICKS, 0));
            if (level instanceof ServerLevel server) {
                // Steam from the ears.
                server.sendParticles(ParticleTypes.CLOUD, drinker.getX(), drinker.getEyeY() + 0.1, drinker.getZ(),
                        14, 0.3, 0.05, 0.3, 0.03);
                server.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.5f, 1.6f);
            }
        } else {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FAILED_NAUSEA_TICKS, 0));
            drinker.addEffect(new MobEffectInstance(MobEffects.POISON, FAILED_POISON_TICKS, 0));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic." + kind.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
