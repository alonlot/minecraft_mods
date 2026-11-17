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
 * One custom visual effect particle: a {@link Kind} of light, smoke or fire with a colour, a size in blocks and
 * a lifetime in ticks. It travels like any particle (ServerLevel.sendParticles, Level.addParticle), so spell
 * code that used a vanilla particle can send one of these instead; the client draws it with the mod's own
 * glow sprites (client.SpellGlowParticle).
 */
public record SpellVfxOptions(int kind, int rgb, float size, int lifetime, boolean always) implements ParticleOptions {

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
        BLAST,
        /**
         * A small tumbling 3D chip of a block, textured like it (replaces flat block crumbs). For this kind the
         * colour field carries the block state id instead of a colour.
         */
        CHUNK;

        public static Kind byId(int id) {
            Kind[] values = values();
            return id >= 0 && id < values.length ? values[id] : GLOW;
        }
    }

    public static final MapCodec<SpellVfxOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("kind").forGetter(SpellVfxOptions::kind),
            Codec.INT.fieldOf("rgb").forGetter(SpellVfxOptions::rgb),
            Codec.FLOAT.fieldOf("size").forGetter(SpellVfxOptions::size),
            Codec.INT.fieldOf("lifetime").forGetter(SpellVfxOptions::lifetime),
            Codec.BOOL.optionalFieldOf("always", false).forGetter(SpellVfxOptions::always)
    ).apply(instance, SpellVfxOptions::new));

    public static final StreamCodec<ByteBuf, SpellVfxOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpellVfxOptions::kind,
            ByteBufCodecs.INT, SpellVfxOptions::rgb,
            ByteBufCodecs.FLOAT, SpellVfxOptions::size,
            ByteBufCodecs.VAR_INT, SpellVfxOptions::lifetime,
            ByteBufCodecs.BOOL, SpellVfxOptions::always,
            SpellVfxOptions::new);

    public SpellVfxOptions(Kind kind, int rgb, float size, int lifetime) {
        this(kind.ordinal(), rgb, size, lifetime, false);
    }

    /**
     * The same effect, shown at any range and whatever the particle setting, as vanilla shows the particles
     * it stands in for there (explosions, shockwaves, gusts).
     */
    public SpellVfxOptions alwaysShown() {
        return this.always ? this : new SpellVfxOptions(this.kind, this.rgb, this.size, this.lifetime, true);
    }

    public Kind kindEnum() {
        return Kind.byId(this.kind);
    }

    @Override
    public ParticleType<SpellVfxOptions> getType() {
        return this.always ? ModParticles.SPELL_VFX_ALWAYS.get() : ModParticles.SPELL_VFX.get();
    }
}
