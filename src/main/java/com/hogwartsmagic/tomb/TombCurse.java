package com.hogwartsmagic.tomb;

import net.minecraft.util.StringRepresentable;

/**
 * The curses a tomb's glyph tiles can carry. Each goes off once.
 *
 * <p>Each has its own colour and sigil, used by every effect it makes: the sigil that floats over a
 * revealed tile, the rune circle when it goes off, the light it throws. So a player who has learned
 * the colours can read a revealed tile before deciding whether to lift it.
 */
public enum TombCurse implements StringRepresentable {
    /** The ceiling comes down: sand falls round the player. Amber, the djed pillar. */
    COLLAPSE("collapse", 0xFFB040, 4),
    /** The dead get up: husks and an Inferius climb out of the floor. Necrotic green, the ankh. */
    RISEN("risen", 0x5CFFA8, 0),
    /** The pharaoh's curse: Wither, Hunger and Mining Fatigue. Violet, the Eye of Horus. */
    WITHERING("withering", 0xB060FF, 1);

    private final String name;
    private final int rgb;
    private final int sigil;

    TombCurse(String name, int rgb, int sigil) {
        this.name = name;
        this.rgb = rgb;
        this.sigil = sigil;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The curse's colour, 0xRRGGBB. */
    public int rgb() {
        return rgb;
    }

    /** Its sigil: an index into textures/fx/curse_sigils.png (see build_scripts/generate_curse_sigils.py). */
    public int sigil() {
        return sigil;
    }

    public static TombCurse byId(int id) {
        TombCurse[] all = values();
        return id >= 0 && id < all.length ? all[id] : COLLAPSE;
    }
}
