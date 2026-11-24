package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The shimmer that runs out through a hidden wizard house when Revelio breaks its glamour: a thin
 * shell of pale silver-gold light that grows from the caster at the speed the blocks appear, with a
 * ring of it skimming the ground, so the cottage seems to condense out of the glitter as it passes.
 *
 * <p>Cheap by design: at most {@link #MAX_WAVES} waves at once, {@link #MOTES_PER_TICK} particles a
 * wave a tick, nothing beyond {@link #CULL_DISTANCE} blocks, all dropped on logout.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HiddenHouseFx {
    private static final int MAX_WAVES = 4;
    private static final int MOTES_PER_TICK = 24;
    /** Of those, how many skim the ground as a ring. */
    private static final int RING_PER_TICK = 8;
    private static final double CULL_DISTANCE = 64.0;
    /** Pale silver-gold. */
    private static final int SHIMMER_RGB = 0xE8E4C8;
    private static final int MOTE_LIFETIME = 24;

    private record Wave(Vec3 origin, float radius, float speed, long start) {
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    private HiddenHouseFx() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WAVES.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            WAVES.clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        RandomSource random = level.random;
        WAVES.removeIf(w -> (now - w.start) * w.speed > w.radius + 1.0F);
        for (Wave w : WAVES) {
            if (w.origin.distanceToSqr(camera) > CULL_DISTANCE * CULL_DISTANCE) {
                continue;
            }
            double r = Math.max(0.5, (now - w.start) * (double) w.speed);
            for (int i = 0; i < MOTES_PER_TICK; i++) {
                double yaw = random.nextDouble() * Math.PI * 2.0;
                boolean ring = i < RING_PER_TICK;
                // Upper hemisphere (a little below the horizon too), or a flat ring on the ground.
                double up = ring ? 0.0 : random.nextDouble() * 1.15 - 0.15;
                double flat = Math.sqrt(Math.max(0.0, 1.0 - up * up));
                double x = w.origin.x + Math.cos(yaw) * flat * r;
                double z = w.origin.z + Math.sin(yaw) * flat * r;
                double y = ring ? w.origin.y - 1.0 + random.nextDouble() * 0.4 : w.origin.y + up * r;
                float size = 0.18F + random.nextFloat() * 0.2F;
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, SHIMMER_RGB, size, MOTE_LIFETIME),
                        x, y, z, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WAVES.clear();
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.HIDDEN_HOUSE_SHIMMER, e -> {
                ClientLevel level = Minecraft.getInstance().level;
                if (level == null) {
                    return;
                }
                if (WAVES.size() >= MAX_WAVES) {
                    WAVES.remove(0);
                }
                WAVES.add(new Wave(e.pos(), Math.max(1.0F, e.param(0, 16.0F)), Math.max(0.1F, e.param(1, 0.8F)),
                        level.getGameTime()));
            });
        }
    }
}
