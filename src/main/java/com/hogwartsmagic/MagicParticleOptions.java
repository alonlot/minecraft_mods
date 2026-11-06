package com.hogwartsmagic;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A magic particle with a colour chosen per spawn: the server writes
 * {@code level.sendParticles(MagicParticleOptions.mote(0.96f, 0.84f, 0.38f, 1.0f), ...)} and the
 * client draws a glowing dot, spark, glint or puff of smoke in that colour. One class serves the four
 * tinted particle types; {@code kind} says which. Common code: it only carries numbers.
 */
public final class MagicParticleOptions implements ParticleOptions {

    public static final int MOTE = 0;
    public static final int SPARK = 1;
    public static final int SMOKE = 2;
    public static final int GLINT = 3;

    public final int kind;
    public final float r;
    public final float g;
    public final float b;
    /** 1 is the normal size of the particle. */
    public final float scale;

    public MagicParticleOptions(int kind, float r, float g, float b, float scale) {
        this.kind = kind;
        this.r = r;
        this.g = g;
        this.b = b;
        this.scale = scale;
    }

    /** A soft glowing dot that drifts and twinkles. */
    public static MagicParticleOptions mote(float r, float g, float b, float scale) {
        return new MagicParticleOptions(MOTE, r, g, b, scale);
    }

    public static MagicParticleOptions mote(int rgb, float scale) {
        return new MagicParticleOptions(MOTE, red(rgb), green(rgb), blue(rgb), scale);
    }

    /** A short bright streak along its velocity. */
    public static MagicParticleOptions spark(float r, float g, float b, float scale) {
        return new MagicParticleOptions(SPARK, r, g, b, scale);
    }

    public static MagicParticleOptions spark(int rgb, float scale) {
        return new MagicParticleOptions(SPARK, red(rgb), green(rgb), blue(rgb), scale);
    }

    /** A big soft puff that grows and fades. Grey (0.8, 0.8, 0.8) is a plain smoke. */
    public static MagicParticleOptions smoke(float r, float g, float b, float scale) {
        return new MagicParticleOptions(SMOKE, r, g, b, scale);
    }

    public static MagicParticleOptions smoke(int rgb, float scale) {
        return new MagicParticleOptions(SMOKE, red(rgb), green(rgb), blue(rgb), scale);
    }

    /** A four point star that twinkles and goes out. */
    public static MagicParticleOptions glint(float r, float g, float b, float scale) {
        return new MagicParticleOptions(GLINT, r, g, b, scale);
    }

    public static MagicParticleOptions glint(int rgb, float scale) {
        return new MagicParticleOptions(GLINT, red(rgb), green(rgb), blue(rgb), scale);
    }

    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    @Override
    public ParticleType<?> getType() {
        if (kind == SPARK) return ModParticles.SPARK.get();
        if (kind == SMOKE) return ModParticles.SOFT_SMOKE.get();
        if (kind == GLINT) return ModParticles.GLINT.get();
        return ModParticles.MAGIC_MOTE.get();
    }

    /** The disk codec for one kind (the kind is fixed by which particle type owns the codec). */
    static MapCodec<MagicParticleOptions> codec(int kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                com.mojang.serialization.Codec.FLOAT.fieldOf("r").forGetter(o -> o.r),
                com.mojang.serialization.Codec.FLOAT.fieldOf("g").forGetter(o -> o.g),
                com.mojang.serialization.Codec.FLOAT.fieldOf("b").forGetter(o -> o.b),
                com.mojang.serialization.Codec.FLOAT.fieldOf("scale").forGetter(o -> o.scale)
        ).apply(i, (r, g, b, s) -> new MagicParticleOptions(kind, r, g, b, s)));
    }

    /** The network codec for one kind. */
    static StreamCodec<RegistryFriendlyByteBuf, MagicParticleOptions> streamCodec(int kind) {
        return StreamCodec.composite(
                ByteBufCodecs.FLOAT, o -> o.r,
                ByteBufCodecs.FLOAT, o -> o.g,
                ByteBufCodecs.FLOAT, o -> o.b,
                ByteBufCodecs.FLOAT, o -> o.scale,
                (r, g, b, s) -> new MagicParticleOptions(kind, r, g, b, s));
    }
}
