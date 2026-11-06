package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.client.FlyingMobAnimation;
import com.hogwartsmagic.client.model.BasiliskAnimations;
import com.hogwartsmagic.client.model.DementorAnimations;
import com.hogwartsmagic.client.model.GiantAnimations;
import com.hogwartsmagic.client.model.GoblinAnimations;
import com.hogwartsmagic.client.model.GrindylowAnimations;
import com.hogwartsmagic.client.model.HouseElfAnimations;
import com.hogwartsmagic.client.model.InferiusAnimations;
import com.hogwartsmagic.client.model.MerpersonAnimations;
import com.hogwartsmagic.client.model.TrollAnimations;
import com.hogwartsmagic.client.model.WhulvkLycanHumanAnimations;
import com.hogwartsmagic.client.model.WhulvkLycanSlowHumanToWolfAnimations;
import com.hogwartsmagic.client.model.WhulvkLycanWolfIdleAnimations;
import com.hogwartsmagic.client.model.WhulvkLycanWolfToIdleAnimations;
import com.hogwartsmagic.client.model.WhulvkLycanWolfWalkAnimations;
import com.mojang.logging.LogUtils;
import net.minecraft.client.animation.AnimationDefinition;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mod creature animations, by entity id and animation name, so a scene can make a Basilisk {@code REAR} or a
 * Troll {@code SLAM_STRIKE}. The names are the field names of the generated animation classes; a lookup ignores
 * case. Entities with no animation class (hippogriff, owl) have none.
 *
 * <p>Reflection runs once per entity id and is cached; a class that cannot be read yields no animations (and says
 * so in the log). Nothing here throws.
 */
public final class ActorAnimationLibrary {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Entity id (no namespace) to the classes holding its animations. */
    private static final Map<String, Class<?>[]> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("basilisk", new Class<?>[]{BasiliskAnimations.class});
        CLASSES.put("dementor", new Class<?>[]{DementorAnimations.class});
        CLASSES.put("giant", new Class<?>[]{GiantAnimations.class});
        CLASSES.put("goblin", new Class<?>[]{GoblinAnimations.class});
        CLASSES.put("goblin_guard", new Class<?>[]{GoblinAnimations.class});
        CLASSES.put("grindylow", new Class<?>[]{GrindylowAnimations.class});
        CLASSES.put("house_elf", new Class<?>[]{HouseElfAnimations.class});
        CLASSES.put("inferius", new Class<?>[]{InferiusAnimations.class});
        CLASSES.put("merperson", new Class<?>[]{MerpersonAnimations.class});
        CLASSES.put("troll", new Class<?>[]{TrollAnimations.class});
        CLASSES.put("voldemort", new Class<?>[]{com.hogwartsmagic.client.model.VoldemortAnimations.class});
        CLASSES.put("lycanthrope_villager", new Class<?>[]{WhulvkLycanHumanAnimations.class,
                WhulvkLycanSlowHumanToWolfAnimations.class, WhulvkLycanWolfIdleAnimations.class,
                WhulvkLycanWolfToIdleAnimations.class, WhulvkLycanWolfWalkAnimations.class});
        CLASSES.put("flying_mob", new Class<?>[]{FlyingMobAnimation.class});
    }

    /** Entity id to field name to animation, in declaration order. */
    private static final Map<String, Map<String, AnimationDefinition>> CACHE = new HashMap<>();
    /** Entity id to lower-cased name to animation. */
    private static final Map<String, Map<String, AnimationDefinition>> FOLDED = new HashMap<>();

    private ActorAnimationLibrary() {
    }

    /** The entity ids that have named animations. */
    public static Set<String> entities() {
        return Collections.unmodifiableSet(CLASSES.keySet());
    }

    public static Optional<AnimationDefinition> find(String entityType, String name) {
        if (name == null) return Optional.empty();
        Map<String, AnimationDefinition> folded = load(key(entityType), true);
        return Optional.ofNullable(folded.get(name.toLowerCase(Locale.ROOT)));
    }

    /** The animation names an entity type has, as written in the animation classes. Empty when none. */
    public static Set<String> names(String entityType) {
        return Collections.unmodifiableSet(load(key(entityType), false).keySet());
    }

    private static String key(String entityType) {
        if (entityType == null) return "";
        String k = entityType.toLowerCase(Locale.ROOT);
        int colon = k.indexOf(':');
        return colon >= 0 ? k.substring(colon + 1) : k;
    }

    private static synchronized Map<String, AnimationDefinition> load(String key, boolean folded) {
        Map<String, AnimationDefinition> cached = (folded ? FOLDED : CACHE).get(key);
        if (cached != null) return cached;
        Map<String, AnimationDefinition> byName = new LinkedHashMap<>();
        Map<String, AnimationDefinition> lower = new HashMap<>();
        Class<?>[] classes = CLASSES.get(key);
        if (classes != null) {
            for (Class<?> c : classes) {
                try {
                    for (Field f : c.getDeclaredFields()) {
                        int mod = f.getModifiers();
                        if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || !Modifier.isFinal(mod)
                                || f.getType() != AnimationDefinition.class) {
                            continue;
                        }
                        Object v = f.get(null);
                        if (v instanceof AnimationDefinition def) {
                            byName.put(f.getName(), def);
                            lower.putIfAbsent(f.getName().toLowerCase(Locale.ROOT), def);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Pensieve: cannot read the animations of {}: {}", c.getName(), t.toString());
                }
            }
        }
        FOLDED.put(key, lower);
        CACHE.put(key, byName);
        return folded ? lower : byName;
    }
}
