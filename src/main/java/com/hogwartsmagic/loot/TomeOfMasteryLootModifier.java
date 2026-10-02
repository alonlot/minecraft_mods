package com.hogwartsmagic.loot;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.TomeOfMasteryItem;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.mastery.TomeRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Tomes of Mastery in chests: one roll per chest, at most one tome. Common and Uncommon can turn
 * up in any chest that can roll a spell book; Rare only in dungeon-tier chests and mod
 * structures; Epic only in endgame loot. All four chances are config values.
 */
public class TomeOfMasteryLootModifier extends LootModifier {
    /** Structures whose every chest is endgame (the Horntail nest uses a vanilla table, so the table can't tell). */
    private static final TagKey<Structure> ENDGAME_STRUCTURES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tome_endgame"));
    /** Mod structures, for chests inside them that use a vanilla table. */
    private static final TagKey<Structure> RARE_STRUCTURES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tome_rare"));

    private static final Set<ResourceLocation> ENDGAME_TABLES = Set.of(
            ResourceLocation.parse("hogwartsmagic:chests/gringotts_vault"),
            ResourceLocation.parse("hogwartsmagic:chests/chamber_of_secrets")
    );

    private static final Set<ResourceLocation> DUNGEON_TIER_TABLES = Set.of(
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),
            ResourceLocation.parse("minecraft:chests/desert_pyramid"),
            ResourceLocation.parse("minecraft:chests/jungle_temple"),
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("minecraft:chests/stronghold_corridor"),
            ResourceLocation.parse("minecraft:chests/stronghold_crossing"),
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/ancient_city_ice_box"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:chests/bastion_treasure"),
            ResourceLocation.parse("minecraft:chests/bastion_other"),
            ResourceLocation.parse("minecraft:chests/bastion_bridge"),
            ResourceLocation.parse("minecraft:chests/bastion_hoglin_stable"),
            ResourceLocation.parse("minecraft:chests/nether_bridge"),
            ResourceLocation.parse("minecraft:chests/buried_treasure"),
            ResourceLocation.parse("minecraft:chests/shipwreck_treasure")
    );

    public static final MapCodec<TomeOfMasteryLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, TomeOfMasteryLootModifier::new));

    protected TomeOfMasteryLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        // Same gate as spell books: anything under chests/ can roll a book, so it can roll a tome.
        if (tableId == null || !tableId.getPath().startsWith("chests/")) {
            return generatedLoot;
        }
        boolean endgame = ENDGAME_TABLES.contains(tableId) || insideStructure(context, ENDGAME_STRUCTURES);
        boolean rare = endgame
                || DUNGEON_TIER_TABLES.contains(tableId)
                || ExampleMod.MODID.equals(tableId.getNamespace())
                || insideStructure(context, RARE_STRUCTURES);
        SpellLootRarity tier = TomeRules.pickTome(context.getRandom().nextDouble(),
                Config.getTomeCommonChance(), Config.getTomeUncommonChance(),
                Config.getTomeRareChance(), Config.getTomeEpicChance(),
                rare, endgame);
        if (tier != null) {
            generatedLoot.add(TomeOfMasteryItem.stackFor(tier));
        }
        return generatedLoot;
    }

    /** Loot is rolled when the chest is first opened; ORIGIN is the chest's position. */
    private static boolean insideStructure(LootContext context, TagKey<Structure> structures) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return false;
        }
        return context.getLevel().structureManager()
                .getStructureWithPieceAt(BlockPos.containing(origin), structures).isValid();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
