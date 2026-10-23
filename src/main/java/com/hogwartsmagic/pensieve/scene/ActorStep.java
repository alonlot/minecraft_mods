package com.hogwartsmagic.pensieve.scene;

/**
 * Whether an actor's move from one client tick to the next is a cut (a jump the client draws at once) or motion (which
 * it interpolates over the tick). Pure; no Minecraft types, so the tests ask the same question MemoryActors does.
 *
 * <p>Scenes make a cut with keys a tick apart (a swap, a park under the floor: boy_cot in the Letter jumps 13 blocks
 * from t 296 to 297), so a long step alone cannot tell a cut from fast flight. What does is the speed either side: a
 * cut leaps out of slow motion (or into it), while a fast broom was already nearly that fast the tick before and still
 * is the tick after. So a step longer than {@link MemoryBall#MAX_STEP} is a cut only when it differs by more than
 * {@link MemoryBall#MAX_STEP} from the path's step just before it or just after it. A broom flying 2.6 blocks a tick
 * (the Keeper's dive in the Flying match, its steps changing by under 0.1 a tick) or 4.3 (Neville's runaway broom,
 * under 1) is motion; every cut in the shipped memories is still a cut. Before this any step over
 * {@link MemoryBall#MAX_STEP} was a cut, and a broom flying faster than that held still for a tick and then jumped,
 * every tick: it and its rider stuttered at speed.
 *
 * <p>A clock step longer than {@link SmoothedSceneClock#MAX_RATE} ticks (a snap) or backwards is a cut whenever the
 * step is long: the path between says nothing about what the viewer saw.
 */
public final class ActorStep {
    private ActorStep() {
    }

    /**
     * The actor's root at scene time t, scene-relative, exactly as MemoryActors places it: a rowboat on its sea, a broom
     * flying its path (the bob included), the Horntail on hers, a rider on its seat, else its own path.
     */
    public static double[] root(SceneDef scene, SceneDef.ActorDef a, float t) {
        String type = type(a.type());
        if (SceneNames.ROWBOAT_ACTOR.equals(type) && scene != null) {
            Rowboat.Pose p = Rowboat.place(scene, a, t);
            return new double[]{p.x(), p.y(), p.z()};
        }
        if (BroomFlight.isBroom(type) && scene != null) {
            BroomFlight.Pose p = BroomFlight.place(scene, a, t);
            return new double[]{p.x(), p.y(), p.z()};
        }
        if (SceneNames.HORNTAIL_ACTOR.equals(type)) {
            DragonFlight.Pose p = DragonFlight.place(a, t);
            return new double[]{p.x(), p.y(), p.z()};
        }
        return Vehicle.root(scene, a, t);
    }

    /** Whether the move from scene time {@code from} (the last tick) to {@code to} (this tick) is a cut. */
    public static boolean isCut(SceneDef scene, SceneDef.ActorDef a, float from, float to) {
        double[] p0 = root(scene, a, from), p1 = root(scene, a, to);
        double step = dist(p0, p1);
        if (step <= MemoryBall.MAX_STEP) return false;
        double dt = (double) to - from;
        if (!(dt > 0) || dt > SmoothedSceneClock.MAX_RATE) return true;
        double before = dist(root(scene, a, (float) (from - dt)), p0);
        double after = dist(p1, root(scene, a, (float) (to + dt)));
        return Math.abs(step - before) > MemoryBall.MAX_STEP || Math.abs(step - after) > MemoryBall.MAX_STEP;
    }

    private static String type(String t) {
        return t != null && !t.contains(":") && !"player".equals(t) ? "hogwartsmagic:" + t : t;
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
