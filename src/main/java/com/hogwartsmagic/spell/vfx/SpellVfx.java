package com.hogwartsmagic.spell.vfx;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions.Kind;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Turns the vanilla particles the mod used (spells, creatures, blocks, events) into the mod's own effects (SpellVfxOptions): every
 * vanilla type has a look here (kind, colour, size, life). Spell code wraps its particle in {@link #swap}
 * and sends it as before, so counts, spreads and speeds stay as they were tuned; only the look changes.
 * Block crumbs become textured 3D chips; only item pictures and the barrier marker stay vanilla.
 */
public final class SpellVfx {

    /** How one vanilla particle looks as a spell effect. A tinted spec takes the spell's colour when known. */
    private record Spec(Kind kind, int rgb, float size, int life, boolean tinted) {
    }

    private static final Map<ParticleType<?>, Spec> SPECS = new IdentityHashMap<>();
    private static final Spec DEFAULT = new Spec(Kind.GLOW, 0xF0F4FF, 0.12F, 16, true);

    static {
        spec(ParticleTypes.ENCHANT, Kind.MOTE, 0xC8B8FF, 0.10F, 30, true);
        spec(ParticleTypes.END_ROD, Kind.GLOW, 0xF4FFFF, 0.10F, 22, false);
        spec(ParticleTypes.GLOW, Kind.GLOW, 0x7FFFE0, 0.14F, 18, true);
        spec(ParticleTypes.FLASH, Kind.GLOW, 0xFFFFFF, 1.1F, 5, false);
        spec(ParticleTypes.WITCH, Kind.GLOW, 0xC040FF, 0.12F, 16, true);
        spec(ParticleTypes.HAPPY_VILLAGER, Kind.GLOW, 0x60FF70, 0.10F, 18, true);
        spec(ParticleTypes.COMPOSTER, Kind.GLOW, 0x90E060, 0.08F, 18, false);
        spec(ParticleTypes.DRAGON_BREATH, Kind.GLOW, 0xFF40E0, 0.22F, 26, true);
        spec(ParticleTypes.WAX_ON, Kind.GLOW, 0xFFB040, 0.09F, 16, false);
        spec(ParticleTypes.WAX_OFF, Kind.GLOW, 0xE8F4FF, 0.09F, 16, false);
        spec(ParticleTypes.HEART, Kind.GLOW, 0xFF6080, 0.18F, 20, false);
        spec(ParticleTypes.INSTANT_EFFECT, Kind.GLOW, 0xFFFFFF, 0.10F, 14, true);
        spec(ParticleTypes.EFFECT, Kind.GLOW, 0xFFFFFF, 0.10F, 14, true);
        spec(ParticleTypes.BUBBLE, Kind.GLOW, 0x80C8FF, 0.07F, 14, false);
        spec(ParticleTypes.TOTEM_OF_UNDYING, Kind.SPARK, 0xC8FF60, 0.08F, 22, false);
        spec(ParticleTypes.DAMAGE_INDICATOR, Kind.SPARK, 0xB01020, 0.10F, 14, false);
        spec(ParticleTypes.CRIT, Kind.SPARK, 0xFFF0C0, 0.07F, 10, true);
        spec(ParticleTypes.ELECTRIC_SPARK, Kind.SPARK, 0xC8F0FF, 0.06F, 8, true);
        spec(ParticleTypes.ENCHANTED_HIT, Kind.SPARK, 0xC080FF, 0.07F, 10, true);
        spec(ParticleTypes.FIREWORK, Kind.SPARK, 0xFFFFE0, 0.06F, 14, true);
        spec(ParticleTypes.LAVA, Kind.SPARK, 0xFF8020, 0.10F, 18, false);
        spec(ParticleTypes.DRIPPING_LAVA, Kind.SPARK, 0xFF6010, 0.07F, 16, false);
        spec(ParticleTypes.FALLING_LAVA, Kind.SPARK, 0xFF6010, 0.07F, 16, false);
        spec(ParticleTypes.SNOWFLAKE, Kind.SPARK, 0xDDF8FF, 0.08F, 20, false);
        spec(ParticleTypes.ITEM_SNOWBALL, Kind.SPARK, 0xCFF4FF, 0.08F, 12, false);
        spec(ParticleTypes.SPLASH, Kind.SPARK, 0x60B0FF, 0.07F, 12, false);
        spec(ParticleTypes.FALLING_WATER, Kind.SPARK, 0x5090FF, 0.06F, 14, false);
        spec(ParticleTypes.DRIPPING_WATER, Kind.SPARK, 0x5090FF, 0.06F, 14, false);
        spec(ParticleTypes.ANGRY_VILLAGER, Kind.SPARK, 0xFF3020, 0.10F, 12, false);
        spec(ParticleTypes.SWEEP_ATTACK, Kind.SPARK, 0xFF2040, 0.28F, 6, true);
        spec(ParticleTypes.ITEM_SLIME, Kind.SPARK, 0x70E070, 0.08F, 14, false);
        spec(ParticleTypes.PORTAL, Kind.MOTE, 0xA040FF, 0.10F, 28, false);
        spec(ParticleTypes.REVERSE_PORTAL, Kind.MOTE, 0xC060FF, 0.10F, 28, false);
        spec(ParticleTypes.NAUTILUS, Kind.MOTE, 0x60E0D0, 0.09F, 26, true);
        spec(ParticleTypes.SOUL, Kind.MOTE, 0x60F0FF, 0.14F, 30, false);
        spec(ParticleTypes.SCULK_SOUL, Kind.MOTE, 0x40D0E0, 0.14F, 30, false);
        spec(ParticleTypes.WARPED_SPORE, Kind.MOTE, 0x40D0B0, 0.07F, 30, false);
        spec(ParticleTypes.SMOKE, Kind.SMOKE, 0x505050, 0.25F, 26, false);
        spec(ParticleTypes.LARGE_SMOKE, Kind.SMOKE, 0x303030, 0.55F, 34, false);
        spec(ParticleTypes.SQUID_INK, Kind.SMOKE, 0x0A0A12, 0.35F, 30, false);
        spec(ParticleTypes.ASH, Kind.SMOKE, 0x2A2A2A, 0.10F, 30, false);
        spec(ParticleTypes.CLOUD, Kind.SMOKE, 0xF0F0F0, 0.45F, 26, false);
        spec(ParticleTypes.POOF, Kind.SMOKE, 0xE8E8E8, 0.30F, 14, false);
        spec(ParticleTypes.WHITE_SMOKE, Kind.SMOKE, 0xF4F4F4, 0.30F, 22, false);
        spec(ParticleTypes.CAMPFIRE_COSY_SMOKE, Kind.SMOKE, 0xB0B0B0, 0.70F, 60, false);
        spec(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, Kind.SMOKE, 0xA0A0A0, 0.80F, 70, false);
        spec(ParticleTypes.SMALL_GUST, Kind.SMOKE, 0xF0F8FF, 0.35F, 10, false);
        spec(ParticleTypes.GUST_EMITTER_SMALL, Kind.RING, 0xF0F8FF, 0.30F, 8, false);
        spec(ParticleTypes.GUST, Kind.RING, 0xF0F8FF, 0.40F, 8, false);
        spec(ParticleTypes.FLAME, Kind.FLAME, 0xFFB040, 0.14F, 18, false);
        spec(ParticleTypes.SMALL_FLAME, Kind.FLAME, 0xFFB040, 0.09F, 14, false);
        spec(ParticleTypes.SOUL_FIRE_FLAME, Kind.FLAME, 0x60E0FF, 0.14F, 18, false);
        spec(ParticleTypes.BUBBLE_COLUMN_UP, Kind.MOTE, 0x90D8FF, 0.07F, 20, false);
        spec(ParticleTypes.SONIC_BOOM, Kind.RING, 0x60E0FF, 0.9F, 12, false);
        spec(ParticleTypes.WHITE_ASH, Kind.SMOKE, 0xE8E8E8, 0.08F, 30, false);
        spec(ParticleTypes.DRIPPING_HONEY, Kind.SPARK, 0xFFB030, 0.07F, 16, false);
        spec(ParticleTypes.NOTE, Kind.GLOW, 0x60FF60, 0.14F, 16, true);
        spec(ParticleTypes.EXPLOSION, Kind.BLAST, 0xFFC080, 1.2F, 6, false);
        spec(ParticleTypes.EXPLOSION_EMITTER, Kind.BLAST, 0xFFC080, 2.4F, 8, false);
    }

    private static void spec(ParticleType<?> type, Kind kind, int rgb, float size, int life, boolean tinted) {
        SPECS.put(type, new Spec(kind, rgb, size, life, tinted));
    }

    private SpellVfx() {
    }

    /**
     * The mod's version of vanilla's "block broke" level event (2001): the block's own break sound and a spray of
     * textured chips, instead of vanilla's flat crumbs.
     */
    public static void blockBreak(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                  net.minecraft.world.level.block.state.BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || state.isAir()) {
            return;
        }
        net.minecraft.world.level.block.SoundType sound = state.getSoundType();
        server.playSound(null, pos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        server.sendParticles(new SpellVfxOptions(Kind.CHUNK, net.minecraft.world.level.block.Block.getId(state), 0.1F, 30),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.25, 0.25, 0.25, 0.12);
    }

    /** The spell effect that replaces {@code vanilla}, in its own default colour. */
    public static ParticleOptions swap(ParticleOptions vanilla) {
        return swap(vanilla, -1, 1.0F, false);
    }

    /** The spell effect that replaces {@code vanilla}; generic magic (sparkles, glows, motes) takes the spell's colour. */
    public static ParticleOptions swap(ParticleOptions vanilla, SpellType spell) {
        return swap(vanilla, spell == null ? -1 : lightRgb(spell), 1.0F, false);
    }

    /**
     * The smaller version for a beam's trail: a trail particle is spawned every tick, so it is kept small, and
     * a blast becomes a spark (Bombarda's trail used the explosion puff).
     */
    public static ParticleOptions trail(ParticleOptions vanilla, SpellType spell) {
        return swap(vanilla, lightRgb(spell), 0.8F, true);
    }

    /** A plain glow in a colour. */
    public static SpellVfxOptions glow(int rgb, float size, int lifetime) {
        return new SpellVfxOptions(Kind.GLOW, rgb, size, lifetime);
    }

    /** The spell's own glow (what a coloured dust particle used to be). */
    public static SpellVfxOptions glow(SpellType spell, float size, int lifetime) {
        return glow(lightRgb(spell), size, lifetime);
    }

    private static ParticleOptions swap(ParticleOptions vanilla, int tint, float scale, boolean trail) {
        if (vanilla instanceof SpellVfxOptions) {
            return vanilla;
        }
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(vanilla.getType());
        if (key == null || !"minecraft".equals(key.getNamespace())) {
            return vanilla; // the mod's own particles (MagicParticles, the Fx kinds) already look the way they should
        }
        if (vanilla instanceof DustParticleOptions dust) {
            Vector3f c = dust.getColor();
            if (Math.max(c.x(), Math.max(c.y(), c.z())) < 0.6F) {
                // Dark dust (blood, chains, ink) cannot be light: a small dark puff instead.
                return new SpellVfxOptions(Kind.SMOKE, rgb(c), 0.12F * Math.max(0.5F, dust.getScale()) * scale, 20);
            }
            return glow(rgb(c), 0.16F * Math.max(0.5F, dust.getScale()) * scale, 16);
        }
        if (vanilla instanceof DustColorTransitionOptions dust) {
            return glow(rgb(dust.getFromColor()), 0.16F * Math.max(0.5F, dust.getScale()) * scale, 18);
        }
        ParticleType<?> type = vanilla.getType();
        if (vanilla instanceof net.minecraft.core.particles.BlockParticleOption block
                && type != ParticleTypes.BLOCK_MARKER) {
            // Crumbs of a block become small tumbling 3D chips of that block.
            return new SpellVfxOptions(Kind.CHUNK, net.minecraft.world.level.block.Block.getId(block.getState()),
                    0.09F, 30);
        }
        if (type == ParticleTypes.ITEM || type == ParticleTypes.BLOCK_MARKER) {
            return vanilla; // an item's own picture, or the barrier marker: nothing to draw instead
        }
        Spec spec = SPECS.getOrDefault(type, DEFAULT);
        Kind kind = spec.kind();
        float size = spec.size() * scale;
        int life = spec.life();
        if (trail) {
            if (kind == Kind.BLAST || kind == Kind.RING) {
                kind = Kind.SPARK;
                size = 0.1F;
                life = 12;
            } else if (kind == Kind.SMOKE) {
                size = Math.min(size, 0.3F);
            }
        }
        int rgb = spec.tinted() && tint >= 0 ? tint : spec.rgb();
        SpellVfxOptions options = new SpellVfxOptions(kind, rgb, size, life);
        return type.getOverrideLimiter() ? options.alwaysShown() : options;
    }

    /** The spell's colour lifted so it reads as light (a dark spell keeps its hue at a usable brightness). */
    public static int lightRgb(SpellType spell) {
        float r = spell.getR();
        float g = spell.getG();
        float b = spell.getB();
        float max = Math.max(r, Math.max(g, b));
        if (max < 0.05F) {
            return 0x9999A6;
        }
        if (max < 0.7F) {
            float lift = 0.7F / max;
            r *= lift;
            g *= lift;
            b *= lift;
        }
        return rgb(r, g, b);
    }

    private static int rgb(Vector3f c) {
        return rgb(c.x(), c.y(), c.z());
    }

    private static int rgb(float r, float g, float b) {
        int ri = Math.round(Math.max(0.0F, Math.min(1.0F, r)) * 255.0F);
        int gi = Math.round(Math.max(0.0F, Math.min(1.0F, g)) * 255.0F);
        int bi = Math.round(Math.max(0.0F, Math.min(1.0F, b)) * 255.0F);
        return ri << 16 | gi << 8 | bi;
    }
}
