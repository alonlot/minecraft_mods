package com.hogwartsmagic.worldgen.undercroft;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Dumps and audits the undercroft from {@code gradlew runData}, which takes seconds and needs no
 * world.
 *
 * <p>The obvious place to put this check was a command on a dedicated server, and that is where it
 * started. It worked, but a server wants a world, a world wants a lock, and a run that does not
 * shut down cleanly leaves that lock held - so the check became "boot a server for two minutes and
 * hope the last one exited". The data generator has none of that: registries are bootstrapped, no
 * world is loaded, nothing is locked, and it exits when it is done.
 *
 * <p>The command is kept as well, because being able to ask a live server is worth having. This is
 * simply the one to reach for while iterating.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UndercroftDataGen {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Undercroft");

    /** Audited at both ends of the range and in the middle, because the shape scales with depth. */
    private static final int[] DEPTHS = {CartTrack.DEPTH_MIN, 122, CartTrack.DEPTH_MAX};

    private UndercroftDataGen() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(true, new Provider());
    }

    private static final class Provider implements DataProvider {

        @Override
        public String getName() {
            return "Gringotts undercroft dump";
        }

        @Override
        public CompletableFuture<?> run(CachedOutput output) {
            Path out = Path.of("undercroft_dumps");
            int failures = 0;
            for (int depth : DEPTHS) {
                try {
                    UndercroftDump.Result result =
                            UndercroftDump.dump(out, "corridor_d" + depth, depth);
                    LOGGER.info("UNDERCROFT depth {}  {}x{}x{}  {} blocks  -> {}", depth,
                            result.width(), result.height(), result.length(), result.blocks(),
                            result.path());
                    List<String> problems = result.problems();
                    if (problems.isEmpty()) {
                        LOGGER.info("UNDERCROFT depth {}  rideable end to end", depth);
                    }
                    for (String problem : problems) {
                        LOGGER.error("UNDERCROFT depth {}  PROBLEM  {}", depth, problem);
                        failures++;
                    }
                } catch (Exception e) {
                    LOGGER.error("UNDERCROFT depth {} failed to dump", depth, e);
                    failures++;
                }
            }
            LOGGER.info("UNDERCROFT {} problem(s) across {} depths", failures, DEPTHS.length);
            return CompletableFuture.completedFuture(null);
        }
    }
}
