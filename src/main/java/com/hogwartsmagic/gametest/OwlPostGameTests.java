package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.owlpost.OwlPostData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.UUID;

/**
 * Owl post end to end: a stack put in a perch's outbox is taken by the owl that lives there, lands in the
 * destination's inbox, and the owl comes back home; a parcel for a perch broken mid-flight comes back; hoppers
 * feed a perch's default destination.
 *
 * <p>Layout (test-local): stone floor at y = 1, perch A at (1, 2, 1), perch B at (7, 2, 7).
 */
@GameTestHolder(ExampleMod.MODID)
public final class OwlPostGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty9";
    private static final BlockPos A = new BlockPos(1, 2, 1);
    private static final BlockPos B = new BlockPos(7, 2, 7);

    private OwlPostGameTests() {
    }

    private record Setup(OwlPerchBlockEntity a, OwlPerchBlockEntity b, GlobalPos ga, GlobalPos gb, OwlEntity owl) {
    }

    private static Setup setUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // The owl flies off before it vanishes; keep the chunks around ticking so it never freezes at the edge.
        ChunkPos centre = new ChunkPos(helper.absolutePos(A));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setChunkForced(centre.x + dx, centre.z + dz, true);
            }
        }
        helper.setBlock(A, ExampleMod.OWL_PERCH.get());
        helper.setBlock(B, ExampleMod.OWL_PERCH.get());
        OwlPerchBlockEntity a = (OwlPerchBlockEntity) level.getBlockEntity(helper.absolutePos(A));
        OwlPerchBlockEntity b = (OwlPerchBlockEntity) level.getBlockEntity(helper.absolutePos(B));
        a.registerNow(level);
        b.registerNow(level);
        GlobalPos ga = a.globalPos();
        GlobalPos gb = b.globalPos();

        ItemStack tag = new ItemStack(ExampleMod.OWL_ROUTE_TAG.get());
        CompoundTag data = new CompoundTag();
        data.put("Perches", OwlPostData.writePosList(List.of(ga, gb)));
        tag.set(DataComponents.CUSTOM_DATA, CustomData.of(data));

        OwlEntity owl = ModEntities.OWL.get().create(level);
        BlockPos above = helper.absolutePos(A).above();
        owl.moveTo(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D, 0.0F, 0.0F);
        owl.setTame(true, false);
        owl.setOwnerUUID(UUID.randomUUID());
        level.addFreshEntity(owl);
        owl.giveRouteTag(tag);
        return new Setup(a, b, ga, gb, owl);
    }

    private static int count(OwlPerchBlockEntity perch, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 1; i <= OwlPerchBlockEntity.INBOX_SIZE; i++) {
            if (perch.getItem(i).is(item)) {
                n += perch.getItem(i).getCount();
            }
        }
        return n;
    }

    /** Passes once the owl is back home; then takes it away so it never lands in the next batch. */
    private static void owlBackThenCleanUp(GameTestHelper helper, Setup s, UUID owlId) {
        Entity back = helper.getLevel().getEntity(owlId);
        helper.assertTrue(back instanceof OwlEntity && !back.isRemoved(), "the owl has not come home");
        OwlEntity owl = (OwlEntity) back;
        helper.assertTrue(s.ga().equals(owl.getHome()) && owl.knows(s.gb()), "the returned owl forgot its home or route");
        helper.assertTrue(owl.getCarriedMail().isEmpty() && !owl.isOnErrand(), "the returned owl still holds a parcel");
        OwlPostData.get(helper.getLevel().getServer()).removeOwl(owlId);
        owl.discard();
        // The forced chunks stay: the tests sit 14 blocks apart, and un-forcing ours froze a neighbour's owl.
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void stackFliesToTheOtherPerchAndTheOwlComesHome(GameTestHelper helper) {
        Setup s = setUp(helper);
        UUID owlId = s.owl().getUUID();
        helper.assertTrue(s.ga().equals(s.owl().getHome()), "owl did not settle at the nearest perch on its tag");
        helper.assertTrue(s.a().setOutbox(new ItemStack(Items.COBBLESTONE, 40), s.gb(), UUID.randomUUID()), "outbox refused");
        helper.runAfterDelay(30, () -> helper.assertTrue(s.owl().isOnErrand() || s.owl().isRemoved(), "the owl at home did not take the stack"));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(s.b(), Items.COBBLESTONE) == 40, "B's inbox has " + count(s.b(), Items.COBBLESTONE) + " cobblestone");
            helper.assertFalse(s.a().hasOutbox(), "A still holds the stack");
            owlBackThenCleanUp(helper, s, owlId);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void parcelForABrokenPerchComesBack(GameTestHelper helper) {
        Setup s = setUp(helper);
        UUID owlId = s.owl().getUUID();
        s.a().setOutbox(new ItemStack(Items.OAK_LOG, 7), s.gb(), UUID.randomUUID());
        // Break B once the owl is out of sight with the parcel.
        helper.runAfterDelay(90, () -> helper.setBlock(B, Blocks.AIR));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(s.a(), Items.OAK_LOG) == 7,
                    "the parcel did not come back to A (A has " + count(s.a(), Items.OAK_LOG) + ")");
            owlBackThenCleanUp(helper, s, owlId);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void hopperFedStackGoesToTheDefaultDestination(GameTestHelper helper) {
        Setup s = setUp(helper);
        UUID owlId = s.owl().getUUID();
        helper.assertFalse(s.a().canPlaceItem(OwlPerchBlockEntity.OUTBOX, new ItemStack(Items.WHEAT)),
                "a perch without a default destination took hopper input");
        s.a().setDefaultDest(s.gb());
        helper.assertTrue(s.a().canPlaceItem(OwlPerchBlockEntity.OUTBOX, new ItemStack(Items.WHEAT)), "default set, still refused");
        s.a().setItem(OwlPerchBlockEntity.OUTBOX, new ItemStack(Items.WHEAT, 5));
        helper.assertFalse(s.a().canTakeItemThroughFace(OwlPerchBlockEntity.OUTBOX, s.a().getItem(0), net.minecraft.core.Direction.DOWN),
                "a hopper below could pull the outbox");
        helper.succeedWhen(() -> {
            helper.assertTrue(count(s.b(), Items.WHEAT) == 5, "B has " + count(s.b(), Items.WHEAT) + " wheat");
            owlBackThenCleanUp(helper, s, owlId);
        });
    }
}
