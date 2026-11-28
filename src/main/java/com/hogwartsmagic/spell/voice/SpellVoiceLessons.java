package com.hogwartsmagic.spell.voice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * When a spell is learned, a teacher's voice says its incantation, so the player hears how to say it for voice
 * casting, with a chat line spelling it out. Only the learner hears it ({@code playNotifySound}). It comes a little
 * after the learning chime so the two do not talk over each other. Server side only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellVoiceLessons {
    /** Ticks after the chime starts: its bright attack is over by then. */
    static final int DELAY_TICKS = 30;

    private record Pending(UUID player, String spellId, long dueTick) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private SpellVoiceLessons() {
    }

    /** Queue the spoken incantation for {@code spell}; a spell with no voice line (or no incantation) does nothing. */
    public static void teach(ServerPlayer player, SpellType spell) {
        if (!ModSounds.SPELL_VOICE.containsKey(spell.getId()) || player.getServer() == null) {
            return;
        }
        long due = player.getServer().getTickCount() + DELAY_TICKS;
        synchronized (PENDING) {
            PENDING.add(new Pending(player.getUUID(), spell.getId(), due));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<Pending> due = new ArrayList<>();
        MinecraftServer server = event.getServer();
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (server.getTickCount() >= p.dueTick() || server.getTickCount() < p.dueTick() - DELAY_TICKS * 2L) {
                    due.add(p);
                    it.remove();
                }
            }
        }
        for (Pending p : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            RegistryObject<SoundEvent> sound = ModSounds.SPELL_VOICE.get(p.spellId());
            if (player == null || player.isRemoved() || sound == null || !sound.isPresent()) {
                continue;
            }
            player.playNotifySound(sound.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            String words = IncantationMatcher.incantations().get(p.spellId());
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".spell_say_it",
                    Component.literal(words).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }
}
