package com.hogwartsmagic.client.sounds;

import com.hogwartsmagic.entity.BroomEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Same idea as vanilla elytra flight: one looping {@link SoundEvents#ELYTRA_FLYING} stream whose volume
 * (and pitch) follows broom speed so it weakens when you slow and is silent when nearly still.
 */
@OnlyIn(Dist.CLIENT)
public class BroomFlightSoundInstance extends AbstractTickableSoundInstance {

    /** Lower = louder at the same speed (reaches engine max volume sooner). */
    private static final float SPEED_SQR_SCALE = 28.0F;
    /** Extra multiplier before clamp; Minecraft caps final output at 1.0 per channel. */
    private static final float VOLUME_GAIN = 1.45F;

    private final LocalPlayer player;
    private int time;

    public BroomFlightSoundInstance(LocalPlayer player) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        // Match elytra: non-zero start so the sound engine registers the tickable instance (zero volume is skipped otherwise).
        this.volume = 0.18F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        this.time++;
        if (this.player.isRemoved() || !(this.player.getVehicle() instanceof BroomEntity broom)) {
            this.stop();
            return;
        }

        this.x = broom.getX();
        this.y = broom.getY();
        this.z = broom.getZ();

        float speedSqr = (float) broom.getDeltaMovement().lengthSqr();
        float targetVol = speedSqr >= 1.0E-7F
                ? Mth.clamp(speedSqr / SPEED_SQR_SCALE * VOLUME_GAIN, 0.0F, 1.0F)
                : 0.0F;

        // Match elytra: no blast on mount, then ease in.
        if (this.time < 20) {
            targetVol = 0.0F;
        } else if (this.time < 40) {
            targetVol *= (float) (this.time - 20) / 20.0F;
        }

        this.volume = targetVol;
        if (this.volume > 0.8F) {
            this.pitch = 1.0F + (this.volume - 0.8F);
        } else {
            this.pitch = 1.0F;
        }
    }
}
