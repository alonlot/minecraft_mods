package com.hogwartsmagic.client.fx.bloom;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which of the mod's render types glow. A batch drawn with one of them is drawn a second time into the glow buffer
 * ({@link Bloom}), so only the magic and the fire bloom, never the sky, snow or white wool.
 *
 * <ul>
 *   <li>{@link #GLOW}: spell light (beams, strikes, clashes, wand trails, the Killing Curse gathering, Protego and the
 *   Ward, the Patronus and its shield, the Dark Mark's light). Colour only.</li>
 *   <li>{@link #FIRE}: Fiendfyre and dragon fire. Colour, and its alpha marks the air over it for the heat haze.</li>
 *   <li>{@link #OCCLUDE}: the dark smoke and shrouds drawn over the light (a curse's black sheath, the Dark Mark's
 *   skull smoke, Fiendfyre's char). They dim the glow behind them, as they dim the light itself, and never glow.</li>
 * </ul>
 *
 * The decision is made once per render type instance, from its name (every mod type is created under one), and cached.
 */
final class GlowRouting {
    static final int NONE = 0;
    static final int GLOW = 1;
    static final int FIRE = 2;
    static final int OCCLUDE = 3;

    /** Render types are long-lived (memoised or static); a type made fresh every frame would grow this without end. */
    private static final int MAX_CACHED = 1024;

    private static final Map<String, Integer> BY_NAME = Map.ofEntries(
            Map.entry("hogwartsmagic_avada_light", GLOW),
            Map.entry("hogwartsmagic_protego_light", GLOW),
            Map.entry("hogwartsmagic_patronus_light", GLOW),
            Map.entry("hogwartsmagic_mark_glow", GLOW),
            Map.entry("hogwartsmagic_magic_glow", GLOW),
            Map.entry("hogwartsmagic_fiendfyre_glow", FIRE),
            Map.entry("hogwartsmagic_fire_glow", FIRE),
            Map.entry("hogwartsmagic_avada_shadow", OCCLUDE),
            Map.entry("hogwartsmagic_mark_smoke", OCCLUDE),
            Map.entry("hogwartsmagic_fiendfyre_smoke", OCCLUDE));

    /** The Patronus draws with vanilla's (memoised) translucent emissive type on its own two textures. */
    private static final String EMISSIVE = "entity_translucent_emissive";
    private static final ResourceLocation PATRONUS = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/entity/patronus.png");
    private static final ResourceLocation PATRONUS_FORM = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/entity/patronus_form.png");

    private static final Map<RenderType, Integer> CACHE = new IdentityHashMap<>();

    private GlowRouting() {
    }

    static int of(RenderType type) {
        Integer route = CACHE.get(type);
        if (route == null) {
            if (CACHE.size() >= MAX_CACHED) {
                CACHE.clear();
            }
            route = classify(type);
            CACHE.put(type, route);
        }
        return route;
    }

    private static int classify(RenderType type) {
        String name = nameOf(type);
        Integer route = BY_NAME.get(name);
        if (route != null) {
            return route;
        }
        if (EMISSIVE.equals(name) && (type == RenderType.entityTranslucentEmissive(PATRONUS)
                || type == RenderType.entityTranslucentEmissive(PATRONUS_FORM))) {
            return GLOW;
        }
        return NONE;
    }

    /** A composite type prints as {@code RenderType[name:state]}, a plain one as its name. */
    private static String nameOf(RenderType type) {
        String s = String.valueOf(type);
        String prefix = "RenderType[";
        if (s.startsWith(prefix)) {
            int end = s.indexOf(':', prefix.length());
            return end > 0 ? s.substring(prefix.length(), end) : s.substring(prefix.length());
        }
        return s;
    }
}
