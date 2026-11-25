package com.hogwartsmagic.cosy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a Bertie Bott's bean can taste of. Weights add up to 100: good flavours 40, neutral 30, silly 30. A flavour
 * with no effect is neutral or a plain joke; the {@code burp} flavour is special-cased by the item (a sound).
 * The action-bar line for a flavour is the lang key {@code bean.hogwartsmagic.<flavourKey>}.
 *
 * <p>The effect is a registry id, not a {@code Holder}: this table is pure, so JUnit can load it (Forge's
 * bootstrap cannot run in a plain unit test), and {@link BertieBottsItem} looks the effect up when a bean is eaten.
 */
public final class BeanTable {
    public static final int GOOD = 0;
    public static final int NEUTRAL = 1;
    public static final int SILLY = 2;

    /** How many beans a fresh box holds. */
    public static final int BOX_SIZE = 10;
    /** The flavour that makes the eater burp. */
    public static final String BURP = "burp";

    /** One flavour: its lang key, weight, the id of the effect it gives (or null) with its length in ticks and amplifier. */
    public record Outcome(String flavourKey, int weight, @Nullable ResourceLocation effect, int ticks, int amp) {
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static final List<Outcome> GOOD_LIST = List.of(
            new Outcome("toffee", 8, mc("speed"), 300, 0),
            new Outcome("peppermint", 6, mc("night_vision"), 600, 0),
            new Outcome("honeycomb", 8, mc("regeneration"), 100, 0),
            new Outcome("lemon_sherbet", 6, mc("jump_boost"), 300, 1),
            new Outcome("treacle_fudge", 6, mc("saturation"), 40, 0),
            new Outcome("candy_floss", 6, mc("speed"), 200, 1));

    private static final List<Outcome> NEUTRAL_LIST = List.of(
            new Outcome("marshmallow", 6, null, 0, 0),
            new Outcome("buttered_toast", 6, null, 0, 0),
            new Outcome("cabbage", 6, null, 0, 0),
            new Outcome("parsley", 6, null, 0, 0),
            new Outcome("plain_vanilla", 6, null, 0, 0));

    private static final List<Outcome> SILLY_LIST = List.of(
            new Outcome("earwax", 6, mc("nausea"), 140, 0),
            new Outcome("soap", 5, mc("nausea"), 100, 0),
            new Outcome("bogey", 4, mc("nausea"), 60, 0),
            new Outcome("fizzing_whizbee", 5, mc("levitation"), 60, 0),
            new Outcome(BURP, 6, null, 0, 0),
            new Outcome("sardine", 4, mc("nausea"), 80, 0));

    public static final List<Outcome> ALL;
    private static final int TOTAL_WEIGHT;

    static {
        List<Outcome> all = new ArrayList<>(GOOD_LIST);
        all.addAll(NEUTRAL_LIST);
        all.addAll(SILLY_LIST);
        ALL = Collections.unmodifiableList(all);
        int total = 0;
        for (Outcome o : all) {
            total += o.weight();
        }
        TOTAL_WEIGHT = total;
    }

    private BeanTable() {
    }

    /** GOOD, NEUTRAL or SILLY. */
    public static int category(Outcome outcome) {
        if (GOOD_LIST.contains(outcome)) {
            return GOOD;
        }
        return NEUTRAL_LIST.contains(outcome) ? NEUTRAL : SILLY;
    }

    /** A random flavour, by weight. */
    public static Outcome roll(RandomSource r) {
        int pick = r.nextInt(TOTAL_WEIGHT);
        for (Outcome o : ALL) {
            pick -= o.weight();
            if (pick < 0) {
                return o;
            }
        }
        return ALL.get(ALL.size() - 1);
    }
}
