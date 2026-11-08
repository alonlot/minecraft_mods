package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GiantEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.TrollEntity;
import com.hogwartsmagic.worldgen.ModBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
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
import java.util.stream.Collectors;

/** The Giants' Highland: that the biome exists in the real overworld, and the giants' manners. */
@GameTestHolder(ExampleMod.MODID)
public final class HighlandGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private HighlandGameTests() {
    }

    /**
     * A biome census of the real overworld generator, not the test world (which is flat). Samples a
     * grid at the terrain surface over two seeds and logs every biome's share, so a change to any
     * climate slice can be measured instead of guessed at.
     *
     * <p>The highland must be findable: an earlier Horntail Peaks slice passed every review and
     * then generated almost nowhere. The bounds are relative to the Forbidden Forest, which is
     * confirmed findable in game.
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void giantsHighlandIsFindable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess ra = level.registryAccess();
        Holder<MultiNoiseBiomeSourceParameterList> preset = ra
                .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
        Holder<NoiseGeneratorSettings> settings = ra.registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(source, settings);

        Map<ResourceKey<Biome>, Integer> counts = new HashMap<>();
        int total = 0;
        final int n = 72;
        final int spacing = 176;
        for (long seed : new long[] {1234567L, -8_100_442_017L}) {
            RandomState random = RandomState.create(settings.value(),
                    ra.lookupOrThrow(Registries.NOISE), seed);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int x = (i - n / 2) * spacing;
                    int z = (j - n / 2) * spacing;
                    int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z), random.sampler());
                    biome.unwrapKey().ifPresent(k -> counts.merge(k, 1, Integer::sum));
                    total++;
                }
            }
        }
        final int all = total;
        LOGGER.info("BIOME-CENSUS {} samples: {}", all, counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> String.format("%s=%.2f%%", e.getKey().location().getPath(), 100.0 * e.getValue() / all))
                .collect(Collectors.joining(" ")));

        int highland = counts.getOrDefault(ModBiomes.GIANTS_HIGHLAND, 0);
        int forest = counts.getOrDefault(ModBiomes.FORBIDDEN_FOREST, 0);
        int peaks = counts.getOrDefault(ModBiomes.HORNTAIL_PEAKS, 0);
        helper.assertTrue(highland > 0.004 * all,
                "the highland should cover more than 0.4% of the overworld, got " + 100.0 * highland / all + "%");
        helper.assertTrue(highland < 2.0 * forest,
                "the highland should not outgrow the Forbidden Forest twice over: " + highland + " vs " + forest);
        helper.assertTrue(peaks > 0, "Horntail Peaks should still generate somewhere");
        helper.succeed();
    }

    /** Giants bully every mob in reach but their own kind and the trolls that hang about the camp. */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void giantsLeaveTrollsAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrollEntity troll = new TrollEntity(ModEntities.TROLL.get(), level);
        GiantEntity giant = new GiantEntity(ModEntities.GIANT.get(), level);
        net.minecraft.world.entity.animal.Cow cow = new net.minecraft.world.entity.animal.Cow(EntityType.COW, level);
        helper.assertFalse(GiantEntity.isFairGame(troll), "a giant should not pick on a troll");
        helper.assertFalse(GiantEntity.isFairGame(giant), "a giant should not pick on another giant");
        helper.assertTrue(GiantEntity.isFairGame(cow), "a giant should still bully a cow");
        helper.succeed();
    }

    /**
     * The real encampment template, placed high over the test grid: three giants and two trolls
     * come out of it, and after five seconds of ticking side by side the trolls are still alive.
     * The trolls were the giants' first meal before giants learned to leave them alone.
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void encampmentGiantsSpareTheirTrolls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level
                .getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation
                        .fromNamespaceAndPath(ExampleMod.MODID, "giant_encampment"));
        net.minecraft.core.Vec3i size = template.getSize();
        helper.assertTrue(size.getX() > 40 && size.getZ() > 40,
                "the encampment template should load at full size, got " + size);
        net.minecraft.core.BlockPos origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO).atY(160);
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + size.getX()) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + size.getZ()) >> 4; cz++) {
                // Forced, not just loaded: a loaded chunk with no player near it does not tick its
                // entities, and giants that never tick never pick a target, so the test would pass
                // with any targeting at all.
                level.setChunkForced(cx, cz, true);
            }
        }
        template.placeInWorld(level, origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.random, 2);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(origin)
                .expandTowards(size.getX(), size.getY(), size.getZ()).inflate(4);
        java.util.List<GiantEntity> giants = level.getEntitiesOfClass(GiantEntity.class, box);
        java.util.List<TrollEntity> trolls = level.getEntitiesOfClass(TrollEntity.class, box);
        helper.assertTrue(giants.size() == 3, "the encampment should hold 3 giants, got " + giants.size());
        helper.assertTrue(trolls.size() == 2, "the encampment should hold 2 trolls, got " + trolls.size());
        helper.runAfterDelay(100, () -> {
            boolean ticked = giants.stream().allMatch(g -> g.tickCount >= 80);
            boolean hunting = giants.stream().anyMatch(g -> g.getTarget() instanceof TrollEntity);
            int alive = (int) trolls.stream().filter(t -> t.isAlive() && t.getHealth() >= t.getMaxHealth()).count();
            // Clean up before any assert can throw: left behind, the giants would wander into the
            // other tests, and the forced chunks would stay loaded for the rest of the run.
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                    e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(e -> e.discard());
            net.minecraft.core.BlockPos.betweenClosed(origin, origin.offset(size).offset(-1, -1, -1))
                    .forEach(p -> level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16));
            for (int cx = origin.getX() >> 4; cx <= (origin.getX() + size.getX()) >> 4; cx++) {
                for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + size.getZ()) >> 4; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
            helper.assertTrue(ticked, "the giants did not tick, so this test proves nothing");
            helper.assertFalse(hunting, "a giant is hunting a troll");
            helper.assertTrue(alive == 2, "both trolls should be alive and unhurt after 5 s with the giants, got " + alive);
            helper.succeed();
        });
    }
}
