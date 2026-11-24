package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.HorcruxSoulEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.hideout.CursedCup;
import com.hogwartsmagic.hideout.HideoutLeader;
import com.hogwartsmagic.hideout.HideoutRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gametests for the dark wizard hideouts: the cursed cup (burned, mined, unbound, blocked reward)
 * and the leader's one-time arming and leash. Each logs a HIDEOUT-GAMETEST PASS line.
 */
@GameTestHolder(ExampleMod.MODID)
public final class HideoutGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String ROOMY = "hogwartsmagic:gametest/empty9";
    private static final ResourceLocation VAULT =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, HideoutRules.VAULT_LOOT);

    private HideoutGameTests() {
    }

    private static int souls(GameTestHelper helper, BlockPos rel, double range) {
        Vec3 c = helper.absoluteVec(Vec3.atCenterOf(rel));
        return helper.getLevel().getEntitiesOfClass(HorcruxSoulEntity.class, new AABB(c, c).inflate(range)).size();
    }

    private static void assertVaultChest(GameTestHelper helper, BlockPos rel) {
        helper.assertTrue(helper.getBlockEntity(rel) instanceof ChestBlockEntity chest
                        && VAULT.equals(chest.getLootTable() == null ? null : chest.getLootTable().location()),
                "the reward chest should stand where the cup was, with the vault loot");
    }

    @GameTest(template = ROOMY, timeoutTicks = 120)
    public static void fiendfyreDevoursTheCupAndFreesTheSoul(GameTestHelper helper) {
        BlockPos cup = new BlockPos(2, 1, 2);
        BlockPos plain = new BlockPos(4, 1, 2);
        helper.setBlock(cup, ExampleMod.CURSED_CUP.get());
        helper.setBlock(plain, ExampleMod.HORCRUX.get());
        CursedCup.arm(helper.getLevel(), helper.absolutePos(cup));
        CursedCup.arm(helper.getLevel(), helper.absolutePos(plain));
        FiendfyreSerpentEntity serpent = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), helper.getLevel());
        serpent.configure(0, 1, 400, 400);
        serpent.setDestroysHorcrux(true);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.5, 2.5));
        serpent.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(serpent);
        AtomicBoolean sawSoul = new AtomicBoolean();
        AtomicBoolean scheduled = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (souls(helper, cup, 4) > 0) {
                sawSoul.set(true);
            }
            if (!helper.getBlockState(cup).is(ExampleMod.CURSED_CUP.get()) && scheduled.compareAndSet(false, true)) {
                helper.runAfterDelay(HideoutRules.REWARD_DELAY_TICKS + 5, () -> {
                    serpent.discard();
                    helper.assertTrue(sawSoul.get(), "a soul should rise from the cup");
                    helper.assertTrue(!helper.getBlockState(plain).is(ExampleMod.HORCRUX.get()), "the player's own Horcrux should burn too");
                    assertVaultChest(helper, cup);
                    helper.succeed();
                    LOGGER.info("HIDEOUT-GAMETEST PASS fiendfyreDevoursTheCupAndFreesTheSoul");
                });
            }
        });
        helper.runAfterDelay(110, () -> serpent.discard());
    }

    @GameTest(template = ROOMY, timeoutTicks = 80)
    public static void minedCupFreesTheSoulAndDropsNothing(GameTestHelper helper) {
        BlockPos cup = new BlockPos(2, 1, 2);
        helper.setBlock(cup, ExampleMod.CURSED_CUP.get());
        CursedCup.arm(helper.getLevel(), helper.absolutePos(cup));
        ServerPlayer player = survivalPlayer(helper, "Cupbreaker");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        try {
            player.gameMode.destroyBlock(helper.absolutePos(cup));
            helper.assertTrue(helper.getBlockState(cup).isAir(), "the cup should be gone");
            helper.assertTrue(souls(helper, cup, 4) > 0, "a soul should rise");
            Vec3 c = helper.absoluteVec(Vec3.atCenterOf(cup));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(3)).isEmpty(),
                    "a destroyed cup drops nothing");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.runAfterDelay(HideoutRules.REWARD_DELAY_TICKS + 5, () -> {
            assertVaultChest(helper, cup);
            helper.succeed();
            LOGGER.info("HIDEOUT-GAMETEST PASS minedCupFreesTheSoulAndDropsNothing");
        });
    }

    @GameTest(template = ROOMY, timeoutTicks = 60)
    public static void unboundCupFreesNothing(GameTestHelper helper) {
        BlockPos cup = new BlockPos(2, 1, 2);
        helper.setBlock(cup, ExampleMod.CURSED_CUP.get());
        helper.getLevel().destroyBlock(helper.absolutePos(cup), false);
        helper.runAfterDelay(HideoutRules.REWARD_DELAY_TICKS + 5, () -> {
            helper.assertTrue(souls(helper, cup, 6) == 0, "an unbound cup frees no soul");
            helper.assertTrue(!(helper.getBlockEntity(cup) instanceof ChestBlockEntity), "an unbound cup pays nothing");
            helper.succeed();
            LOGGER.info("HIDEOUT-GAMETEST PASS unboundCupFreesNothing");
        });
    }

    @GameTest(template = ROOMY, timeoutTicks = 60)
    public static void occupiedSpotSpillsTheReward(GameTestHelper helper) {
        BlockPos spot = new BlockPos(2, 1, 2);
        helper.setBlock(spot, Blocks.STONE);
        CursedCup.payOut(helper.getLevel(), helper.absolutePos(spot));
        helper.assertTrue(helper.getBlockState(spot).is(Blocks.STONE), "the stone stays");
        Vec3 c = helper.absoluteVec(Vec3.atCenterOf(spot));
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c, c).inflate(2)).isEmpty(),
                "the reward should spill as items");
        helper.succeed();
        LOGGER.info("HIDEOUT-GAMETEST PASS occupiedSpotSpillsTheReward");
    }

    @GameTest(template = ROOMY, timeoutTicks = 40)
    public static void leaderArmsOnceAndIsLeashed(GameTestHelper helper) {
        WizardEntity wizard = new WizardEntity(ModEntities.WIZARD.get(), helper.getLevel());
        wizard.addTag(HideoutRules.LEADER_TAG);
        wizard.setRaidLeader(true);
        wizard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WHITE_BANNER));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
        wizard.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(wizard);
        BlockPos home = helper.absolutePos(new BlockPos(4, 1, 4));
        try {
            helper.assertTrue(wizard.getMaxHealth() == 80.0F && wizard.getHealth() == 80.0F, "80 health");
            helper.assertTrue(wizard.getArmorValue() == 8, "armour 8, got " + wizard.getArmorValue());
            helper.assertTrue(wizard.getCustomName() != null
                    && HideoutRules.LEADER_NAMES.contains(wizard.getCustomName().getString()), "named");
            helper.assertTrue(!wizard.isRaidLeader() && wizard.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "no raid-leader banner");
            helper.assertTrue(wizard.hasRestriction() && wizard.getRestrictCenter().equals(home)
                    && (int) wizard.getRestrictRadius() == 9, "restricted to home");
            helper.assertTrue(goals(wizard) == 1, "exactly one guard goal");
            wizard.setHealth(30.0F);
            HideoutLeader.prepare(wizard);
            helper.assertTrue(wizard.getHealth() == 30.0F, "a reload must not heal him");
            helper.assertTrue(goals(wizard) == 1, "still exactly one guard goal");
            Vec3 far = helper.absoluteVec(new Vec3(4.5, 1, 4.5)).add(12, 0, 0);
            wizard.moveTo(far.x, far.y, far.z);
        } catch (RuntimeException e) {
            wizard.discard();
            throw e;
        }
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertTrue(!wizard.getNavigation().isDone(), "he should be walking home");
                BlockPos target = wizard.getNavigation().getTargetPos();
                helper.assertTrue(target != null && target.distSqr(home) <= 9, "his path should lead home");
            } finally {
                wizard.discard();
            }
            helper.succeed();
            LOGGER.info("HIDEOUT-GAMETEST PASS leaderArmsOnceAndIsLeashed");
        });
    }

    private static long goals(WizardEntity wizard) {
        return wizard.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof HideoutLeader.GuardAltarGoal).count();
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, player, cookie);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(at.x, at.y, at.z);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return player;
    }
}
