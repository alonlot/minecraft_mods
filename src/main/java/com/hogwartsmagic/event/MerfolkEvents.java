package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MerpersonEntity;
import com.hogwartsmagic.item.MerchieftainessCrownItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Merpeople and the hoard at the foot of their chieftainess.
 *
 * <p>Opening or breaking any chest inside the merfolk village turns the whole
 * village on you at once. This is the Goblet of Fire beat: they let Harry take
 * his own hostage and swim away, and levelled spears the moment he reached for
 * one that was not his. Their ordinary territorial ladder is forgiving — a
 * warning, then five seconds of grace. Theft skips all of it.
 *
 * <p>Membership is decided by a <b>structure tag</b> rather than by proximity to
 * a guard (the way {@link GoblinEvents} does it), because merpeople swim: a pod
 * scattered round a lakebed village is nowhere near its own treasure most of the
 * time, and a radius check would let you rob the place whenever they drifted
 * off. The tag also means the lesser stash behind the huts counts as theft too.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class MerfolkEvents {

    /** Structures whose containers the merpeople consider theirs. */
    private static final TagKey<Structure> MERFOLK_GUARDED = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "merfolk_guarded"));

    /**
     * Everyone in the village answers, not just those who saw it. The village is
     * 25 blocks across and they wander, so this reaches past its walls.
     */
    private static final double ALARM_RADIUS = 64.0D;

    private MerfolkEvents() {
    }

    @SubscribeEvent
    public static void onHoardOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        // Sneaking with a block in hand places against the chest instead of
        // opening it, and that is not a robbery.
        if (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
            return;
        }
        disturb(player, event.getPos());
    }

    @SubscribeEvent
    public static void onHoardBroken(BlockEvent.BreakEvent event) {
        disturb(event.getPlayer(), event.getPos());
    }

    private static void disturb(Player player, BlockPos pos) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!isContainer(level.getBlockState(pos))) {
            return;
        }
        if (!level.structureManager().getStructureWithPieceAt(pos, MERFOLK_GUARDED).isValid()) {
            return;
        }
        MerpersonEntity.raiseAlarm(level, pos, player, ALARM_RADIUS);
    }

    /**
     * The crown's gift: the sea's own conduit power for as long as it is worn.
     *
     * <p>Applied from a player tick rather than {@code inventoryTick}, because
     * the vanilla inventory tick walks the main inventory and never reaches the
     * armour slots. Re-applied once a second with a three-second duration so it
     * never visibly lapses between ticks, hidden from the HUD and given no
     * particles — it should feel like a property of the wearer, not a buff.
     */
    @SubscribeEvent
    public static void onCrownWorn(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide || player.tickCount % 20 != 0) {
            return;
        }
        if (!MerchieftainessCrownItem.isWornBy(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 60, 0, true, false, false));
    }

    private static boolean isContainer(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }
}
