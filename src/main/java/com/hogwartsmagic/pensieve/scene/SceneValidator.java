package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a parsed scene against the names that exist, so a typo gets a message naming its JSON path instead of
 * a crash or a silent no-op. Pure; one message per problem.
 */
public final class SceneValidator {
    private SceneValidator() {
    }

    public static List<String> validate(SceneDef scene, SceneNames names) {
        List<String> out = new ArrayList<>();
        float length = scene.length();
        if (length <= 0) out.add("length: must be greater than 0, got " + scene.length());

        if (scene.radius() > 200) out.add("radius: must be at most 200, got " + scene.radius());
        if (scene.fogFar() < 0 || scene.fogFar() > SceneNames.MAX_FOG_FAR) {
            out.add("fog_far: must be 1 to " + SceneNames.MAX_FOG_FAR + " blocks (or left out), got " + scene.fogFar());
        }

        if (scene.next() != null) {
            if (!scene.next().matches("[a-z0-9_]+")) out.add("next: must be a lowercase scene id, got '" + scene.next() + "'");
            else if (scene.next().equals(scene.id())) out.add("next: a scene cannot chain to itself");
        }

        if (!SceneDef.BLACK.equals(scene.handoverColour()) && !SceneDef.WHITE.equals(scene.handoverColour())) {
            out.add("handover_colour: must be \"black\" or \"white\", got '" + scene.handoverColour() + "'");
        } else if (SceneDef.WHITE.equals(scene.handoverColour()) && scene.next() == null) {
            out.add("handover_colour: \"white\" needs a next scene to hand over to");
        }

        Set<String> actorIds = new HashSet<>();
        for (int i = 0; i < scene.actors().size(); i++) {
            String id = scene.actors().get(i).id();
            if (!actorIds.add(id)) out.add("actors[" + i + "].id: duplicate actor id '" + id + "'");
        }

        SceneDef.SetDef set = scene.set();
        for (int i = 0; i < set.templates().size(); i++) {
            known(out, "set.templates[" + i + "].id", "template", set.templates().get(i).id(), names.templates());
        }
        for (int i = 0; i < set.schematics().size(); i++) {
            SceneDef.SchematicRef ref = set.schematics().get(i);
            if (!SceneNames.SCHEMATICS.contains(ref.id())) {
                out.add("set.schematics[" + i + "].id: unknown schematic '" + ref.id() + "' (expected one of "
                        + sorted(SceneNames.SCHEMATICS) + ")");
            }
            if (!SceneNames.SCHEMATIC_ROTATIONS.contains(ref.rotation())) {
                out.add("set.schematics[" + i + "].rotation: must be 0, 90, 180 or 270, got " + ref.rotation());
            }
        }
        for (int i = 0; i < set.fills().size(); i++) {
            known(out, "set.fills[" + i + "].block", "block", set.fills().get(i).block(), names.blocks());
        }
        for (int i = 0; i < set.disks().size(); i++) {
            known(out, "set.disks[" + i + "].block", "block", set.disks().get(i).block(), names.blocks());
        }
        for (int i = 0; i < set.features().size(); i++) {
            known(out, "set.features[" + i + "].id", "feature", set.features().get(i).id(), names.features());
        }

        extent(out, scene.radius(), set);

        for (int i = 0; i < scene.actors().size(); i++) {
            actor(out, scene, scene.actors().get(i), "actors[" + i + "]", actorIds, names, length);
        }

        for (int i = 0; i < scene.events().size(); i++) {
            event(out, scene.events().get(i), "events[" + i + "]", names, length, scene.radius());
        }
        crowdCap(out, scene);
        for (int i = 0; i < scene.actors().size(); i++) {
            if (SceneNames.ROWBOAT_ACTOR.equals(scene.actors().get(i).type())) boatSea(out, scene, i, length);
            if (Vehicle.isVehicle(scene.actors().get(i).type())) riders(out, scene, i, length);
            if (MemoryBall.isBall(scene.actors().get(i).type()) || SNITCH.equals(scene.actors().get(i).type())
                    || GoldenEgg.isEgg(scene.actors().get(i).type())
                    || SceneNames.HORNTAIL_ACTOR.equals(scene.actors().get(i).type())) {
                fastMover(out, scene.actors().get(i), i);
            }
        }

        for (int i = 0; i < scene.words().size(); i++) {
            SceneDef.WordDef w = scene.words().get(i);
            String p = "words[" + i + "]";
            time(out, p + ".t", w.t(), length);
            if (!(w.size() >= SceneNames.MIN_WORD_SIZE && w.size() <= SceneNames.MAX_WORD_SIZE)) {
                out.add(p + ".size: " + w.size() + " is outside " + SceneNames.MIN_WORD_SIZE + " to " + SceneNames.MAX_WORD_SIZE);
            } else if (w.size() != 1f && !w.narration()) {
                out.add(p + ".size: size is only for narration");
            }
            if (w.subtitle() && (w.by() != null || w.narration())) {
                out.add(p + ".subtitle: a subtitle cannot have by or narration");
            }
            if (w.speaker() != null && !w.subtitle()) {
                out.add(p + ".speaker: speaker is only for subtitle lines");
            }
            if (w.by() != null && !actorIds.contains(w.by())) {
                out.add(p + ".by: unknown actor '" + w.by() + "'");
            }
        }

        List<CameraKey> keys = scene.camera().keys();
        for (int i = 0; i < keys.size(); i++) {
            CameraKey k = keys.get(i);
            String p = "camera[" + i + "]";
            time(out, p + ".t", k.t(), length);
            if (k.lookAtActor() != null && !actorIds.contains(k.lookAtActor())) {
                out.add(p + ".look_at: unknown actor '" + k.lookAtActor() + "'");
            }
        }

        if (scene.endAction() != null && !SceneNames.END_ACTIONS.contains(scene.endAction())) {
            out.add("end_action: unknown end action '" + scene.endAction() + "' (expected one of "
                    + sorted(SceneNames.END_ACTIONS) + ")");
        }
        return out;
    }

