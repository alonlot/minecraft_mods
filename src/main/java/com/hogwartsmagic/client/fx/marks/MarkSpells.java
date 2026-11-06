package com.hogwartsmagic.client.fx.marks;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.client.fx.Marks;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.SpellBeamFx;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Which spells leave which mark. A beam strike the client already learns of ({@code SpellBeamClientState}) marks the
 * room for the spells with no server-side blast of their own: Incendio's fire scorches a small patch, Glacius frosts
 * a wider one. Bombarda and Confringo blasts happen on the server (they can detonate with no beam, and Bombarda
 * must wait for the blocks it breaks), so the server sends those marks ({@code FxKinds.MARK_SCORCH}, handled here).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MarkSpells {
    /** Incendio's scorch: radius (blocks) and seconds. */
    private static final float INCENDIO_RADIUS = 1.2f, INCENDIO_SECONDS = 45f;
    /** Glacius' frost: radius (blocks) and seconds. */
    private static final float GLACIUS_RADIUS = 1.7f, GLACIUS_SECONDS = 60f;

    private MarkSpells() {
    }

    /** A beam of {@code spell} struck at {@code pos} flying along {@code dir}, as one of {@code SpellBeamFx}'s kinds. */
    public static void onImpact(SpellType spell, byte kind, Vec3 pos, Vec3 dir) {
        if (spell == null || kind != SpellBeamFx.HIT) return;
        // Step back out of the surface the beam struck, onto the air side.
        Vec3 at = dir.lengthSqr() > 1.0e-6 ? pos.subtract(dir.normalize().scale(0.06)) : pos;
        if (spell == SpellType.INCENDIO) {
            Marks.scorch(at, INCENDIO_RADIUS, INCENDIO_SECONDS);
        } else if (spell == SpellType.GLACIUS) {
            Marks.frost(at, GLACIUS_RADIUS, GLACIUS_SECONDS);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // params: radius, seconds, delay in ticks (optional).
        FxClient.register(FxKinds.MARK_SCORCH, e -> Marks.scorch(e.pos(), e.param(0, 2.0f), e.param(1, 60.0f),
                Math.round(e.param(2, 0.0f))));
        FxClient.register(FxKinds.MARK_FROST, e -> Marks.frost(e.pos(), e.param(0, 1.5f), e.param(1, 60.0f)));
    }
}
