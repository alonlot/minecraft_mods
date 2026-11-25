package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.cosy.AmbienceRules;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One looping ambience sound whose volume fades in and out over about two seconds. The owner says each tick
 * whether the loop is wanted (and at what scale, for a loop that fades with distance); once it has faded out
 * unwanted it stops itself and the owner lets go of it. Client only.
 */
public class FadingLoopSound extends AbstractTickableSoundInstance {
    private final float maxVolume;
    private float fade;
    private boolean wanted = true;
    private float scale = 1.0f;

    public FadingLoopSound(SoundEvent event, float maxVolume) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.maxVolume = maxVolume;
        this.looping = true;
        this.delay = 0;
        // Not positional: the owner scales the volume itself, and a room loop should not pan around the head.
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.volume = 0.0f;
    }

    /** What the owner wants this tick: whether the loop should be audible, and its distance scale (0 to 1). */
    public void want(boolean wanted, float scale) {
        this.wanted = wanted;
        this.scale = scale;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        fade = AmbienceRules.fadeStep(fade, wanted);
        volume = maxVolume * fade * scale;
        if (!wanted && fade <= 0.0f) {
            stop();
        }
    }
}
