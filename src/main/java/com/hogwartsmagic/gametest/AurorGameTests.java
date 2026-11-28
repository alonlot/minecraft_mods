package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.auror.AurorJustice;
import com.hogwartsmagic.auror.AurorPrison;
import com.hogwartsmagic.auror.AurorRules;
import com.hogwartsmagic.entity.AurorEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

/**
 * Gametests for the Aurors: the tower template's garrison and caged Death Eaters, a lever breakout, Aurors turning
 * on a witnessed dark caster, and Death Eaters going for Aurors. Each logs an AUROR-GAMETEST PASS line.
 */
@GameTestHolder(ExampleMod.MODID)
public final class AurorGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String ROOMY = "hogwartsmagic:gametest/empty9";

    private AurorGameTests() {
    }

    /** The vanilla mock player wired the same way, but in survival (the vanilla one always reports creative). */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "auror-test-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        net.minecraft.network.Connection connection =
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    @GameTest(template = "hogwartsmagic:auror_tower", timeoutTicks = 200)
    public static void towerHoldsItsGarrisonAndALeverFreesAPrisoner(GameTestHelper helper) {
        List<AurorEntity> aurors = helper.getEntities(ModEntities.AUROR.get());
        helper.assertTrue(aurors.size() == 6, "six Aurors in the tower, found " + aurors.size());
        List<WizardEntity> prisoners = helper.getEntities(ModEntities.WIZARD.get()).stream()
                .filter(AurorPrison::isPrisoner).toList();
        helper.assertTrue(prisoners.size() == 5, "five caged Death Eaters, found " + prisoners.size());
        var ra = helper.getLevel().registryAccess();
        var id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "auror_tower");
        helper.assertTrue(ra.registryOrThrow(Registries.STRUCTURE).containsKey(id), "the auror_tower structure did not load");
        helper.assertTrue(ra.registryOrThrow(Registries.STRUCTURE_SET).containsKey(id), "the auror_tower structure set did not load");

        helper.runAfterDelay(20, () -> {
            // Caged: disarmed and fighting nobody, though six Aurors stand about.
            for (WizardEntity w : prisoners) {
                helper.assertTrue(AurorPrison.isPrisoner(w) && w.isDisarmed() && w.getTarget() == null,
                        "a caged Death Eater should stay disarmed and idle");
                helper.assertTrue(!MagicWandItem.isAnyWand(w.getMainHandItem()), "a caged Death Eater holds no wand");
            }
            WizardEntity prisoner = prisoners.get(0);
            BlockPos cell = prisoner.blockPosition();
            BlockPos lever = null;
            for (BlockPos p : BlockPos.betweenClosed(cell.offset(-3, -1, -4), cell.offset(3, 2, 4))) {
                if (helper.getLevel().getBlockState(p).is(Blocks.LEVER)) {
                    if (lever == null || p.distSqr(cell) < lever.distSqr(cell)) {
                        lever = p.immutable();
                    }
                }
            }
            helper.assertTrue(lever != null, "every cell has a lever on the corridor side");
            ServerPlayer player = survivalPlayer(helper);
            Vec3 stand = Vec3.atBottomCenterOf(lever).add(0, -1, 0);
            player.moveTo(stand.x, stand.y, stand.z);
            var leverState = helper.getLevel().getBlockState(lever);
            ((net.minecraft.world.level.block.LeverBlock) leverState.getBlock()).pull(leverState, helper.getLevel(), lever, null);
            helper.runAfterDelay(AurorRules.PRISONER_CHECK_TICKS + 10, () -> {
                helper.assertTrue(!AurorPrison.isPrisoner(prisoner), "pulling the lever should free that cell's prisoner");
                helper.assertTrue(!prisoner.isDisarmed() && MagicWandItem.isAnyWand(prisoner.getMainHandItem()),
                        "the freed Death Eater takes up a wand");
                helper.assertTrue(player.getUUID().equals(prisoner.getEscortOwnerId()),
                        "the freed Death Eater fights for whoever let him out");
                helper.assertTrue(AurorJustice.isHunted(player), "freeing a prisoner makes the liberator wanted");
                boolean answered = helper.getEntities(ModEntities.AUROR.get()).stream()
                        .anyMatch(a -> a.getTarget() == prisoner);
                helper.assertTrue(answered, "the Aurors should answer the breakout");
                long stillCaged = helper.getEntities(ModEntities.WIZARD.get()).stream().filter(AurorPrison::isPrisoner).count();
                helper.assertTrue(stillCaged == 4, "only that one cell opened, " + stillCaged + " still caged");
                helper.succeed();
                LOGGER.info("AUROR-GAMETEST PASS towerHoldsItsGarrisonAndALeverFreesAPrisoner");
            });
        });
    }

    @GameTest(template = ROOMY, timeoutTicks = 60)
    public static void anAurorWhoSeesADarkCastTurnsOnTheCaster(GameTestHelper helper) {
        AurorEntity auror = helper.spawn(ModEntities.AUROR.get(), new Vec3(2.5, 1.0, 2.5));
        // A plain survival player: the gametest mock player always reports creative, and Aurors ignore creative players.
        ServerPlayer player = survivalPlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
        player.moveTo(at.x, at.y, at.z);
        helper.assertTrue(!AurorJustice.isHunted(player), "a clean player is not hunted");
        helper.runAfterDelay(2, () -> {
            AurorJustice.onDarkCast(player, SpellType.CRUCIO);
            helper.assertTrue(AurorJustice.isHunted(player), "a witnessed dark cast makes the caster wanted");
            helper.assertTrue(auror.getTarget() == player, "the Auror should turn on the caster");
            helper.succeed();
            LOGGER.info("AUROR-GAMETEST PASS anAurorWhoSeesADarkCastTurnsOnTheCaster");
        });
    }

    @GameTest(template = ROOMY, timeoutTicks = 100)
    public static void deathEatersAndAurorsFight(GameTestHelper helper) {
        AurorEntity auror = helper.spawn(ModEntities.AUROR.get(), new Vec3(2.5, 1.0, 2.5));
        WizardEntity wizard = helper.spawn(ModEntities.WIZARD.get(), new Vec3(6.5, 1.0, 6.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(wizard.getTarget() == auror, "the Death Eater should go for the Auror");
            helper.assertTrue(auror.getTarget() == wizard, "the Auror should go for the Death Eater");
            LOGGER.info("AUROR-GAMETEST PASS deathEatersAndAurorsFight");
        });
    }

    @GameTest(template = ROOMY, timeoutTicks = 80)
    public static void lightFlightHidesTheAurorAndLands(GameTestHelper helper) {
        AurorEntity auror = helper.spawn(ModEntities.AUROR.get(), new Vec3(1.5, 1.0, 1.5));
        helper.runAfterDelay(2, () -> {
            auror.startLightFlight(helper.absoluteVec(new Vec3(7.5, 1.0, 7.5)), false);
            helper.assertTrue(auror.isLightFlying() && auror.isInvisible(), "in flight the Auror is a streak, not a body");
            helper.assertTrue(!auror.hurt(auror.damageSources().magic(), 5.0f), "spells pass through the streak");
            helper.succeedWhen(() -> {
                helper.assertTrue(!auror.isLightFlying(), "the flight should land");
                helper.assertTrue(!auror.isNoGravity() && !auror.isInvisible(), "landed, it has a body and weight again");
                helper.assertTrue(auror.position().distanceTo(helper.absoluteVec(new Vec3(7.5, 1.0, 7.5))) < 3.0,
                        "it should land at the destination");
                LOGGER.info("AUROR-GAMETEST PASS lightFlightHidesTheAurorAndLands");
            });
        });
    }
}
