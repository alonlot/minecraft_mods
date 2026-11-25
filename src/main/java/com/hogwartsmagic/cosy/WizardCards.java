package com.hogwartsmagic.cosy;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Famous Wizard Card table. Pure: no registries, so JUnit covers it. Names and one-line flavour texts live in
 * the lang file under {@code card.hogwartsmagic.<key>.name} and {@code .line}.
 */
public final class WizardCards {
    public static final int COMMON = 0;
    public static final int RARE = 1;
    public static final int LEGENDARY = 2;

    /** Roll weights per rarity; they add up to 100. */
    public static final int WEIGHT_COMMON = 70;
    public static final int WEIGHT_RARE = 25;
    public static final int WEIGHT_LEGENDARY = 5;

    /** One card: a stable id (never zero), the lang key, and the rarity tier. */
    public record Card(int id, String key, int rarity) {
    }

    public static final List<Card> ALL;

    static {
        List<Card> all = new ArrayList<>();
        // Legendary
        add(all, "merlin", LEGENDARY);
        add(all, "albus_dumbledore", LEGENDARY);
        // Rare
        add(all, "godric_gryffindor", RARE);
        add(all, "helga_hufflepuff", RARE);
        add(all, "rowena_ravenclaw", RARE);
        add(all, "salazar_slytherin", RARE);
        add(all, "nicolas_flamel", RARE);
        add(all, "morgana", RARE);
        // Common
        add(all, "circe", COMMON);
        add(all, "agrippa", COMMON);
        add(all, "ptolemy", COMMON);
        add(all, "paracelsus", COMMON);
        add(all, "cliodna", COMMON);
        add(all, "hengist_of_woodcroft", COMMON);
        add(all, "bowman_wright", COMMON);
        add(all, "elfrida_clagg", COMMON);
        add(all, "gulliver_pokeby", COMMON);
        add(all, "herpo_the_foul", COMMON);
        add(all, "bertie_bott", COMMON);
        add(all, "newt_scamander", COMMON);
        ALL = Collections.unmodifiableList(all);
    }

    private static void add(List<Card> all, String key, int rarity) {
        all.add(new Card(all.size() + 1, key, rarity));
    }

    private WizardCards() {
    }

    @Nullable
    public static Card byId(int id) {
        return id >= 1 && id <= ALL.size() ? ALL.get(id - 1) : null;
    }

    @Nullable
    public static Card byKey(String key) {
        for (Card c : ALL) {
            if (c.key().equals(key)) {
                return c;
            }
        }
        return null;
    }

    /** A random card: the tier by weight (70 / 25 / 5), then evenly among the cards of that tier. */
    public static Card roll(RandomSource r) {
        int pick = r.nextInt(WEIGHT_COMMON + WEIGHT_RARE + WEIGHT_LEGENDARY);
        int tier = pick < WEIGHT_COMMON ? COMMON : pick < WEIGHT_COMMON + WEIGHT_RARE ? RARE : LEGENDARY;
        int count = 0;
        for (Card c : ALL) {
            if (c.rarity() == tier) {
                count++;
            }
        }
        int n = r.nextInt(count);
        for (Card c : ALL) {
            if (c.rarity() == tier && n-- == 0) {
                return c;
            }
        }
        throw new IllegalStateException("no card of tier " + tier);
    }

    /** The album after taking in {@code offered}: the old set plus every offered id that is a real card. */
    public static Set<Integer> absorb(Set<Integer> album, List<Integer> offered) {
        Set<Integer> out = new LinkedHashSet<>(album);
        for (int id : offered) {
            if (byId(id) != null) {
                out.add(id);
            }
        }
        return out;
    }
}
