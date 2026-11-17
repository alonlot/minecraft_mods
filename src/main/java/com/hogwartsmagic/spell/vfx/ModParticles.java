package com.hogwartsmagic.spell.vfx;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The mod's particle types. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, ExampleMod.MODID);

    /** Every spell effect; the look is in its options (see SpellVfxOptions.Kind). */
    public static final RegistryObject<ParticleType<SpellVfxOptions>> SPELL_VFX = PARTICLE_TYPES.register("spell_vfx",
            () -> new ParticleType<SpellVfxOptions>(false) {
                @Override
                public MapCodec<SpellVfxOptions> codec() {
                    return SpellVfxOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, SpellVfxOptions> streamCodec() {
                    return SpellVfxOptions.STREAM_CODEC;
                }
            });

    /** The same effect with the always-show flag, for the vanilla particles that are never culled. */
    public static final RegistryObject<ParticleType<SpellVfxOptions>> SPELL_VFX_ALWAYS = PARTICLE_TYPES.register(
            "spell_vfx_always", () -> new ParticleType<SpellVfxOptions>(true) {
                @Override
                public MapCodec<SpellVfxOptions> codec() {
                    return SpellVfxOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, SpellVfxOptions> streamCodec() {
                    return SpellVfxOptions.STREAM_CODEC;
                }
            });

    private ModParticles() {
    }
}
