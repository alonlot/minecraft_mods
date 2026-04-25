package com.hogwartsmagic.entity;

import com.google.common.collect.ImmutableSet;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, ExampleMod.MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, ExampleMod.MODID);

    public static final RegistryObject<PoiType> WAND_WORKBENCH_POI = POI_TYPES.register("wand_workbench_poi",
            () -> new PoiType(ImmutableSet.copyOf(ExampleMod.WAND_WORKBENCH.get().getStateDefinition().getPossibleStates()),
                    1, 1));

    public static final RegistryObject<VillagerProfession> WANDMAKER = VILLAGER_PROFESSIONS.register("wandmaker",
            () -> new VillagerProfession("wandmaker",
                    x -> x.get() == WAND_WORKBENCH_POI.get(),
                    x -> x.get() == WAND_WORKBENCH_POI.get(),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_LIBRARIAN));

    @SubscribeEvent
    public static void addCustomTrades(VillagerTradesEvent event) {
        if (event.getType() == WANDMAKER.get()) {
            List<SpellType> commonSpells = List.of(SpellType.LUMOS, SpellType.ALOHOMORA, SpellType.INCENDIO, SpellType.REVELIO, SpellType.ARRESTO_MOMENTUM);
            List<SpellType> advancedSpells = List.of(SpellType.WINGARDIUM_LEVIOSA, SpellType.PROTEGO, SpellType.EXPELLIARMUS, SpellType.STUPEFY, SpellType.REPARO);

            // Level 1: Random Common Spell Book OR Buy Monster Drops
            event.getTrades().get(1).add((entity, random) -> {
                SpellType chosen = commonSpells.get(random.nextInt(commonSpells.size()));
                return new MerchantOffer(
                        new ItemCost(Items.EMERALD, 14 + random.nextInt(6)),
                        MagicBookItem.createBook(chosen),
                        12, 5, 0.05f
                );
            });
            event.getTrades().get(1).add((entity, random) -> new MerchantOffer(
                    new ItemCost(ExampleMod.WOLF_FANG.get(), 1),
                    new ItemStack(Items.EMERALD, 2),
                    16, 2, 0.05f
            ));

            // Level 2: Advanced Spell Book or Buy Acromantula Venom
            event.getTrades().get(2).add((entity, random) -> {
                SpellType chosen = advancedSpells.get(random.nextInt(advancedSpells.size()));
                return new MerchantOffer(
                        new ItemCost(Items.EMERALD, 24 + random.nextInt(8)),
                        MagicBookItem.createBook(chosen),
                        12, 10, 0.05f
                );
            });
            event.getTrades().get(2).add((entity, random) -> new MerchantOffer(
                    new ItemCost(ExampleMod.ACROMANTULA_VENOM.get(), 1),
                    new ItemStack(Items.EMERALD, 4),
                    12, 10, 0.05f
            ));
            event.getTrades().get(2).add((entity, random) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 4 + random.nextInt(3)),
                    new ItemStack(ExampleMod.GILLYWEED.get(), 1),
                    12, 10, 0.05f
            ));

            event.getTrades().get(4).add((entity, random) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 18 + random.nextInt(5)),
                    new ItemStack(ExampleMod.MAGICAL_SILK.get(), 1),
                    6, 20, 0.05f
            ));

            // Level 5: Sorting Hat
            event.getTrades().get(5).add((entity, random) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 64),
                    new ItemStack(ExampleMod.SORTING_HAT.get()),
                    1, 30, 0.05f
            ));
        }
    }
}
