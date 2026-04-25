package com.hogwartsmagic.loot;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MagicBookLootModifier extends LootModifier {
    private static final double FOUND_ONLY_RELIC_CHEST_CHANCE = 0.10;
    private static final List<ResourceLocation> FOUND_ONLY_RELIC_CHESTS = List.of(
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("hogwartsmagic:chests/forbidden_ruin"),
            ResourceLocation.parse("hogwartsmagic:chests/death_eaters_hideout"),
            ResourceLocation.parse("hogwartsmagic:chests/dueling_hall")
    );

    public static final MapCodec<MagicBookLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, MagicBookLootModifier::new));

    protected MagicBookLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null || !tableId.getPath().startsWith("chests/")) {
            return generatedLoot;
        }
        boolean guaranteed = MagicBookChestLoot.isGuaranteedSpellBookChest(tableId);
        if (!guaranteed && context.getRandom().nextDouble() >= Config.spellBookChestChance) {
            return generatedLoot;
        }
        SpellType[] pool = MagicBookChestLoot.spellPoolFor(tableId);
        SpellType spell = MagicBookChestLoot.pickWeightedSpell(pool, context.getRandom());
        generatedLoot.add(MagicBookItem.createBook(spell));
        tryAddFoundOnlyRelic(generatedLoot, context, tableId);
        return generatedLoot;
    }

    private static void tryAddFoundOnlyRelic(ObjectArrayList<ItemStack> generatedLoot, LootContext context, ResourceLocation tableId) {
        if (!FOUND_ONLY_RELIC_CHESTS.contains(tableId)) {
            return;
        }
        if (context.getRandom().nextDouble() >= FOUND_ONLY_RELIC_CHEST_CHANCE) {
            return;
        }
        int pick = context.getRandom().nextInt(3);
        ItemStack relic = switch (pick) {
            case 0 -> new ItemStack(ExampleMod.RAVENCLAW_DIADEM_FRAGMENT.get());
            case 1 -> new ItemStack(ExampleMod.ENCHANTED_INKWELL.get());
            default -> new ItemStack(ExampleMod.MOONLIT_PHIAL.get());
        };
        generatedLoot.add(relic);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
