package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.capture.CaptureHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the Pensieve recorder the sounds, particles, level events and explosions the server sends. Common mixin:
 * everything here is server-only code. Each hook is a few field writes behind an O(1) watched-chunk check.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelCaptureMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void hogwartsmagic$captureSound(Player except, double x, double y, double z, Holder<SoundEvent> sound,
                                            SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        CaptureHooks.onSound((ServerLevel) (Object) this, x, y, z, sound, volume, pitch);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void hogwartsmagic$captureEntitySound(Player except, Entity entity, Holder<SoundEvent> sound,
                                                  SoundSource source, float volume, float pitch, long seed,
                                                  CallbackInfo ci) {
        CaptureHooks.onSound((ServerLevel) (Object) this, entity.getX(), entity.getY(), entity.getZ(), sound, volume,
                pitch);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void hogwartsmagic$captureParticles(ParticleOptions options, double x, double y, double z, int count,
                                                double dx, double dy, double dz, double speed,
                                                CallbackInfoReturnable<Integer> cir) {
        CaptureHooks.onParticles((ServerLevel) (Object) this, options, x, y, z, count, dx, dy, dz, speed);
    }

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void hogwartsmagic$captureLevelEvent(Player except, int type, BlockPos pos, int data, CallbackInfo ci) {
        CaptureHooks.onLevelEvent((ServerLevel) (Object) this, type, pos, data);
    }

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;",
            at = @At("RETURN"))
    private void hogwartsmagic$captureExplosion(Entity source, DamageSource damage, ExplosionDamageCalculator calc,
                                                double x, double y, double z, float radius, boolean fire,
                                                Level.ExplosionInteraction interaction, ParticleOptions small,
                                                ParticleOptions large, Holder<SoundEvent> sound,
                                                CallbackInfoReturnable<Explosion> cir) {
        CaptureHooks.onExplosion((ServerLevel) (Object) this, x, y, z, radius);
    }
}
