package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.mixin.client.ModelPartAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Finds a model limb by name. Hierarchical models: every part below the root, by the name it was baked with.
 * Humanoid models: head, hat, body, right_arm, left_arm, right_leg, left_leg (plus the five outer layers of a
 * player model). Anything else: every {@link ModelPart} field, with camelCase turned into snake_case. In every
 * case the children of each part found are walked too.
 *
 * <p>Nothing here throws into the render path: a model that cannot be read yields an empty index (and says so
 * once). The reflection plan is cached per model class, the result per model instance.
 */
public final class ModelPartIndex {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The named parts, and every part (named or not) the model owns, for snapshotting. */
    public record Index(Map<String, ModelPart> byName, List<ModelPart> all) {
        static final Index EMPTY = new Index(Map.of(), List.of());
    }

    private record FieldRef(String name, Field field) {
    }

    private static final Map<Class<?>, List<FieldRef>> PLAN = new HashMap<>();
    private static final Map<EntityModel<?>, Index> BY_MODEL = new WeakHashMap<>();
    private static final Set<Class<?>> WARNED = new HashSet<>();

    private ModelPartIndex() {
    }

    /** Part name to part. Never null. */
    public static Map<String, ModelPart> of(EntityModel<?> model) {
        return index(model).byName();
    }

    /** Every part the model owns, once each. Never null. */
    public static List<ModelPart> allParts(EntityModel<?> model) {
        return index(model).all();
    }

    public static synchronized Index index(EntityModel<?> model) {
        if (model == null) return Index.EMPTY;
        Index cached = BY_MODEL.get(model);
        if (cached != null) return cached;
        Index built;
        try {
            built = build(model);
        } catch (Throwable t) {
            warnOnce(model.getClass(), t);
            built = Index.EMPTY;
        }
        BY_MODEL.put(model, built);
        return built;
    }

    private static Index build(EntityModel<?> model) {
        Map<String, ModelPart> named = new LinkedHashMap<>();
        List<ModelPart> roots = new ArrayList<>();
        if (model instanceof HierarchicalModel<?> h) {
            ModelPart root = h.root();
            roots.add(root);
            walkChildren(root, named);
        } else if (model instanceof HumanoidModel<?> hm) {
            put(named, roots, "head", hm.head);
            put(named, roots, "hat", hm.hat);
            put(named, roots, "body", hm.body);
            put(named, roots, "right_arm", hm.rightArm);
            put(named, roots, "left_arm", hm.leftArm);
            put(named, roots, "right_leg", hm.rightLeg);
            put(named, roots, "left_leg", hm.leftLeg);
            if (model instanceof PlayerModel<?> pm) {
                put(named, roots, "jacket", pm.jacket);
                put(named, roots, "left_sleeve", pm.leftSleeve);
                put(named, roots, "right_sleeve", pm.rightSleeve);
                put(named, roots, "left_pants", pm.leftPants);
                put(named, roots, "right_pants", pm.rightPants);
            }
        } else {
            for (FieldRef ref : planFor(model.getClass())) {
                try {
                    Object v = ref.field().get(model);
                    if (v instanceof ModelPart part) put(named, roots, ref.name(), part);
                } catch (Throwable t) {
                    warnOnce(model.getClass(), t);
                }
            }
        }
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ModelPart> all = new ArrayList<>();
        for (ModelPart r : roots) {
            r.getAllParts().forEach(p -> {
                if (seen.add(p)) all.add(p);
            });
        }
        return new Index(Collections.unmodifiableMap(named), List.copyOf(all));
    }

    private static void put(Map<String, ModelPart> named, List<ModelPart> roots, String name, ModelPart part) {
        if (part == null) return;
        named.putIfAbsent(name, part);
        roots.add(part);
        walkChildren(part, named);
    }

    private static void walkChildren(ModelPart part, Map<String, ModelPart> out) {
        Map<String, ModelPart> kids = ((ModelPartAccessor) (Object) part).hogwartsmagic$children();
        for (Map.Entry<String, ModelPart> e : kids.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
            walkChildren(e.getValue(), out);
        }
    }

    private static List<FieldRef> planFor(Class<?> type) {
        List<FieldRef> plan = PLAN.get(type);
        if (plan != null) return plan;
        plan = new ArrayList<>();
        try {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !ModelPart.class.isAssignableFrom(f.getType())) continue;
                    try {
                        f.setAccessible(true);
                        plan.add(new FieldRef(PoseMath.snake(f.getName()), f));
                    } catch (Throwable t) {
                        warnOnce(type, t);
                    }
                }
            }
        } catch (Throwable t) {
            warnOnce(type, t);
        }
        plan = List.copyOf(plan);
        PLAN.put(type, plan);
        return plan;
    }

    private static void warnOnce(Class<?> type, Throwable t) {
        if (WARNED.add(type)) LOGGER.warn("Pensieve: cannot index the limbs of {}: {}", type.getName(), t.toString());
    }
}
