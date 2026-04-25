package com.hogwartsmagic.loot;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class InvisibilityCloakLootModifier extends LootModifier {
    private static final Set<ResourceLocation> ELIGIBLE_CHESTS = Set.of(
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure")
    );

    public static final MapCodec<InvisibilityCloakLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, InvisibilityCloakLootModifier::new));

    protected InvisibilityCloakLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Config.enableInvisibilityCloakChestLoot) {
            return generatedLoot;
        }
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null || !ELIGIBLE_CHESTS.contains(tableId)) {
            return generatedLoot;
        }
        if (context.getRandom().nextDouble() >= Config.invisibilityCloakChestChance) {
            return generatedLoot;
        }
        generatedLoot.add(new ItemStack(ExampleMod.INVISIBILITY_CLOAK.get()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
