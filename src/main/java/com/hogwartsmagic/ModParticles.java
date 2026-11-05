package com.hogwartsmagic;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
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

    private static RegistryObject<SimpleParticleType> simple(String name) {
        // overrideLimiter false: these are spawned in bursts and should respect the client's
        // particle setting like any vanilla burst would
        return PARTICLE_TYPES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
