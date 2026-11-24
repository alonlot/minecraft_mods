package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.player.Input;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The kneel of the last cups. The server sets a short hidden {@code despair_kneel} effect; while the local player
 * has it (and the despair effect is at stage 4 or more) the crouch is forced and walking and jumping are zeroed.
 * The crouch reaches the server and other players through normal input.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DespairKneelClient {
    private DespairKneelClient() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        var player = event.getEntity();
        MobEffectInstance despair = player.getEffect(ExampleMod.DESPAIR.getHolder().get());
        if (despair == null || despair.getAmplifier() < 4 || !player.hasEffect(ExampleMod.DESPAIR_KNEEL.getHolder().get())) {
            return;
        }
        Input input = event.getInput();
        input.shiftKeyDown = true;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
    }
}
