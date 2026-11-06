package com.hogwartsmagic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own particle types. Common code: the server only ever names these in
 * {@code ServerLevel.sendParticles}; how they look and move lives in
 * {@code client.particle.ModParticleProviders}, which a dedicated server never loads.
 */
public final class ModParticles {

    private ModParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ExampleMod.MODID);

    /** A narrow willow leaf that tumbles and flutters down, then lies where it lands. */
    public static final RegistryObject<SimpleParticleType> WILLOW_LEAF = simple("willow_leaf");
    /** A splinter of bark, flung hard, spinning, bouncing once. */
    public static final RegistryObject<SimpleParticleType> BARK_CHIP = simple("bark_chip");
    /** Earth thrown up by roots and slammed limbs: billows, spreads and settles. */
    public static final RegistryObject<SimpleParticleType> ROOT_DUST = simple("root_dust");
    /** The tell: a sickly glint rising off the ground where a limb is about to land. */
    public static final RegistryObject<SimpleParticleType> WILLOW_GLINT = simple("willow_glint");

    /** A soft glowing dot that drifts and twinkles, coloured per spawn (MagicParticleOptions.mote). */
    public static final RegistryObject<ParticleType<MagicParticleOptions>> MAGIC_MOTE =
            tinted("magic_mote", MagicParticleOptions.MOTE);
    /** A short bright streak stretched along its velocity, coloured per spawn. */
    public static final RegistryObject<ParticleType<MagicParticleOptions>> SPARK =
            tinted("spark", MagicParticleOptions.SPARK);
    /** A big soft puff that grows, turns slowly and fades, tinted per spawn. */
    public static final RegistryObject<ParticleType<MagicParticleOptions>> SOFT_SMOKE =
            tinted("soft_smoke", MagicParticleOptions.SMOKE);
    /** A four point star twinkle, coloured per spawn. */
    public static final RegistryObject<ParticleType<MagicParticleOptions>> GLINT =
            tinted("glint", MagicParticleOptions.GLINT);
    /** A full-bright spark of fire: drifts up, flickers and cools from yellow to deep red. */
    public static final RegistryObject<SimpleParticleType> EMBER = simple("ember");
    /** A big faint wisp of ground mist that drifts low and slow; spawned client-side by BiomeFogClient. */
    public static final RegistryObject<SimpleParticleType> GROUND_MIST = simple("ground_mist");

    private static RegistryObject<ParticleType<MagicParticleOptions>> tinted(String name, int kind) {
        return PARTICLE_TYPES.register(name, () -> new ParticleType<MagicParticleOptions>(false) {
            private final MapCodec<MagicParticleOptions> codec = MagicParticleOptions.codec(kind);
            private final StreamCodec<RegistryFriendlyByteBuf, MagicParticleOptions> stream =
                    MagicParticleOptions.streamCodec(kind);

            @Override
            public MapCodec<MagicParticleOptions> codec() {
                return codec;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, MagicParticleOptions> streamCodec() {
                return stream;
            }
        });
    }

    private static RegistryObject<SimpleParticleType> simple(String name) {
        // overrideLimiter false: these are spawned in bursts and should respect the client's
        // particle setting like any vanilla burst would
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