    private static void actor(List<String> out, SceneDef scene, SceneDef.ActorDef a, String p, Set<String> actorIds,
                              SceneNames names, float length) {
        known(out, p + ".type", "entity", a.type(), names.entityTypes());
        // A block actor may be small (a pot shard is a 0.16 block); everything else keeps the 0.25 floor.
        float minScale = "hogwartsmagic:memory_block".equals(a.type()) ? 0.05f : 0.25f;
        if (a.scale() < minScale || a.scale() > 4f) out.add(p + ".scale: must be " + minScale + " to 4, got " + a.scale());
        if (a.skin() != null) {
            if (!"player".equals(a.type())) out.add(p + ".skin: only a player actor can have a skin");
            if (a.skin().viewer() && (a.skin().texture() != null || a.skin().username() != null)) {
                out.add(p + ".skin: viewer cannot be combined with texture or username");
            }
        }
        if (a.skin() != null && a.skin().texture() != null) {
            known(out, p + ".skin.texture", "skin", a.skin().texture(), names.skins());
        }
        for (var item : a.items().entrySet()) {
            item(out, p + ".items." + item.getKey(), item.getValue(), names);
        }
        track(out, p + ".path", a.path(), length);
        track(out, p + ".look", a.look(), length);
        boolean blockActor = SceneNames.BLOCK_ACTOR.equals(a.type());
        if (a.block() != null && !blockActor) {
            out.add(p + ".block: block is only for " + SceneNames.BLOCK_ACTOR);
        } else if (blockActor && a.block() == null) {
            out.add(p + ".block: a " + SceneNames.BLOCK_ACTOR + " actor needs a block (a block state, e.g. minecraft:bricks)");
        } else if (blockActor) {
            blockState(out, p + ".block", a.block(), names);
        }
        if (!SceneNames.ROLL_TYPES.contains(a.type())) {
            for (Keyframe k : a.look().keys()) {
                if (k.v().length > 2 && k.v()[2] != 0f) {
                    out.add(p + ".look: roll is only for " + sorted(SceneNames.ROLL_TYPES));
                    break;
                }
            }
        }
        for (var limb : a.limbs().entrySet()) {
            track(out, p + ".limbs." + limb.getKey() + ".rot", limb.getValue().rot(), length);
            track(out, p + ".limbs." + limb.getKey() + ".offset", limb.getValue().offset(), length);
        }
        for (int i = 0; i < a.poses().size(); i++) {
            SceneDef.Cue c = a.poses().get(i);
            String cp = p + ".poses[" + i + "]";
            time(out, cp + ".t", c.t(), length);
            if (!scene.poses().containsKey(c.name()) && !SceneNames.POSE_PRESETS.contains(c.name())) {
                out.add(cp + ".name: unknown pose '" + c.name() + "' (define it under poses, or use a preset: "
                        + sorted(SceneNames.POSE_PRESETS) + ")");
            }
        }
        if (!a.life() && !"player".equals(a.type())) out.add(p + ".life: only a player actor has life");
        if (a.ride() != null) ride(out, scene, a, p, actorIds, length);
        for (int i = 0; i < a.anims().size(); i++) {
            SceneDef.Cue c = a.anims().get(i);
            time(out, p + ".anims[" + i + "].t", c.t(), length);
            // Creature animation names need the client's models (PartCheck); player clips are known here.
            if (SceneNames.HORNTAIL_ACTOR.equals(a.type())
                    && !SceneNames.HORNTAIL_ANIMS.contains(c.name().toUpperCase(java.util.Locale.ROOT))) {
                out.add(p + ".anims[" + i + "].name: unknown Horntail cue '" + c.name() + "' (expected one of "
                        + String.join(", ", SceneNames.HORNTAIL_ANIMS) + ")");
            }
            if ("player".equals(a.type()) && HumanMotion.clipName(c.name()) == null) {
                out.add(p + ".anims[" + i + "].name: unknown player clip '" + c.name() + "' (expected one of "
                        + String.join(", ", HumanMotion.CLIPS) + ")");
            }
        }
        for (int i = 0; i < a.actions().size(); i++) {
            SceneDef.ActionCue c = a.actions().get(i);
            String cp = p + ".actions[" + i + "]";
            time(out, cp + ".t", c.t(), length);
            if (!SceneNames.ACTIONS.contains(c.action())) {
                out.add(cp + ".do: unknown action '" + c.action() + "' (expected one of "
                        + sorted(SceneNames.ACTIONS) + ")");
            }
            if ("cast".equals(c.action())) {
                if (c.spell() == null) {
                    out.add(cp + ".spell: a cast action needs a spell");
                } else if (!names.spells().contains(c.spell()) && !SceneNames.MEMORY_ONLY_SPELLS.contains(c.spell())) {
                    out.add(cp + ".spell: unknown spell '" + c.spell() + "'");
                } else if (!SceneNames.SPELL_VISUALS.contains(c.spell())) {
                    out.add(cp + ".spell: no memory visual for '" + c.spell() + "'");
                }
            }
            if ("equip".equals(c.action())) {
                if (c.slot() == null) {
                    out.add(cp + ".slot: an equip action needs a slot");
                } else if (!SceneNames.EQUIP_SLOTS.contains(c.slot())) {
                    out.add(cp + ".slot: equip slot must be one of " + sorted(SceneNames.EQUIP_SLOTS));
                }
                if (c.item() == null) out.add(cp + ".item: an equip action needs an item (minecraft:air clears the slot)");
            }
            if (!"equip".equals(c.action()) && c.slot() != null) out.add(cp + ".slot: slot is only for equip");
            breathe(out, scene, a, c, cp);
            if (c.item() != null) item(out, cp + ".item", c.item(), names);
            if (c.target() != null && c.target().actor() != null && !actorIds.contains(c.target().actor())) {
                out.add(cp + ".target: unknown actor '" + c.target().actor() + "'");
            }
        }
    }

