package com.hogwartsmagic.fx;

import com.hogwartsmagic.entity.AcromantulaEntity;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.GiantEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.TrollEntity;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import net.minecraft.world.entity.Entity;

/**
 * The great beasts whose first sighting is marked ({@link BossIntroDetector} on the server, the title card on the
 * client). A beast is its index in {@link #IDS}; the id is also the lang key stem
 * ({@code hogwartsmagic.boss_intro.<id>.name} and {@code .sub}) and the key remembered on the player. Common code:
 * no client class.
 */
public final class BossBeasts {
    public static final String[] IDS = {
            "hungarian_horntail", "ukrainian_ironbelly", "basilisk", "giant", "acromantula", "troll", "voldemort"
    };
    public static final int HORNTAIL = 0;
    public static final int IRONBELLY = 1;
    public static final int BASILISK = 2;
    public static final int GIANT = 3;
    public static final int ACROMANTULA = 4;
    public static final int TROLL = 5;
    public static final int VOLDEMORT = 6;

    private BossBeasts() {
    }

    /** The index of the beast {@code entity} is, or -1 if it is not one (a Pensieve memory's dragon is not). */
    public static int indexOf(Entity entity) {
        if (entity instanceof MemoryHorntailEntity) {
            return -1;
        }
        if (entity instanceof UkrainianIronbellyEntity) {
            return IRONBELLY;
        }
        if (entity instanceof HungarianHorntailEntity) {
            return HORNTAIL;
        }
        if (entity instanceof BasiliskEntity) {
            return BASILISK;
        }
        if (entity instanceof GiantEntity) {
            return GIANT;
        }
        if (entity instanceof AcromantulaEntity) {
            return ACROMANTULA;
        }
        if (entity instanceof TrollEntity) {
            return TROLL;
        }
        if (entity instanceof VoldemortEntity) {
            return VOLDEMORT;
        }
        return -1;
    }

    public static boolean isDragon(int index) {
        return index == HORNTAIL || index == IRONBELLY;
    }

    public static String id(int index) {
        return index >= 0 && index < IDS.length ? IDS[index] : null;
    }
}
