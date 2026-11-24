package com.hogwartsmagic.hideout;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.entity.HorcruxSoulEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.util.MagicParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * What happens when a hideout's golden cup dies: the cup shrieks, a dark soul rises through the
 * ceiling, and a moment later a chest of treasure stands where the cup was (or, if something
 * solid took the spot, the treasure spills out as items). All of it runs on the server thread.
 */
public final class CursedCup {
    private CursedCup() {
    }

    private static ResourceKey<LootTable> vaultKey() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, HideoutRules.VAULT_LOOT));
    }

    /** Binds the cup block entity at {@code pos} the way the baked template does (Active, no owner). For tests. */
    public static void arm(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HorcruxBlockEntity be) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Active", true);
            be.loadCustomOnly(tag, level.registryAccess());
            be.setChanged();
        }
    }

    /** The cup has just been broken: shriek, free the soul, tell the room, and queue the reward. */
    public static void release(ServerLevel level, BlockPos cup) {
        Vec3 centre = Vec3.atCenterOf(cup);
        HorcruxSoulEntity soul = ModEntities.HORCRUX_SOUL.get().create(level);
        if (soul != null) {
            soul.setPlayerName("");
            soul.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
            soul.setTarget(cup.above(HideoutRules.SOUL_RISE_BLOCKS));
            level.addFreshEntity(soul);
        }
        level.playSound(null, cup, ModSounds.HORCRUX_SCREAM.get(), SoundSource.HOSTILE, 1.4F, 0.8F);
        level.playSound(null, cup, ModSounds.HORCRUX_SHATTER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(MagicParticles.SMOKE_GREEN, centre.x, centre.y + 0.2, centre.z, 40, 0.35, 0.4, 0.35, 0.04);
        level.sendParticles(MagicParticles.SMOKE_DARK, centre.x, centre.y + 0.2, centre.z, 30, 0.4, 0.5, 0.4, 0.03);
        Fx.shake(level, centre, 0.6F, 12.0F, 12);
        double r = HideoutRules.FREED_MESSAGE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centre) <= r * r) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.cursed_cup.freed"), false);
            }
        }
        // Next server tick, not a CurseScheduler job: that list is dropped when the server stops, and a player
        // who leaves (Portkey, Floo, quit) right after breaking the cup must still get the chest.
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1,
                () -> payOut(level, cup)));
    }

    /** The reward: a chest on the cup's spot if it is free (fire and air give way), else the loot spilled. */
    public static void payOut(ServerLevel level, BlockPos cup) {
        if (!level.isLoaded(cup)) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(cup);
        BlockState here = level.getBlockState(cup);
        if (here.canBeReplaced() && !here.is(Blocks.LAVA)) {
            Direction facing = Direction.SOUTH;
            Player near = level.getNearestPlayer(centre.x, centre.y, centre.z, 16.0, false);
            if (near != null) {
                Direction toward = Direction.getNearest(near.getX() - centre.x, 0.0, near.getZ() - centre.z);
                if (toward.getAxis().isHorizontal()) {
                    facing = toward;
                }
            }
            level.setBlock(cup, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
            if (level.getBlockEntity(cup) instanceof ChestBlockEntity chest) {
                chest.setLootTable(vaultKey(), level.random.nextLong());
            }
            level.sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y + 0.6, centre.z, 40, 0.4, 0.4, 0.4, 0.5);
            level.playSound(null, cup, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.9F);
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(vaultKey());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, centre)
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params, level.random.nextLong())) {
            Block.popResource(level, cup, stack);
        }
        level.playSound(null, cup, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2F, 0.9F);
    }
}
