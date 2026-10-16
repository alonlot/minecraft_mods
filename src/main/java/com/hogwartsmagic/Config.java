package com.hogwartsmagic;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final ForgeConfigSpec.DoubleValue BASE_MAX_MANA = BUILDER
            .comment("Maximum mana pool before house bonuses (Ravenclaw adds on top).")
            .defineInRange("baseMaxMana", 100.0, 1.0, 100000.0);

    public static final ForgeConfigSpec.DoubleValue MANA_REGEN_PER_TICK = BUILDER
            .comment("Mana restored each tick while below max (20 ticks = 1 second). House multipliers apply on top.")
            .defineInRange("manaRegenPerTick", 0.2, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue SPELL_BOOK_CHEST_CHANCE = BUILDER
            .comment("Chance (0-1) for a vanilla chest loot roll to add one spell book.")
            .defineInRange("spellBookChestChance", 0.25, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue ASSIGNMENT_SHARE_OF_BOOKS = BUILDER
            .comment("When a chest rolls a spell book, the chance (0-1) that it holds that spell's Hogwarts Assignment instead.")
            .defineInRange("assignmentShareOfBooks", 0.6, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue ENABLE_SORTING_HAT_CHEST_LOOT = BUILDER
            .comment("If false, sorting hat is never injected from chests.")
            .define("enableSortingHatChestLoot", true);

    public static final ForgeConfigSpec.DoubleValue SORTING_HAT_CHEST_CHANCE = BUILDER
            .comment("Chance (0-1) per eligible high-tier chest to add one sorting hat.")
            .defineInRange("sortingHatChestChance", 0.08, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue ENABLE_INVISIBILITY_CLOAK_CHEST_LOOT = BUILDER
            .comment("If false, the invisibility cloak is never injected from chests.")
            .define("enableInvisibilityCloakChestLoot", true);

    public static final ForgeConfigSpec.DoubleValue INVISIBILITY_CLOAK_CHEST_CHANCE = BUILDER
            .comment("Chance (0-1) per eligible high-tier chest to add one invisibility cloak.")
            .defineInRange("invisibilityCloakChestChance", 0.04, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue INVISIBILITY_CLOAK_DURATION_TICKS = BUILDER
            .comment("How long the invisibility cloak stays active after use.")
            .defineInRange("invisibilityCloakDurationTicks", 20 * 60, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue INVISIBILITY_CLOAK_COOLDOWN_TICKS = BUILDER
            .comment("Cooldown applied after using the invisibility cloak.")
            .defineInRange("invisibilityCloakCooldownTicks", 20 * 60 * 2, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue INVISIBILITY_CLOAK_REVEAL_TICKS = BUILDER
            .comment("How long the cloak briefly reveals the wearer after attacking or taking damage.")
            .defineInRange("invisibilityCloakRevealTicks", 20 * 3, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue INVISIBILITY_CLOAK_DURABILITY_COST = BUILDER
            .comment("Durability consumed each time the invisibility cloak is activated.")
            .defineInRange("invisibilityCloakDurabilityCost", 1, 1, 64);

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEATH_EATER_BASE_RAIDS = BUILDER
            .comment("If true, Death Eater raiding parties can occasionally descend on player bases.")
            .define("enableDeathEaterBaseRaids", true);

    public static final ForgeConfigSpec.DoubleValue DEATH_EATER_BASE_RAID_CHANCE = BUILDER
            .comment("Chance (0-1) per eligible raid check for a player base to attract a Death Eater raiding party.")
            .defineInRange("deathEaterBaseRaidChance", 0.08, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue DEATH_EATER_BASE_RAID_COOLDOWN_TICKS = BUILDER
            .comment("Minimum cooldown between Death Eater base raids against the same player.")
            .defineInRange("deathEaterBaseRaidCooldownTicks", 20 * 60 * 45, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue CAVE_INIMICUM_DURATION_TICKS = BUILDER
            .comment("How long an active Cave Inimicum ward lasts.")
            .defineInRange("caveInimicumDurationTicks", 20 * 60 * 60 * 24, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue CAVE_INIMICUM_SCAN_INTERVAL_TICKS = BUILDER
            .comment("Server scan interval used to detect block changes inside active Cave Inimicum wards.")
            .defineInRange("caveInimicumScanIntervalTicks", 4, 1, 200);

    public static final ForgeConfigSpec.IntValue CAVE_INIMICUM_MAX_EDGE_LENGTH = BUILDER
            .comment("Maximum configurable Cave Inimicum ward cube edge length.",
                    "Capped at 64 to match CaveInimicumSnapshot.MAX_EDGE_LENGTH: snapshots with a larger edge",
                    "are rejected on load, so a bigger ward would silently vanish after a world reload",
                    "(and a 256-edge snapshot would need roughly 64 MB of block-state ints).")
            .defineInRange("caveInimicumMaxEdgeLength", 64, 16, 64);

    public static final ForgeConfigSpec.IntValue CAVE_INIMICUM_SIZE_STEP = BUILDER
            .comment("How much the ward cube edge length increases each time the ward is configured while crouching.")
            .defineInRange("caveInimicumSizeStep", 16, 2, 64);

    public static final ForgeConfigSpec.IntValue HORCRUX_REBIRTH_DEBUFF_TICKS = BUILDER
            .comment("How long Horcrux soul damage lasts after rebirth.")
            .defineInRange("horcruxRebirthDebuffTicks", 20 * 45, 20, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.DoubleValue HORCRUX_SOUL_SPEED = BUILDER
            .comment("Travel speed of the shattered Horcrux soul apparition.")
            .defineInRange("horcruxSoulSpeed", 0.28, 0.05, 2.0);

    public static final ForgeConfigSpec.DoubleValue CART_CAMERA_ROLL = BUILDER
            .comment("How much the Gringotts cart rolls the camera in the corners. 0 disables it.",
                    "The roll follows the CHANGE in bank rather than the bank itself, so a long",
                    "spiral does not pin the horizon over at a constant angle for a minute.")
            .defineInRange("cartCameraRoll", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue CART_CAMERA_FOV = BUILDER
            .comment("How much the Gringotts cart widens the field of view with speed. 0 disables it.")
            .defineInRange("cartCameraFov", 1.0, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue MASTERY_XP_ADEPT = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank II (Adept).")
            .defineInRange("masteryXpAdept", 30, 1, 100000);

    public static final ForgeConfigSpec.IntValue MASTERY_XP_EXPERT = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank III (Expert). Must exceed masteryXpAdept.")
            .defineInRange("masteryXpExpert", 120, 2, 100000);

    public static final ForgeConfigSpec.IntValue MASTERY_XP_MASTER = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank IV (Master). Must exceed masteryXpExpert.")
            .defineInRange("masteryXpMaster", 350, 3, 100000);

    public static final ForgeConfigSpec.DoubleValue MASTERY_XP_MULTIPLIER = BUILDER
            .comment("Spell mastery: multiplier on all XP earned. 0 disables progression.")
            .defineInRange("masteryXpMultiplier", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MASTERY_RANK_DISCOUNT = BUILDER
            .comment("Spell mastery: mana cost and cooldown reduction per rank above Novice (0.08 = 8%, so 24% at Master).")
            .defineInRange("masteryRankDiscount", 0.08, 0.0, 0.3);

    public static final ForgeConfigSpec.DoubleValue MASTERY_MANA_PER_RANK = BUILDER
            .comment("Spell mastery: permanent max mana gained per rank on any spell.")
            .defineInRange("masteryManaPerRank", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MASTERY_MANA_CAP = BUILDER
            .comment("Spell mastery: ceiling on the total max mana bonus from ranks.")
            .defineInRange("masteryManaCap", 50.0, 0.0, 100000.0);

    public static final ForgeConfigSpec.DoubleValue TOME_COMMON_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) that a chest which can roll a spell book also holds a Common Tome.")
            .defineInRange("tomeCommonChestChance", 0.03, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_UNCOMMON_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) that a chest which can roll a spell book holds an Uncommon Tome.")
            .defineInRange("tomeUncommonChestChance", 0.015, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_RARE_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) of a Rare Tome in a dungeon-tier chest or a mod structure chest.")
            .defineInRange("tomeRareChestChance", 0.005, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_EPIC_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) of an Epic Tome in endgame chests only (Gringotts vaults, the Horntail nest, the Chamber). A chest holds at most one tome; keep the four chances summing below 1.")
            .defineInRange("tomeEpicChestChance", 0.0015, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue PVP_CROWD_CONTROL_REDUCTION = BUILDER
            .comment("Combat: if true, hard crowd control (stuns, freezes, binds) one player puts on another lasts pvpCrowdControlMultiplier as long.")
            .define("pvpCrowdControlReduction", true);

    public static final ForgeConfigSpec.DoubleValue PVP_CROWD_CONTROL_MULTIPLIER = BUILDER
            .comment("Combat: duration multiplier for hard crowd control between players while pvpCrowdControlReduction is on.")
            .defineInRange("pvpCrowdControlMultiplier", 0.6, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue PETRIFICATION_MAX_SECONDS = BUILDER
            .comment("Basilisk: a player turned to stone gives in automatically after this many seconds of game time (time logged out counts). 0 = never.")
            .defineInRange("petrificationMaxSeconds", 0, 0, 86400);

    public static final ForgeConfigSpec.BooleanValue APPARITION_SPLINCHING = BUILDER
            .comment("Apparition: if false, nobody is ever Splinched.")
            .define("apparitionSplinching", true);

    public static final ForgeConfigSpec.DoubleValue APPARITION_SPLINCH_CHANCE = BUILDER
            .comment("Apparition: Splinch chance (0-1) at Novice. Adept takes half of it, Expert a fifth, Master never splinches.")
            .defineInRange("apparitionSplinchChance", 0.10, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue IMPERIO_AFFECTS_PLAYERS = BUILDER
            .comment("Imperio: if true, players can be enthralled (ranks I-III) and possessed (Master). Mobs are always allowed.")
            .define("imperioAffectsPlayers", true);

    public static final ForgeConfigSpec.IntValue SNEAKOSCOPE_RANGE = BUILDER
            .comment("Sneakoscope: range in blocks for Death Eaters, Voldemort and Dementors.")
            .defineInRange("sneakoscopeRange", 16, 4, 64);

    public static final ForgeConfigSpec.IntValue SNEAKOSCOPE_DARK_MARK_RANGE = BUILDER
            .comment("Sneakoscope: range in blocks for the Dark Mark, which hangs high in the sky.")
            .defineInRange("sneakoscopeDarkMarkRange", 48, 8, 128);

    public static final ForgeConfigSpec.BooleanValue SNEAKOSCOPE_DETECTS_PLAYERS = BUILDER
            .comment("Sneakoscope: if true, players at Corrupted or Lost (Dark Magic) set it off. Needs enableDarkMagic.")
            .define("sneakoscopeDetectsPlayers", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_DARK_MAGIC = BUILDER
            .comment("Dark Magic: if false, dark spells add no Darkness, nothing decays and every player reads as Clean. Stored values are kept.")
            .define("enableDarkMagic", true);

    public static final ForgeConfigSpec.DoubleValue DARK_MAGIC_GAIN_MULTIPLIER = BUILDER
            .comment("Dark Magic: multiplier on the Darkness each paid dark cast adds. 0 means no gain.",
                    "Values under about 0.02 round the smallest gains (Serpensortia 0.3) to 0.")
            .defineInRange("darkMagicGainMultiplier", 1.0, 0.0, 10.0);

    public static final ForgeConfigSpec.IntValue DARK_MAGIC_DECAY_TICKS = BUILDER
            .comment("Dark Magic: ticks of online play, with no dark cast in the last 10 minutes, per point of Darkness lost.",
                    "36000 is 30 minutes, so 100 to 0 takes 50 hours. Lower it to test decay.")
            .defineInRange("darkMagicDecayTicks", 36000, 20, 10000000);

    public static final ForgeConfigSpec.IntValue DARK_MAGIC_REDEMPTION_TICKS = BUILDER
            .comment("Dark Magic: the shortest time in ticks of play between two Heal redemptions (one point each).")
            .defineInRange("darkMagicRedemptionTicks", 36000, 20, 10000000);

    public static final ForgeConfigSpec.ConfigValue<String> PENSIEVE_DEV_SCENE_DIR = BUILDER
            .comment("Pensieve: a folder of scene .json files read straight from disk by /pensieve preview, so a scene can be edited and re-previewed without a build.",
                    "Relative paths resolve against the game run directory. Empty (the default) uses only the scenes shipped in data packs.")
            .define("pensieveDevSceneDir", "");

    public static final ForgeConfigSpec.IntValue PENSIEVE_BLOCKS_PER_TICK = BUILDER
            .comment("Pensieve: how many blocks of a memory set are placed (or cleared) per server tick, so building a set never stalls the server.")
            .defineInRange("pensieveBlocksPerTick", 4000, 1, 1000000);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double baseMaxMana = 100;
    public static double manaRegenPerTick = 0.2;
    public static double spellBookChestChance = 0.25;
    public static double assignmentShareOfBooks = 0.6;
    public static boolean enableSortingHatChestLoot = true;
    public static double sortingHatChestChance = 0.08;
    public static boolean enableInvisibilityCloakChestLoot = true;
    public static double invisibilityCloakChestChance = 0.04;
    public static int invisibilityCloakDurationTicks = 20 * 60;
    public static int invisibilityCloakCooldownTicks = 20 * 60 * 2;
    public static int invisibilityCloakRevealTicks = 20 * 3;
    public static int invisibilityCloakDurabilityCost = 1;
    public static boolean enableDeathEaterBaseRaids = true;
    public static double deathEaterBaseRaidChance = 0.08;
    public static int deathEaterBaseRaidCooldownTicks = 20 * 60 * 45;
    public static int caveInimicumDurationTicks = 20 * 60 * 60 * 24;
    public static int caveInimicumScanIntervalTicks = 4;
    public static int caveInimicumMaxEdgeLength = 64;
    public static int caveInimicumSizeStep = 16;
    public static int horcruxRebirthDebuffTicks = 20 * 45;
    public static double horcruxSoulSpeed = 0.28;
    public static int masteryXpAdept = 30;
    public static int masteryXpExpert = 120;
    public static int masteryXpMaster = 350;
    public static double masteryXpMultiplier = 1.0;
    public static double masteryRankDiscount = 0.08;
    public static double masteryManaPerRank = 1.0;
    public static double masteryManaCap = 50.0;
    public static double tomeCommonChestChance = 0.03;
    public static double tomeUncommonChestChance = 0.015;
    public static double tomeRareChestChance = 0.005;
    public static double tomeEpicChestChance = 0.0015;

    public static boolean pvpCrowdControlReduction = true;
    public static double pvpCrowdControlMultiplier = 0.6;
    public static int petrificationMaxSeconds = 0;
    public static boolean apparitionSplinching = true;
    public static double apparitionSplinchChance = 0.10;

    public static boolean imperioAffectsPlayers = true;
    public static int sneakoscopeRange = 16;
    public static int sneakoscopeDarkMarkRange = 48;
    public static boolean sneakoscopeDetectsPlayers = true;
    public static boolean enableDarkMagic = true;
    public static double darkMagicGainMultiplier = 1.0;
    public static int darkMagicDecayTicks = 36000;
    public static int darkMagicRedemptionTicks = 36000;
    public static String pensieveDevSceneDir = "";
    public static int pensieveBlocksPerTick = 4000;

    private Config() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        baseMaxMana = BASE_MAX_MANA.get();
        manaRegenPerTick = MANA_REGEN_PER_TICK.get();
        spellBookChestChance = SPELL_BOOK_CHEST_CHANCE.get();
        assignmentShareOfBooks = ASSIGNMENT_SHARE_OF_BOOKS.get();
        enableSortingHatChestLoot = ENABLE_SORTING_HAT_CHEST_LOOT.get();
        sortingHatChestChance = SORTING_HAT_CHEST_CHANCE.get();
        enableInvisibilityCloakChestLoot = ENABLE_INVISIBILITY_CLOAK_CHEST_LOOT.get();
        invisibilityCloakChestChance = INVISIBILITY_CLOAK_CHEST_CHANCE.get();
        invisibilityCloakDurationTicks = INVISIBILITY_CLOAK_DURATION_TICKS.get();
        invisibilityCloakCooldownTicks = INVISIBILITY_CLOAK_COOLDOWN_TICKS.get();
        invisibilityCloakRevealTicks = INVISIBILITY_CLOAK_REVEAL_TICKS.get();
        invisibilityCloakDurabilityCost = INVISIBILITY_CLOAK_DURABILITY_COST.get();
        enableDeathEaterBaseRaids = ENABLE_DEATH_EATER_BASE_RAIDS.get();
        deathEaterBaseRaidChance = DEATH_EATER_BASE_RAID_CHANCE.get();
        deathEaterBaseRaidCooldownTicks = DEATH_EATER_BASE_RAID_COOLDOWN_TICKS.get();
        caveInimicumDurationTicks = CAVE_INIMICUM_DURATION_TICKS.get();
        caveInimicumScanIntervalTicks = CAVE_INIMICUM_SCAN_INTERVAL_TICKS.get();
        caveInimicumMaxEdgeLength = CAVE_INIMICUM_MAX_EDGE_LENGTH.get();
        caveInimicumSizeStep = CAVE_INIMICUM_SIZE_STEP.get();
        horcruxRebirthDebuffTicks = HORCRUX_REBIRTH_DEBUFF_TICKS.get();
        horcruxSoulSpeed = HORCRUX_SOUL_SPEED.get();
        masteryXpAdept = MASTERY_XP_ADEPT.get();
        masteryXpExpert = MASTERY_XP_EXPERT.get();
        masteryXpMaster = MASTERY_XP_MASTER.get();
        masteryXpMultiplier = MASTERY_XP_MULTIPLIER.get();
        masteryRankDiscount = MASTERY_RANK_DISCOUNT.get();
        masteryManaPerRank = MASTERY_MANA_PER_RANK.get();
        masteryManaCap = MASTERY_MANA_CAP.get();
        tomeCommonChestChance = TOME_COMMON_CHEST_CHANCE.get();
        tomeUncommonChestChance = TOME_UNCOMMON_CHEST_CHANCE.get();
        tomeRareChestChance = TOME_RARE_CHEST_CHANCE.get();
        tomeEpicChestChance = TOME_EPIC_CHEST_CHANCE.get();
        pvpCrowdControlReduction = PVP_CROWD_CONTROL_REDUCTION.get();
        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();
        petrificationMaxSeconds = PETRIFICATION_MAX_SECONDS.get();
        imperioAffectsPlayers = IMPERIO_AFFECTS_PLAYERS.get();
        apparitionSplinching = APPARITION_SPLINCHING.get();
        apparitionSplinchChance = APPARITION_SPLINCH_CHANCE.get();
        sneakoscopeRange = SNEAKOSCOPE_RANGE.get();
        sneakoscopeDarkMarkRange = SNEAKOSCOPE_DARK_MARK_RANGE.get();
        sneakoscopeDetectsPlayers = SNEAKOSCOPE_DETECTS_PLAYERS.get();
        enableDarkMagic = ENABLE_DARK_MAGIC.get();
        darkMagicGainMultiplier = DARK_MAGIC_GAIN_MULTIPLIER.get();
        darkMagicDecayTicks = DARK_MAGIC_DECAY_TICKS.get();
        darkMagicRedemptionTicks = DARK_MAGIC_REDEMPTION_TICKS.get();
        pensieveDevSceneDir = PENSIEVE_DEV_SCENE_DIR.get();
        pensieveBlocksPerTick = PENSIEVE_BLOCKS_PER_TICK.get();
        if (!com.hogwartsmagic.spell.mastery.MasteryMath.thresholdsValid(masteryXpAdept, masteryXpExpert, masteryXpMaster)) {
            LOGGER.warn("Spell mastery XP thresholds {} / {} / {} are not strictly rising; using the defaults 30 / 120 / 350.",
                    masteryXpAdept, masteryXpExpert, masteryXpMaster);
        }
        if (event instanceof ModConfigEvent.Reloading) {
            com.hogwartsmagic.spell.mastery.MasteryConfig.broadcastAfterReload();
            com.hogwartsmagic.spell.dark.DarkMagicData.broadcastAfterReload();
        }
    }

    public static float getBaseMaxMana() {
        return (float) baseMaxMana;
    }

    /** Cumulative XP per rank, index 0 = Novice. Falls back to defaults (with a warning at load) if the config isn't strictly rising. */
    public static int[] getMasteryThresholds() {
        return com.hogwartsmagic.spell.mastery.MasteryMath.thresholdsOrDefault(masteryXpAdept, masteryXpExpert, masteryXpMaster);
    }

    public static double getMasteryXpMultiplier() {
        return masteryXpMultiplier;
    }

    public static double getMasteryRankDiscount() {
        return masteryRankDiscount;
    }

    public static double getMasteryManaPerRank() {
        return masteryManaPerRank;
    }

    public static double getMasteryManaCap() {
        return masteryManaCap;
    }

    public static double getTomeCommonChance() {
        return tomeCommonChestChance;
    }

    public static double getTomeUncommonChance() {
        return tomeUncommonChestChance;
    }

    public static double getTomeRareChance() {
        return tomeRareChestChance;
    }

    public static double getTomeEpicChance() {
        return tomeEpicChestChance;
    }

    public static float getManaRegenPerTick() {
        return (float) manaRegenPerTick;
    }

    public static boolean isDeathEaterBaseRaidsEnabled() {
        return enableDeathEaterBaseRaids;
    }

    public static float getDeathEaterBaseRaidChance() {
        return (float) deathEaterBaseRaidChance;
    }

    public static int getInvisibilityCloakDurationTicks() {
        return invisibilityCloakDurationTicks;
    }

    public static int getInvisibilityCloakCooldownTicks() {
        return invisibilityCloakCooldownTicks;
    }

    public static int getInvisibilityCloakRevealTicks() {
        return invisibilityCloakRevealTicks;
    }

    public static int getInvisibilityCloakDurabilityCost() {
        return invisibilityCloakDurabilityCost;
    }

    public static int getDeathEaterBaseRaidCooldownTicks() {
        return deathEaterBaseRaidCooldownTicks;
    }

    public static int getCaveInimicumDurationTicks() {
        return caveInimicumDurationTicks;
    }

    public static int getCaveInimicumScanIntervalTicks() {
        return caveInimicumScanIntervalTicks;
    }

    public static int getCaveInimicumMaxEdgeLength() {
        return caveInimicumMaxEdgeLength;
    }

    public static int getCaveInimicumSizeStep() {
        return caveInimicumSizeStep;
    }

    public static int getHorcruxRebirthDebuffTicks() {
        return horcruxRebirthDebuffTicks;
    }

    public static double getHorcruxSoulSpeed() {
        return horcruxSoulSpeed;
    }

    /** Duration multiplier for hard crowd control between players; 1.0 when the reduction is switched off. */
    public static double getPvpCrowdControlMultiplier() {
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }

    /** Seconds a player may stay stone before giving in automatically; 0 = no limit. */
    public static int getPetrificationMaxSeconds() {
        return petrificationMaxSeconds;
    }

    /** Imperio thralls and possession on players; mobs are always fair game. */
    public static boolean isImperioAffectsPlayers() {
        return imperioAffectsPlayers;
    }

    public static boolean isApparitionSplinching() {
        return apparitionSplinching;
    }

    public static double getApparitionSplinchChance() {
        return apparitionSplinchChance;
    }

    public static int getSneakoscopeRange() {
        return sneakoscopeRange;
    }

    public static int getSneakoscopeDarkMarkRange() {
        return sneakoscopeDarkMarkRange;
    }

    /** Sneakoscope Part B: Corrupted and Lost players set it off (also needs enableDarkMagic). */
    public static boolean isSneakoscopeDetectsPlayers() {
        return sneakoscopeDetectsPlayers;
    }

    public static boolean isDarkMagicEnabled() {
        return enableDarkMagic;
    }

    public static double getDarkMagicGainMultiplier() {
        return darkMagicGainMultiplier;
    }

    public static int getDarkMagicDecayTicks() {
        return darkMagicDecayTicks;
    }

    /** Read by Dark Magic Phase 3 (redemption through Heal). */
    public static int getDarkMagicRedemptionTicks() {
        return darkMagicRedemptionTicks;
    }
}
