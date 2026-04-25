package com.hogwartsmagic.relic;

public enum RelicType {
    MINOR_MANA_CHARM("minor_mana_charm", RelicSlotType.CAPACITY, 20.0f, 1.0f, false),
    REFINED_MANA_CHARM("refined_mana_charm", RelicSlotType.CAPACITY, 35.0f, 1.0f, false),
    ARCANE_FOCUS("arcane_focus", RelicSlotType.REGEN, 0.0f, 1.18f, false),
    RAVENCLAW_DIADEM_FRAGMENT("ravenclaw_diadem_fragment", RelicSlotType.CAPACITY, 50.0f, 1.0f, true),
    ENCHANTED_INKWELL("enchanted_inkwell", RelicSlotType.REGEN, 0.0f, 1.28f, true),
    MOONLIT_PHIAL("moonlit_phial", RelicSlotType.REGEN, 0.0f, 1.36f, true);

    private final String id;
    private final RelicSlotType slotType;
    private final float maxManaBonus;
    private final float manaRegenMultiplier;
    private final boolean foundOnly;

    RelicType(String id, RelicSlotType slotType, float maxManaBonus, float manaRegenMultiplier, boolean foundOnly) {
        this.id = id;
        this.slotType = slotType;
        this.maxManaBonus = maxManaBonus;
        this.manaRegenMultiplier = manaRegenMultiplier;
        this.foundOnly = foundOnly;
    }

    public String getId() {
        return id;
    }

    public RelicSlotType getSlotType() {
        return slotType;
    }

    public float getMaxManaBonus() {
        return maxManaBonus;
    }

    public float getManaRegenMultiplier() {
        return manaRegenMultiplier;
    }

    public boolean isFoundOnly() {
        return foundOnly;
    }
}
