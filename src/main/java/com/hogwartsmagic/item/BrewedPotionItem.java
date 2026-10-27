package com.hogwartsmagic.item;

import com.hogwartsmagic.spell.support.HealSpell;
import com.hogwartsmagic.spell.support.SupportMana;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
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
        FAILED("failed_potion", 0x6B5A45),
        DITTANY("essence_of_dittany", 0xC9E3A6),
        FELIX("felix_felicis", 0xFFD966),
        MANDRAKE("mandrake_draught", 0x5FD3C0),
        REMORSE("potion_of_remorse", 0x8FA8C8);

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
            applyDrink(stack, level, drinker);
        }
        if (drinker instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
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

    private void applyDrink(ItemStack stack, Level level, LivingEntity drinker) {
        // Read before the stack shrinks; a bottle without the component is Standard.
        int grade = stack.getOrDefault(com.hogwartsmagic.ExampleMod.BREW_QUALITY.get(), 0);
        if (kind == Kind.WIGGENWELD) {
            // The only removal path for Bleeding that the drink guard lets through.
            HealSpell.cureBleeding(drinker);
            drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                    Math.round(com.hogwartsmagic.brewing.PotionRules.qualityAmount(WIGGENWELD_REGEN_TICKS, grade)), WIGGENWELD_REGEN_AMPLIFIER));
        } else if (kind == Kind.PEPPERUP) {
            if (drinker instanceof ServerPlayer player) {
                SupportMana.add(player, com.hogwartsmagic.brewing.PotionRules.qualityAmount(PEPPERUP_MANA, grade));
            }
            drinker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                    com.hogwartsmagic.brewing.PotionRules.qualityTicks(PEPPERUP_FIRE_RESISTANCE_TICKS, grade), 0));
            if (level instanceof ServerLevel server) {
                // Steam from the ears.
                server.sendParticles(ParticleTypes.CLOUD, drinker.getX(), drinker.getEyeY() + 0.1, drinker.getZ(),
                        14, 0.3, 0.05, 0.3, 0.03);
                server.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.5f, 1.6f);
            }
        } else if (kind == Kind.DITTANY) {
            // The only removal path for Cursed Wound that the drink guard lets through.
            com.hogwartsmagic.spell.combat.CombatEffects.cureCursedWound(drinker);
            drinker.heal(com.hogwartsmagic.brewing.PotionRules.qualityAmount(com.hogwartsmagic.brewing.PotionRules.DITTANY_HEAL, grade));
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, drinker.getX(), drinker.getY(0.5), drinker.getZ(),
                        8, 0.3, 0.4, 0.3, 0.0);
            }
        } else if (kind == Kind.MANDRAKE) {
            // A free drinker: the basilisk's hold is gone and cannot build for 8 seconds.
            com.hogwartsmagic.petrification.Petrification.wardGaze(drinker,
                    com.hogwartsmagic.brewing.PotionRules.qualityTicks(com.hogwartsmagic.brewing.PotionRules.MANDRAKE_GAZE_WARD_TICKS, grade));
            if (drinker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.drink"), true);
            }
        } else if (kind == Kind.FELIX) {
            drinker.addEffect(new MobEffectInstance(MobEffects.LUCK,
                    com.hogwartsmagic.brewing.PotionRules.qualityTicks(com.hogwartsmagic.brewing.PotionRules.FELIX_TICKS, grade),
                    com.hogwartsmagic.brewing.PotionRules.FELIX_LUCK_AMPLIFIER));
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.WAX_ON, drinker.getX(), drinker.getY(0.6), drinker.getZ(),
                        16, 0.35, 0.5, 0.35, 0.02);
                server.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.0f, 1.6f);
            }
            if (drinker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.felix.drink"), true);
            }
        } else if (kind == Kind.REMORSE) {
            drinkRemorse(drinker, grade);
        } else {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FAILED_NAUSEA_TICKS, 0));
            drinker.addEffect(new MobEffectInstance(MobEffects.POISON, FAILED_POISON_TICKS, 0));
        }
    }

    /** Remorse always hurts; whether the soul is lightened depends on the Darkness and the play-time window. */
    private static void drinkRemorse(LivingEntity drinker, int grade) {
        float damage = com.hogwartsmagic.brewing.PotionRules.remorseDamage(drinker.getHealth());
        if (damage > 0.0f) {
            drinker.hurt(drinker.damageSources().magic(), damage);
        }
        drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, com.hogwartsmagic.brewing.PotionRules.REMORSE_NAUSEA_TICKS, 0));
        drinker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, com.hogwartsmagic.brewing.PotionRules.REMORSE_WEAKNESS_TICKS, 0));
        if (drinker instanceof ServerPlayer player) {
            com.hogwartsmagic.spell.dark.DarkDeeds.RemorseResult result =
                    com.hogwartsmagic.spell.dark.DarkDeeds.onRemorse(player,
                            com.hogwartsmagic.brewing.PotionRules.qualityAmount(com.hogwartsmagic.brewing.PotionRules.REMORSE_DARKNESS, grade));
            String key = result == com.hogwartsmagic.spell.dark.DarkDeeds.RemorseResult.LIGHTENED ? "lightened"
                    : result == com.hogwartsmagic.spell.dark.DarkDeeds.RemorseResult.NOTHING_TO_REGRET ? "nothing"
                    : result == com.hogwartsmagic.spell.dark.DarkDeeds.RemorseResult.SPENT ? "spent" : "off";
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.remorse." + key), true);
        }
    }

    /** A Mandrake Draught used on a player turned to stone brings them back (one dose). */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                                      InteractionHand hand) {
        if (kind != Kind.MANDRAKE || !com.hogwartsmagic.petrification.Petrification.isPetrified(target)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (target instanceof ServerPlayer stone && com.hogwartsmagic.petrification.Petrification.cure(stone, player)) {
            useUpDose(player, hand, stack);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.PASS; // nothing happened on the server
    }

    /**
     * One dose used on someone else: the bottle comes back (not in creative). A last bottle is swapped for the empty
     * one without shrinking it first: vanilla Player.interactOn wipes the hand when the original stack ends up empty.
     */
    public static void useUpDose(Player player, InteractionHand hand, ItemStack stack) {
        if (player.hasInfiniteMaterials()) {
            return;
        }
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.getCount() <= 1) {
            player.setItemInHand(hand, bottle);
            return;
        }
        stack.shrink(1);
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
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
        int quality = stack.getOrDefault(com.hogwartsmagic.ExampleMod.BREW_QUALITY.get(), 0);
        if (quality > 0) {
            tooltip.add(Component.translatable(com.hogwartsmagic.brewing.BrewQuality.langKey(quality))
                    .withStyle(quality >= com.hogwartsmagic.brewing.BrewQuality.EXCEPTIONAL ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        }
    }
}
