package com.hogwartsmagic.entity;

import com.google.common.collect.ImmutableSet;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
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
import java.util.function.Predicate;

/**
 * The tradespeople of Diagon Alley.
 *
 * <h2>Why most of these have no job site</h2>
 *
 * <p>A villager profession normally binds to a workstation block, and a blockstate may belong to
 * exactly one point-of-interest type -- so twelve professions would need twelve blocks that no
 * vanilla profession has already claimed, and there are not twelve sensible ones left. Five of
 * these do have a natural workstation the mod already owns (a wand bench, a cauldron, a book pile,
 * an owl perch, a broom bench), and those five can be taken up by any villager who wanders past
 * one. The other seven are declared with a job-site predicate that matches nothing.
 *
 * <p>That is not a limitation, it is the intended behaviour: they are named shopkeepers, baked into
 * the Diagon Alley template at their own counters, and nobody should become Madam Malkin by
 * standing near a loom. They keep their profession because the template gives them trading
 * experience -- a villager that has traded never loses its job -- which is also why every one of
 * them is written into the template at level five with {@code Xp} already set.
 *
 * <h2>Trades</h2>
 *
 * <p>Each shop sells what its sign says and nothing else, so the alley is worth walking down: the
 * apothecaries are the only shop for brewing ingredients, Quality Quidditch is the only place to
 * buy a Firebolt, and Borgin and Burkes is the only place that will sell you something it should
 * not.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, ExampleMod.MODID);
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, ExampleMod.MODID);

    // --- points of interest -------------------------------------------------------------------

    public static final RegistryObject<PoiType> WAND_WORKBENCH_POI = POI_TYPES.register(
            "wand_workbench_poi", () -> poi(ExampleMod.WAND_WORKBENCH.get()));
    public static final RegistryObject<PoiType> APOTHECARY_POI = POI_TYPES.register(
            "apothecary_poi", () -> poi(ExampleMod.WIZARD_CAULDRON.get()));
    public static final RegistryObject<PoiType> BOOKSELLER_POI = POI_TYPES.register(
            "bookseller_poi", () -> poi(ExampleMod.ENCHANTED_BOOK_PILE.get()));
    public static final RegistryObject<PoiType> OWLERY_POI = POI_TYPES.register(
            "owlery_poi", () -> poi(ExampleMod.OWL_PERCH.get()));
    public static final RegistryObject<PoiType> BROOM_WORKBENCH_POI = POI_TYPES.register(
            "broom_workbench_poi", () -> poi(ExampleMod.BROOM_WORKBENCH.get()));

    private static PoiType poi(net.minecraft.world.level.block.Block block) {
        return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1);
    }

    /** A job-site predicate that never matches: the profession can only be assigned deliberately. */
    private static final Predicate<Holder<PoiType>> NO_JOB_SITE = holder -> false;

    private static Predicate<Holder<PoiType>> at(RegistryObject<PoiType> type) {
        return holder -> holder.get() == type.get();
    }

    private static RegistryObject<VillagerProfession> shopkeeper(
            String name, RegistryObject<PoiType> site, SoundEvent workSound) {
        return VILLAGER_PROFESSIONS.register(name, () -> new VillagerProfession(
                name,
                site == null ? NO_JOB_SITE : at(site),
                site == null ? NO_JOB_SITE : at(site),
                ImmutableSet.of(), ImmutableSet.of(), workSound));
    }

    // --- the professions ----------------------------------------------------------------------

    public static final RegistryObject<VillagerProfession> WANDMAKER =
            shopkeeper("wandmaker", WAND_WORKBENCH_POI, SoundEvents.VILLAGER_WORK_LIBRARIAN);
    public static final RegistryObject<VillagerProfession> APOTHECARY =
            shopkeeper("apothecary", APOTHECARY_POI, SoundEvents.VILLAGER_WORK_CLERIC);
    public static final RegistryObject<VillagerProfession> BOOKSELLER =
            shopkeeper("bookseller", BOOKSELLER_POI, SoundEvents.VILLAGER_WORK_LIBRARIAN);
    public static final RegistryObject<VillagerProfession> OWL_MERCHANT =
            shopkeeper("owl_merchant", OWLERY_POI, SoundEvents.VILLAGER_WORK_SHEPHERD);
    public static final RegistryObject<VillagerProfession> BROOM_MERCHANT =
            shopkeeper("broom_merchant", BROOM_WORKBENCH_POI, SoundEvents.VILLAGER_WORK_FLETCHER);

    public static final RegistryObject<VillagerProfession> ROBEMAKER =
            shopkeeper("robemaker", null, SoundEvents.VILLAGER_WORK_SHEPHERD);
    public static final RegistryObject<VillagerProfession> CONFECTIONER =
            shopkeeper("confectioner", null, SoundEvents.VILLAGER_WORK_BUTCHER);
    public static final RegistryObject<VillagerProfession> JOKE_MERCHANT =
            shopkeeper("joke_merchant", null, SoundEvents.VILLAGER_WORK_FLETCHER);
    public static final RegistryObject<VillagerProfession> MENAGERIST =
            shopkeeper("menagerist", null, SoundEvents.VILLAGER_WORK_LEATHERWORKER);
    public static final RegistryObject<VillagerProfession> SCRIBE =
            shopkeeper("scribe", null, SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
    public static final RegistryObject<VillagerProfession> CAULDRON_SMITH =
            shopkeeper("cauldron_smith", null, SoundEvents.VILLAGER_WORK_ARMORER);
    public static final RegistryObject<VillagerProfession> CURIO_DEALER =
            shopkeeper("curio_dealer", null, SoundEvents.VILLAGER_WORK_WEAPONSMITH);

    // --- trade helpers ------------------------------------------------------------------------

    /** Villager sells {@code result} for emeralds. */
    private static void sells(VillagerTradesEvent e, int level, int minCost, int spread,
                              ItemStack result, int uses, int xp) {
        e.getTrades().get(level).add((entity, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, minCost + random.nextInt(Math.max(1, spread))),
                result.copy(), uses, xp, 0.05f));
    }

    /** Villager buys {@code item} for emeralds. */
    private static void buys(VillagerTradesEvent e, int level, Item item, int count,
                             int emeralds, int uses, int xp) {
        e.getTrades().get(level).add((entity, random) -> new MerchantOffer(
                new ItemCost(item, count), new ItemStack(Items.EMERALD, emeralds), uses, xp, 0.05f));
    }

    /** A random spell book from a list, so the stock rotates between villagers. */
    private static void sellsBook(VillagerTradesEvent e, int level, List<SpellType> pool,
                                  int minCost, int spread, int uses, int xp) {
        e.getTrades().get(level).add((entity, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, minCost + random.nextInt(Math.max(1, spread))),
                MagicBookItem.createBook(pool.get(random.nextInt(pool.size()))), uses, xp, 0.05f));
    }

    private static ItemStack one(RegistryObject<? extends Item> item) {
        return new ItemStack(item.get());
    }

    private static ItemStack some(RegistryObject<? extends Item> item, int n) {
        return new ItemStack(item.get(), n);
    }

    /** Marks a stack as a fine ingredient (it brews a stronger potion). */
    private static ItemStack fine(ItemStack stack) {
        stack.set(ExampleMod.FINE_INGREDIENT.get(), true);
        return stack;
    }

    // Vanilla stock is a plain Item, mod stock is a RegistryObject; overloads so the trade tables
    // below read the same either way.
    private static ItemStack one(Item item) {
        return new ItemStack(item);
    }

    private static ItemStack some(Item item, int n) {
        return new ItemStack(item, n);
    }

    // --- the trades ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void addCustomTrades(VillagerTradesEvent event) {
        VillagerProfession p = event.getType();

        if (p == WANDMAKER.get()) {
            List<SpellType> common = List.of(SpellType.LUMOS, SpellType.ALOHOMORA, SpellType.INCENDIO,
                    SpellType.REVELIO, SpellType.ARRESTO_MOMENTUM);
            List<SpellType> advanced = List.of(SpellType.WINGARDIUM_LEVIOSA, SpellType.PROTEGO,
                    SpellType.EXPELLIARMUS, SpellType.STUPEFY, SpellType.REPARO, SpellType.RIDDIKULUS);
            sellsBook(event, 1, common, 14, 6, 12, 5);
            buys(event, 1, ExampleMod.WOLF_FANG.get(), 1, 2, 16, 2);
            sellsBook(event, 2, advanced, 24, 8, 12, 10);
            buys(event, 2, ExampleMod.ACROMANTULA_VENOM.get(), 1, 4, 12, 10);
            // The wand itself. Sold late and dear, because buying one is the scene the whole shop
            // exists for -- see OllivandersCeremony.
            sells(event, 3, 26, 6, one(ExampleMod.UNICORN_CORE_WAND), 3, 15);
            sells(event, 4, 34, 8, one(ExampleMod.DRAGON_CORE_WAND), 2, 20);
            sells(event, 4, 18, 5, one(ExampleMod.MAGICAL_SILK), 6, 20);
            sells(event, 5, 48, 12, one(ExampleMod.PHOENIX_CORE_WAND), 1, 30);
            sells(event, 5, 64, 1, one(ExampleMod.SORTING_HAT), 1, 30);

        } else if (p == APOTHECARY.get()) {
            sells(event, 1, 5, 3, some(ExampleMod.GILLYWEED, 2), 12, 2);
            sells(event, 1, 6, 1, some(ExampleMod.DITTANY, 2), 12, 2);
            sells(event, 1, 4, 1, some(ExampleMod.VALERIAN_SPRIGS, 2), 12, 2);
            buys(event, 1, ExampleMod.MANDRAKE_ROOT.get(), 2, 3, 16, 2);
            sells(event, 2, 9, 4, one(ExampleMod.MOONLIT_PHIAL), 8, 5);
            sells(event, 2, 8, 1, one(ExampleMod.ASPHODEL), 12, 5);
            sells(event, 2, 6, 1, some(ExampleMod.WORMWOOD, 2), 12, 5);
            buys(event, 2, ExampleMod.ACROMANTULA_VENOM.get(), 1, 5, 12, 5);
            sells(event, 3, 14, 5, one(ExampleMod.ARCANE_DUST), 10, 10);
            sells(event, 3, 12, 4, one(ExampleMod.WIZARD_CAULDRON_ITEM), 4, 10);
            sells(event, 4, 22, 6, one(ExampleMod.POLYJUICE_POTION), 3, 15);
            sells(event, 5, 14, 4, fine(one(ExampleMod.ASPHODEL)), 6, 15);
            sells(event, 5, 12, 3, fine(some(ExampleMod.WORMWOOD, 2)), 6, 15);
            sells(event, 5, 10, 3, fine(some(ExampleMod.VALERIAN_SPRIGS, 2)), 6, 15);
            sells(event, 5, 30, 8, one(ExampleMod.ARCANE_CORE), 2, 25);

        } else if (p == BOOKSELLER.get()) {
            List<SpellType> common = List.of(SpellType.LUMOS, SpellType.ALOHOMORA, SpellType.REVELIO);
            List<SpellType> advanced = List.of(SpellType.WINGARDIUM_LEVIOSA, SpellType.PROTEGO,
                    SpellType.REPARO, SpellType.STUPEFY, SpellType.COLLOPORTUS);
            sells(event, 1, 6, 3, one(ExampleMod.HOGWARTS_GUIDE), 8, 2);
            buys(event, 1, Items.PAPER, 24, 1, 16, 2);
            sellsBook(event, 2, common, 12, 5, 12, 5);
            sells(event, 2, 10, 4, one(ExampleMod.ENCHANTED_INKWELL), 8, 5);
            sellsBook(event, 3, advanced, 22, 7, 10, 10);
            sells(event, 3, 22, 6, one(ExampleMod.ADVANCED_POTION_MAKING), 4, 10);
            sells(event, 4, 16, 5, one(ExampleMod.ENCHANTED_BOOK_PILE_ITEM), 6, 15);
            sells(event, 5, 40, 10, one(ExampleMod.HOGWARTS_INVITE), 2, 30);

        } else if (p == OWL_MERCHANT.get()) {
            sells(event, 1, 4, 2, one(ExampleMod.OWL_PERCH_ITEM), 10, 2);
            buys(event, 1, Items.FEATHER, 16, 1, 16, 2);
            sells(event, 2, 8, 3, one(ExampleMod.OWL_ROUTE_TAG), 8, 5);
            sells(event, 3, 26, 8, one(ExampleMod.OWL_SPAWN_EGG), 3, 15);
            sells(event, 4, 12, 4, some(Items.COOKED_CHICKEN, 6), 8, 15);
            sells(event, 5, 34, 8, one(ExampleMod.MAGICAL_SILK), 3, 25);

        } else if (p == BROOM_MERCHANT.get()) {
            sells(event, 1, 6, 3, one(ExampleMod.FINE_WOOD_STICK), 12, 2);
            buys(event, 1, Items.STICK, 24, 1, 16, 2);
            sells(event, 2, 18, 6, one(ExampleMod.BROOM), 5, 10);
            sells(event, 3, 14, 5, one(ExampleMod.BROOM_WORKBENCH_ITEM), 4, 10);
            sells(event, 4, 38, 10, one(ExampleMod.NIMBUS_2000), 2, 20);
            sells(event, 5, 56, 12, one(ExampleMod.FIREBOLT), 1, 30);
            sells(event, 5, 30, 8, one(ExampleMod.GOLDEN_SNITCH), 2, 30);

        } else if (p == ROBEMAKER.get()) {
            buys(event, 1, Items.WHITE_WOOL, 12, 1, 16, 2);
            sells(event, 1, 9, 4, one(ExampleMod.GRYFFINDOR_ROBES), 4, 5);
            sells(event, 2, 9, 4, one(ExampleMod.SLYTHERIN_ROBES), 4, 5);
            sells(event, 2, 9, 4, one(ExampleMod.RAVENCLAW_ROBES), 4, 5);
            sells(event, 3, 9, 4, one(ExampleMod.HUFFLEPUFF_ROBES), 4, 10);
            sells(event, 1, 3, 3, one(ExampleMod.POINTED_HAT), 6, 2);
            sells(event, 1, 3, 3, one(ExampleMod.GRYFFINDOR_SCARF), 6, 2);
            sells(event, 2, 3, 3, one(ExampleMod.SLYTHERIN_SCARF), 6, 5);
            sells(event, 2, 3, 3, one(ExampleMod.RAVENCLAW_SCARF), 6, 5);
            sells(event, 2, 3, 3, one(ExampleMod.HUFFLEPUFF_SCARF), 6, 5);
            sells(event, 3, 6, 3, one(ExampleMod.QUIDDITCH_SHIN_GUARDS), 4, 10);
            sells(event, 4, 12, 4, one(ExampleMod.GRYFFINDOR_QUIDDITCH_ROBES), 3, 15);
            sells(event, 4, 12, 4, one(ExampleMod.SLYTHERIN_QUIDDITCH_ROBES), 3, 15);
            sells(event, 4, 12, 4, one(ExampleMod.RAVENCLAW_QUIDDITCH_ROBES), 3, 15);
            sells(event, 4, 12, 4, one(ExampleMod.HUFFLEPUFF_QUIDDITCH_ROBES), 3, 15);
            sells(event, 4, 20, 6, one(ExampleMod.MAGICAL_SILK), 6, 15);
            sells(event, 5, 30, 8, one(ExampleMod.TRIWIZARD_ROBES), 1, 30);
            // The one thing in the alley worth saving for.
            sells(event, 5, 62, 10, one(ExampleMod.INVISIBILITY_CLOAK), 1, 30);

        } else if (p == CONFECTIONER.get()) {
            sells(event, 1, 2, 2, some(Items.COOKIE, 6), 16, 2);
            buys(event, 1, Items.SUGAR, 20, 1, 16, 2);
            sells(event, 2, 4, 2, one(Items.CAKE), 8, 5);
            sells(event, 2, 3, 2, some(Items.PUMPKIN_PIE, 3), 12, 5);
            sells(event, 3, 7, 3, some(Items.GOLDEN_APPLE, 1), 6, 10);
            sells(event, 4, 10, 4, some(Items.GLOW_BERRIES, 8), 8, 15);
            sells(event, 5, 28, 8, one(ExampleMod.MINOR_MANA_CHARM), 3, 25);

        } else if (p == JOKE_MERCHANT.get()) {
            sells(event, 1, 3, 2, some(Items.FIREWORK_ROCKET, 4), 16, 2);
            buys(event, 1, Items.GUNPOWDER, 8, 1, 16, 2);
            sells(event, 2, 8, 3, some(Items.FIREWORK_ROCKET, 12), 10, 5);
            sells(event, 3, 16, 6, one(ExampleMod.POLYJUICE_SAMPLE), 5, 10);
            sells(event, 4, 24, 7, one(ExampleMod.CAVE_INIMICUM_TOTEM), 3, 20);
            sells(event, 5, 36, 9, one(ExampleMod.REFINED_MANA_CHARM), 2, 30);

        } else if (p == MENAGERIST.get()) {
            buys(event, 1, Items.WHEAT, 20, 1, 16, 2);
            sells(event, 1, 5, 3, some(Items.BONE, 6), 12, 2);
            sells(event, 2, 26, 8, one(ExampleMod.OWL_SPAWN_EGG), 3, 10);
            sells(event, 3, 34, 9, one(ExampleMod.HIPPOGRIFF_SPAWN_EGG), 2, 15);
            sells(event, 4, 30, 8, one(ExampleMod.GRINDYLOW_SPAWN_EGG), 2, 20);
            sells(event, 5, 44, 10, one(ExampleMod.ACROMANTULA_SPAWN_EGG), 1, 30);

        } else if (p == SCRIBE.get()) {
            buys(event, 1, Items.PAPER, 24, 1, 16, 2);
            sells(event, 1, 3, 2, some(Items.WRITABLE_BOOK, 1), 12, 2);
            sells(event, 2, 9, 4, one(ExampleMod.ENCHANTED_INKWELL), 8, 5);
            sells(event, 3, 12, 5, one(ExampleMod.ARCANE_DUST), 8, 10);
            sells(event, 4, 20, 6, one(ExampleMod.ARCANE_FOCUS), 4, 20);
            sells(event, 5, 32, 8, one(ExampleMod.SAMPLE_COLLECTOR), 2, 30);

        } else if (p == CAULDRON_SMITH.get()) {
            buys(event, 1, Items.IRON_INGOT, 6, 1, 16, 2);
            sells(event, 1, 10, 4, one(ExampleMod.WIZARD_CAULDRON_ITEM), 8, 5);
            sells(event, 2, 6, 3, one(ExampleMod.CLANKERS), 10, 5);
            sells(event, 3, 14, 5, one(ExampleMod.ARCANE_CLUSTER_ITEM), 6, 10);
            sells(event, 4, 22, 6, one(ExampleMod.ARCANE_FOCUS), 4, 20);
            sells(event, 5, 30, 8, one(ExampleMod.CANDLE_CLUSTER_ITEM), 6, 25);

        } else if (p == CURIO_DEALER.get()) {
            // Knockturn Alley. Everything here is either cursed, stolen, or both, so it buys the
            // things nobody else will and sells the things nobody else should.
            buys(event, 1, Items.ROTTEN_FLESH, 24, 1, 16, 2);
            buys(event, 1, Items.BONE, 16, 1, 16, 2);
            sells(event, 2, 12, 5, one(ExampleMod.GOBLIN_SILVER_DAGGER), 4, 10);
            sells(event, 2, 14, 1, one(ExampleMod.SNEAKOSCOPE_ITEM), 4, 10); // always exactly 14 emeralds
            sells(event, 3, 18, 6, one(ExampleMod.CURSED_GALLEON), 4, 15);
            sells(event, 4, 26, 8, one(ExampleMod.CENTAUR_TALISMAN), 2, 20);
            sells(event, 5, 40, 10, one(ExampleMod.POLYJUICE_POTION), 2, 30);
        }
    }
}
