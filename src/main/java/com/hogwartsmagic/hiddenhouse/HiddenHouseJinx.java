package com.hogwartsmagic.hiddenhouse;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.tomb.TombCurses;
import com.hogwartsmagic.block.entity.HiddenHouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The jinxed chest of a hidden wizard house. It is an ordinary chest with an invisible
 * {@link ExampleMod#JINX_WARD} floating over it; a player who opens it before Revelio has lifted the
 * ward is thrown back across the room and blinded for six seconds, once. The ward is gone after that
 * (and also when Revelio unmasks the house), so the chest then opens like any other.
 *
 * <p>The flag lives in the world as the ward block, not in the chest: Forge 51 block entities have no
 * persistent data. The ward has no collision and conducts no redstone, so it never gets in the way.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class HiddenHouseJinx {
    private static final float SLAM_RADIUS = 6.0F;
    private static final float SLAM_STRENGTH = 0.6F;
    private static final float SHAKE_STRENGTH = 1.2F;
    private static final int SHAKE_TICKS = 14;
    /** The violet of the hex, as its burst draws it. */
    private static final int HEX_RGB = 0x8A3CE0;

    private HiddenHouseJinx() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player) || !TombCurses.canTrigger(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).getBlock() instanceof ChestBlock && tryHex(level, pos, player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Mining the chest is no way round the jinx: the hex fires, and the chest stays for now. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)
                || !TombCurses.canTrigger(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockState(pos).getBlock() instanceof ChestBlock && tryHex(level, pos, player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Removes the ward at {@code wardPos} and hands the chest below the loot table the ward was holding.
     * The jinxed chest is stored with no loot of its own, so a hopper under it, or a broken chest, can
     * take nothing until the ward is gone.
     */
    public static void liftWard(ServerLevel level, BlockPos wardPos) {
        BlockEntity be = level.getBlockEntity(wardPos);
        CompoundTag held = be instanceof HiddenHouseBlockEntity hidden ? hidden.realNbt() : null;
        level.setBlock(wardPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (held != null && held.contains("LootTable")
                && level.getBlockEntity(wardPos.below()) instanceof ChestBlockEntity chest) {
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.parse(held.getString("LootTable"))), level.random.nextLong());
            chest.setChanged();
        }
    }

    /**
     * Fires the hex if a ward sits over {@code chest}: knockback away from the chest, blindness, the
     * ward consumed.
     *
     * @return true when the hex fired
     */
    public static boolean tryHex(ServerLevel level, BlockPos chest, ServerPlayer player) {
        BlockPos above = chest.above();
        if (!level.getBlockState(above).is(ExampleMod.JINX_WARD.get())) {
            return false;
        }
        liftWard(level, above);
        player.knockback(HiddenHouseRules.JINX_PUSH, chest.getX() + 0.5 - player.getX(),
                chest.getZ() + 0.5 - player.getZ());
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HiddenHouseRules.JINX_BLIND_TICKS));
        Vec3 at = Vec3.atCenterOf(chest);
        Fx.send(level, FxKinds.SLAM, at, 48.0, -1, SLAM_RADIUS, SLAM_STRENGTH);
        Fx.shake(level, at, SHAKE_STRENGTH, 16.0F, SHAKE_TICKS);
        level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, HEX_RGB, 0.6F, 20),
                at.x, at.y + 0.3, at.z, 14, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, at.x, at.y, at.z, ModSounds.HIDDEN_HOUSE_JINX.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.hidden_house.jinx"), true);
        return true;
    }
}