    /**
     * The Horntail's fire ({@link DragonFire}): {@code breathe} only on her, {@code duration} only on it and within
     * {@link DragonFire#MIN_DURATION}..{@link DragonFire#MAX_DURATION}, and the whole breathe inside the fire window of
     * a {@code BREATH} or {@code AIR_BREATH} cue (her jaw open at full fire, {@link DragonFire#fireWindow}); an actor
     * target must be a player (the fire and its mirror aim at a player's chest).
     */
    private static void breathe(List<String> out, SceneDef scene, SceneDef.ActorDef a, SceneDef.ActionCue c,
                                String cp) {
        boolean breathe = DragonFire.ACTION.equals(c.action());
        if (!breathe) {
            if (!Float.isNaN(c.duration())) out.add(cp + ".duration: duration is only for breathe");
            return;
        }
        if (!SceneNames.HORNTAIL_ACTOR.equals(a.type())) {
            out.add(cp + ".do: breathe is only for " + SceneNames.HORNTAIL_ACTOR);
            return;
        }
        float d = Float.isNaN(c.duration()) ? DragonFire.DEFAULT_DURATION : c.duration();
        if (d < DragonFire.MIN_DURATION || d > DragonFire.MAX_DURATION) {
            out.add(cp + ".duration: " + d + " is out of range (" + DragonFire.MIN_DURATION + " to "
                    + DragonFire.MAX_DURATION + " ticks)");
            return;
        }
        // An actor target is aimed at its chest from a player's height (DragonFireRenderer, dragon_engine.fire_target).
        if (c.target() != null && c.target().actor() != null) {
            for (SceneDef.ActorDef other : scene.actors()) {
                if (other.id().equals(c.target().actor()) && !"player".equals(other.type())) {
                    out.add(cp + ".target: a breathe actor target must be a player actor ('" + other.id() + "' is "
                            + other.type() + "); aim at a point instead");
                }
            }
        }
        double[] w = DragonFire.fireWindow(a.anims(), c.t());
        if (w == null || c.t() < w[0] - 1e-3 || c.t() + d > w[1] + 1e-3) {
            out.add(cp + ": breathe at t " + c.t() + " for " + d + " ticks lies outside the fire window of a BREATH or "
                    + "AIR_BREATH cue" + (w == null ? " (none is in force then)"
                    : " (her jaw is open at full fire from " + w[0] + " to " + w[1] + ")"));
        }
    }

    /** The Snitch's entity type (a living actor that darts like the balls). */
    private static final String SNITCH = "hogwartsmagic:golden_snitch";

