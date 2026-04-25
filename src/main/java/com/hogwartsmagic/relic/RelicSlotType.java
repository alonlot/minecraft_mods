package com.hogwartsmagic.relic;

public enum RelicSlotType {
    CAPACITY(0, "capacity"),
    REGEN(1, "regen");

    private final int index;
    private final String id;

    RelicSlotType(int index, String id) {
        this.index = index;
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public static RelicSlotType byIndex(int index) {
        for (RelicSlotType value : values()) {
            if (value.index == index) {
                return value;
            }
        }
        return CAPACITY;
    }
}
