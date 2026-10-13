package com.hogwartsmagic.spell.summon;

/**
 * The shapes a corporeal Patronus can take. GENERIC is the indistinct stag that Novice and Adept casters
 * summon; the other eight are personal forms (Expert and up). The id is what PatronusEntity syncs to
 * clients and what PlayerPersisted stores, so ids never change meaning.
 */
public enum PatronusForm {
    GENERIC(0, "generic"),
    STAG(1, "stag"),
    DOE(2, "doe"),
    HARE(3, "hare"),
    WOLF(4, "wolf"),
    CAT(5, "cat"),
    OWL(6, "owl"),
    FOX(7, "fox"),
    PHOENIX(8, "phoenix");

    private final int id;
    private final String key;

    PatronusForm(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return this.id;
    }

    public String langKey() {
        return "patronus.hogwartsmagic.form." + this.key;
    }

    public static PatronusForm byId(int id) {
        for (PatronusForm form : values()) {
            if (form.id == id) {
                return form;
            }
        }
        return GENERIC;
    }
}
