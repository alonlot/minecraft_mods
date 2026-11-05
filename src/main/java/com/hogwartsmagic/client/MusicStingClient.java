package com.hogwartsmagic.client;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.MusicStings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Plays a fight's music sting under the Music slider. A boss's entrance or a victory takes the floor: the
 * background music is stopped for it (Minecraft brings its own back later on its own schedule). Never inside a
 * Pensieve memory, which has its own score.
 */
public final class MusicStingClient {
    private MusicStingClient() {
    }

    public static void accept(SpellPackets.MusicStingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || com.hogwartsmagic.client.pensieve.PensieveVoid.active()) {
            return;
        }
        byte sting = packet.sting();
        SoundEvent sound;
        if (sting == MusicStings.PRIORI) {
            sound = ModSounds.STING_PRIORI.get();
        } else if (sting == MusicStings.BOSS_DARK) {
            sound = ModSounds.STING_BOSS_DARK.get();
        } else if (sting == MusicStings.BOSS_SERPENT) {
            sound = ModSounds.STING_BOSS_SERPENT.get();
        } else if (sting == MusicStings.BOSS_DRAGON) {
            sound = ModSounds.STING_BOSS_DRAGON.get();
        } else if (sting == MusicStings.VICTORY) {
            sound = ModSounds.STING_VICTORY.get();
        } else {
            sound = ModSounds.STING_DUEL.get();
        }
        if (sting == MusicStings.DUEL || sting == MusicStings.PRIORI) {
            // Over whatever is playing.
            minecraft.getSoundManager().play(SimpleSoundInstance.forMusic(sound));
            return;
        }
        // A boss's entrance or a victory takes the floor, through the music manager so no track starts over it.
        minecraft.getMusicManager().startPlaying(
                new net.minecraft.sounds.Music(net.minecraft.core.Holder.direct(sound), 0, 0, true));
    }

    /** Deep dread: a chilling stab of strings, at most every two minutes (DementorDread). */
    public static void dread() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || com.hogwartsmagic.client.pensieve.PensieveVoid.active()) {
            return;
        }
        long now = minecraft.level.getGameTime();
        if (now < nextDread && now >= nextDread - 2400L) {
            return;
        }
        nextDread = now + 2400L;
        minecraft.getSoundManager().play(SimpleSoundInstance.forMusic(ModSounds.STING_DREAD.get()));
    }

    private static long nextDread;
}
