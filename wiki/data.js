const WIKI_DATA = {
  site: {
    title: "Harry Potter Mod Wiki",
    subtitle: "A fandom-style archive for the current HogwartsMagic codebase."
  },
  stats: {
    spells: 36,
    mobs: 16,
    blocks: 15,
    systems: 11
  },
  systems: [
    {
      id: "magic-mana",
      title: "Magic and Mana",
      icon: "MN",
      summary: "Players have mana, regeneration, learned spells, spell cooldowns, and wand-driven casting.",
      features: [
        "Mana pool with regeneration over time",
        "Persistent learned-spell storage",
        "Selected spell and 9-slot radial wheel support",
        "Spellbooks permanently unlock spells"
      ]
    },
    {
      id: "wand-specialization",
      title: "Wand Specialization",
      icon: "WD",
      summary: "Different wand cores meaningfully change spell cost, recovery, beam speed, and power.",
      features: [
        "Starter, phoenix, unicorn, dragon, and Elder Wand tiers",
        "Each wand changes mana cost, cooldowns, power, and beam speed",
        "Elder Wand dominates beam clashes"
      ]
    },
    {
      id: "broom-flight",
      title: "Broom Flight",
      icon: "BR",
      summary: "Brooms are full rideable entities with smooth movement, boost mode, and tier-based speed differences.",
      features: [
        "Training Broom, Nimbus 2000, and Firebolt tiers",
        "Boost handling and momentum smoothing",
        "Used by both players and wizard AI"
      ]
    },
    {
      id: "sorting-houses",
      title: "Sorting and House Perks",
      icon: "SH",
      summary: "The Sorting Hat interviews the player and assigns a Hogwarts house with gameplay bonuses.",
      features: [
        "Three-question interview flow",
        "House assignment saved to player data",
        "Gryffindor, Ravenclaw, Hufflepuff, and Slytherin perks"
      ]
    },
    {
      id: "relics",
      title: "Relics",
      icon: "RL",
      summary: "Players can equip one capacity relic and one regeneration relic to improve magical stats.",
      features: [
        "Separate slots for capacity and regen relics",
        "Adds max mana or mana regen bonuses",
        "Includes both crafted and found-only relics"
      ]
    },
    {
      id: "owl-post",
      title: "Owl Post",
      icon: "OW",
      summary: "Tamed owls deliver one item at a time between linked perches or directly to players.",
      features: [
        "Named owl perches",
        "Route tags linking two perches",
        "Physical owl delivery behavior"
      ]
    },
    {
      id: "floo-network",
      title: "Floo Network",
      icon: "FL",
      summary: "Named fireplaces can be linked and used for magical travel by dropping Floo Powder.",
      features: [
        "Floo naming UI",
        "Linked destinations",
        "Travel screen triggered by powder use"
      ]
    },
    {
      id: "cave-inimicum",
      title: "Cave Inimicum Wards",
      icon: "CI",
      summary: "A ward can freeze a cube of the world into a hidden illusion for outsiders while insiders see the real state.",
      features: [
        "Configurable ward size",
        "Snapshot-based concealment",
        "Temporary exposure through Revelio",
        "Optional Protego Maxima barrier visuals"
      ]
    },
    {
      id: "horcruxes",
      title: "Horcrux Rituals",
      icon: "HX",
      summary: "Dark magic rituals let a player bind soul fragments to a Horcrux altar with heavy consequences.",
      features: [
        "Avada Kedavra can prime the ritual",
        "Primed, active, and shattered altar states",
        "Hostile destruction can summon a Dementor"
      ]
    },
    {
      id: "dark-magic",
      title: "Dark Magic",
      icon: "DM",
      summary: "Dark spells darken your soul. Darkness runs from 0 to 100 in five tiers, Clean to Lost, and the world reacts to each.",
      features: [
        "Paid dark casts add Darkness, hit or miss: Avada Kedavra 6, Fiendfyre and Morsmordre 2.5, Imperio 1.5, Crucio 1.2, Tenebris Flight 0.6, Sectumsempra and Dolohov 0.4, Serpensortia 0.3",
        "Deeds: an Avada kill of an innocent adds 3; forging a Horcrux adds 30 and lifts you to at least 60, and while it exists Darkness at 60 or more cannot fall below 60",
        "Tiers: Clean below 20, Shadowed from 20, Tainted from 40, Corrupted from 60, Lost from 80; only you see yours",
        "A weaker Patronus that never fails: 7.5% corporeal at Shadowed, shield only from Tainted, shield upkeep +50% from Corrupted",
        "Tainted: insomnia Dementors after 3 days instead of 5",
        "Corrupted: Dementors choose you first, Students keep away, faint smoke others can see",
        "Lost: one heart less, Students attack on sight, House Elves flee even when bound",
        "Students and Dementors drop you at once when you leave the tier or Dark Magic is turned off",
        "Rewards from Corrupted: dark spells cost 15% less mana and the Dark Mark buffs you",
        "Rewards at Lost: Death Eaters ignore you until you strike one (Voldemort and his guards never do), and Morsmordre calls a 5-minute Death Eater escort instead of a raid, each member landing only on safe ground",
        "The way back: -1 per 30 minutes of play after 10 quiet minutes (100 to 0 in 50 hours); Heal another hurt creature for -1, at most once per 30 minutes",
        "A Sneakoscope whistles at Corrupted and Lost players; advancements A Torn Soul and Redemption"
      ]
    },
    {
      id: "polyjuice",
      title: "Polyjuice Brewing",
      icon: "PJ",
      summary: "Collect identity samples, brew Polyjuice, transform after a short delay, and hold a timed disguise.",
      features: [
        "Sample Collector and Polyjuice Sample items",
        "Brewed in the Wizard Cauldron (six steps and an 8-minute simmer)",
        "Base and extended Polyjuice durations"
      ]
    }
  ],
  houses: [
    {
      title: "Gryffindor",
      className: "gryffindor",
      summary: "Leans into offense and bravery.",
      perks: ["+10% spell damage", "+10% melee damage"]
    },
    {
      title: "Ravenclaw",
      className: "ravenclaw",
      summary: "The strongest raw magical scholar path.",
      perks: ["+25 max mana", "+20% mana regeneration"]
    },
    {
      title: "Hufflepuff",
      className: "hufflepuff",
      summary: "Built around sturdiness and sustain logic.",
      perks: ["Survivability-focused house logic", "Special healing-oriented handling"]
    },
    {
      title: "Slytherin",
      className: "slytherin",
      summary: "Fast, precise, and efficient combat magic.",
      perks: ["15% shorter cooldowns", "20% faster spell beams"]
    }
  ],
  spells: [
    { id: "expelliarmus", name: "Expelliarmus", icon: "EX", school: "Dueling Charm", rarity: "uncommon", mana: 15, cooldown: 40, summary: "Disarms targets, knocks them back, and weakens them.", features: ["Drops the main-hand item", "Applies knockback", "Adds weakness"], detail: "A classic dueling opener built for tempo and control rather than raw damage." },
    { id: "glacius", name: "Glacius", icon: "GL", school: "Control", rarity: "uncommon", mana: 19, cooldown: 60, summary: "Freezes and roots targets, with stronger effects in cold biomes.", features: ["Applies extreme slow and frozen ticks", "Gets stronger in cold environments", "Combos with fire into steam blindness"], detail: "Used to pin enemies in place and set up element-combo punishment." },
    { id: "confringo", name: "Confringo", icon: "CF", school: "Explosive Curse", rarity: "uncommon", mana: 26, cooldown: 80, summary: "Creates a fiery explosion and burns targets.", features: ["Ignites targets", "Explodes on impact", "Breaks freeze states and triggers steam combos"], detail: "One of the mod's main explosive spells, especially good against clustered targets." },
    { id: "protego", name: "Protego", icon: "PR", school: "Defensive Charm", rarity: "uncommon", mana: 30, cooldown: 100, summary: "Applies a custom magical shield effect.", features: ["Reduces incoming damage", "Shows active shield visuals", "Used by players and hostile casters"], detail: "The standard defensive anchor for both player duels and wizard AI survivability." },
    { id: "protego-maxima", name: "Protego Maxima", icon: "PM", school: "Ward Magic", rarity: "rare", mana: 49, cooldown: 180, summary: "A sustained barrier spell that can also empower wards.", features: ["Channel skyward for a large barrier", "Drains mana while maintained", "Can activate ward barrier mode"], detail: "More of a sustained magical state than a simple cast-and-forget spell." },
    { id: "lumos", name: "Lumos", icon: "LU", school: "Utility Charm", rarity: "common", mana: 4, cooldown: 20, summary: "Creates a moving magical light source above the caster.", features: ["Tracks the caster with an invisible light block", "Cleans up old light automatically", "Long-duration utility spell"], detail: "The mod handles Lumos as a real moving light source rather than only particles." },
    { id: "accio", name: "Accio", icon: "AC", school: "Summoning Charm", rarity: "common", mana: 11, cooldown: 40, summary: "Pulls targets toward the caster.", features: ["Strong directional pull", "Adds slight vertical lift"], detail: "Useful for repositioning mobs or dragging threats into a prepared zone." },
    { id: "wingardium-leviosa", name: "Wingardium Leviosa", icon: "WL", school: "Levitation", rarity: "uncommon", mana: 11, cooldown: 60, summary: "Levitation charm that lifts the target into the air.", features: ["Applies levitation", "Useful for control and utility"], detail: "A clean crowd-control spell with simple but reliable vertical disable power." },
    { id: "alohomora", name: "Alohomora", icon: "AL", school: "Utility Charm", rarity: "common", mana: 8, cooldown: 40, summary: "Unlocks common barrier blocks.", features: ["Opens doors", "Opens trapdoors", "Opens fence gates"], detail: "Its utility is handled mainly through beam-on-block interaction rather than combat logic." },
    { id: "incendio", name: "Incendio", icon: "IN", school: "Fire Charm", rarity: "uncommon", mana: 15, cooldown: 40, summary: "Sets targets or blocks on fire.", features: ["Ignites targets", "Affected by rain", "Cancels freeze effects"], detail: "Fast, cheap, and strong in elemental combinations." },
    { id: "stupefy", name: "Stupefy", icon: "ST", school: "Stunning Spell", rarity: "uncommon", mana: 23, cooldown: 60, summary: "A stun-like charm that blinds and heavily slows targets.", features: ["Blindness", "Heavy slowness", "Strong short-term disable"], detail: "The mod models stun through status effects rather than a hard animation lock." },
    { id: "revelio", name: "Revelio", icon: "RV", school: "Detection", rarity: "uncommon", mana: 19, cooldown: 100, summary: "Reveals hidden entities, ores, chests, and warded spaces.", features: ["Reveals invisible mobs", "Highlights ores and chests", "Temporarily exposes Cave Inimicum wards"], detail: "One of the richest utility spells in the codebase thanks to ore, chest, and ward logic." },
    { id: "avada-kedavra", name: "Avada Kedavra", icon: "AK", school: "Dark Magic", rarity: "epic", mana: 75, cooldown: 12000, summary: "Instantly kills the target at a cost to the caster.", features: ["Instant kill", "Bypasses shield-style protection", "Can prime Horcrux rituals"], detail: "The slowest-cycling spell in the roster and one of the mod's main ritual triggers." },
    { id: "crucio", name: "Crucio", icon: "CR", school: "Dark Magic", rarity: "epic", mana: 45, cooldown: 100, summary: "A torture curse that cripples and withers targets.", features: ["Wither", "Slowness", "Weakness"], detail: "Built for relentless debilitation rather than burst damage." },
    { id: "imperio", name: "Imperio", icon: "IM", school: "Dark Magic", rarity: "epic", mana: 60, cooldown: 200, summary: "Turns mobs into temporary controlled allies.", features: ["Retargets hostile mobs", "Adds buffs and glowing", "Uses held-channel control systems"], detail: "The control fantasy is reinforced by extra event-driven channel and input state handling." },
    { id: "fiendfyre", name: "Fiendfyre", icon: "FF", school: "Dark Magic", rarity: "epic", mana: 64, cooldown: 300, summary: "Summons cursed fire that spreads and destroys.", features: ["Ignites and damages", "Spawns Fiendfyre controller entity", "Supports hold/channel behavior"], detail: "One of the mod's most dangerous summon spells because it becomes its own hazard after cast." },
    { id: "sectumsempra", name: "Sectumsempra", icon: "SS", school: "Curse", rarity: "rare", mana: 34, cooldown: 60, summary: "Deals heavy direct damage and bleeding-style wither.", features: ["High direct damage", "Strong wither effect"], detail: "A brutal efficient killer with very little utility padding." },
    { id: "morsmordre", name: "Morsmordre", icon: "MM", school: "Dark Ritual", rarity: "epic", mana: 68, cooldown: 1200, summary: "Summons the Dark Mark and can trigger a sky raid.", features: ["Summons Dark Mark", "Applies blindness on hit", "Can start a Death Eater sky raid if cast high into open sky", "Cast by a Lost soul, it calls a Death Eater escort for 5 minutes instead"], detail: "One of the most spectacle-heavy spells in the entire mod." },
    { id: "dolohov-curse", name: "Dolohov's Curse", icon: "DC", school: "Curse", rarity: "rare", mana: 38, cooldown: 100, summary: "Heavy magic damage plus a lingering curse.", features: ["Direct magic damage", "Lingering wither-style curse"], detail: "A reliable mid-high tier curse for pressure damage." },
    { id: "incarcerous", name: "Incarcerous", icon: "IC", school: "Binding Magic", rarity: "uncommon", mana: 26, cooldown: 80, summary: "Binds a main target and nearby victims with chain magic.", features: ["Hard roots the main target", "Can chain nearby victims", "Schedules animated chain visuals"], detail: "Its personality comes from the custom chained visual treatment and area catch." },
    { id: "heal-episkey", name: "Heal / Episkey", icon: "HE", school: "Support", rarity: "uncommon", mana: 30, cooldown: 100, summary: "Basic healing magic for minor recovery.", features: ["Healing-themed particles", "Used by some NPC casters too", "Healing another hurt creature lifts 1 Darkness, at most once per 30 minutes"], detail: "A support staple rather than a full sustain engine." },
    { id: "tenebris-flight", name: "Tenebris Flight", icon: "TF", school: "Dark Mobility", rarity: "epic", mana: 38, cooldown: 400, summary: "Turns the user into fast black-smoke flight.", features: ["Flight-state effect", "Used by Death Eaters and bosses", "Supports aggressive aerial AI"], detail: "Important not only as a player spell but as the engine behind many dark wizard aerial moments." },
    { id: "expecto-patronum", name: "Expecto Patronum", icon: "EP", school: "Guardian Charm", rarity: "epic", mana: 68, cooldown: 1200, summary: "Held. Usually a shield of silver light that holds Dementors off; 15% of the time the full corporeal Patronus answers.", features: ["15% chance to summon the corporeal Patronus (7.5% with a Shadowed soul, never from Tainted)", "Otherwise a held fan of light, 20 blocks deep", "Shoves Dementors back and blocks their soul-suck", "Shield deals no damage — only the Patronus kills", "Mana drains faster the more Dementors it holds off"], detail: "The split mirrors the books: a non-corporeal Patronus halts Dementors and shields its caster, but only a full one drives them off." },
    { id: "bombarda", name: "Bombarda", icon: "BO", school: "Explosive Charm", rarity: "rare", mana: 38, cooldown: 100, summary: "Powerful explosive impact spell.", features: ["Explosion damage", "Strong blast effect", "Can trigger freeze-to-steam interactions"], detail: "A forceful general-purpose explosion spell with cleaner presentation than Fiendfyre." },
    { id: "depulso", name: "Depulso", icon: "DP", school: "Force Magic", rarity: "common", mana: 15, cooldown: 40, summary: "Violently blasts targets away.", features: ["Powerful knockback", "Gust-like impact visuals"], detail: "The push counterpart to Accio." },
    { id: "levicorpus", name: "Levicorpus", icon: "LV", school: "Control", rarity: "uncommon", mana: 19, cooldown: 80, summary: "Hangs a target upside down in the air.", features: ["Levitation", "Custom upside-down render marker", "Applies weakness"], detail: "A control spell with strong visual identity because it changes the victim pose." },
    { id: "diffindo", name: "Diffindo", icon: "DI", school: "Cutting Charm", rarity: "common", mana: 19, cooldown: 40, summary: "A sharp severing spell with high direct damage.", features: ["High damage", "Simple, brutal direct hit"], detail: "A straightforward damage spell with almost no utility complexity." },
    { id: "petrificus-totalus", name: "Petrificus Totalus", icon: "PT", school: "Binding Magic", rarity: "uncommon", mana: 34, cooldown: 100, summary: "Full body-bind paralysis.", features: ["Extreme slowness", "Suppresses jumping", "Applies extreme weakness"], detail: "A harsher lock-down option than Stupefy." },
    { id: "aqua-eructo", name: "Aqua Eructo", icon: "AQ", school: "Elemental", rarity: "uncommon", mana: 15, cooldown: 40, summary: "A water blast that extinguishes targets and punishes fire mobs.", features: ["Extinguishes burning targets", "Pushes targets back", "Deals bonus damage to Blaze and Magma Cube"], detail: "This spell has one of the nicest impact treatments in the projectile code." },
    { id: "obliviate", name: "Obliviate", icon: "OB", school: "Mind Magic", rarity: "rare", mana: 45, cooldown: 100, summary: "Wipes aggression and heavily confuses victims.", features: ["Clears target aggro", "Applies blindness", "Applies confusion"], detail: "More about resetting encounters than killing things outright." },
    { id: "ascendio", name: "Ascendio", icon: "AS", school: "Mobility", rarity: "common", mana: 15, cooldown: 60, summary: "Launches the target and nearby entities into the air.", features: ["Strong upward launch", "Area pop-up effect"], detail: "Useful both for traversal moments and for battlefield disruption." },
    { id: "confundo", name: "Confundo", icon: "CO", school: "Mind Magic", rarity: "uncommon", mana: 30, cooldown: 80, summary: "Makes targets confused and unreliable.", features: ["Confusion", "Slowness", "May force mobs to attack random nearby entities"], detail: "The destabilizing cousin to Imperio." },
    { id: "cave-inimicum", name: "Cave Inimicum", icon: "CI", school: "Ward Magic", rarity: "rare", mana: 45, cooldown: 200, summary: "Prepares a ward for illusion concealment.", features: ["Anchors hidden-space magic to Ward blocks", "Uses snapshot-based outside illusion"], detail: "This spell is the front door to one of the mod's deepest building systems." },
    { id: "alatus", name: "Alatus", icon: "AT", school: "Craft Magic", rarity: "uncommon", mana: 18, cooldown: 55, summary: "Infuses a loaded Broom Workbench with flight magic.", features: ["Not a normal combat spell", "Used to complete broom crafting", "Requires a valid recipe already present"], detail: "A production spell rather than a battlefield spell." },
    { id: "serpensortia", name: "Serpensortia", icon: "SP", school: "Summoning", rarity: "rare", mana: 45, cooldown: 200, summary: "Currently summons allied cave spiders as magical attackers.", features: ["Summons multiple allied attackers", "Uses allied targeting rules", "Not yet a custom snake summon"], detail: "The fantasy name overshoots the current implementation, but it still adds ally-summon gameplay." },
    { id: "arresto-momentum", name: "Arresto Momentum", icon: "AM", school: "Utility Defense", rarity: "uncommon", mana: 14, cooldown: 50, summary: "Catches falls and bleeds away momentum.", features: ["Applies slow falling", "Damps movement speed", "Beam can home toward falling targets"], detail: "One of the smartest utility spells in the projectile layer because it tracks a falling victim." },
    { id: "reparo", name: "Reparo", icon: "RP", school: "Utility", rarity: "uncommon", mana: 17, cooldown: 80, summary: "Repairs nearby recently broken blocks by spending matching items and extra mana.", features: ["Repairs recent tracked simple blocks", "Consumes matching block items", "Uses magical flying-item restoration visuals"], detail: "Handled by a dedicated repair manager that tracks recent destruction and animates the rebuild." }
  ],
  mobs: [
    { id: "death-eater", name: "Death Eater", icon: "DE", role: "Hostile Spellcaster", threat: "high", habitat: "Raids, hostile events", summary: "Dark wizard enemy with spells, shields, raids, and broom flight.", features: ["Uses spell AI", "Can cast Protego and offensive spells", "Can fly with Tenebris Flight", "Some can lead raids and summon more wizards"], detail: "The mod's signature humanoid enemy and the backbone of dark-wizard encounters." },
    { id: "hogwarts-student", name: "Hogwarts Student", icon: "HS", role: "Friendly NPC", threat: "ally", habitat: "Village and school-flavored spaces", summary: "Friendly student caster that sleeps, fights dark enemies, and uses utility magic.", features: ["Student spellcasting AI", "Sleep behavior", "Can self-shield", "Supports flight state"], detail: "Students make the world feel inhabited by other young magic users rather than only monsters and bosses." },
    { id: "lord-voldemort", name: "Lord Voldemort", icon: "LV", role: "Boss", threat: "boss", habitat: "Summoning encounters", summary: "High-end dark wizard boss with queued spell windups and special summoning.", features: ["Boss-tier spell logic", "Defensive buffs", "Drops the Elder Wand", "Connected to a ritual summoning sequence"], detail: "Built as a dramatic set-piece enemy rather than a standard scaled-up wizard." },
    { id: "dementor", name: "Dementor", icon: "DM", role: "Flying Horror", threat: "high", habitat: "Night skies and dark events", summary: "Soul-sucking undead hunter with aura debuffs and special Patronus fear.", features: ["Cold dread aura slows nearby victims", "Custom soul-suck attack", "Pulls victims inward", "Hard-counters against Patronuses"], detail: "The most oppressive ambient hunter in the mod, especially before the player can field Patronus magic." },
    { id: "patronus", name: "Patronus", icon: "PA", role: "Summoned Guardian", threat: "ally", habitat: "Summoned by Expecto Patronum", summary: "Anti-Dementor magical defender that lights the area and pushes evil back.", features: ["Actively hunts Dementors", "Deals expanding aura damage", "Creates temporary magical light blocks", "Pushes Dementors outward in pulses"], detail: "A specialist guardian designed with one clear nemesis in mind." },
    { id: "basilisk", name: "Basilisk", icon: "BA", role: "Boss Monster", threat: "boss", habitat: "Major danger encounter", summary: "Massive serpent whose full gaze turns players to stone and creatures into statues.", features: ["Gaze charges while you meet its eyes", "Full gaze turns a player to stone: wait for a Mandrake Draught or give in", "Other creatures become statues that a Mandrake Draught brings back; bosses are excluded"], detail: "A terrifying pressure monster that punishes even being close to it. A Mandrake Restorative Draught frees stone players and brings statues back." },
    { id: "acromantula", name: "Acromantula", icon: "AR", role: "Monster", threat: "high", habitat: "Dark forest zones", summary: "Giant spider predator tied to dark forest content.", features: ["Leaps at targets", "Uses spider melee logic", "Naturally aggressive toward players"], detail: "The classic forest ambush threat with heavy silhouette and aggressive melee pressure." },
    { id: "hippogriff", name: "Hippogriff", icon: "HI", role: "Tamable Mount", threat: "neutral", habitat: "Wild magical creature biomes", summary: "Tameable, breedable magical beast and mount.", features: ["Rabbit-fed taming and breeding path", "Can sit on command", "Can be ridden", "Uses custom flying movement"], detail: "One of the mod's biggest creature fantasy wins because it acts as both companion and transport." },
    { id: "owl", name: "Owl", icon: "OW", role: "Tamable Courier", threat: "ally", habitat: "Wilderness and player logistics routes", summary: "Flying companion that handles magical post routes.", features: ["Can sit and follow owner", "Delivers items between perches", "Can directly deliver to players", "Works with Owl Route Tags"], detail: "The owl is more than a pet; it is a real logistics unit in the world." },
    { id: "centaur", name: "Centaur", icon: "CE", role: "Forest Threat", threat: "mid", habitat: "Forbidden Forest progression", summary: "Hostile magical forest creature registered in code as Minator.", features: ["Large aggressive wilderness mob", "Part of deeper forest progression"], detail: "The code name differs from the fantasy identity, but it clearly serves the centaur combat role." },
    { id: "lycanthrope-villager", name: "Lycanthrope Villager", icon: "LY", role: "Transformation NPC", threat: "high", habitat: "Rare village-related events", summary: "Villager that morphs into a werewolf form with different AI packages.", features: ["Automatic human and wolf form switching", "Separate human and werewolf goals", "Transformation animation support", "Aggressive wolf-form behavior"], detail: "One of the most technically interesting mobs because it swaps identity, AI, and animation state." },
    { id: "broom-entity", name: "Broom Entity", icon: "BR", role: "Vehicle", threat: "neutral", habitat: "Player and wizard flight", summary: "Rideable flying mount used by both players and wizard AI.", features: ["Three broom tiers", "Boost handling", "Fall-damage prevention while ridden", "Wizard aerial combat movement"], detail: "The broom deserves bestiary space because its movement behavior is a major gameplay system on its own." },
    { id: "fiendfyre", name: "Fiendfyre", icon: "FY", role: "Cursed Fire Entity", threat: "boss", habitat: "Created by spell casting", summary: "Main cursed-fire spreading controller.", features: ["Grows over time", "Periodically spreads fire", "Limited lifetime"], detail: "A temporary entity that acts more like an environmental disaster than a normal mob." },
    { id: "fiendfyre-serpent", name: "Fiendfyre Serpent", icon: "FS", role: "Guided Summon", threat: "high", habitat: "Created by spell casting", summary: "Guided cursed-fire serpent that steers toward the caster aim.", features: ["Steers toward aim", "Burns targets on contact", "Produces dramatic burst particles"], detail: "The showpiece cursed-fire manifestation with visible steering and burst motion." },
    { id: "dark-mark", name: "Dark Mark", icon: "DK", role: "Atmospheric Entity", threat: "high", habitat: "Sky raid and dark-cast events", summary: "Sky mark that drives dramatic ambience and empowers dark wizards.", features: ["Long-lived visual entity", "Atmosphere and weather drama", "Buffs nearby hostile wizards"], detail: "This entity exists mainly to reshape the feeling of the entire scene." },
    { id: "horcrux-soul", name: "Horcrux Soul", icon: "HX", role: "Ritual Entity", threat: "ritual", habitat: "Horcrux death and rebirth sequences", summary: "Soul apparition used during Horcrux rebirth sequences.", features: ["Travels toward a ritual target", "Carries display gear state", "Triggers ritual arrival logic"], detail: "A pure ritual delivery entity that exists to sell the Horcrux fantasy." }
  ],
  blocks: [
    { name: "Wandmaker's Bench", kind: "Workbench", summary: "Custom station for advanced wand crafting.", features: ["Dedicated GUI", "Persistent block entity", "Crafts real core-based wands"] },
    { name: "Broom Workbench", kind: "Workbench", summary: "Persistent broomcraft station completed by Alatus.", features: ["Stores ingredients persistently", "Requires Alatus to finish crafting", "Supports multiple broom tiers"] },
    { name: "Ward", kind: "Magic Block", summary: "Anchor block for Cave Inimicum illusions and Protego Maxima barriers.", features: ["Configurable size", "Outline preview", "Can activate prepared modes", "Barrier visuals when shielded"] },
    { name: "Horcrux", kind: "Ritual Block", summary: "Forbidden altar for dark soul-binding rituals.", features: ["Primed and active states", "Status messages", "Can punish hostile destruction by spawning a Dementor"] },
    { name: "Floo Fireplace", kind: "Travel Block", summary: "Named magical hearth used for Floo travel.", features: ["Stores links and names", "Activates from dropped Floo Powder", "Opens travel selection UI"] },
    { name: "Owl Perch", kind: "Logistics Block", summary: "Single-item perch for owl post routing.", features: ["Named station", "Links to another perch", "Dispatches valid nearby tame owls"] },
    { name: "Moving Portrait", kind: "Decor", summary: "Living magical portrait block.", features: ["Directional placement", "Ambient magical particles"] },
    { name: "Candle Cluster", kind: "Decor", summary: "Magical candle decoration.", features: ["Ambient flame particles"] },
    { name: "Enchanted Book Pile", kind: "Decor", summary: "Stack of enchanted books with ambience.", features: ["Ambient magical particles"] },
    { name: "Wizard Cauldron", kind: "Brewing", summary: "Brewing cauldron: fill it with water, set it over heat and add ingredients in order.", features: ["Wiggenweld, Pepperup and Polyjuice recipes", "The liquid colour shows every step", "A finished brew gives 3 doses", "Ingredients need a full cauldron (3 doses of water).", "Keep your other hand empty when you sneak-stir."] },
    { name: "Sneakoscope", kind: "Magic Block", summary: "Small glass spinning top on a brass plate that spins and whistles when dark wizards, Dementors or the Dark Mark are near.", features: ["Carried: whistles to the holder, faster as danger comes closer", "Placed: redstone signal 1-15 on every side, light while it spins", "Death Eaters, Voldemort and Dementors within 16 blocks, the Dark Mark within 48", "Corrupted or Lost players within 16 blocks, even cloaked or disguised", "Crafted, or bought from the Curio Dealer for 14 emeralds"] },
    { name: "Gillyweed Plant", kind: "Worldgen Plant", summary: "Custom underwater plant used in swamp generation.", features: ["Grows underwater", "Supports muddy or rooty wet terrain"] },
    { name: "Fine Wood Tree Set", kind: "Building Set", summary: "Custom magical wood set used for wandcraft and building.", features: ["Log, stripped log, leaves, sapling, and planks", "Main wandcraft wood source"] },
    { name: "Arcane Cluster", kind: "Resource Block", summary: "Glowing magical crystal cluster used in progression.", features: ["Light-emitting crystal block", "Feeds magical crafting progression"] },
    { name: "House Banner Blocks", kind: "Decor", summary: "Decorative house-aligned banner blocks.", features: ["Gryffindor, Ravenclaw, Hufflepuff, Slytherin variants"] },
    { name: "Patronus Light Block", kind: "Utility Block", summary: "Invisible internal light block used by Patronus effects.", features: ["No collision", "No standard loot", "Created and cleaned up by Patronus logic"] }
  ],
  items: [
    { name: "Training Broom", kind: "Mount Item", summary: "Spawns the basic broom entity.", features: ["Slowest broom tier", "Rideable flying mount"] },
    { name: "Nimbus 2000", kind: "Mount Item", summary: "Mid-tier broom with improved flight performance.", features: ["Better speed and chase handling", "Faster than Training Broom"] },
    { name: "Firebolt", kind: "Mount Item", summary: "Top-tier broom with the best overall movement stats.", features: ["Fastest broom", "Best cruise and boost speeds"] },
    { name: "Spellbooks", kind: "Learning Item", summary: "Consumable books that permanently unlock spells.", features: ["One per spell", "Can appear in chest loot", "Saved to player learned-spell data"] },
    { name: "Sorting Hat", kind: "Armor and System Item", summary: "Wearable hat that begins the Hogwarts interview.", features: ["Starts sorting process", "Applies house progression path"] },
    { name: "House Robes", kind: "Armor", summary: "Themed robe chestpieces for the four houses.", features: ["Gryffindor, Ravenclaw, Hufflepuff, Slytherin variants"] },
    { name: "Sample Collector", kind: "Potion Utility", summary: "Collects a Polyjuice identity sample from a living target.", features: ["Transforms into a filled sample item", "Works on mobs and players"] },
    { name: "Polyjuice Sample", kind: "Potion Utility", summary: "Stores a sampled target identity.", features: ["Carries target appearance data"] },
    { name: "Polyjuice Potion", kind: "Potion", summary: "Drinkable disguise potion with delayed transformation.", features: ["Brewed in the Wizard Cauldron with a filled sample", "Redstone extends it, gunpowder makes it splash"] },
    { name: "Wizard Potion", kind: "Potion", summary: "Mana regeneration potion from the brewing stand.", features: ["Made from Awkward Potion plus Acromantula Venom", "Improves mana regeneration"] },
    { name: "Gillyweed", kind: "Consumable", summary: "Edible magical plant that grants underwater adaptation.", features: ["Underwater utility effect", "Themed as gills and aquatic movement"] },
    { name: "Floo Binding Token", kind: "Travel Utility", summary: "Links two Floo Fireplaces and helps with naming.", features: ["Stores first fireplace", "Completes link with second fireplace"] },
    { name: "Floo Powder", kind: "Travel Fuel", summary: "Required fuel for Floo travel.", features: ["Dropped onto a Floo Fireplace to trigger travel"] },
    { name: "Owl Route Tag", kind: "Logistics Utility", summary: "Links two owl perches and can be assigned to a tamed owl.", features: ["Stores both perch endpoints", "Checks route validity before assignment"] },
    { name: "Cave Inimicum Totem", kind: "Ward Utility", summary: "Binds to an active ward and reveals truth inside it.", features: ["Stores bound ward position", "Lets carriers see through the illusion from outside"] },
    { name: "Hogwarts Invite", kind: "Flavor and UI Item", summary: "Acceptance letter delivered by owl and opened in a custom screen.", features: ["Custom reading screen", "Spawned for first-year players"] },
    { name: "Wand Materials", kind: "Crafting", summary: "Core wandcraft ingredients including Fine Wood Stick, Arcane Dust, Arcane Core, and magical cores.", features: ["Used in advanced wand crafting", "Supports phoenix, unicorn, and dragon wand lines"] },
    { name: "Creature Ingredients", kind: "Crafting", summary: "Drops and magical materials from special creatures.", features: ["Werewolf Fang", "Centaur Talisman", "Acromantula Venom"] },
    { name: "Relics", kind: "Progression", summary: "Magic-enhancing equipment for capacity and regeneration slots.", features: ["Includes Minor Mana Charm, Refined Mana Charm, Arcane Focus, Ravenclaw Diadem Fragment, Enchanted Inkwell, Moonlit Phial"] }
  ]
};

WIKI_DATA.spellMap = Object.fromEntries(WIKI_DATA.spells.map((entry) => [entry.id, entry]));
WIKI_DATA.mobMap = Object.fromEntries(WIKI_DATA.mobs.map((entry) => [entry.id, entry]));
