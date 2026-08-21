package com.hogwartsmagic.client.sounds;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.GringottsCartEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The two loops that make the cart ride sound like a place rather than like a vehicle.
 *
 * <p>One class, two instances, told apart by a flag rather than by an enum - the mod has been
 * bitten before by a synthetic switch-map class failing to load in render code, and there is no
 * reason to risk it for two cases.
 *
 * <dl>
 *   <dt><b>The rumble</b></dt>
 *   <dd>Scaled <em>linearly</em> in speed. The broom's loop squares its speed term and gets away
 *       with it because a broom is either moving or not; a cart that spends most of a minute at
 *       two thirds of top speed would be almost inaudible on a squared curve.</dd>
 *   <dt><b>The flange screech</b></dt>
 *   <dd>Keyed on <b>curvature</b>, not speed. This is the one that matters: it is silent on the
 *       straights and rises through every switchback, so the shape of the line is something you
 *       can hear with your eyes shut. Keyed on speed instead, it would just be a second rumble.</dd>
 * </dl>
 *
 * <p>And both duck at the lip of a drop. Real drops are quiet - the noise stops, and then the
 * ground arrives - so the moment the line pitches past thirty degrees the loops cut to a tenth and
 * climb back over the next second. The silence is the effect.
 */
@OnlyIn(Dist.CLIENT)
public class GringottsCartSoundInstance extends AbstractTickableSoundInstance {

    /** Below this a corner is a straight as far as the flanges are concerned. */
    private static final double SCREECH_FLOOR = 0.010D;
    /** Curvature at which the screech is flat out. Roughly a 20-block radius. */
    private static final double SCREECH_FULL = 0.055D;

    /** Descent angle, in degrees, past which the line counts as a plunge rather than a grade. */
    private static final float PLUNGE_PITCH = 30.0F;
    /** How long the hush at the lip of a plunge lasts before the noise comes back. */
    private static final int PLUNGE_HUSH = 20;

    private final LocalPlayer player;
    private final boolean screech;
    private int time;
    private int plungeTicks;

    public GringottsCartSoundInstance(LocalPlayer player, boolean screech) {
        super(screech ? ModSounds.CART_SCREECH.get() : ModSounds.CART_RUMBLE.get(),
                SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.player = player;
        this.screech = screech;
        this.looping = true;
        this.delay = 0;
        // Non-zero, or the sound engine skips registering the tickable instance at all.
        this.volume = 0.12F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        this.time++;
        if (this.player.isRemoved()
                || !(this.player.getVehicle() instanceof GringottsCartEntity cart)) {
            stop();
            return;
        }

        this.x = cart.getX();
        this.y = cart.getY();
        this.z = cart.getZ();

        double speed = Mth.clamp(cart.getSpeed() / GringottsCartEntity.maxSpeed(), 0.0D, 1.0D);
        float target;
        if (this.screech) {
            double k = (cart.getCurvature() - SCREECH_FLOOR) / (SCREECH_FULL - SCREECH_FLOOR);
            float bite = (float) Mth.clamp(k, 0.0D, 1.0D);
            // Both, multiplied: a tight corner taken slowly does not scream either.
            target = bite * (float) speed * 0.85F;
            this.pitch = 0.85F + 0.45F * bite;
        } else {
            target = (float) speed * 0.9F;
            this.pitch = 0.8F + 0.35F * (float) speed;
        }

        if (cart.getStateByte() != GringottsCartEntity.STATE_RIDING) {
            target = 0.0F;
        }

        // The lip of a plunge. Counted rather than tested each tick, so the hush has a shape.
        if (cart.displayPitch > PLUNGE_PITCH) {
            this.plungeTicks++;
        } else {
            this.plungeTicks = 0;
        }
        if (this.plungeTicks > 0 && this.plungeTicks < PLUNGE_HUSH) {
            target *= 0.12F + 0.88F * (this.plungeTicks / (float) PLUNGE_HUSH);
        }

        // No blast on boarding, same as the broom loop.
        if (this.time < 6) {
            target = 0.0F;
        } else if (this.time < 20) {
            target *= (this.time - 6) / 14.0F;
        }

        // Eased rather than snapped, or every tick of curvature change is an audible step.
        this.volume += (target - this.volume) * 0.35F;
    }
}
