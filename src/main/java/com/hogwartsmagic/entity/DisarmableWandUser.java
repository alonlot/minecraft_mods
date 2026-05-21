package com.hogwartsmagic.entity;

import net.minecraft.world.entity.Mob;

/**
 * Implemented by NPCs that wield a wand and can be disarmed by Expelliarmus. When disarmed,
 * the entity's top-priority goal becomes recovering the dropped wand (see {@link RetrieveDroppedWandGoal}).
 */
public interface DisarmableWandUser {
    boolean isDisarmed();

    void setDisarmed(boolean disarmed);

    /** The implementing entity, narrowed to {@link Mob} since the goal needs it for navigation/inventory. */
    Mob asMob();
}
