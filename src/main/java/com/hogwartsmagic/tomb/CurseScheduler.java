package com.hogwartsmagic.tomb;

import com.hogwartsmagic.ExampleMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the steps of a tomb curse at their ticks: the wind-up (rumble, swirling sand, coiling smoke),
 * the moment it lands, the aura that stays after, and the Revelio wave reaching each tile.
 *
 * <p>Short-lived by design (a few seconds at most), so nothing is saved: a server stopping in the
 * middle of a curse drops the rest of it, which only spares the player. Server thread only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class CurseScheduler {

    /** A step that runs once per tick from {@code from} to {@code to} (inclusive), given the tick index. */
    @FunctionalInterface
    public interface Step {
        /** @return false to stop early (the player left, the curse has nothing left to do) */
        boolean run(int tick);
    }

    private record Job(long from, long to, Step step) {
    }

    private static final List<Job> JOBS = new ArrayList<>();
    private static long now;

    private CurseScheduler() {
    }

    /** Run {@code step} once, {@code delay} ticks from now. */
    public static void after(int delay, Runnable step) {
        JOBS.add(new Job(now + Math.max(0, delay), now + Math.max(0, delay), t -> {
            step.run();
            return false;
        }));
    }

    /** Run {@code step} every tick for {@code ticks} ticks, starting {@code delay} ticks from now. */
    public static void every(int delay, int ticks, Step step) {
        JOBS.add(new Job(now + Math.max(0, delay), now + Math.max(0, delay) + Math.max(0, ticks - 1), step));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        now++;
        if (JOBS.isEmpty()) {
            return;
        }
        List<Job> due = new ArrayList<>(JOBS);
        for (Job job : due) {
            if (now < job.from) {
                continue;
            }
            boolean more;
            try {
                more = job.step.run((int) (now - job.from));
            } catch (RuntimeException e) {
                // A broken effect must never take the server down; it just stops.
                com.mojang.logging.LogUtils.getLogger().error("Tomb curse step failed", e);
                more = false;
            }
            if (!more || now >= job.to) {
                JOBS.remove(job);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    /** For tests and debugging: how many steps are waiting. */
    public static int pending() {
        return JOBS.size();
    }
}
