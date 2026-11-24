package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * How a Portkey journey sounds, as one piece: the charge hum follows the holder and climbs in pitch and loudness
 * with the charge (and slides down and away if they let go); the hook's yank is in the traveller's own head; the
 * wind roars round them in the tunnel, fading in behind the yank and out under the landing; the landing thumps.
 * Bystanders hear the hum, a pop as the group whisks away, and a pop where it arrives.
 *
 * <p>No sound outlives its state: the hum stops when its charge is gone or its holder is, the wind when the tunnel
 * has let go. A dimension change stops every sound the client is playing; the wind is started again if the tunnel
 * is still up.
 */
final class PortkeySounds {

    /** Volumes of the one-shots (the files are level-matched; these set the mix: nothing louder than the hook). */
    static final float HOOK_VOLUME = 1.0f;
    static final float WIND_VOLUME = 0.85f;
    static final float LAND_VOLUME = 0.95f;
    static final float POP_VOLUME = 1.0f;
    static final float CRUMBLE_VOLUME = 0.7f;
    static final float BIND_VOLUME = 0.8f;
    /** The charge hum's volume at full (the timeline's curve times this). */
    static final float CHARGE_VOLUME = 0.9f;
    /** The wind is started again when it has been silent this many ticks while the tunnel is up. */
    private static final int WIND_LOST_TICKS = 2;

    private static final Map<Integer, ChargeSound> CHARGES = new HashMap<>();
    private static WindLoop wind;
    private static int windMissing;

    private PortkeySounds() {
    }

    /** A sound in the listener's own head (relative, no attenuation), on the players channel. */
    private static SoundInstance inHead(SoundEvent sound, float volume, float pitch) {
        return new SimpleSoundInstance(sound.getLocation(), SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
    }

    private static void at(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    static void startCharge(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        ChargeSound old = CHARGES.remove(entityId);
        if (old != null) {
            old.end();
        }
        ChargeSound sound = new ChargeSound(entityId);
        CHARGES.put(entityId, sound);
        mc.getSoundManager().play(sound);
    }

    static void hook() {
        Minecraft.getInstance().getSoundManager().play(inHead(ModSounds.PORTKEY_HOOK.get(), HOOK_VOLUME, 1.0f));
    }

    static void startWind() {
        if (wind != null) {
            wind.end();
        }
        wind = new WindLoop();
        windMissing = 0;
        Minecraft.getInstance().getSoundManager().play(wind);
    }

    static void land() {
        Minecraft.getInstance().getSoundManager().play(inHead(ModSounds.PORTKEY_LAND.get(), LAND_VOLUME, 1.0f));
    }

    static void popDepart(Vec3 pos) {
        at(pos, ModSounds.PORTKEY_POP_DEPART.get(), POP_VOLUME, 0.95f + (float) Math.random() * 0.1f);
    }

    static void popArrive(Vec3 pos) {
        at(pos, ModSounds.PORTKEY_POP_ARRIVE.get(), POP_VOLUME, 0.95f + (float) Math.random() * 0.1f);
    }

    static void crumble(Vec3 pos) {
        at(pos, ModSounds.PORTKEY_CRUMBLE.get(), CRUMBLE_VOLUME, 1.0f);
    }

    static void bind(Vec3 pos) {
        at(pos, ModSounds.PORTKEY_BIND.get(), BIND_VOLUME, 1.0f);
    }

    /** Once a client tick: bring the wind back after a dimension change stopped every sound. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        CHARGES.values().removeIf(ChargeSound::isStopped);
        if (!PortkeyClient.windWanted()) {
            if (wind != null) {
                wind.end();
                wind = null;
            }
            return;
        }
        if (wind == null || wind.isStopped()) {
            startWind();
            return;
        }
        if (mc.getSoundManager().isActive(wind)) {
            windMissing = 0;
        } else if (++windMissing > WIND_LOST_TICKS) {
            startWind();
        }
    }

    static void stopCharges() {
        for (ChargeSound s : CHARGES.values()) {
            s.end();
        }
        CHARGES.clear();
    }

    static void stopAll() {
        stopCharges();
        if (wind != null) {
            wind.end();
            wind = null;
        }
    }

    /** The hum of a charging Portkey, following its holder. */
    static final class ChargeSound extends AbstractTickableSoundInstance {
        private final int entityId;

        ChargeSound(int entityId) {
            super(ModSounds.PORTKEY_CHARGE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entityId = entityId;
            this.looping = false;
            this.delay = 0;
            this.volume = PortkeyTimeline.chargeVolume(0.0f) * CHARGE_VOLUME;
            this.pitch = PortkeyTimeline.chargePitch(0.0f);
            Entity holder = holder();
            if (holder != null) {
                this.x = holder.getX();
                this.y = holder.getEyeY();
                this.z = holder.getZ();
            }
        }

        private Entity holder() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : mc.level.getEntity(entityId);
        }

        void end() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            Entity holder = holder();
            if (mc.level == null || holder == null || holder.isRemoved() || CHARGES.get(entityId) != this
                    || !PortkeyClient.charging(entityId)) {
                stop(); // hooked (the yank takes over), let go and faded, or the holder is gone
                return;
            }
            float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
            float e = PortkeyClient.chargeOf(entityId, partial);
            float cancel = PortkeyClient.cancelProgress(entityId, mc.level.getGameTime() + partial);
            float fade = cancel < 0.0f ? 1.0f : 1.0f - cancel;
            float slide = cancel < 0.0f ? 1.0f : Mth.lerp(cancel, 1.0f, PortkeyTimeline.CANCEL_PITCH);
            // The ease already folds the cancel in, so a let-go hum both sinks back down its curve and slides.
            this.volume = PortkeyTimeline.chargeVolume(e) * CHARGE_VOLUME * fade;
            this.pitch = PortkeyTimeline.chargePitch(e) * slide;
            this.x = holder.getX();
            this.y = holder.getEyeY();
            this.z = holder.getZ();
            if (cancel >= 1.0f) {
                stop();
            }
        }
    }

    /** The wind round the traveller in the tunnel: in their head, looping, its volume from the trip. */
    static final class WindLoop extends AbstractTickableSoundInstance {
        WindLoop() {
            super(ModSounds.PORTKEY_TRANSIT.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.volume = PortkeyClient.windVolume() * WIND_VOLUME;
        }

        void end() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (wind != this || !PortkeyClient.windWanted()) {
                stop();
                return;
            }
            this.volume = Math.max(0.0001f, PortkeyClient.windVolume() * WIND_VOLUME);
        }
    }
}
