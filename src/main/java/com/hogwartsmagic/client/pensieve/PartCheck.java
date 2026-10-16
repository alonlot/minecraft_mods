package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Preview sessions only: a limb, pose part or animation name the actor model does not have is reported in chat,
 * the way the scene validator reports a bad id, so a typo does not just silently do nothing. The server cannot
 * run this check (it has no models), so it happens here as the memory starts.
 */
final class PartCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_LINES = 12;

    private PartCheck() {
    }

    static void run(SceneDef scene, List<ActorRuntime> actors) {
        try {
            List<String> problems = new ArrayList<>();
            for (ActorRuntime a : actors) {
                int index = indexOf(scene, a.def);
                check(scene, a, "actors[" + index + "]", problems);
            }
            report(problems);
        } catch (Throwable t) {
            LOGGER.warn("Pensieve: part check failed: {}", t.toString());
        }
    }

    private static int indexOf(SceneDef scene, SceneDef.ActorDef def) {
        for (int i = 0; i < scene.actors().size(); i++) {
            if (scene.actors().get(i) == def) return i;
        }
        return -1;
    }

    private static void check(SceneDef scene, ActorRuntime a, String at, List<String> out) {
        SceneDef.ActorDef def = a.def;
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(a.entity);
        EntityModel<?> model = renderer instanceof LivingEntityRenderer<?, ?> lr ? lr.getModel() : null;
        Map<String, ModelPart> parts = ModelPartIndex.of(model);
        String has = list(parts.keySet());

        for (String limb : def.limbs().keySet()) {
            if (!parts.containsKey(limb) && !parts.containsKey(PoseMath.snake(limb))) {
                out.add(at + ".limbs." + limb + ": no part '" + limb + "' on " + def.type() + " (has: " + has + ")");
            }
        }
        for (int j = 0; j < def.poses().size(); j++) {
            String name = def.poses().get(j).name();
            Map<String, float[]> custom = scene.poses().get(name);
            if (custom == null && PosePresets.get(name) == null) {
                TreeSet<String> known = new TreeSet<>(PosePresets.names());
                known.addAll(scene.poses().keySet());
                out.add(at + ".poses[" + j + "]: no pose '" + name + "' (has: " + list(known) + ")");
            } else if (custom != null) {
                for (String part : custom.keySet()) {
                    if (!parts.containsKey(part)) {
                        out.add(at + ".poses." + name + "." + part + ": no part '" + part + "' on " + def.type()
                                + " (has: " + has + ")");
                    }
                }
            }
        }
        for (int j = 0; j < def.anims().size(); j++) {
            String name = def.anims().get(j).name();
            if (ActorAnimationLibrary.find(def.type(), name).isEmpty()) {
                out.add(at + ".anims[" + j + "]: no animation '" + name + "' on " + def.type() + " (has: "
                        + list(ActorAnimationLibrary.names(def.type())) + ")");
            } else if (!(model instanceof HierarchicalModel<?>)) {
                out.add(at + ".anims[" + j + "]: " + def.type() + " cannot play named animations");
            }
        }
    }

    private static String list(Iterable<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(n);
        }
        return sb.isEmpty() ? "none" : sb.toString();
    }

    private static void report(List<String> problems) {
        if (problems.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < problems.size() && i < MAX_LINES; i++) {
            LOGGER.warn("Pensieve preview: {}", problems.get(i));
            mc.gui.getChat().addMessage(Component.literal("[Pensieve] " + problems.get(i)).withStyle(ChatFormatting.RED));
        }
        if (problems.size() > MAX_LINES) {
            mc.gui.getChat().addMessage(Component.literal("[Pensieve] ...and " + (problems.size() - MAX_LINES) + " more")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
