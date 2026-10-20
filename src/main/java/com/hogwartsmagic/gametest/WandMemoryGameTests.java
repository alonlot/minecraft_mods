package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModVillagers;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.event.OllivandersCeremony;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.WandMemory;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Wand memory trigger: the trade hook, the once-only flag, the fallback to the ceremony and {@code /ollivanders}.
 * The memory level is the PensieveSessions test seam (the test level stands in for it) and the scene is a stub named
 * {@code wand_wall}, so none of this depends on the shipped scene's content. Every test has its own batch: the seam
 * is global.
 */
@GameTestHolder(ExampleMod.MODID)
public final class WandMemoryGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final String ELDER = "hogwartsmagic:elder_wand";

    private WandMemoryGameTests() {
    }

    private static SceneDef stubScene(String id) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", 400, 8, set, Map.of(), List.of(), List.of(), List.of(),
                CameraTrack.EMPTY, null, 0, null);
    }

    /** The seam with a (long) {@code wand_wall} stub, or with no scenes at all. */
    private static void useSeam(GameTestHelper helper, boolean withScene) {
        String json = "{\"actors\":[{\"id\":\"boy\",\"type\":\"player\",\"items\":{\"mainhand\":\"{viewer_wand}\"}}]}";
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> withScene && WandMemory.SCENE.equals(id)
                ? Optional.of(new MemoryRegistry.LoadedScene(stubScene(id), json))
                : Optional.empty());
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        return p;
    }

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
    }

    private static Villager wandmaker(GameTestHelper helper) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.WANDMAKER.get()));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        v.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    private static ItemStack wand() {
        return new ItemStack(ExampleMod.ELDER_WAND.get());
    }

    /** The purchase as the trade screen finishes it: the wand is in the inventory, then the event fires. */
    private static void buy(ServerPlayer p, Villager v, ItemStack wand) {
        p.getInventory().add(wand.copy());
        ModEvents.onWandBought(new TradeWithVillagerEvent(p,
                new MerchantOffer(new ItemCost(Items.EMERALD), wand.copy(), 1, 1, 0F), v));
    }

    private static int wands(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(wand().getItem())) n += s.getCount();
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = "wand_first_starts", timeoutTicks = 100)
    public static void firstWandStartsMemory(GameTestHelper helper) {
        useSeam(helper, true);
        ServerPlayer p = player(helper);
        Villager v = wandmaker(helper);
        helper.assertTrue(!OllivandersCeremony.hasHadCeremony(p), "a fresh player has not had the ceremony");
        buy(p, v, wand());
        helper.assertTrue(PensieveSessions.isViewer(p), "the first wand should start the memory");
        helper.assertTrue(PensieveSessions.sessionOf(p).map(s -> WandMemory.SCENE.equals(s.sceneId())).orElse(false),
                "wrong scene");
        helper.assertTrue(PensieveSessions.sessionOf(p).get().rawJson().contains(ELDER),
                "the memory should show the bought wand");
        helper.assertTrue(!OllivandersCeremony.isBusy(p), "the ceremony must not run as well");
        helper.assertTrue(wands(p) == 1, "exactly the bought wand: " + wands(p));
        PensieveSessions.leave(p, ExitReason.LEFT);
        logout(helper, p);
        LOGGER.info("WANDMEMORY-GAMETEST PASS firstWandStartsMemory");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "wand_flagged", timeoutTicks = 100)
    public static void flaggedPlayerGetsNothing(GameTestHelper helper) {
        useSeam(helper, true);
        ServerPlayer p = player(helper);
        Villager v = wandmaker(helper);
        OllivandersCeremony.markDone(p);
        buy(p, v, wand());
        helper.assertTrue(!PensieveSessions.isViewer(p), "no memory for a player who has had it");
        helper.assertTrue(!OllivandersCeremony.isBusy(p), "no ceremony either");
        helper.assertTrue(wands(p) == 1, "exactly the bought wand: " + wands(p));
        logout(helper, p);
        LOGGER.info("WANDMEMORY-GAMETEST PASS flaggedPlayerGetsNothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "wand_scene_missing", timeoutTicks = 100)
    public static void wandKeptWhenMemoryFails(GameTestHelper helper) {
        useSeam(helper, false);
        ServerPlayer p = player(helper);
        Villager v = wandmaker(helper);
        buy(p, v, wand());
        helper.assertTrue(!PensieveSessions.isViewer(p), "no scene, so no memory");
        helper.assertTrue(OllivandersCeremony.isBusy(p), "the ceremony should be the fallback");
        helper.assertTrue(wands(p) == 1, "exactly the bought wand: " + wands(p));
        OllivandersCeremony.endAll();
        logout(helper, p);
        LOGGER.info("WANDMEMORY-GAMETEST PASS wandKeptWhenMemoryFails");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "wand_leave_early", timeoutTicks = 200)
    public static void leaveEarlyKeepsWand(GameTestHelper helper) {
        useSeam(helper, true);
        ServerPlayer p = player(helper);
        Villager v = wandmaker(helper);
        buy(p, v, wand());
        helper.assertTrue(PensieveSessions.isViewer(p), "the memory should have started");
        helper.assertTrue(!OllivandersCeremony.hasHadCeremony(p), "the flag waits for the end or a leave");
        AtomicBoolean left = new AtomicBoolean();
        helper.onEachTick(() -> {
            var s = PensieveSessions.sessionOf(p);
            if (left.get() || s.isEmpty() || s.get().phase() != Session.Phase.PLAY) return;
            left.set(true);
            PensieveSessions.leave(p, ExitReason.LEFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get(), "not left yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(OllivandersCeremony.hasHadCeremony(p), "leaving should set the flag");
            helper.assertTrue(wands(p) == 1, "exactly the bought wand: " + wands(p));
            logout(helper, p);
            LOGGER.info("WANDMEMORY-GAMETEST PASS leaveEarlyKeepsWand");
        });
    }

    @GameTest(template = EMPTY, batch = "wand_trade_closed", timeoutTicks = 100)
    public static void tradeScreenClosedBeforeMemory(GameTestHelper helper) {
        useSeam(helper, true);
        ServerPlayer p = player(helper);
        Villager v = wandmaker(helper);
        v.setTradingPlayer(p);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new MerchantMenu(id, inv, v), Component.literal("Wands")));
        helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen should be open");
        buy(p, v, wand());
        helper.assertTrue(PensieveSessions.isViewer(p), "the memory should have started");
        helper.assertTrue(p.containerMenu == p.inventoryMenu, "the trade screen should be closed");
        helper.assertTrue(v.getTradingPlayer() == null, "the wandmaker should not be left trading");
        helper.assertTrue(wands(p) == 1, "exactly the bought wand: " + wands(p));
        PensieveSessions.leave(p, ExitReason.LEFT);
        logout(helper, p);
        LOGGER.info("WANDMEMORY-GAMETEST PASS tradeScreenClosedBeforeMemory");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "wand_command", timeoutTicks = 100)
    public static void commandReplays(GameTestHelper helper) {
        useSeam(helper, true);
        ServerPlayer p = player(helper);
        OllivandersCeremony.markDone(p);
        p.setItemInHand(InteractionHand.MAIN_HAND, wand());
        helper.getLevel().getServer().getCommands()
                .performPrefixedCommand(p.createCommandSourceStack().withPermission(2), "ollivanders");
        helper.assertTrue(PensieveSessions.isViewer(p), "/ollivanders should replay the memory");
        helper.assertTrue(PensieveSessions.sessionOf(p).get().rawJson().contains(ELDER),
                "the replay should show the held wand");
        helper.assertTrue(!OllivandersCeremony.isBusy(p), "not the ceremony");
        helper.assertTrue(p.getMainHandItem().is(wand().getItem()) && p.getMainHandItem().getCount() == 1,
                "the held wand is unchanged");
        PensieveSessions.leave(p, ExitReason.LEFT);
        logout(helper, p);
        LOGGER.info("WANDMEMORY-GAMETEST PASS commandReplays");
        helper.succeed();
    }
}
