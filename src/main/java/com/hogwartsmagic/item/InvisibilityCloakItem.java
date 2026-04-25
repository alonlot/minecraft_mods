package com.hogwartsmagic.item;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class InvisibilityCloakItem extends Item {
    public static final int TRANSITION_TICKS = 10;
    public static final String TAG_CLOAK_ACTIVE_UNTIL = "CloakActiveUntil";
    public static final String TAG_CLOAK_REVEAL_UNTIL = "CloakRevealUntil";
    public static final String TAG_CLOAK_COOLDOWN_UNTIL = "CloakCooldownUntil";
    public static final String TAG_CLOAK_EFFECT_APPLIED = "CloakEffectApplied";
    public static final String TAG_CLOAK_TRANSITION_UNTIL = "CloakTransitionUntil";
    public static final String TAG_CLOAK_TRANSITION_MODE = "CloakTransitionMode";
    public static final int TRANSITION_MODE_NONE = 0;
    public static final int TRANSITION_MODE_HIDE = 1;
    public static final int TRANSITION_MODE_SHOW = 2;

    public InvisibilityCloakItem(Properties properties) {
        super(properties.stacksTo(1).durability(192));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long gameTime = level.getGameTime();
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        boolean cloakActive = isCloakActive(modData, gameTime);

        if (!level.isClientSide && cloakActive && player instanceof ServerPlayer serverPlayer) {
            deactivate(serverPlayer);
            level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 0.9f);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        if (isOnCloakCooldown(modData, gameTime)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.invisibility_cloak.cooldown"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            activate(serverPlayer, stack);
            level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.15f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void activate(ServerPlayer player, ItemStack stack) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long now = player.level().getGameTime();
        modData.putLong(TAG_CLOAK_ACTIVE_UNTIL, now + Config.getInvisibilityCloakDurationTicks());
        modData.putLong(TAG_CLOAK_REVEAL_UNTIL, 0L);
        modData.remove(TAG_CLOAK_COOLDOWN_UNTIL);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);

        beginHideTransition(player);
        if (!player.getAbilities().instabuild) {
            damageCloak(stack, player);
        }
    }

    private static void deactivate(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        startCooldown(player, modData);
        modData.remove(TAG_CLOAK_ACTIVE_UNTIL);
        modData.remove(TAG_CLOAK_REVEAL_UNTIL);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);

        if (modData.getBoolean(TAG_CLOAK_EFFECT_APPLIED)) {
            beginShowTransition(player);
            return;
        }

        player.setInvisible(false);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        modData.putInt(TAG_CLOAK_TRANSITION_MODE, TRANSITION_MODE_NONE);
        modData.putLong(TAG_CLOAK_TRANSITION_UNTIL, 0L);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
    }

    public static void startCooldown(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {
        int cooldownTicks = Config.getInvisibilityCloakCooldownTicks();
        modData.putLong(TAG_CLOAK_COOLDOWN_UNTIL, player.level().getGameTime() + cooldownTicks);
        player.getCooldowns().addCooldown(ExampleMod.INVISIBILITY_CLOAK.get(), cooldownTicks);
    }

    private static void damageCloak(ItemStack stack, Player player) {
        int durabilityCost = Math.max(1, Config.getInvisibilityCloakDurabilityCost());
        int nextDamage = stack.getDamageValue() + durabilityCost;
        if (nextDamage >= stack.getMaxDamage()) {
            stack.shrink(1);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
        } else {
            stack.setDamageValue(nextDamage);
        }
    }

    public static void applyStealthEffect(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        player.setInvisible(true);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, true);
        modData.putInt(TAG_CLOAK_TRANSITION_MODE, TRANSITION_MODE_NONE);
        modData.putLong(TAG_CLOAK_TRANSITION_UNTIL, 0L);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
    }

    public static void clearStealthEffect(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        player.setInvisible(false);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
    }

    public static void stopCloak(ServerPlayer player, boolean applyCooldown) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long gameTime = player.level().getGameTime();
        boolean wasActive = isCloakActive(modData, gameTime)
                || modData.getBoolean(TAG_CLOAK_EFFECT_APPLIED)
                || modData.getInt(TAG_CLOAK_TRANSITION_MODE) != TRANSITION_MODE_NONE;

        if (applyCooldown && wasActive) {
            startCooldown(player, modData);
        }

        player.setInvisible(false);
        modData.remove(TAG_CLOAK_ACTIVE_UNTIL);
        modData.remove(TAG_CLOAK_REVEAL_UNTIL);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        modData.putInt(TAG_CLOAK_TRANSITION_MODE, TRANSITION_MODE_NONE);
        modData.putLong(TAG_CLOAK_TRANSITION_UNTIL, 0L);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);

        if (wasActive) {
            SpellPackets.syncCloakTransition(player, false);
        }
    }

    public static void beginHideTransition(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        if (modData.getInt(TAG_CLOAK_TRANSITION_MODE) == TRANSITION_MODE_HIDE
                && modData.getLong(TAG_CLOAK_TRANSITION_UNTIL) > gameTime) {
            return;
        }
        player.setInvisible(false);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        modData.putInt(TAG_CLOAK_TRANSITION_MODE, TRANSITION_MODE_HIDE);
        modData.putLong(TAG_CLOAK_TRANSITION_UNTIL, gameTime + TRANSITION_TICKS);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
        SpellPackets.syncCloakTransition(player, true);
    }

    public static void beginShowTransition(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        if (modData.getInt(TAG_CLOAK_TRANSITION_MODE) == TRANSITION_MODE_SHOW
                && modData.getLong(TAG_CLOAK_TRANSITION_UNTIL) > gameTime) {
            return;
        }
        player.setInvisible(false);
        modData.putBoolean(TAG_CLOAK_EFFECT_APPLIED, false);
        modData.putInt(TAG_CLOAK_TRANSITION_MODE, TRANSITION_MODE_SHOW);
        modData.putLong(TAG_CLOAK_TRANSITION_UNTIL, gameTime + TRANSITION_TICKS);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put(PlayerMagicData.PLAYER_PERSISTED, persisted);
        SpellPackets.syncCloakTransition(player, false);
    }

    public static boolean isCloakActive(net.minecraft.nbt.CompoundTag modData, long gameTime) {
        return modData.getLong(TAG_CLOAK_ACTIVE_UNTIL) > gameTime;
    }

    public static boolean isCloakRevealed(net.minecraft.nbt.CompoundTag modData, long gameTime) {
        return modData.getLong(TAG_CLOAK_REVEAL_UNTIL) > gameTime;
    }

    public static boolean isOnCloakCooldown(net.minecraft.nbt.CompoundTag modData, long gameTime) {
        return modData.getLong(TAG_CLOAK_COOLDOWN_UNTIL) > gameTime;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repairCandidate) {
        return repairCandidate.is(ExampleMod.MAGICAL_SILK.get()) || super.isValidRepairItem(toRepair, repairCandidate);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.invisibility_cloak.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hogwartsmagic.invisibility_cloak.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
