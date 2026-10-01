package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reaching for a guarded chest brings the stone up.
 *
 * <p>A dormant {@link LivingStatueEntity} already wakes on anything that walks inside its own
 * {@link LivingStatueEntity#WAKE_RADIUS}, and that sense is generous -- five blocks measured off a
 * hitbox nearly three blocks tall. Any spot a chest can be reached from is inside the sense box of
 * a statue standing beside that chest, so the near one is essentially always already rising by the
 * time the lid is touched: this handler is not what gets that one up, and the {@code isDormant}
 * filter below deliberately leaves it alone.
 *
 * <p>What it is for is the guardians proximity does not reach -- the far one of a set, the one
 * posted across the room, the one behind the wall. Robbing the chest is the moment the trap is
 * supposed to close on <i>all</i> of them, not just on the one whose feet you walked past.
 *
 * <p>Radius from the chest, rather than membership of a structure the way {@link MerfolkEvents}
 * does it. The merpeople need the tag because they swim and are rarely near their own hoard; a
 * statue is bolted to the floor it is guarding, so where it is standing is the whole of the
 * information needed, and a radius keeps working for one a player has stood next to a chest of
 * their own.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class LivingStatueEvents {

    /**
     * Measured from the chest rather than from the player, because reach lets you open one from
     * several blocks back and it is the chest being guarded, not you.
     *
     * <p>Twelve is loose on purpose. The ruin camp's far statue stands five blocks from the near
     * chest, so six would cover that build as it is drawn today; the margin is what makes this a
     * rule about the room a guardian is standing in rather than a hard wire between two particular
     * blocks, which would quietly stop working the first time the hearth is rebuilt a block over.
     */
    private static final double ALARM_RADIUS = 12.0D;

    private LivingStatueEvents() {
    }

    /**
     * {@code PlayerInteractEvent.RightClickBlock} rather than {@code PlayerContainerEvent.Open},
     * which is the event that literally means "a chest was opened": that one carries only the
     * menu, and a menu has no block position on it, so there would be nothing to measure a radius
     * from.
     */
    @SubscribeEvent
    public static void onChestOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The same exclusions the statue's own sense makes: this is a trap for somebody who came
        // to rob the place, not for a builder passing through it.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        // Sneaking with something in hand places against the chest instead of opening it.
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            return;
        }
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof ChestBlock)) {
            return;
        }
        rouseGuardians(level, event.getPos());
    }

    /**
     * Wake every ruin guardian in earshot of {@code pos}.
     *
     * <p>Bound statues are skipped. A bound one is a sentry a player raised and it follows them
     * about, so waking it off a chest would mean it stood up every time its own owner opened a
     * crate at camp -- noise, not an ambush.
     */
    private static void rouseGuardians(ServerLevel level, BlockPos pos) {
        for (LivingStatueEntity statue : level.getEntitiesOfClass(LivingStatueEntity.class,
                new AABB(pos).inflate(ALARM_RADIUS),
                s -> s.isAlive() && s.isDormant() && !s.isBound())) {
            statue.wake();
        }
    }
}
