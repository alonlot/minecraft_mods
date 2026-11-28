package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /voicepractice}: opens voice practice (client only; the screen opens on the next tick, after chat). */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class VoiceCommands {
    private VoiceCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voicepractice").executes(c -> {
            VoiceCastClient.requestPractice();
            return 1;
        }));
    }
}
