package com.hogwartsmagic.spell.vfx;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One spell visual effect particle: a {@link Kind} of light, smoke or fire with a colour, a size in blocks and
 * a lifetime in ticks. It travels like any particle (ServerLevel.sendParticles, Level.addParticle), so spell
 * code that used a vanilla particle can send one of these instead; the client draws it with the mod's own
 * glow sprites (client.SpellGlowParticle).
 */
public record SpellVfxOptions(int kind, int rgb, float size, int lifetime) implements ParticleOptions {

    /** The looks a spell effect can have. Ids are sent over the network: append, never reorder. */
    public enum Kind {
        /** A soft additive light that drifts and fades. */
        GLOW,
        /** A small hot spark that falls and shrinks. */
        SPARK,
        /** A mote of light that rises slowly with a wobble. */
        MOTE,
        /** A puff of smoke, drawn with normal blending so it can be dark. */
        SMOKE,
        /** A tongue of magical fire that rises and cools from its colour towards red. */
        FLAME,
        /** A thin shockwave ring that grows and fades. */
        RING,
        /** A blast: a big flash that also throws a ring, smoke and sparks. */
        BLAST;

        public static Kind byId(int id) {
            Kind[] values = values();
            return id >= 0 && id < values.length ? values[id] : GLOW;
        }
    }

    public static final MapCodec<SpellVfxOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("kind").forGetter(SpellVfxOptions::kind),
            Codec.INT.fieldOf("rgb").forGetter(SpellVfxOptions::rgb),
            Codec.FLOAT.fieldOf("size").forGetter(SpellVfxOptions::size),
            Codec.INT.fieldOf("lifetime").forGetter(SpellVfxOptions::lifetime)
    ).apply(instance, SpellVfxOptions::new));

    public static final StreamCodec<ByteBuf, SpellVfxOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpellVfxOptions::kind,
            ByteBufCodecs.INT, SpellVfxOptions::rgb,
            ByteBufCodecs.FLOAT, SpellVfxOptions::size,
            ByteBufCodecs.VAR_INT, SpellVfxOptions::lifetime,
            SpellVfxOptions::new);

    public SpellVfxOptions(Kind kind, int rgb, float size, int lifetime) {
        this(kind.ordinal(), rgb, size, lifetime);
    }

    public Kind kindEnum() {
        return Kind.byId(this.kind);
    }

    @Override
    public ParticleType<SpellVfxOptions> getType() {
        return ModParticles.SPELL_VFX.get();
    }
}
