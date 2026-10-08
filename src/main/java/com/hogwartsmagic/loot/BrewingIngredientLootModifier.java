package com.hogwartsmagic.loot;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Brewing ingredients in mod structure chests. The Death Eater camp fills its chests from the vanilla pillager
 * outpost table and the Hogwarts ruin camp from the vanilla simple dungeon table, so those two only count when the
 * chest stands inside a piece of the camp. That check reads the structure data of the chest's chunk and of the
 * structure start's chunk; the start lies within a few chunks of its pieces, so around a player opening the chest it is
 * normally already loaded. The Chamber has its own table.
 */
public class BrewingIngredientLootModifier extends LootModifier {
    private static final ResourceLocation SIMPLE_DUNGEON = ResourceLocation.parse("minecraft:chests/simple_dungeon");
    private static final ResourceLocation PILLAGER_OUTPOST = ResourceLocation.parse("minecraft:chests/pillager_outpost");
    private static final ResourceLocation CHAMBER = ResourceLocation.parse("hogwartsmagic:chests/chamber_of_secrets");
    private static final ResourceKey<Structure> HOGWARTS_RUIN_CAMP = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hogwarts_ruin_camp"));
    private static final ResourceKey<Structure> DEATH_EATER_CAMP = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "death_eater_camp"));

    public static final MapCodec<BrewingIngredientLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, BrewingIngredientLootModifier::new));

    protected BrewingIngredientLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null) {
            return generatedLoot;
        }
        RandomSource random = context.getRandom();
        if (CHAMBER.equals(tableId)) {
            if (PotionRules.rolls(random.nextDouble(), PotionRules.ASHWINDER_CHAMBER_CHANCE)) {
                generatedLoot.add(new ItemStack(ExampleMod.ASHWINDER_EGG.get()));
            }
            return generatedLoot;
        }
        boolean dungeon = SIMPLE_DUNGEON.equals(tableId);
        boolean outpost = PILLAGER_OUTPOST.equals(tableId);
        if (!dungeon && !outpost) {
            return generatedLoot;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return generatedLoot;
        }
        ServerLevel level = context.getLevel();
        BlockPos pos = BlockPos.containing(origin);
        if (dungeon && inStructure(level, pos, HOGWARTS_RUIN_CAMP)
                && PotionRules.rolls(random.nextDouble(), PotionRules.VALERIAN_RUIN_CAMP_CHANCE)) {
            generatedLoot.add(new ItemStack(ExampleMod.VALERIAN_SPRIGS.get(), 1 + random.nextInt(2)));
        }
        if (outpost && inStructure(level, pos, DEATH_EATER_CAMP)
                && PotionRules.rolls(random.nextDouble(), PotionRules.ASHWINDER_DEATH_EATER_CAMP_CHANCE)) {
            generatedLoot.add(new ItemStack(ExampleMod.ASHWINDER_EGG.get()));
        }
        return generatedLoot;
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> key) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(key)).isValid();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
