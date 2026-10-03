package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DiagonArchwayBlock;
import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Alohomora, the unlocking charm. Novice: wooden doors, trapdoors and fence gates. Adept: iron doors
 * and iron trapdoors too, which swing shut again after 100 ticks (an unloaded door is left open).
 * Expert: every openable door within 4 blocks, and a piston door with a small lock (see PistonLockSpell). Master: a Gringotts vault door from range (through the
 * door's own beginOpening, so the bank's alarm still fires) any piston door lock (forcing a comparator, observer or sensor if it must), and a hostile Ward's barrier, off for
 * 100 ticks. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlohomoraSpell {
    private record PendingClose(ResourceKey<Level> dimension, BlockPos pos, long closeAt) {
    }

    private static final List<PendingClose> PENDING = new ArrayList<>();

    private AlohomoraSpell() {
    }

    public static boolean onBlockHit(ServerLevel level, BlockPos pos, BlockState state, Vec3 impact, @Nullable Entity caster) {
        Entity owner = CombatEffects.responsible(caster);
        if (state.getBlock() instanceof DiagonArchwayBlock) {
            // The wall behind the Leaky Cauldron. Arming it is what makes the taps
            // start counting; until then it is just a wall.
            return DiagonArchwayBlock.enchant(level, pos, owner instanceof ServerPlayer sp ? sp : null);
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALOHOMORA);
        if (state.getBlock() instanceof GringottsVaultDoorBlock) {
            return openVault(level, pos, impact, owner, rank);
        }
        if (state.is(ExampleMod.WARD.get()) && level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
            return owner instanceof ServerPlayer player && suppressWard(level, ward, player, rank);
        }
        boolean opened = openOne(level, pos, state, owner, rank);
        int radius = AlohomoraRules.areaRadius(rank);
        if (radius > 0) {
            opened |= openAround(level, pos, radius, owner, rank) > 0;
        }
        if (!opened && owner instanceof ServerPlayer player
                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(rank)
                && com.hogwartsmagic.spell.mind.MindControl.hitPuppeteer() == null) {
            // A piston door: its lock is read and worked over the next seconds; XP is paid when it gives way.
            com.hogwartsmagic.spell.support.lock.PistonLockSpell.begin(level, pos, player, rank,
                    com.hogwartsmagic.spell.support.lock.PistonLockSpell.Use.OPEN);
        }
        return opened;
    }

    /** A Master Alohomora reaching a hostile barrier switches it off instead of being stopped by it. */
    public static boolean tryUnlockBarrier(ServerLevel level, CaveInimicumWardBlockEntity ward, SpellBeamEntity beam) {
        if (beam.getSpellType() != SpellType.ALOHOMORA || beam.isReflected() || !(beam.getOwner() instanceof ServerPlayer player)) {
            return false;
        }
        int rank = DisableSupport.casterRank(player, SpellType.ALOHOMORA);
        if (!suppressWard(level, ward, player, rank)) {
            return false;
        }
        beam.discard();
        MasteryAwards.onBlockEffect(player, SpellType.ALOHOMORA);
        return true;
    }

    private static boolean openOne(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity owner, int rank) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock door) {
            if (state.getValue(DoorBlock.OPEN)) {
                return false;
            }
            boolean iron = !door.type().canOpenByHand();
            if (iron && !AlohomoraRules.opensIron(rank)) {
                return false;
            }
            if (!mayUse(level, pos, owner)) {
                return false;
            }
            door.setOpen(owner, level, state, pos, true); // plays the door's own sound
            if (iron) {
                PENDING.add(new PendingClose(level.dimension(), pos.immutable(), level.getGameTime() + AlohomoraRules.IRON_OPEN_TICKS));
            }
            return true;
        }
        if (block instanceof TrapDoorBlock) {
            if (state.getValue(TrapDoorBlock.OPEN)) {
                return false;
            }
            boolean iron = state.is(Blocks.IRON_TRAPDOOR);
            if (iron && !AlohomoraRules.opensIron(rank)) {
                return false;
            }
            if (!mayUse(level, pos, owner)) {
                return false;
            }
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.levelEvent(null, iron ? 1037 : 1007, pos, 0);
            if (iron) {
                PENDING.add(new PendingClose(level.dimension(), pos.immutable(), level.getGameTime() + AlohomoraRules.IRON_OPEN_TICKS));
            }
            return true;
        }
        if (block instanceof FenceGateBlock) {
            if (state.getValue(FenceGateBlock.OPEN)) {
                return false;
            }
            if (!mayUse(level, pos, owner)) {
                return false;
            }
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.levelEvent(null, 1008, pos, 0);
            return true;
        }
        return false;
    }

    /**
     * Would a hand be allowed to use this block? A player caster passes spawn protection and the world
     * border, and the same right-click event claim mods guard doors with. An NPC caster is unchecked.
     */
    private static boolean mayUse(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return true;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND,
                pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled() && event.getUseBlock() != Event.Result.DENY;
    }

    private static int openAround(ServerLevel level, BlockPos center, int radius, @Nullable Entity owner, int rank) {
        int opened = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (opened >= AlohomoraRules.MAX_AREA_OPENINGS) {
                break;
            }
            if (pos.equals(center) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue; // each door once, by its lower half
            }
            if (openOne(level, pos.immutable(), state, owner, rank)) {
                opened++;
            }
        }
        return opened;
    }

    private static boolean openVault(ServerLevel level, BlockPos pos, Vec3 impact, @Nullable Entity owner, int rank) {
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        if (!AlohomoraRules.opensVaults(rank)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.alohomora.vault")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        // The caster is the opener: a non-goblin raises the alarm and the vault reseals, as by hand.
        return level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity door
                && door.beginOpening(level, impact, player);
    }

    private static boolean suppressWard(ServerLevel level, CaveInimicumWardBlockEntity ward, ServerPlayer player, int rank) {
        if (!AlohomoraRules.suppressesWards(rank) || !ward.isBarrierUp(level.getGameTime()) || !isHostile(level, ward, player)) {
            return false;
        }
        ward.suppressBarrier(level, AlohomoraRules.WARD_SUPPRESS_TICKS);
        BlockPos pos = ward.getBlockPos();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.9f, 1.6f);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.alohomora.ward")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** Someone else's Ward, and not a team-mate's. An unowned Ward is nobody's enemy. */
    private static boolean isHostile(ServerLevel level, CaveInimicumWardBlockEntity ward, ServerPlayer player) {
        UUID ownerId = ward.getOwnerUuid();
        if (ownerId == null || ownerId.equals(player.getUUID())) {
            return false;
        }
        ServerPlayer wardOwner = level.getServer().getPlayerList().getPlayer(ownerId);
        return wardOwner == null || !wardOwner.isAlliedTo(player);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<PendingClose> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingClose close = it.next();
            if (close.dimension() != level.dimension() || now < close.closeAt()) {
                continue;
            }
            it.remove();
            if (!level.isLoaded(close.pos())) {
                continue; // left open: never load a chunk to shut a door
            }
            BlockState state = level.getBlockState(close.pos());
            if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN)) {
                if (state.getValue(DoorBlock.POWERED) || level.hasNeighborSignal(close.pos())) {
                    continue; // redstone holds it open
                }
                door.setOpen(null, level, state, close.pos(), false);
            } else if (state.is(Blocks.IRON_TRAPDOOR) && state.getValue(TrapDoorBlock.OPEN)
                    && !state.getValue(TrapDoorBlock.POWERED) && !level.hasNeighborSignal(close.pos())) {
                level.setBlock(close.pos(), state.setValue(TrapDoorBlock.OPEN, false), Block.UPDATE_CLIENTS);
                level.levelEvent(null, 1036, close.pos(), 0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
