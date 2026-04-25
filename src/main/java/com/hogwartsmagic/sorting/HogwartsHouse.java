package com.hogwartsmagic.sorting;

public enum HogwartsHouse {
    GRYFFINDOR,
    RAVENCLAW,
    HUFFLEPUFF,
    SLYTHERIN;

    public static HogwartsHouse fromModelText(String value) {
        if (value == null) {
            return GRYFFINDOR;
        }
        String normalized = value.trim().toUpperCase();
        for (HogwartsHouse house : values()) {
            if (normalized.contains(house.name())) {
                return house;
            }
        }
        return GRYFFINDOR;
    }

    public String displayName() {
        String lower = name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
