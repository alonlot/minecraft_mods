package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.Map;

/** Cursebreaker's Desert: that the biome exists, and that it leaves the vanilla desert in place. */
@GameTestHolder(ExampleMod.MODID)
public final class DesertGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private DesertGameTests() {
    }

    /** Biome share at the terrain surface of the real overworld generator, over two seeds. */
    static Map<ResourceKey<Biome>, Integer> census(ServerLevel level, int[] total) {
        RegistryAccess ra = level.registryAccess();
        Holder<MultiNoiseBiomeSourceParameterList> preset = ra
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
        Holder<NoiseGeneratorSettings> settings = ra.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(source, settings);
        Map<ResourceKey<Biome>, Integer> counts = new HashMap<>();
        final int n = 72;
        final int spacing = 176;
        for (long seed : new long[] {1234567L, -8_100_442_017L}) {
            RandomState random = RandomState.create(settings.value(), ra.lookupOrThrow(Registries.NOISE), seed);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int x = (i - n / 2) * spacing;
                    int z = (j - n / 2) * spacing;
                    int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                            random.sampler()).unwrapKey().ifPresent(k -> counts.merge(k, 1, Integer::sum));
                    total[0]++;
                }
            }
        }
        return counts;
    }

    /**
     * The desert must be findable, but it must not swallow the vanilla desert: a slice that took the
     * whole hot band would turn every desert in the world into this one, and the vanilla villages and
     * pyramids would never generate again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void desertIsFindableAndVanillaDesertSurvives(GameTestHelper helper) {
        int[] total = {0};
        Map<ResourceKey<Biome>, Integer> counts = census(helper.getLevel(), total);
        int all = total[0];
        int desert = counts.getOrDefault(ModBiomes.CURSEBREAKERS_DESERT, 0);
        int vanilla = counts.getOrDefault(Biomes.DESERT, 0);
        int forest = counts.getOrDefault(ModBiomes.FORBIDDEN_FOREST, 0);
        int peaks = counts.getOrDefault(ModBiomes.HORNTAIL_PEAKS, 0);
        LOGGER.info("DESERT-CENSUS {} samples: cursebreakers_desert={}% desert={}% forbidden_forest={}% horntail_peaks={}%",
                all, pct(desert, all), pct(vanilla, all), pct(forest, all), pct(peaks, all));
        helper.assertTrue(desert > 0.006 * all,
                "the desert should cover more than 0.6% of the overworld, got " + pct(desert, all) + "%");
        helper.assertTrue(desert < 2.0 * forest,
                "the desert should not outgrow the Forbidden Forest twice over: " + desert + " vs " + forest);
        helper.assertTrue(vanilla > desert / 2,
                "the vanilla desert should survive next to it: " + vanilla + " vanilla vs " + desert);
        helper.assertTrue(peaks > 0, "Horntail Peaks should still generate somewhere");
        helper.succeed();
    }

    private static final String ROOM = "hogwartsmagic:gametest/empty9";

    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper, int x, int y, int z) {
        net.minecraft.server.level.ServerPlayer p = helper.makeMockServerPlayerInLevel();
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        return p;
    }

    private static net.minecraft.world.level.block.state.BlockState glyph(com.hogwartsmagic.tomb.TombCurse curse) {
        return ExampleMod.CURSED_GLYPH.get().defaultBlockState()
                .setValue(com.hogwartsmagic.block.CursedGlyphBlock.CURSE, curse);
    }

    /**
     * Revelio's tomb reach is short on purpose: a glyph five blocks off shows, one twelve blocks off
     * does not, and the concealed wall opens (loses its collision) when it is in reach.
     */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void revelioShowsOnlyNearbyCurses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.BlockPos near = helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 1));
        net.minecraft.core.BlockPos far = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).offset(-11, 0, 0);
        net.minecraft.core.BlockPos wall = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 1));
        level.setBlock(near, glyph(com.hogwartsmagic.tomb.TombCurse.RISEN), 3);
        level.setBlock(far, glyph(com.hogwartsmagic.tomb.TombCurse.RISEN), 3);
        level.setBlock(wall, ExampleMod.CONCEALED_SANDSTONE.get().defaultBlockState(), 3);
        net.minecraft.world.phys.Vec3 cast = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 1.5));
        int shown = com.hogwartsmagic.tomb.TombCurses.reveal(level, cast);
        boolean nearShown = level.getBlockState(near).getValue(com.hogwartsmagic.block.CursedGlyphBlock.REVEALED);
        boolean farShown = level.getBlockState(far).getValue(com.hogwartsmagic.block.CursedGlyphBlock.REVEALED);
        boolean wallOpen = level.getBlockState(wall).getCollisionShape(level, wall).isEmpty();
        level.setBlock(far, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(nearShown, "a cursed glyph 5 blocks from the cast should show");
        helper.assertFalse(farShown, "a cursed glyph 12 blocks from the cast should stay hidden");
        helper.assertTrue(wallOpen, "concealed sandstone in reach should open");
        helper.assertTrue(shown == 2, "two blocks should be revealed, got " + shown);
        helper.succeed();
    }

    /** The collapse drops two layers of sand over the three by three round the player. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void collapseCurseDropsSandAndIsSpent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(x, 1, z, net.minecraft.world.level.block.Blocks.SANDSTONE);
                helper.setBlock(x, 7, z, net.minecraft.world.level.block.Blocks.SANDSTONE);
            }
        }
        net.minecraft.core.BlockPos tile = helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4));
        level.setBlock(tile, glyph(com.hogwartsmagic.tomb.TombCurse.COLLAPSE), 3);
        net.minecraft.server.level.ServerPlayer p = player(helper, 4, 2, 4);
        com.hogwartsmagic.tomb.TombCurses.trigger(level, tile, com.hogwartsmagic.tomb.TombCurse.COLLAPSE, p);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(tile).inflate(3, 8, 3);
        int falling = level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, box).size();
        boolean spent = level.getBlockState(tile).is(ExampleMod.HIEROGLYPH_SANDSTONE.get());
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, box).forEach(e -> e.discard());
        helper.assertTrue(falling == 18, "18 sand blocks should fall (2 layers over 3x3), got " + falling);
        helper.assertTrue(spent, "a curse that went off should leave plain hieroglyph sandstone");
        helper.succeed();
    }

    /** Two husks and an Inferius get up round the player, already after them. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void risenCurseRaisesThreeHunters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(x, 1, z, net.minecraft.world.level.block.Blocks.SANDSTONE);
            }
        }
        net.minecraft.server.level.ServerPlayer p = player(helper, 4, 2, 4);
        java.util.List<net.minecraft.world.entity.Mob> risen = com.hogwartsmagic.tomb.TombCurses.risen(level, p);
        long husks = risen.stream().filter(m -> m.getType() == net.minecraft.world.entity.EntityType.HUSK).count();
        long inferi = risen.stream().filter(m -> m instanceof com.hogwartsmagic.entity.InferiusEntity).count();
        boolean hunting = risen.stream().allMatch(m -> m.getTarget() == p);
        risen.forEach(m -> m.discard());
        helper.assertTrue(husks == 2 && inferi == 1, "expected 2 husks and 1 Inferius, got " + husks + " and " + inferi);
        helper.assertTrue(hunting, "every risen mob should be hunting the player");
        helper.succeed();
    }

    /** The two templates load at full size, so the structure JSON is not pointing at nothing. */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void desertTemplatesLoad(GameTestHelper helper) {
        var manager = helper.getLevel().getStructureManager();
        net.minecraft.core.Vec3i tomb = manager.getOrCreate(net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath(ExampleMod.MODID, "cursed_tomb")).getSize();
        net.minecraft.core.Vec3i dig = manager.getOrCreate(net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath(ExampleMod.MODID, "cursebreaker_dig")).getSize();
        helper.assertTrue(tomb.getY() > 35 && tomb.getX() > 30, "the tomb template should load, got " + tomb);
        helper.assertTrue(dig.getX() > 20, "the dig site template should load, got " + dig);
        helper.succeed();
    }

    /**
     * The new loot tables parse (a table that fails to parse loads as empty, silently), and the dig
     * site's chest always holds the six-page field notes: they are the only place the tomb is explained.
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void desertLootTablesLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var registries = level.getServer().reloadableRegistries();
        for (String path : new String[] {"chests/cursed_tomb", "chests/tomb_pot", "chests/cursebreaker_dig",
                "archaeology/cursed_tomb"}) {
            var key = ResourceKey.create(Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
            helper.assertTrue(registries.getLootTable(key) != net.minecraft.world.level.storage.loot.LootTable.EMPTY,
                    "loot table " + path + " did not load");
        }
        var dig = registries.getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "chests/cursebreaker_dig")));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                        helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        var items = dig.getRandomItems(params);
        var book = items.stream().filter(i -> i.is(net.minecraft.world.item.Items.WRITTEN_BOOK)).findFirst();
        helper.assertTrue(book.isPresent(), "the dig site chest should always hold the field notes, got " + items);
        var content = book.get().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(content != null && content.pages().size() == 6,
                "the field notes should have 6 pages, got " + (content == null ? "none" : content.pages().size()));
        helper.succeed();
    }

    private static com.hogwartsmagic.entity.SphinxEntity sphinx(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(x, 1, z, net.minecraft.world.level.block.Blocks.SANDSTONE);
            }
        }
        return helper.spawn(com.hogwartsmagic.entity.ModEntities.SPHINX.get(), new net.minecraft.core.BlockPos(4, 2, 6));
    }

    /**
     * The right answer shows the curses round her (far beyond Revelio's reach), gives a gift, and
     * she has nothing more to ask that player.
     */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void sphinxRewardsTheRightAnswer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.hogwartsmagic.entity.SphinxEntity sphinx = sphinx(helper);
        net.minecraft.core.BlockPos glyphPos = helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 6)).offset(0, 0, 20);
        level.setBlock(glyphPos, glyph(com.hogwartsmagic.tomb.TombCurse.WITHERING), 3);
        net.minecraft.server.level.ServerPlayer p = player(helper, 4, 2, 3);
        p.getInventory().clearContent();
        sphinx.ask(p);
        int right = sphinx.rightAnswerShownAt(p);
        helper.assertTrue(right >= 0, "a riddle should be pending after she asks");
        boolean taken = sphinx.answer(p, right);
        boolean shown = level.getBlockState(glyphPos).getValue(com.hogwartsmagic.block.CursedGlyphBlock.REVEALED);
        boolean gifted = !p.getInventory().isEmpty();
        boolean again = sphinx.rightAnswerShownAt(p) >= 0;
        level.setBlock(glyphPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        sphinx.discard();
        helper.assertTrue(taken, "the answer should be taken");
        helper.assertTrue(shown, "a cursed glyph 20 blocks off should show after the right answer");
        helper.assertTrue(gifted, "the right answer should give a gift");
        helper.assertFalse(again, "nothing should be pending after an answer");
        helper.assertFalse(sphinx.isAngry(), "she should not be angry after a right answer");
        helper.succeed();
    }

    /** A wrong answer turns her on the player who gave it; an answer from too far away is not heard. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void sphinxAttacksAWrongAnswer(GameTestHelper helper) {
        com.hogwartsmagic.entity.SphinxEntity sphinx = sphinx(helper);
        net.minecraft.server.level.ServerPlayer p = player(helper, 4, 2, 3);
        sphinx.ask(p);
        int wrong = (sphinx.rightAnswerShownAt(p) + 1) % 3;
        p.teleportTo(p.getX(), p.getY(), p.getZ() - 20);
        boolean heardFar = sphinx.answer(p, wrong);
        p.teleportTo(p.getX(), p.getY(), p.getZ() + 20);
        boolean heard = sphinx.answer(p, wrong);
        boolean angry = sphinx.isAngry() && sphinx.getTarget() == p;
        sphinx.discard();
        helper.assertFalse(heardFar, "an answer from 20 blocks away should not be heard");
        helper.assertTrue(heard, "the wrong answer should be taken");
        helper.assertTrue(angry, "she should turn on the player who answered wrong");
        helper.succeed();
    }

    private static String pct(int count, int all) {
        return String.format("%.2f", 100.0 * count / all);
    }
}
