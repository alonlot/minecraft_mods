package com.hogwartsmagic.client;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * The sound of a cast, on top of each spell's own sound (played by the server): the whoosh of the swing (light,
 * heavy or rising, by gesture), the crack as the spell leaves the wand (darker for the curses), and the hit (a
 * deep blast for the heavy spells). Several takes of each, at a slightly different pitch every time
 * (tools/gen_cast_sounds.py). Client-side and cosmetic.
 */
public final class CastSounds {

    private static final Set<SpellType> DARK = EnumSet.of(SpellType.AVADA_KEDAVRA, SpellType.CRUCIO,
            SpellType.IMPERIO, SpellType.SECTUMSEMPRA, SpellType.DOLOHOV_CURSE, SpellType.MORSMORDRE,
            SpellType.TENEBRIS_FLIGHT, SpellType.SERPENSORTIA);
    private static final Set<SpellType> HEAVY = EnumSet.of(SpellType.BOMBARDA, SpellType.CONFRINGO,
            SpellType.DEPULSO, SpellType.STUPEFY, SpellType.AVADA_KEDAVRA, SpellType.EXPELLIARMUS,
            SpellType.FIENDFYRE);
    private static final Set<FirstPersonCast.Gesture> HEAVY_SWING = Set.of(FirstPersonCast.SLASH,
            FirstPersonCast.DOUBLE_SLASH, FirstPersonCast.WHIP, FirstPersonCast.CURSE, FirstPersonCast.PUSH);
    private static final Set<FirstPersonCast.Gesture> RISING_SWING = Set.of(FirstPersonCast.RAISE,
            FirstPersonCast.SWISH_FLICK);

    private CastSounds() {
    }

    /** {@code caster} swings its wand to cast {@code spell}. */
    public static void swing(Entity caster, SpellType spell) {
        FirstPersonCast.Gesture gesture = FirstPersonCast.forSpell(spell);
        SoundEvent sound = HEAVY_SWING.contains(gesture) ? ModSounds.CAST_SWISH_HEAVY.get()
                : RISING_SWING.contains(gesture) ? ModSounds.CAST_SWISH_RISE.get()
                : ModSounds.CAST_SWISH_LIGHT.get();
        Vec3 at = caster.position().add(0.0, caster.getEyeHeight() * 0.8, 0.0);
        play(at, sound, 0.5F, caster instanceof Player);
        if (CastDelay.anticipation(spell) > 0.0F) {
            // A fighting spell: the wand whips through with a snap as it strikes.
            play(at, ModSounds.CAST_WHOOSH_SNAP.get(), 0.6F, caster instanceof Player);
        }
    }

    /** {@code spell} leaves a wand at {@code at}. */
    public static void release(Vec3 at, SpellType spell, boolean player) {
        SoundEvent sound = spell != null && DARK.contains(spell) ? ModSounds.CAST_RELEASE_DARK.get()
                : ModSounds.CAST_RELEASE.get();
        play(at, sound, 0.65F, player);
        // Under the crack, a deep punch: heavier for the hard-hitting spells.
        play(at, ModSounds.CAST_RELEASE_PUNCH.get(), spell != null && HEAVY.contains(spell) ? 0.85F : 0.5F, player);
    }

    /** A spell strikes at {@code at}; {@code spell} may be unknown (null). */
    public static void impact(Vec3 at, SpellType spell) {
        boolean heavy = spell != null && HEAVY.contains(spell);
        play(at, heavy ? ModSounds.CAST_IMPACT_HEAVY.get() : ModSounds.CAST_IMPACT.get(), heavy ? 0.9F : 0.7F, true);
        // The blow itself: a crack and a thud under the burst.
        play(at, ModSounds.CAST_IMPACT_CRUNCH.get(), heavy ? 0.9F : 0.65F, true);
    }

    private static void play(Vec3 at, SoundEvent sound, float volume, boolean player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || com.hogwartsmagic.client.pensieve.PensieveClientState.active()) {
            return; // a memory has its own authored sound
        }
        RandomSource random = minecraft.level.random;
        minecraft.level.playLocalSound(at.x, at.y, at.z, sound, player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                volume, 0.92F + random.nextFloat() * 0.16F, false);
    }
}
