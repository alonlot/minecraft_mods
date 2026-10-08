package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Flagrante: the burning half of the curse on a Gringotts hoard.
 *
 * <p>Carrying cursed gold sets you alight and makes more of it in your pockets. The damage scales
 * with how much you are carrying, so it is greed that hurts rather than bad luck.
 *
 * <p><b>Fire Resistance does not save you.</b> Flagrante is a curse, not a fire - a thief who
 * drank a potion on the way in should not walk out with the vault. It is dealt as magic damage
 * when they have the effect, which is also why the Thief's Downfall stripping every potion on the
 * way in matters so much.
 *
 * <p>Three ways out, all findable without being told: drop it, get in water, or carry it back
 * through the Downfall - the trap on the way in is the cure on the way out.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class GeminoEvents {

    /** Beyond this it stops breeding: one burning stack is a punishment, forty is a lost save. */
    private static final int CARRY_CAP = 64;
    private static final int DAMAGE_INTERVAL = 20;
    private static final int MULTIPLY_INTERVAL = 40;
    /** How long a dropped one survives before the curse burns it out. */
    private static final int DROPPED_FUSE = 200;

    private GeminoEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        int carried = count(player);
        if (carried <= 0) {
            return;
        }

        boolean inWater = player.isInWaterOrRain();
        if (inWater) {
            // Water is the mercy. One coin a second goes out, and nothing burns while it does.
            if (player.tickCount % DAMAGE_INTERVAL == 0) {
                removeOne(player);
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
            }
            return;
        }

        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 20));

        if (player.tickCount % DAMAGE_INTERVAL == 0) {
            float damage = 1.0F + 0.5F * Math.min(carried, 8);
            // Magic, not fire: a potion is not a way round a curse.
            player.hurt(player.hasEffect(MobEffects.FIRE_RESISTANCE)
                    ? player.damageSources().magic()
                    : player.damageSources().onFire(), damage);
        }

        if (player.tickCount % MULTIPLY_INTERVAL == 0 && carried < CARRY_CAP) {
            player.getInventory().add(new ItemStack(ExampleMod.CURSED_GALLEON.get()));
            player.displayClientMessage(Component
                    .translatable("item.hogwartsmagic.cursed_galleon.spreading")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Dropping it works, and the coin burns itself out shortly after. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();
        if (entity.getItem().is(ExampleMod.CURSED_GALLEON.get())) {
            // Not setUnlimitedLifetime(): that pins the age at -32768, so age >= lifespan is never
            // reached and the coin would lie there forever instead of burning out.
            entity.lifespan = DROPPED_FUSE;
        }
    }

    private static int count(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ExampleMod.CURSED_GALLEON.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeOne(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ExampleMod.CURSED_GALLEON.get())) {
                stack.shrink(1);
                return;
            }
        }
    }
}
