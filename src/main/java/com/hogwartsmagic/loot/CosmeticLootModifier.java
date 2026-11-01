package com.hogwartsmagic.loot;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Hogwarts clothing left about the world: a house scarf or a pointed hat in a village house, now
 * and then a set of school robes in a stronghold library or a mansion, and very rarely a
 * Triwizard champion's robes. Every piece can also be crafted or bought from a Robemaker.
 */
public class CosmeticLootModifier extends LootModifier {
    private static final double HOUSE_CHANCE = 0.10;
    private static final double LIBRARY_CHANCE = 0.12;
    private static final double CHAMPION_CHANCE = 0.03;

    private static final Set<ResourceLocation> VILLAGE_HOUSES = Set.of(
            ResourceLocation.parse("minecraft:chests/village/village_plains_house"),
            ResourceLocation.parse("minecraft:chests/village/village_taiga_house"),
            ResourceLocation.parse("minecraft:chests/village/village_savanna_house"),
            ResourceLocation.parse("minecraft:chests/village/village_snowy_house"),
            ResourceLocation.parse("minecraft:chests/village/village_desert_house")
    );
    private static final Set<ResourceLocation> LIBRARIES = Set.of(
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion")
    );

    private static final List<RegistryObject<Item>> HOUSE_FINDS = List.of(
            ExampleMod.GRYFFINDOR_SCARF, ExampleMod.SLYTHERIN_SCARF, ExampleMod.RAVENCLAW_SCARF,
            ExampleMod.HUFFLEPUFF_SCARF, ExampleMod.POINTED_HAT);
    private static final List<RegistryObject<Item>> ROBES = List.of(
            ExampleMod.GRYFFINDOR_ROBES, ExampleMod.SLYTHERIN_ROBES, ExampleMod.RAVENCLAW_ROBES,
            ExampleMod.HUFFLEPUFF_ROBES);

    public static final MapCodec<CosmeticLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, CosmeticLootModifier::new));

    protected CosmeticLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null) {
            return generatedLoot;
        }
        RandomSource random = context.getRandom();
        if (VILLAGE_HOUSES.contains(tableId)) {
            if (random.nextDouble() < HOUSE_CHANCE) {
                generatedLoot.add(pick(HOUSE_FINDS, random));
            }
        } else if (LIBRARIES.contains(tableId)) {
            if (random.nextDouble() < LIBRARY_CHANCE) {
                generatedLoot.add(pick(ROBES, random));
            }
            if (random.nextDouble() < CHAMPION_CHANCE) {
                generatedLoot.add(new ItemStack(ExampleMod.TRIWIZARD_ROBES.get()));
            }
        }
        return generatedLoot;
    }

    private static ItemStack pick(List<RegistryObject<Item>> items, RandomSource random) {
        return new ItemStack(items.get(random.nextInt(items.size())).get());
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