    /**
     * A ball, the Snitch or the Horntail must not move {@link MemoryBall#MAX_STEP} blocks or more in one tick: the
     * client reads such a step as a cut (no interpolation, no roll), so a path that does it by accident would step at
     * 20 Hz. Two keys under half a tick apart are a deliberate cut and exempt that tick.
     */
    private static void fastMover(List<String> out, SceneDef.ActorDef a, int index) {
        List<Keyframe> keys = a.path().keys();
        if (keys.size() < 2) return;
        int from = (int) Math.floor(keys.get(0).t()), to = (int) Math.ceil(keys.get(keys.size() - 1).t());
        for (int t = from; t < to; t++) {
            boolean cut = false;
            for (int i = 1; i < keys.size(); i++) {
                float k0 = keys.get(i - 1).t(), k1 = keys.get(i).t();
                if (k1 - k0 < 0.5f && k1 > t && k0 < t + 1) cut = true;
            }
            if (cut) continue;
            float[] p = a.path().sample(t), q = a.path().sample(t + 1);
            double dx = q[0] - p[0], dy = (q.length > 1 ? q[1] : 0) - (p.length > 1 ? p[1] : 0),
                    dz = (q.length > 2 ? q[2] : 0) - (p.length > 2 ? p[2] : 0);
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= MemoryBall.MAX_STEP) {
                out.add("actors[" + index + "]: " + a.type().substring(a.type().indexOf(':') + 1) + " '" + a.id()
                        + "' moves " + (Math.round(d * 10) / 10.0) + " blocks in a tick at t " + t + "; at most "
                        + MemoryBall.MAX_STEP + " (more is read as a cut: no interpolation, no roll)");
                return;
            }
        }
    }

    /**
     * A rowboat may only ride a rough_sea of intensity at most {@link RoughSea#MAX_BOAT_INTENSITY}: rougher swells grow
     * faster than a 4-block hull can follow, and the hull would leave the drawn water by more than 0.15 blocks. Checked
     * along the boat's path every 5 ticks and at each path key, with the same binding the client uses.
     */
    private static void boatSea(List<String> out, SceneDef scene, int index, float length) {
        SceneDef.ActorDef boat = scene.actors().get(index);
        List<Float> times = new ArrayList<>();
        for (float t = 0; t <= length; t += 5) times.add(t);
        for (Keyframe k : boat.path().keys()) times.add(k.t());
        for (float t : times) {
            float[] p = boat.path().isEmpty() ? new float[]{0, 0, 0} : boat.path().sample(t);
            RoughSea.Sea sea = RoughSea.bind(scene.events(), t, p[0], p[2]);
            if (sea != null && sea.intensity() > RoughSea.MAX_BOAT_INTENSITY + 1e-6) {
                out.add("actors[" + index + "]: memory_rowboat '" + boat.id() + "' rides a rough_sea of intensity "
                        + (float) sea.intensity() + " at t " + t + "; a sea a boat rides must be at most "
                        + RoughSea.MAX_BOAT_INTENSITY + " (the hull cannot follow rougher swells)");
                return;
            }
        }
    }

    /**
     * {@code ride}: the vehicle must be another actor of a vehicle type, a vehicle cannot itself ride, and a window
     * lies inside the scene with {@code from} before {@code until}.
     */
    private static void ride(List<String> out, SceneDef scene, SceneDef.ActorDef a, String p, Set<String> actorIds,
                             float length) {
        if (Vehicle.isVehicle(a.type())) {
            out.add(p + ".ride: a vehicle cannot ride another actor");
        } else if (a.ride().equals(a.id())) {
            out.add(p + ".ride: an actor cannot ride itself");
        } else if (!actorIds.contains(a.ride())) {
            out.add(p + ".ride: unknown actor '" + a.ride() + "'");
        } else {
            for (SceneDef.ActorDef b : scene.actors()) {
                if (b.id().equals(a.ride()) && !Vehicle.isVehicle(b.type())) {
                    out.add(p + ".ride: '" + a.ride() + "' is not a vehicle (expected one of "
                            + sorted(SceneNames.VEHICLES) + ")");
                }
            }
        }
        boolean from = !Float.isInfinite(a.rideFrom()), until = !Float.isInfinite(a.rideUntil());
        if (from && (a.rideFrom() < 0 || a.rideFrom() > length)) {
            out.add(p + ".ride.from: " + a.rideFrom() + " is outside the scene (0 to " + length + ")");
        }
        if (until && (a.rideUntil() < 0 || a.rideUntil() > length)) {
            out.add(p + ".ride.until: " + a.rideUntil() + " is outside the scene (0 to " + length + ")");
        }
        if (a.rideFrom() >= a.rideUntil()) {
            out.add(p + ".ride: from (" + a.rideFrom() + ") must be before until (" + a.rideUntil() + ")");
        }
    }

    /**
     * A vehicle carries at most {@link Vehicle#capacity} riders at any moment (a broom 2, the rowboat its seats),
     * counted over the riders' windows.
     */
    private static void riders(List<String> out, SceneDef scene, int index, float length) {
        SceneDef.ActorDef v = scene.actors().get(index);
        List<float[]> windows = new ArrayList<>();
        for (SceneDef.ActorDef a : scene.actors()) {
            if (v.id().equals(a.ride()) && a.rideFrom() < a.rideUntil()) windows.add(new float[]{a.rideFrom(), a.rideUntil()});
        }
        int cap = Vehicle.capacity(v.type());
        if (windows.size() <= cap) return;
        // The count only rises at a window's start: check each one.
        for (float[] w : windows) {
            int n = 0;
            for (float[] o : windows) {
                if (o[0] <= w[0] && w[0] < o[1]) n++;
            }
            if (n > cap) {
                String at = Float.isInfinite(w[0]) ? "the start" : "t " + w[0];
                out.add("actors[" + index + "]: " + v.type() + " '" + v.id() + "' carries " + n + " riders at " + at
                        + "; it carries at most " + cap);
                return;
            }
        }
    }

    private static void event(List<String> out, SceneDef.EventDef e, String p, SceneNames names, float length,
                              int radius) {
        time(out, p + ".t", e.t(), length);
        if (!SceneNames.EVENT_KINDS.contains(e.kind())) {
            out.add(p + ".kind: unknown event kind '" + e.kind() + "' (expected one of "
                    + sorted(SceneNames.EVENT_KINDS) + ")");
            return;
        }
        if (!Float.isNaN(e.yaw()) && !"effect".equals(e.kind())) {
            out.add(p + ".yaw: yaw is only for effects");
        }
        boolean crowd = "effect".equals(e.kind()) && "crowd".equals(e.id());
        boolean react = "effect".equals(e.kind()) && "crowd_react".equals(e.id());
        if (e.style() != null && !"enchanted_ceiling".equals(e.id()) && !"sky".equals(e.id()) && !crowd && !react) {
            out.add(p + ".style: style is only for enchanted_ceiling, sky, crowd and crowd_react");
        }
        if (!Float.isNaN(e.intensity()) && !("effect".equals(e.kind())
                && ("light_rays".equals(e.id()) || "eyes_peek".equals(e.id()) || "wand_choice".equals(e.id())
                || "rough_sea".equals(e.id()) || "rain".equals(e.id()) || crowd))) {
            out.add(p + ".intensity: intensity is only for light_rays, eyes_peek, wand_choice, rough_sea, rain and crowd");
        }
        if (e.rows() != null && !crowd) {
            out.add(p + ".rows: rows is only for crowd");
        }
        if (e.box() != null && !("effect".equals(e.kind()) && "letter_flood".equals(e.id()))) {
            out.add(p + ".box: box is only for letter_flood");
        }
        if (e.id() == null) {
            out.add(p + ".id: missing required key for a " + e.kind() + " event");
            return;
        }
        switch (e.kind()) {
            case "block" -> {
                known(out, p + ".id", "block", e.id(), names.blocks());
                if (e.at() != null) {
                    for (float c : e.at()) {
                        if (c != Math.rint(c)) {
                            out.add(p + ".at: a block event needs whole-block coordinates");
                            break;
                        }
                    }
                }
            }
            case "sound" -> known(out, p + ".id", "sound", e.id(), names.sounds());
            case "music", "voice", "sfx" -> {
                known(out, p + ".id", "sound", e.id(), names.sounds());
                if ("sfx".equals(e.kind())) {
                    out.addAll(CueKinds.loopProblems(p, e.loop(), e.duration(), e.t(), length));
                }
                float v = e.volume();
                if (Float.isNaN(v) || v < 0 || v > SceneNames.MAX_CUE_VOLUME) {
                    out.add(p + ".volume: " + v + " is outside 0 to " + SceneNames.MAX_CUE_VOLUME);
                }
            }
            case "particles" -> known(out, p + ".id", "particle", e.id(), names.particles());
            default -> {
                if (!SceneNames.EFFECTS.contains(e.id())) {
                    out.add(p + ".id: unknown effect '" + e.id() + "' (expected one of " + sorted(SceneNames.EFFECTS) + ")");
                } else if ("letter_flood".equals(e.id())) {
                    letterFlood(out, e, p, radius);
                } else if ("enchanted_ceiling".equals(e.id())) {
                    ceiling(out, e, p);
                } else if ("sky".equals(e.id())) {
                    sky(out, e, p);
                } else if ("light_rays".equals(e.id())) {
                    lightRays(out, e, p);
                } else if ("floating_candles".equals(e.id())) {
                    candles(out, e, p);
                } else if ("wand_choice".equals(e.id())) {
                    wandChoice(out, e, p);
                } else if ("rough_sea".equals(e.id())) {
                    roughSea(out, e, p, radius);
                } else if ("rain".equals(e.id())) {
                    rain(out, e, p);
                } else if ("crowd".equals(e.id())) {
                    crowd(out, e, p, radius, length);
                } else if ("crowd_react".equals(e.id())) {
                    crowdReact(out, e, p, radius);
                } else if ("eyes_peek".equals(e.id()) && !Float.isNaN(e.intensity())
                        && !(e.intensity() >= Eyelids.MIN_PEEK_OPEN && e.intensity() <= 1f)) {
                    out.add(p + ".intensity: " + e.intensity() + " is outside " + Eyelids.MIN_PEEK_OPEN
                            + " to 1 (how far eyes_peek opens)");
                }
            }
        }
    }

    /** The letter_flood effect: it needs a mouth and a facing, and its optional numbers have ranges. */
    private static void letterFlood(List<String> out, SceneDef.EventDef e, String p, int radius) {
        if (e.at() == null) out.add(p + ": letter_flood needs at");
        if (Float.isNaN(e.yaw()) || Float.isInfinite(e.yaw())) {
            out.add(p + ": letter_flood needs yaw");
        } else if (e.yaw() % 90f != 0f) {
            out.add(p + ".yaw: " + e.yaw() + " is not a multiple of 90 (the room faces a wall)");
        }
        if (e.box() != null) {
            float[] a = e.box()[0], b = e.box()[1];
            String[] axes = {"x", "y", "z"};
            for (int i = 0; i < 3; i++) {
                if (!(a[i] < b[i])) {
                    out.add(p + ".box: min " + axes[i] + " " + a[i] + " must be below max " + axes[i] + " " + b[i]
                            + " (give the lower corner first)");
                }
            }
            for (int i : new int[] {0, 2}) {
                if (Math.abs(a[i]) > radius || Math.abs(b[i]) > radius) {
                    out.add(p + ".box: " + axes[i] + " " + a[i] + " to " + b[i] + " is outside the scene radius " + radius);
                }
            }
        }
        if (e.count() != -1 && (e.count() < 1 || e.count() > SceneNames.MAX_FLOOD_LETTERS)) {
            out.add(p + ".count: " + e.count() + " is outside 1 to " + SceneNames.MAX_FLOOD_LETTERS);
        }
        if (e.spread() != null) {
            for (int i = 0; i < e.spread().length; i++) {
                float s = e.spread()[i];
                if (!(s >= SceneNames.MIN_FLOOD_SPREAD && s <= SceneNames.MAX_FLOOD_SPREAD)) {
                    out.add(p + ".spread[" + i + "]: " + s + " is outside " + SceneNames.MIN_FLOOD_SPREAD + " to "
                            + SceneNames.MAX_FLOOD_SPREAD);
                }
            }
        }
    }

    /** The enchanted_ceiling effect: it needs a centre and a half-size, and the half-size has a range (y is unused). */
    private static void ceiling(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": enchanted_ceiling needs at");
        if (e.style() != null && !SceneNames.CEILING_STYLES.contains(e.style())) {
            out.add(p + ".style: '" + e.style() + "' is not one of " + SceneNames.CEILING_STYLES);
        }
        if (e.spread() == null) {
            out.add(p + ": enchanted_ceiling needs spread [hx, 0, hz]");
            return;
        }
        for (int i = 0; i < e.spread().length; i += 2) {
            float s = e.spread()[i];
            if (!(s >= SceneNames.MIN_CEILING_SPREAD && s <= SceneNames.MAX_CEILING_SPREAD)) {
                out.add(p + ".spread[" + i + "]: " + s + " is outside " + SceneNames.MIN_CEILING_SPREAD + " to "
                        + SceneNames.MAX_CEILING_SPREAD);
            }
        }
    }

    /** The sky effect: a set centre and an optional look. */
    private static void sky(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": sky needs at");
        if (e.style() != null && !SkyDome.STYLES.contains(e.style())) {
            out.add(p + ".style: '" + e.style() + "' is not one of " + SkyDome.STYLES);
        }
    }

    /** The light_rays effect: a doorway centre, a direction and a size [half-width, height, length], each ranged. */
    private static void lightRays(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": light_rays needs at");
        if (Float.isNaN(e.yaw()) || Float.isInfinite(e.yaw())) out.add(p + ": light_rays needs yaw");
        if (!Float.isNaN(e.intensity()) && !(e.intensity() >= LightRays.MIN_INTENSITY && e.intensity() <= LightRays.MAX_INTENSITY)) {
            out.add(p + ".intensity: " + e.intensity() + " is outside " + LightRays.MIN_INTENSITY + " to "
                    + LightRays.MAX_INTENSITY);
        }
        if (e.spread() == null) {
            out.add(p + ": light_rays needs spread [half-width, height, length]");
            return;
        }
        float[] lo = {SceneNames.MIN_RAYS_HALF_WIDTH, SceneNames.MIN_RAYS_HEIGHT, SceneNames.MIN_RAYS_LENGTH};
        float[] hi = {SceneNames.MAX_RAYS_HALF_WIDTH, SceneNames.MAX_RAYS_HEIGHT, SceneNames.MAX_RAYS_LENGTH};
        for (int i = 0; i < 3 && i < e.spread().length; i++) {
            float s = e.spread()[i];
            if (!(s >= lo[i] && s <= hi[i])) out.add(p + ".spread[" + i + "]: " + s + " is outside " + lo[i] + " to " + hi[i]);
        }
    }

    /** The wand_choice effect: a centre (the wand tip or the boy's chest) and an optional ranged intensity. */
    private static void wandChoice(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": wand_choice needs at");
        if (!Float.isNaN(e.intensity()) && !(e.intensity() >= WandChoice.MIN_INTENSITY && e.intensity() <= WandChoice.MAX_INTENSITY)) {
            out.add(p + ".intensity: " + e.intensity() + " is outside " + WandChoice.MIN_INTENSITY + " to "
                    + WandChoice.MAX_INTENSITY);
        }
    }

    /** The rough_sea effect: a still-water centre, an area [hx, 0, hz] inside the radius, a ranged intensity. */
    private static void roughSea(List<String> out, SceneDef.EventDef e, String p, int radius) {
        if (e.at() == null) out.add(p + ": rough_sea needs at");
        intensity(out, e, p);
        if (e.spread() == null) {
            out.add(p + ": rough_sea needs spread [hx, 0, hz]");
            return;
        }
        boolean ranged = true;
        for (int i = 0; i < e.spread().length; i += 2) {
            float s = e.spread()[i];
            if (!(s >= RoughSea.MIN_SPREAD && s <= RoughSea.MAX_SPREAD)) {
                out.add(p + ".spread[" + i + "]: " + s + " is outside " + RoughSea.MIN_SPREAD + " to " + RoughSea.MAX_SPREAD);
                ranged = false;
            }
        }
        if (ranged && e.at() != null) {
            float[] a = e.at(), s = e.spread();
            if (Math.abs(a[0]) + s[0] > radius || Math.abs(a[2]) + s[2] > radius) {
                out.add(p + ": the rough_sea area (at +- spread) reaches beyond the scene radius " + radius);
            }
        }
    }

    /** The rain effect: a box centre and half-size [hx, hy, hz], each ranged, and a ranged intensity. */
    private static void rain(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": rain needs at");
        intensity(out, e, p);
        if (e.spread() == null) {
            out.add(p + ": rain needs spread [hx, hy, hz]");
            return;
        }
        for (int i = 0; i < e.spread().length; i++) {
            float s = e.spread()[i];
            if (!(s >= SceneNames.MIN_RAIN_SPREAD && s <= SceneNames.MAX_RAIN_SPREAD)) {
                out.add(p + ".spread[" + i + "]: " + s + " is outside " + SceneNames.MIN_RAIN_SPREAD + " to "
                        + SceneNames.MAX_RAIN_SPREAD);
            }
        }
    }

    /**
     * The crowd effect: a centre to face, seat rows inside the scene radius, a style from {@link Crowd#STYLES}, a fill
     * share of 0.1 to 1 and a positive cap.
     */
    private static void crowd(List<String> out, SceneDef.EventDef e, String p, int radius, float length) {
        if (e.at() == null) out.add(p + ": crowd needs at (the point the crowd watches)");
        if (e.rows() == null || e.rows().length == 0) {
            out.add(p + ": crowd needs rows [[x1,y1,z1,x2,y2,z2], ...] (seat tops)");
        } else {
            for (int i = 0; i < e.rows().length; i++) {
                float[] r = e.rows()[i];
                // A seat may be nudged sideways off its row, so the row keeps that far inside the radius.
                double reach = radius - Crowd.SIDE_JITTER;
                if (Math.abs(r[0]) > reach || Math.abs(r[2]) > reach || Math.abs(r[3]) > reach || Math.abs(r[5]) > reach) {
                    out.add(p + ".rows[" + i + "]: reaches beyond the scene radius " + radius);
                }
                if (r[1] < -64 || r[1] > 320 || r[4] < -64 || r[4] > 320) {
                    out.add(p + ".rows[" + i + "]: y must be -64 to 320 (scene-relative), got " + r[1] + " to " + r[4]);
                }
                if (r.length > 6 && r[6] != 0f && r[6] != 1f) {
                    out.add(p + ".rows[" + i + "][6]: the standing flag must be 0 or 1, got " + r[6]);
                }
            }
        }
        if (e.style() != null && !Crowd.STYLES.contains(e.style())) {
            out.add(p + ".style: '" + e.style() + "' is not one of " + Crowd.STYLES);
        }
        if (!Float.isNaN(e.intensity()) && !(e.intensity() >= 0.1f && e.intensity() <= 1f)) {
            out.add(p + ".intensity: " + e.intensity() + " is outside 0.1 to 1 (the share of seats filled)");
        }
        if (e.count() != -1 && (e.count() < 1 || e.count() > Crowd.MAX_SPECTATORS)) {
            out.add(p + ".count: " + e.count() + " is outside 1 to " + Crowd.MAX_SPECTATORS);
        }
        if (e.duration() < 0 || (e.duration() > 0 && e.t() + e.duration() > length)) {
            out.add(p + ".duration: " + e.duration() + " must be 0 (until the scene ends) or end inside the scene (length "
                    + length + ")");
        }
    }

    /** A crowd reaction: a centre of attention, a reaction from {@link Crowd#REACTIONS}, a positive box, a long enough erupt. */
    private static void crowdReact(List<String> out, SceneDef.EventDef e, String p, int radius) {
        if (e.at() == null) {
            out.add(p + ": crowd_react needs at (the centre of attention)");
        } else if (Math.abs(e.at()[0]) > radius || Math.abs(e.at()[2]) > radius) {
            out.add(p + ".at: the centre of attention is beyond the scene radius " + radius);
        }
        if (e.style() == null) {
            out.add(p + ": crowd_react needs a style, one of " + Crowd.REACTIONS);
        } else if (!Crowd.REACTIONS.contains(e.style())) {
            out.add(p + ".style: '" + e.style() + "' is not one of " + Crowd.REACTIONS);
        }
        if (e.spread() != null) {
            for (int i = 0; i < e.spread().length; i++) {
                if (!(e.spread()[i] > 0f)) out.add(p + ".spread[" + i + "]: " + e.spread()[i] + " must be above 0 (a half-box)");
            }
        }
        if ("erupt".equals(e.style()) && e.duration() > 0 && e.duration() < Crowd.MIN_ERUPT_TICKS) {
            out.add(p + ".duration: an erupt lasts at least " + Crowd.MIN_ERUPT_TICKS + " ticks (3 s), got " + e.duration());
        }
    }

    /**
     * Every crowd effect's spectators, summed over the scene (seated together, standers resolved against every crowd),
     * stay within {@link Crowd#MAX_SPECTATORS}.
     */
    private static void crowdCap(List<String> out, SceneDef scene) {
        int total = 0;
        for (Crowd c : Crowd.ofScene(scene.events())) total += c.size();
        if (total > Crowd.MAX_SPECTATORS) {
            out.add("events: the crowd effects seat " + total + " spectators; a scene holds at most "
                    + Crowd.MAX_SPECTATORS + " (lower intensity or count)");
        }
    }

    /** rough_sea and rain share the intensity range 0.1 to 2. */
    private static void intensity(List<String> out, SceneDef.EventDef e, String p) {
        if (!Float.isNaN(e.intensity()) && !(e.intensity() >= RoughSea.MIN_INTENSITY && e.intensity() <= RoughSea.MAX_INTENSITY)) {
            out.add(p + ".intensity: " + e.intensity() + " is outside " + RoughSea.MIN_INTENSITY + " to " + RoughSea.MAX_INTENSITY);
        }
    }

    /** The floating_candles effect: a centre and a half-box, each axis ranged, and an optional count. */
    private static void candles(List<String> out, SceneDef.EventDef e, String p) {
        if (e.at() == null) out.add(p + ": floating_candles needs at");
        if (e.count() != -1 && (e.count() < 1 || e.count() > SceneNames.MAX_CANDLES)) {
            out.add(p + ".count: " + e.count() + " is outside 1 to " + SceneNames.MAX_CANDLES);
        }
        if (e.spread() == null) {
            out.add(p + ": floating_candles needs spread [hx, hy, hz]");
            return;
        }
        for (int i = 0; i < e.spread().length; i++) {
            float s = e.spread()[i];
            if (!(s >= SceneNames.MIN_CANDLES_SPREAD && s <= SceneNames.MAX_CANDLES_SPREAD)) {
                out.add(p + ".spread[" + i + "]: " + s + " is outside " + SceneNames.MIN_CANDLES_SPREAD + " to "
                        + SceneNames.MAX_CANDLES_SPREAD);
            }
        }
    }

    private static void track(List<String> out, String p, Track t, float length) {
        for (int i = 0; i < t.keys().size(); i++) {
            // Keys are stored sorted by time, so the index may differ from the JSON order; the time value says which.
            time(out, p + "[" + i + " (sorted)].t", t.keys().get(i).t(), length);
        }
    }

    private static void time(List<String> out, String p, float t, float length) {
        if (Float.isNaN(t) || Float.isInfinite(t)) {
            out.add(p + ": time is not a finite number");
        } else if (t < 0 || t > length) {
            out.add(p + ": time " + t + " is outside the scene (0 to " + length + ")");
        }
    }

    /** Largest single fill, in blocks. */
    public static final long MAX_FILL_BLOCKS = 2_000_000L;

    /** Everything in the set must sit within the scene radius (horizontally) of the region origin. */
    private static void extent(List<String> out, int radius, SceneDef.SetDef set) {
        for (int i = 0; i < set.templates().size(); i++) {
            int[] at = set.templates().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.templates[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.schematics().size(); i++) {
            int[] at = set.schematics().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.schematics[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.fills().size(); i++) {
            SceneDef.Fill f = set.fills().get(i);
            int x0 = Math.min(f.from()[0], f.to()[0]), x1 = Math.max(f.from()[0], f.to()[0]);
            int z0 = Math.min(f.from()[2], f.to()[2]), z1 = Math.max(f.from()[2], f.to()[2]);
            int y0 = Math.min(f.from()[1], f.to()[1]), y1 = Math.max(f.from()[1], f.to()[1]);
            if (beyond(radius, x0, x1, z0, z1)) {
                out.add("set.fills[" + i + "]: extends beyond radius " + radius);
            }
            long blocks = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (blocks > MAX_FILL_BLOCKS) {
                out.add("set.fills[" + i + "]: fill too large (" + blocks + " blocks, max " + MAX_FILL_BLOCKS + ")");
            }
        }
        for (int i = 0; i < set.disks().size(); i++) {
            SceneDef.Disk d = set.disks().get(i);
            int cx = d.center()[0], cz = d.center()[2], r = Math.abs(d.radius());
            if (beyond(radius, cx - r, cx + r, cz - r, cz + r)) {
                out.add("set.disks[" + i + "]: extends beyond radius " + radius);
            }
        }
        for (int i = 0; i < set.features().size(); i++) {
            int[] at = set.features().get(i).at();
            if (beyond(radius, at[0], at[0], at[2], at[2])) {
                out.add("set.features[" + i + "]: anchor extends beyond radius " + radius);
            }
        }
    }

    private static boolean beyond(int radius, int x0, int x1, int z0, int z1) {
        return x0 < -radius || x1 > radius || z0 < -radius || z1 > radius;
    }

    /** An item id, or a {@code {viewer_wand}} / {@code {reject_wand_N}} placeholder the server fills in (see {@link ItemText}). */
    private static void item(List<String> out, String p, String id, SceneNames names) {
        if (ItemText.VIEWER_WAND.equals(id) || ItemText.REJECT_WANDS.contains(id)) return;
        known(out, p, "item", id, names.items());
    }

    /** {@code namespace:block} or {@code namespace:block[prop=value,...]}: the shape here, the id against the names. */
    private static final java.util.regex.Pattern BLOCK_STATE = java.util.regex.Pattern.compile(
            "([a-z0-9_.-]+:[a-z0-9_./-]+)(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?");

    /**
     * A memory_block's block state. Property names and values are only checked for shape here (the names hold block
     * ids, not their properties); the client logs and shows nothing for a property the block does not have, and
     * the {@code blockActorSpawns} gametest parses real states with the game's own parser.
     */
    private static void blockState(List<String> out, String p, String state, SceneNames names) {
        java.util.regex.Matcher m = BLOCK_STATE.matcher(state);
        if (!m.matches()) {
            out.add(p + ": '" + state + "' is not a block state (namespace:block or namespace:block[prop=value,...])");
            return;
        }
        known(out, p, "block", m.group(1), names.blocks());
    }

    private static void known(List<String> out, String p, String what, String id, Set<String> valid) {
        if (id == null || !valid.contains(id)) out.add(p + ": unknown " + what + " '" + id + "'");
    }

    private static List<String> sorted(Set<String> s) {
        return s.stream().sorted().toList();
    }
}
