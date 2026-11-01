package com.hogwartsmagic.client.sounds;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.client.AvadaChargeClientState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Killing Curse gathering: a reversed swell of drone and whispers that follows the caster, rises with the
 * charge and is choked off the moment the windup ends, so its peak always lands on the cast (or the collapse)
 * however long the windup was. Short windups use the short clip, the boss's long one the full swell.
 */
@OnlyIn(Dist.CLIENT)
public class AvadaChargeSoundInstance extends AbstractTickableSoundInstance {
    /** Windups at or under this many ticks take the 1.5s clip; longer ones the 2.6s clip. */
    private static final int SHORT_CLIP_MAX_TICKS = 34;

    private final LivingEntity caster;
    private final AvadaChargeClientState.Charge charge;

    public AvadaChargeSoundInstance(LivingEntity caster, AvadaChargeClientState.Charge charge) {
        super(charge.duration <= SHORT_CLIP_MAX_TICKS ? ModSounds.AVADA_CHARGE_SHORT.get() : ModSounds.AVADA_CHARGE.get(),
                caster instanceof net.minecraft.world.entity.player.Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                SoundInstance.createUnseededRandom());
        this.caster = caster;
        this.charge = charge;
        this.looping = false;
        this.delay = 0;
        this.volume = 0.6f;
        // Stretch or squeeze the clip toward the windup so the swell peaks near the cast, within what still
        // sounds like the same voice.
        float clipTicks = charge.duration <= SHORT_CLIP_MAX_TICKS ? 26.0f : 52.0f;
        this.pitch = Mth.clamp(clipTicks / charge.duration, 0.85f, 1.25f);
        this.x = caster.getX();
        this.y = caster.getY() + caster.getBbHeight() * 0.7;
        this.z = caster.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (caster.isRemoved() || AvadaChargeClientState.get(caster.getId()) != charge) {
            stop();
            return;
        }
        this.x = caster.getX();
        this.y = caster.getY() + caster.getBbHeight() * 0.7;
        this.z = caster.getZ();
        double now = AvadaChargeClientState.now(0.0f);
        if (charge.ended()) {
            // Choked off: the cast or the collapse has its own sound.
            this.volume *= 0.35f;
            if (this.volume < 0.02f) {
                stop();
            }
            return;
        }
        float c = charge.charge(now);
        this.volume = 0.6f + 0.6f * c;
    }
}
