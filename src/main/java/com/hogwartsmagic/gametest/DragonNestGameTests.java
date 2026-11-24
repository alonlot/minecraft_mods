package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.dragon.EggThiefTargetGoal;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Gametests for stealing dragon eggs: the grudge, the creative exemption, the wild Ironbelly's
 * nest and the thief who leaves. They use a real survival player, because the helper's mock player
 * is creative and would raise no grudge.
 */
@GameTestHolder(ExampleMod.MODID)
public final class DragonNestGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final String BATCH = "dragon_nest";
    /** Where the egg sits, relative to the test structure. */
    private static final BlockPos EGG = new BlockPos(1, 2, 1);

    private DragonNestGameTests() {
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, player, cookie);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 2.5));
        player.moveTo(at.x, at.y, at.z);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        return player;
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static void putEgg(GameTestHelper helper, Block egg) {
        helper.setBlock(EGG, egg);
    }

    private static HungarianHorntailEntity horntail(GameTestHelper helper) {
        HungarianHorntailEntity dragon = helper.spawn(ModEntities.HUNGARIAN_HORNTAIL.get(), new Vec3(1.5, 2, 9.5));
        dragon.setNoAi(true);
        dragon.setNoGravity(true);
        return dragon;
    }

    private static UkrainianIronbellyEntity ironbelly(GameTestHelper helper) {
        UkrainianIronbellyEntity dragon = helper.spawn(ModEntities.UKRAINIAN_IRONBELLY.get(), new Vec3(1.5, 2, 9.5));
        dragon.setNoAi(true);
        dragon.setNoGravity(true);
        return dragon;
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = BATCH)
    public static void breakingAHorntailEggSetsTheDragonOnTheThief(GameTestHelper helper) {
        floor(helper);
        putEgg(helper, ExampleMod.HORNTAIL_EGG.get());
        HungarianHorntailEntity dragon = horntail(helper);
        ServerPlayer player = survivalPlayer(helper, "egg-thief");
        try {
            helper.assertTrue(dragon.getTarget() == null, "the dragon should start without a target");
            player.gameMode.destroyBlock(helper.absolutePos(EGG));
            helper.assertTrue(dragon.getTarget() == player, "the dragon should hunt the thief");
            helper.assertTrue(player.getUUID().equals(dragon.eggThief()), "the dragon should remember the thief");
            helper.assertTrue(dragon.eggGrudgeUntil() == helper.getLevel().getGameTime() + 6000,
                    "the grudge should last five minutes");
            helper.assertTrue(helper.getBlockState(EGG).isAir(), "the egg block should be gone");
            AABB near = new AABB(helper.absolutePos(EGG)).inflate(3);
            helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, near,
                    e -> e.getItem().is(ExampleMod.HORNTAIL_EGG_ITEM.get())).isEmpty(),
                    "the egg item should drop");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = BATCH)
    public static void aCreativePlayerTakesAnEggWithoutAGrudge(GameTestHelper helper) {
        floor(helper);
        putEgg(helper, ExampleMod.HORNTAIL_EGG.get());
        HungarianHorntailEntity dragon = horntail(helper);
        ServerPlayer player = survivalPlayer(helper, "egg-creative");
        try {
            player.setGameMode(GameType.CREATIVE);
            player.gameMode.destroyBlock(helper.absolutePos(EGG));
            helper.assertTrue(dragon.getTarget() == null, "a creative player must not be hunted");
            helper.assertTrue(dragon.eggThief() == null, "a creative player must not be remembered");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = BATCH)
    public static void theWildIronbellyHuntsTheThiefAndKeepsItsNest(GameTestHelper helper) {
        floor(helper);
        putEgg(helper, ExampleMod.IRONBELLY_EGG.get());
        UkrainianIronbellyEntity dragon = ironbelly(helper);
        CompoundTag tag = new CompoundTag();
        dragon.addAdditionalSaveData(tag);
        tag.putBoolean("Chained", false);
        dragon.readAdditionalSaveData(tag);
        dragon.tick();
        helper.assertTrue(dragon.blockPosition().equals(dragon.nest()), "the wild Ironbelly should take its spot as its nest");
        helper.assertTrue(dragon.anchor() == null, "the wild Ironbelly has no anchor");
        ServerPlayer player = survivalPlayer(helper, "iron-thief");
        try {
            player.gameMode.destroyBlock(helper.absolutePos(EGG));
            helper.assertTrue(dragon.getTarget() == player, "the Ironbelly should hunt the thief");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = BATCH)
    public static void theGringottsIronbellyNeverTakesANest(GameTestHelper helper) {
        floor(helper);
        UkrainianIronbellyEntity dragon = ironbelly(helper);
        dragon.setAnchor(dragon.blockPosition());
        for (int i = 0; i < 5; i++) {
            dragon.tick();
        }
        helper.assertTrue(dragon.nest() == null, "the Gringotts Ironbelly must not adopt a nest");
        helper.assertTrue(dragon.isChained(), "the Gringotts Ironbelly stays chained");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40, batch = BATCH)
    public static void aThiefWhoLeftIsNoLongerHunted(GameTestHelper helper) {
        floor(helper);
        putEgg(helper, ExampleMod.HORNTAIL_EGG.get());
        HungarianHorntailEntity dragon = horntail(helper);
        ServerPlayer player = survivalPlayer(helper, "egg-leaver");
        java.util.UUID id = player.getUUID();
        player.gameMode.destroyBlock(helper.absolutePos(EGG));
        helper.assertTrue(id.equals(dragon.eggThief()), "the theft should be remembered");
        leave(helper, player);
        helper.assertTrue(EggThiefTargetGoal.resolveThief(dragon) == null, "a thief who left cannot be found");
        helper.assertTrue(id.equals(dragon.eggThief()), "the grudge outlives the log-out");
        helper.succeed();
    }
}
