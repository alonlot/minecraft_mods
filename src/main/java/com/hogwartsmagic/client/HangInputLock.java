package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.disable.LevicorpusSpell;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the Levicorpus hang, driven by the levicorpus effect the server sends to the hanging
 * player. Movement is client-authoritative for players, so the walk, jump and sneak input is zeroed
 * here; the server also holds the player at the anchor (LevicorpusSpell.onLivingTick) and teleports
 * them back if they drift, so a modified client gains nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HangInputLock {
    private HangInputLock() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!LevicorpusSpell.isHanging(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
