package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HiddenHouseBlockEntity;
import com.hogwartsmagic.hiddenhouse.HiddenHouseJinx;
import com.hogwartsmagic.hiddenhouse.HiddenHouseRules;
import com.hogwartsmagic.hiddenhouse.HiddenHouses;
import com.hogwartsmagic.worldgen.structure.GlamourRotationProcessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Hidden wizard houses: that a glamour survives everything the world throws at it, that Revelio
 * turns a whole house real (and only once), and that the jinx fires a single time.
 */
@GameTestHolder(ExampleMod.MODID)
public final class HiddenHouseGameTests {
    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";
    private static final String HOUSE_LOOT = "hogwartsmagic:chests/hidden_house";

    private HiddenHouseGameTests() {
    }

    /** A glamoured block at {@code rel} holding {@code real} (and its block-entity data). */
    static void glamour(GameTestHelper helper, BlockPos rel, BlockState real, CompoundTag nbt) {
        helper.setBlock(rel, ExampleMod.GLAMOURED_BLOCK.get().defaultBlockState());
        ((HiddenHouseBlockEntity) helper.getBlockEntity(rel)).setReal(real, nbt);
    }

    /** A chest block-entity data pointing at the house loot table. */
    static CompoundTag chestNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:chest");
        nbt.putString("LootTable", HOUSE_LOOT);
        return nbt;
    }

    /** A ruin stone as the generator writes it. */
    static void phantom(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ExampleMod.PHANTOM_RUIN.get().defaultBlockState());
        ((HiddenHouseBlockEntity) helper.getBlockEntity(rel)).setReal(null, null);
    }

    /** A jinx ward at {@code rel}. */
    static void ward(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ExampleMod.JINX_WARD.get().defaultBlockState());
        ((HiddenHouseBlockEntity) helper.getBlockEntity(rel)).setReal(null, null);
    }

    /** Casts the house reveal from a point in the test own frame. */
    static int cast(GameTestHelper helper, double x, double y, double z) {
        return HiddenHouses.reveal(helper.getLevel(), helper.absoluteVec(new Vec3(x, y, z)), null);
    }

    private static BlockState bed(BedPart part) {
        return Blocks.RED_BED.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.BED_PART, part);
    }

    /** A creeper-sized blast, TNT next to it: nothing breaks, nothing drops, the loot table survives. */
    @GameTest(template = EMPTY9, timeoutTicks = 40)
    public static void glamourSurvivesExplosionsAndDropsNothing(GameTestHelper helper) {
        BlockPos rel = new BlockPos(4, 1, 4);
        glamour(helper, rel, Blocks.CHEST.defaultBlockState(), chestNbt());
        ServerLevel level = helper.getLevel();
        Vec3 centre = helper.absoluteVec(new Vec3(4.5, 1.5, 4.5));
        level.explode(null, centre.x, centre.y, centre.z, 4.0F, Level.ExplosionInteraction.TNT);
        helper.runAfterDelay(2, () -> {
            BlockPos abs = helper.absolutePos(rel);
            helper.assertBlockPresent(ExampleMod.GLAMOURED_BLOCK.get(), rel);
            HiddenHouseBlockEntity be = (HiddenHouseBlockEntity) helper.getBlockEntity(rel);
            helper.assertTrue(be != null && be.real() != null && be.real().is(Blocks.CHEST),
                    "the glamour should still hold the chest");
            helper.assertTrue(be.realNbt() != null && HOUSE_LOOT.equals(be.realNbt().getString("LootTable")),
                    "the chest loot table should survive the blast");
            BlockState state = level.getBlockState(abs);
            helper.assertTrue(state.getCollisionShape(level, abs).isEmpty(), "a glamour has no collision");
            helper.assertTrue(state.getLightBlock(level, abs) == 0, "a glamour blocks no light");
            helper.assertTrue(state.getPistonPushReaction() == PushReaction.BLOCK, "a piston must not move it");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(8)).isEmpty(),
                    "nothing should drop");
            helper.succeed();
        });
    }

    /** The house a cast reveals: whole, exact, and permanent; a block out of reach stays hidden. */
    @GameTest(template = EMPTY9, timeoutTicks = 200)
    public static void revealTurnsGlamourRealAndPhantomToAirForGood(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 1);
        BlockPos chest = new BlockPos(3, 1, 1);
        BlockPos foot = new BlockPos(1, 1, 3);
        BlockPos head = new BlockPos(1, 1, 4);
        BlockPos ph1 = new BlockPos(2, 1, 3);
        BlockPos ph2 = new BlockPos(3, 1, 3);
        BlockPos far = new BlockPos(8, 3, 8);   // 10.7 blocks from the cast, 5 from the nearest house block
        glamour(helper, stone, Blocks.STONE.defaultBlockState(), null);
        glamour(helper, chest, Blocks.CHEST.defaultBlockState(), chestNbt());
        glamour(helper, foot, bed(BedPart.FOOT), null);
        glamour(helper, head, bed(BedPart.HEAD), null);
        phantom(helper, ph1);
        phantom(helper, ph2);
        glamour(helper, far, Blocks.STONE.defaultBlockState(), null);
        BlockPos[] house = {stone, chest, foot, head, ph1, ph2};

        helper.assertTrue(cast(helper, 0.5, 1.5, 0.5) == house.length, "the cast should reveal exactly the house");
        helper.runAfterDelay(HiddenHouseRules.MAX_DELAY + 5, () -> {
            checkRevealed(helper, house, far);
            helper.runAfterDelay(100, () -> {
                checkRevealed(helper, house, far);
                helper.succeed();
            });
        });
    }

    private static void checkRevealed(GameTestHelper helper, BlockPos[] house, BlockPos far) {
        BlockPos stone = house[0];
        BlockPos chest = house[1];
        helper.assertBlockPresent(Blocks.STONE, stone);
        helper.assertBlockPresent(Blocks.CHEST, chest);
        helper.assertBlockPresent(Blocks.AIR, house[4]);
        helper.assertBlockPresent(Blocks.AIR, house[5]);
        BlockState f = helper.getBlockState(house[2]);
        BlockState h = helper.getBlockState(house[3]);
        helper.assertTrue(f.is(Blocks.RED_BED) && f.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT,
                "the bed foot should be real");
        helper.assertTrue(h.is(Blocks.RED_BED) && h.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD,
                "the bed head should be real and sit at foot.relative(facing)");
        BlockEntity be = helper.getBlockEntity(chest);
        helper.assertTrue(be instanceof ChestBlockEntity c && c.getLootTable() != null
                        && c.getLootTable().location().toString().equals(HOUSE_LOOT),
                "the chest should carry the house loot table");
        for (BlockPos p : house) {
            helper.assertFalse(helper.getBlockEntity(p) instanceof HiddenHouseBlockEntity, "still hidden at " + p);
            BlockPos abs = helper.absolutePos(p);
            helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(abs, helper.getBlockState(p).getBlock()),
                    "a scheduled tick would close it again at " + p);
        }
        helper.assertBlockPresent(ExampleMod.GLAMOURED_BLOCK.get(), far);
    }

    /** Nobody is entombed: a block that would appear around a living thing waits until it leaves. */
    @GameTest(template = EMPTY9, timeoutTicks = 100)
    public static void revealWaitsForSomeoneStandingInAWall(GameTestHelper helper) {
        BlockPos wall = new BlockPos(4, 1, 4);
        glamour(helper, wall, Blocks.STONE.defaultBlockState(), null);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 4));
        cast(helper, 4.5, 3.0, 4.5);
        helper.runAfterDelay(15, () -> {
            helper.assertBlockPresent(ExampleMod.GLAMOURED_BLOCK.get(), wall);
            stand.setPos(helper.absoluteVec(new Vec3(0.5, 1.0, 0.5)));
            helper.runAfterDelay(HiddenHouseRules.OCCUPIED_RETRY_TICKS + 2, () -> {
                stand.discard();
                helper.assertBlockPresent(Blocks.STONE, wall);
                helper.succeed();
            });
        });
    }

    /** Two casts at once, and one after: one chest, one loot table, and the late cast finds nothing. */
    @GameTest(template = EMPTY9, timeoutTicks = 120)
    public static void doubleCastRevealsOnceWithoutDuplicates(GameTestHelper helper) {
        BlockPos chest = new BlockPos(4, 1, 4);
        glamour(helper, chest, Blocks.CHEST.defaultBlockState(), chestNbt());
        cast(helper, 4.5, 2.0, 4.5);
        cast(helper, 4.5, 2.0, 4.5);
        helper.runAfterDelay(HiddenHouseRules.MAX_DELAY + 5, () -> {
            helper.assertBlockPresent(Blocks.CHEST, chest);
            helper.assertTrue(helper.getBlockEntity(chest) instanceof ChestBlockEntity c && c.getLootTable() != null,
                    "one chest with its loot table");
            helper.assertTrue(cast(helper, 4.5, 2.0, 4.5) == 0, "a cast after the sweep finds nothing");
            helper.succeed();
        });
    }

    /** A chest with a ward over it hexes whoever opens it, once, and then opens like any chest. */
    @GameTest(template = EMPTY9, timeoutTicks = 60)
    public static void jinxedChestHexesOnceThenOpens(GameTestHelper helper) {
        BlockPos chest = new BlockPos(4, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        ward(helper, chest.above());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(new Vec3(4.5, 1.0, 4.5)));
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(chest);
        helper.assertTrue(HiddenHouseJinx.tryHex(level, abs, player), "the first opening should fire the hex");
        helper.assertTrue(player.getEffect(MobEffects.BLINDNESS) != null
                        && player.getEffect(MobEffects.BLINDNESS).getDuration() >= HiddenHouseRules.JINX_BLIND_TICKS - 1,
                "blinded");
        helper.assertTrue(player.getDeltaMovement().z > 0.0, "thrown away from the chest (south)");
        helper.assertBlockPresent(Blocks.AIR, chest.above());
        helper.assertFalse(HiddenHouseJinx.tryHex(level, abs, player), "the hex fires once");
        helper.succeed();
    }

    /** Revelio disarms the jinx: unmasking the house lifts the ward. */
    @GameTest(template = EMPTY9, timeoutTicks = 100)
    public static void revealDisarmsTheJinx(GameTestHelper helper) {
        BlockPos chest = new BlockPos(4, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        ward(helper, chest.above());
        glamour(helper, new BlockPos(5, 1, 2), Blocks.STONE.defaultBlockState(), null);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(new Vec3(4.5, 1.0, 5.5)));
        cast(helper, 4.5, 2.0, 5.5);
        helper.runAfterDelay(HiddenHouseRules.MAX_DELAY + 5, () -> {
            helper.assertBlockPresent(Blocks.AIR, chest.above());
            helper.assertFalse(HiddenHouseJinx.tryHex(helper.getLevel(), helper.absolutePos(chest), player),
                    "no hex after the reveal");
            helper.succeed();
        });
    }

    /**
     * The real template, placed by the framework: before the cast it is a ruin of invisible blocks and
     * stones that are not there; after it, every glamour holds its real state, the stones are gone, the ward
     * is lifted, the loot chest keeps its table, the cursed tiles stay, and none of it changes afterwards.
     */
    @GameTest(template = "hogwartsmagic:hidden_house", timeoutTicks = HiddenHouseRules.MAX_DELAY + 260)
    public static void hiddenHouseTemplateRevealsCompletely(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB box = helper.getBounds();
        java.util.Map<BlockPos, BlockState> expected = new java.util.HashMap<>();
        int glamoured = 0;
        int phantoms = 0;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
            if (level.getBlockEntity(p) instanceof HiddenHouseBlockEntity be) {
                BlockState real = be.real() != null ? be.real() : Blocks.AIR.defaultBlockState();
                expected.put(p.immutable(), real);
                if (level.getBlockState(p).is(ExampleMod.GLAMOURED_BLOCK.get())) {
                    glamoured++;
                } else if (level.getBlockState(p).is(ExampleMod.PHANTOM_RUIN.get())) {
                    phantoms++;
                }
            }
        }
        helper.assertTrue(glamoured >= 150, "the cottage should hide at least 150 blocks, got " + glamoured);
        helper.assertTrue(phantoms >= 20, "the ruin should hold at least 20 phantom stones, got " + phantoms);
        Vec3 cast = helper.absoluteVec(new Vec3(8.5, 3.0, 13.5));
        HiddenHouses.reveal(level, cast, null);
        helper.runAfterDelay(HiddenHouseRules.MAX_DELAY + 10, () -> {
            checkTemplateRevealed(helper, expected);
            helper.runAfterDelay(200, () -> {
                checkTemplateRevealed(helper, expected);
                helper.succeed();
            });
        });
    }

    private static void checkTemplateRevealed(GameTestHelper helper, java.util.Map<BlockPos, BlockState> expected) {
        ServerLevel level = helper.getLevel();
        for (java.util.Map.Entry<BlockPos, BlockState> e : expected.entrySet()) {
            helper.assertTrue(level.getBlockState(e.getKey()) == e.getValue(),
                    "wrong block at " + e.getKey() + ": " + level.getBlockState(e.getKey()) + " not " + e.getValue());
            helper.assertFalse(level.getBlockEntity(e.getKey()) instanceof HiddenHouseBlockEntity,
                    "still hidden at " + e.getKey());
            if (e.getValue().is(Blocks.CHEST)) {
                helper.assertTrue(level.getBlockEntity(e.getKey()) instanceof ChestBlockEntity c
                        && c.getLootTable() != null && c.getLootTable().location().toString().equals(HOUSE_LOOT),
                        "the loot chest should keep its table");
            }
            helper.assertFalse(level.getBlockState(e.getKey()).is(ExampleMod.JINX_WARD.get()), "the ward is lifted");
        }
        AABB box = helper.getBounds();
        int glyphs = 0;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
            if (level.getBlockState(p).is(ExampleMod.CURSED_GLYPH.get())) {
                glyphs++;
            }
        }
        helper.assertTrue(glyphs >= 2, "the cursed tiles should stay, got " + glyphs);
    }

    /**
     * A house turned by the jigsaw keeps its beds and doors whole: the real states are rotated with the
     * template, so a bed hidden facing south does not appear facing south in a house turned east.
     */
    @GameTest(template = "hogwartsmagic:gametest/empty3", timeoutTicks = HiddenHouseRules.MAX_DELAY + 120)
    public static void rotatedHouseKeepsBedsAndDoorsWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().getOrCreate(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hidden_house"));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90)
                .addProcessor(GlamourRotationProcessor.INSTANCE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).atY(160).offset(40, 0, 40);
        BoundingBox bounds = template.getBoundingBox(settings, origin);
        // Only the chunks this test forces itself are released at the end: forcing is a flag, not a count.
        java.util.List<ChunkPos> ours = new java.util.ArrayList<>();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
                    level.setChunkForced(cx, cz, true);
                    ours.add(new ChunkPos(cx, cz));
                }
            }
        }
        helper.runAfterDelay(5, () -> {
            template.placeInWorld(level, origin, origin, settings, level.random, 2);
            double sx = 0;
            double sy = 0;
            double sz = 0;
            int n = 0;
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                    new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) {
                if (level.getBlockEntity(p) instanceof HiddenHouseBlockEntity) {
                    sx += p.getX() + 0.5;
                    sy += p.getY() + 0.5;
                    sz += p.getZ() + 0.5;
                    n++;
                }
            }
            helper.assertTrue(n > 0, "the placed house should hold hidden blocks");
            HiddenHouses.reveal(level, new Vec3(sx / n, sy / n, sz / n), null);
            helper.runAfterDelay(HiddenHouseRules.MAX_DELAY + 10, () -> {
                int beds = 0;
                int doors = 0;
                String broken = null;
                for (BlockPos p : BlockPos.betweenClosed(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                        new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))) {
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.RED_BED) && s.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
                        beds++;
                        BlockState head = level.getBlockState(p.relative(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
                        if (!head.is(Blocks.RED_BED) || head.getValue(BlockStateProperties.BED_PART) != BedPart.HEAD) {
                            broken = "bed foot at " + p + " has no head";
                        }
                    }
                    if (s.is(Blocks.SPRUCE_DOOR) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                        doors++;
                        BlockState up = level.getBlockState(p.above());
                        if (!up.is(Blocks.SPRUCE_DOOR) || up.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
                            broken = "door at " + p + " has no upper half";
                        }
                    }
                }
                // Clean up before any assert can throw.
                BlockPos.betweenClosed(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                        new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()))
                        .forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
                for (ChunkPos c : ours) {
                    level.setChunkForced(c.x, c.z, false);
                }
                helper.assertTrue(beds >= 1 && doors >= 1, "the turned house should have a bed and a door");
                helper.assertTrue(broken == null, String.valueOf(broken));
                helper.succeed();
            });
        });
    }

    /** The structure, its set, the processor list, the biome tag and the loot tables all load. */
    @GameTest(template = "hogwartsmagic:gametest/empty3", timeoutTicks = 20)
    public static void hiddenHouseDataLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hidden_house");
        var access = level.registryAccess();
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE).containsKey(id), "structure registered");
        helper.assertTrue(access.registryOrThrow(Registries.STRUCTURE_SET).containsKey(id), "structure set registered");
        helper.assertTrue(access.registryOrThrow(Registries.PROCESSOR_LIST).containsKey(id), "processor list registered");
        var tag = access.registryOrThrow(Registries.BIOME).getTag(TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "has_structure/hidden_house")));
        helper.assertTrue(tag.isPresent() && tag.get().size() == 6, "the biome tag should hold 6 biomes");
        var tables = level.getServer().reloadableRegistries();
        LootTable house = tables.getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "chests/hidden_house")));
        helper.assertTrue(house != LootTable.EMPTY, "the house chest table should load");
        LootTable portkey = tables.getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "chests/hidden_house_portkey")));
        boolean haveItem = BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "old_portkey"));
        helper.assertTrue((portkey != LootTable.EMPTY) == haveItem,
                "the portkey table should load exactly when old_portkey exists");
        var template = level.getStructureManager().get(id);
        helper.assertTrue(template.isPresent() && template.get().getSize().getX() >= 15, "the template loads at full size");
        helper.succeed();
    }
}
