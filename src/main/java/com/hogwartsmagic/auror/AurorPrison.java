package com.hogwartsmagic.auror;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WizardEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;

/**
 * A Death Eater locked in an Auror tower cell. The tower generator bakes it in with AurorRules.PRISONER_TAG and no
 * wand. While caged it fights nobody and nobody fights it. It is freed when its cell door stands open (a lever, a
 * spell, a hand) or when it ends up outside its cell; then it takes up a wand, and the nearest player, its
 * liberator, gets it as a Death Eater escort for five minutes. The Aurors nearby hear the breakout and the liberator
 * is wanted.
 */
public final class AurorPrison {
    private static final String KEY_FREED = "chat.hogwartsmagic.auror.prisoner_freed";
    private static final String KEY_ESCAPED = "chat.hogwartsmagic.auror.prisoner_escaped";

    private AurorPrison() {
    }

    public static boolean isPrisoner(WizardEntity wizard) {
        return wizard.getTags().contains(AurorRules.PRISONER_TAG);
    }

    /** From WizardEntity.tick, server side, while the wizard is a prisoner. */
    public static void tick(WizardEntity wizard) {
        wizard.setTarget(null);
        wizard.setAggressive(false);
        if (!(wizard.level() instanceof ServerLevel level)) {
            return;
        }
        var data = wizard.getPersistentData();
        if (!data.contains(AurorRules.CELL_KEY)) {
            data.putLong(AurorRules.CELL_KEY, wizard.blockPosition().asLong());
            if (!wizard.isDisarmed()) {
                wizard.setDisarmed(true);
            }
            wizard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            // Structure placement runs finalizeSpawn, which may have made it a raid leader (banner and all).
            if (wizard.isRaidLeader()) {
                wizard.setRaidLeader(false);
                wizard.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            wizard.setPersistenceRequired();
        }
        if ((wizard.tickCount + wizard.getId()) % AurorRules.PRISONER_CHECK_TICKS != 0) {
            return;
        }
        BlockPos cell = BlockPos.of(data.getLong(AurorRules.CELL_KEY));
        double dx = wizard.getX() - (cell.getX() + 0.5D);
        double dz = wizard.getZ() - (cell.getZ() + 0.5D);
        if (AurorRules.escaped(dx * dx + dz * dz) || cellOpen(level, cell)) {
            free(level, wizard);
        }
    }

    /** Any open door, gate or trapdoor within reach of the cell block, at foot or head height. */
    static boolean cellOpen(ServerLevel level, BlockPos cell) {
        int r = AurorRules.CELL_DOOR_REACH;
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-r, 0, -r), cell.offset(r, 1, r))) {
            BlockState state = level.getBlockState(pos);
            if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                    || state.getBlock() instanceof TrapDoorBlock)
                    && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
                return true;
            }
        }
        return false;
    }

    public static void free(ServerLevel level, WizardEntity wizard) {
        wizard.removeTag(AurorRules.PRISONER_TAG);
        wizard.getPersistentData().remove(AurorRules.CELL_KEY);
        wizard.setDisarmed(false);
        wizard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));

        level.playSound(null, wizard.getX(), wizard.getY(), wizard.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE,
                1.2F, 0.8F);
        level.playSound(null, wizard.getX(), wizard.getY(), wizard.getZ(), com.hogwartsmagic.ModSounds.WIZARD_AMBIENT.get(),
                SoundSource.HOSTILE, 1.2F, 0.9F);
        level.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SQUID_INK), wizard.getX(),
                wizard.getY() + 1.0D, wizard.getZ(), 24, 0.4D, 0.7D, 0.4D, 0.05D);

        ServerPlayer liberator = liberator(level, wizard);
        if (liberator != null) {
            wizard.setEscort(liberator.getUUID(), level.getGameTime() + AurorRules.FREED_ESCORT_TICKS);
            wizard.setEscortOwnerDeaths(liberator.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
            liberator.sendSystemMessage(Component.translatable(KEY_FREED).withStyle(ChatFormatting.DARK_GREEN));
        } else {
            for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class,
                    wizard.getBoundingBox().inflate(AurorRules.ALARM_RADIUS))) {
                near.sendSystemMessage(Component.translatable(KEY_ESCAPED).withStyle(ChatFormatting.DARK_RED));
            }
        }
        AurorJustice.raiseAlarm(level, wizard.blockPosition(), wizard, liberator);
    }

    @Nullable
    private static ServerPlayer liberator(ServerLevel level, WizardEntity wizard) {
        ServerPlayer best = null;
        double bestSq = AurorRules.LIBERATOR_RADIUS * AurorRules.LIBERATOR_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double d = player.distanceToSqr(wizard);
            if (d <= bestSq) {
                bestSq = d;
                best = player;
            }
        }
        return best;
    }
}
