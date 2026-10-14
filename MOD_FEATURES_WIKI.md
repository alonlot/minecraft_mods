# Harry Potter Mod Wiki

This document lists the implemented gameplay systems, spells, mobs, blocks, items, potions, world content, and progression features currently present in the mod source.

It is based on the actual code and registries in the project, not just textures or placeholder assets.

## Core Gameplay Systems

- Magic and mana system
  Players have mana, mana regeneration, spell learning, spell selection, spell cooldowns, and wand-based casting.

- Spell learning
  Spells are learned from spellbooks. Once learned, they are saved to the player and can be assigned to wand slots.

- Wand interface
  Wands support a selected spell, a full spell menu, and a 9-slot radial spell wheel for quick casting.

- Wand specializations
  Different wand types change mana cost, cooldown speed, spell power, and beam speed.

- Broom flight
  Brooms are real rideable entities with smooth movement, boosting, and performance differences by tier.

- House sorting
  The Sorting Hat runs a short Hogwarts interview and assigns a house with gameplay perks.

- Relic system
  Players can equip one capacity relic and one regeneration relic to improve magical stats.

- Owl post
  Owls can deliver items between linked owl perches or directly to players.

- Floo network
  Linked Floo Fireplaces support magical fast travel using Floo Powder.

- Cave Inimicum illusion wards
  A ward can save a cube-shaped snapshot of the world and show outsiders that illusion while insiders still see reality.

- Horcrux ritual system
  Avada Kedavra kills near a Horcrux altar can start a soul-binding ritual with long-term consequences.

- Polyjuice disguises
  Players can collect identity samples, brew Polyjuice, transform after a short delay, and maintain disguises for the potion duration.

- Dynamic world events
  The mod includes Death Eater raids, insomnia-spawned Dementors, first-year invitation owl delivery, and structure-region themed spawning.

## Wand Types

### Student Wand

- Starter wand.
- Slightly higher mana costs.
- Slightly slower cooldown rhythm.
- Lower spell power.
- Slightly slower beams.

### Phoenix Core Wand

- Fast beam speed.
- Faster spell rhythm.
- Balanced mana use.
- Slightly better spell power than the starter wand.

### Unicorn Core Wand

- Lower mana costs.
- Near-normal cooldowns.
- Good efficiency.
- Has a small chance to refund part of spent mana after a cast.

### Dragon Core Wand

- Higher spell power.
- Slower beam speed.
- Slightly slower recovery.
- Higher mana use.

### Elder Wand

- Best overall power profile.
- Reduced mana cost.
- Faster cooldowns.
- Higher spell power.
- Faster beams.
- Automatically wins beam clashes against non-Elder wand beams.

## Spells

Each spell below lists the implemented gameplay behavior currently present in the code.

### Expelliarmus

- Disarms the target's main-hand item.
- Drops the stolen item into the world.
- Applies knockback.
- Applies weakness.

### Glacius

- Strongly slows the target.
- Freezes the target with frozen ticks.
- Becomes stronger in cold biomes.
- Combos with fire-based effects to create steam blindness.

### Confringo

- Ignites the target.
- Creates an explosion at the impact point.
- Explosion strength changes slightly in rain.
- Breaks down freeze effects and can trigger a steam combo.

### Protego

- Grants a custom shield effect.
- Reduces incoming damage.
- Produces visible protective magic visuals.
- Can interact with other shield logic in combat.

### Protego Maxima

- Can be channeled skyward as a sustained protective barrier.
- Slowly drains mana while maintained.
- Supports a large bubble/barrier style defense.
- Can activate a ward’s Protego Maxima barrier mode.

### Lumos

- Grants a magical light effect.
- Places a moving invisible light source above the caster.
- Cleans up old light blocks when the effect moves or ends.

### Accio

- Pulls targets toward the caster.
- Adds vertical lift to make the pull feel more magical.

### Wingardium Leviosa

- Applies levitation to the target.

### Alohomora

- Opens doors.
- Opens trapdoors.
- Opens fence gates.
- Functions as an unlocking spell for common interactable block barriers.

### Incendio

- Ignites the target.
- Can light blocks through beam/block impact handling.
- Is weaker in rain.
- Cancels or weakens freeze states and can trigger steam combos.

### Stupefy

- Applies heavy slowness.
- Applies blindness.
- Functions as a stun-like disabling spell.

### Revelio

- Reveals invisible mobs.
- Applies glowing to revealed invisible targets.
- Highlights nearby ores through walls.
- Highlights nearby chests through walls.
- Places temporary reveal lights near found secrets.
- Temporarily exposes Cave Inimicum wards.

### Avada Kedavra

- Instantly kills the target.
- Bypasses normal shield-style defenses.
- Harms the caster as dark-magic backlash.
- Can prime a Horcrux ritual if conditions are met.

### Crucio

- Applies wither.
- Applies slowness.
- Applies weakness.
- Acts as a sustained torture/debilitation curse.

### Imperio

- Marks a recently hit target for control logic.
- Can turn mobs into temporary allies.
- Can make mobs attack other hostile mobs.
- Clears their hostility toward the caster.
- Applies confusion.
- Applies damage boost.
- Applies glowing so controlled allies are visible.
- Adds an allied tag used by target logic.
- Has hold/channel handling in the event system.

### Fiendfyre

- Ignites the target.
- Deals strong fire damage.
- Spawns a Fiendfyre management entity.
- Supports sustained cursed-fire behavior.
- Interacts with a hold/channel system.

### Sectumsempra

- Deals heavy direct magic damage.
- Applies strong wither as a bleeding-like effect.

### Morsmordre

- Summons the Dark Mark.
- Applies blindness on hit.
- If cast high enough into open sky, it can trigger a full Death Eater sky raid.
- Raid trigger includes lightning, a large Dark Mark, and spawned hostile wizards.

### Dolohov's Curse

- Deals strong magic damage.
- Applies a lingering curse effect using wither.

### Incarcerous

- Heavily roots the main target.
- Applies custom chain marker effects.
- Also binds nearby living targets in an area.
- Applies weakness and slowness to nearby chained victims.
- Schedules chain animations for both main and nearby targets.

### Heal / Episkey

- Functions as a basic healing charm.
- Uses healing-themed particles and sounds.
- Used by both players and some NPC casters.

### Tenebris Flight

- Applies a dark-flight effect.
- Enables black-smoke flight behavior.
- Used by players, Death Eaters, Voldemort, and summoned attackers.
- Supports aggressive aerial ram behavior in hostile AI.

### Expecto Patronum

- Held rather than tapped: the charm lasts only while the cast key is down.
- Some casts produce a corporeal Patronus, which hunts Dementors, burns them in an expanding aura, and lights the area. This is a one-shot summon and pays the full cooldown.
- The chance of a corporeal Patronus grows with the caster's Expecto Patronum rank: 15% at Novice, 30% at Adept, 50% at Expert, 75% at Master.
- At Novice and Adept the corporeal Patronus is a generic silver stag. From Expert it takes the caster's own form, one of eight: Stag, Doe, Hare, Wolf, Cat, Owl, Fox or Phoenix. The form is chosen once from the player's UUID and house (each house has a pool of four; an unsorted player can get any of the eight), is announced in chat, and never changes after that, even after a house change or death.
- The other casts produce a non-corporeal shield: a broad fan of silver light thrown forward 20 blocks at a 25-degree half-angle — a mouth roughly 18 blocks across — tracking where you look.
- The cone is real rendered geometry, not particles — nested additively-blended shells that accumulate into a bright core with a soft volumetric edge. Server and client read the same cone constants, so what you see is exactly what pushes.
- The shield shoves Dementors back out of the cone and smothers their soul-suck, but deals no damage — killing them stays the stag's job.
- Dementors cannot close inside 3 blocks of you while they are in the cone, and their aura of dread cannot reach you while you hold it.
- Drains mana continuously, and drains faster for every Dementor it is currently holding off. Running dry collapses the shield.
- Because it is the weaker half of the charm and already mana-gated, the shield's cooldown is a quarter of the full Patronus cooldown.

### Bombarda

- Creates a powerful explosion.
- Deals explosion damage.
- Does not focus on terrain destruction the way raw TNT-style effects do.
- Can trigger freeze-to-steam interactions.

### Depulso

- Forcefully blasts targets away.
- Stronger push than Accio.
- Produces gust-like impact visuals.

### Levicorpus

- Applies levitation.
- Applies a custom Levicorpus marker effect.
- Applies weakness.
- Uses client render logic to hang the victim upside down.

### Diffindo

- Deals high direct damage.
- Functions as a sharp severing curse.

### Petrificus Totalus

- Applies extreme slowness.
- Removes useful jumping.
- Applies extreme weakness.
- Fully immobilizes the target in practice.

### Aqua Eructo

- Extinguishes burning targets.
- Pushes targets back with water force.
- Deals extra damage to fire mobs like Blaze and Magma Cube.
- Gains a little extra knockback in rainy conditions.
- Creates water-splash and bubble visuals.
- Supports a cone-like water-wave impact effect.

### Obliviate

- Clears a mob’s current target.
- Applies blindness.
- Applies confusion.
- Functions as memory wipe / aggro reset magic.

### Ascendio

- Launches the target upward.
- Also launches nearby living entities upward in an area.
- Prevents immediate fall punishment through movement handling.

### Confundo

- Applies confusion.
- Applies slowness.
- Can cause mobs to attack random nearby entities.

### Cave Inimicum

- Prepares a ward for illusion mode.
- Anchors a hidden-space effect to a Ward block.
- Works with snapshots so outsiders see the fake version and insiders see reality.

### Alatus

- Does not behave like a normal combat spell.
- Used to infuse a Broom Workbench.
- Requires a valid broom recipe to already be loaded on the bench.
- Starts the magical flight infusion process.

### Serpensortia

- Currently summons allied cave spiders rather than a custom snake mob.
- Summons multiple allied attackers around the target zone.
- Uses allied tags so they work with allied-target rules.

### Arresto Momentum

- Applies slow falling.
- Damps movement speed and fall speed.
- Can be self-cast to survive falls.
- Can be fired at others to catch or slow falling targets.
- The beam can home toward nearby falling targets.

### Reparo

- Repairs nearby recently broken simple blocks.
- Only works on tracked recent block breaks.
- Requires matching block items from the player inventory.
- Costs additional mana for each repaired block.
- Sends the consumed block item flying magically back into place before restoring the block.
- Does not repair block-entity blocks or impossible states.

## Hogwarts Assignments

- A Hogwarts Assignment is a scroll that turns into a spell's book when you finish its task.
- Assignments are found in exactly the same chests as spell books. When a chest rolls a spell book, there is a 60% chance (config `assignmentShareOfBooks`) that it holds that same spell's assignment instead. So an assignment has the same rarity and the same chests as its book.
- The task size follows the spell's rarity: Common 1 to 2 lines, Uncommon 2, Rare 3, Epic 3 to 4.
- Right-click to accept. This binds the scroll to you; nobody else can use it. You can have at most 3 assignments in progress.
- Right-click again to read it on a parchment screen with a progress bar for each line. Progress is shown on the action bar as you go, and chat tells you when it is complete.
- Right-click a completed scroll to claim the book. Items that a line asks you to bring are taken at that moment.
- Sneak-right-click twice to give up an assignment. The scroll is destroyed and its progress is lost.
- You cannot accept an assignment for a spell you already know. Progress is saved on the player, not on the scroll, and survives death.
- Dark spells come as **Dark Orders** (black parchment, green seal) from the Half-Blood Prince or a Death Eater, never from a Hogwarts teacher.
- No task needs the spell it teaches. Some need another spell first (for example, Stupefy needs Expelliarmus), and that spell is never rarer.
- Assignments are data files (`data/hogwartsmagic/assignments/<spell>.json`), so a datapack can change or replace them.

### Common

| Spell | Teacher | Task |
|---|---|---|
| Accio | Prof. F. Flitwick, Charms | Fish up 5 catches |
| Alohomora | Prof. F. Flitwick, Charms | Open 3 chests nobody has opened before (villages, ruins, temples) |
| Ascendio | Madam R. Hooch, Flying | Stand above Y 160 for 10 seconds |
| Depulso | Prof. R. J. Lupin, Defence Against the Dark Arts | Knock 3 monsters to their death by a fall |
| Diffindo | Prof. M. McGonagall, Transfiguration | Cut 32 leaf blocks<br>Cut 4 cobwebs |
| Lumos | Prof. F. Flitwick, Charms | Light 12 dark places with torches, where no sky light reaches<br>Spend 60 seconds deep underground, below Y 0 |

### Uncommon

| Spell | Teacher | Task |
|---|---|---|
| Alatus | Madam R. Hooch, Flying | Cut 16 Fine Wood logs<br>Build 1 Broom Workbench |
| Aqua Eructo | Madam P. Pomfrey, Hospital Wing | Defeat 4 Blazes or Magma Cubes<br>Swim for 30 seconds |
| Arresto Momentum | Madam R. Hooch, Flying | Stack 4 hay bales to land on<br>Survive 3 falls of 25 blocks or more (a hay bale helps) |
| Colloportus | A. Filch, Caretaker | Hang 4 iron doors<br>Wire 4 levers or buttons |
| Confringo | Prof. A. Moody, Defence Against the Dark Arts | Know Incendio<br>Defeat 10 monsters while they burn |
| Confundo | Prof. M. McGonagall, Transfiguration | Watch 5 creatures kill another creature (a skeleton that shoots a zombie counts)<br>Defeat 2 witches, who brew the potions that muddle minds |
| Expelliarmus | Prof. R. J. Lupin, Defence Against the Dark Arts | Defeat 5 monsters that carry a weapon<br>Win 1 duel against a Hogwarts Student or a Death Eater |
| Glacius | Prof. F. Flitwick, Charms | Mine 24 blocks of ice<br>Bring 16 snowballs |
| Episkey | Madam P. Pomfrey, Hospital Wing | Brew 3 Potions of Healing<br>Breed 4 animals |
| Incarcerous | Prof. A. Moody, Defence Against the Dark Arts | Defeat 10 spiders<br>Bring 16 string |
| Incendio | Prof. F. Flitwick, Charms | Smelt 32 items in a furnace<br>Build 3 campfires |
| Levicorpus (Dark Orders) | The Half-Blood Prince | Cast Wingardium Leviosa 10 times<br>Defeat 15 monsters |
| Petrificus Totalus | Prof. R. J. Lupin, Defence Against the Dark Arts | Know Stupefy<br>Hit 15 creatures with Stupefy |
| Protego | Prof. R. J. Lupin, Defence Against the Dark Arts | Block 20 attacks with a shield<br>Defeat 5 skeletons |
| Reparo | Prof. F. Flitwick, Charms | Build 1 anvil<br>Repair 5 items at an anvil |
| Revelio | Prof. A. Moody, Defence Against the Dark Arts | Open 1 hidden chest: buried treasure or a monster dungeon<br>Mine 1 diamond ore |
| Stupefy | Prof. R. J. Lupin, Defence Against the Dark Arts | Know Expelliarmus<br>Win 2 duels |
| Wingardium Leviosa | Prof. F. Flitwick, Charms | Bring 16 feathers<br>Cast Lumos 20 times to practise the wand movement |

### Rare

| Spell | Teacher | Task |
|---|---|---|
| Bombarda | Prof. A. Moody, Defence Against the Dark Arts | Defeat 5 monsters with an explosion<br>Defeat 3 Death Eaters<br>Mine 8 obsidian |
| Cave Inimicum | Prof. F. Flitwick, Charms | Make 1 Ward<br>Spend 600 seconds outdoors at night<br>Defeat 20 monsters |
| Dolohov's Curse (Dark Orders) | A Death Eater | Defeat 3 Iron Golems<br>Win 2 duels<br>Defeat 3 witches who would not serve |
| Obliviate | Prof. G. Lockhart, Order of Merlin, Third Class | Bring 3 signed books (your memoirs)<br>Defeat 1 Troll (and tell everyone about it)<br>Win 3 duels |
| Piertotum Locomotor | Prof. M. McGonagall, Transfiguration | Know Reparo<br>Defeat 1 Living Statue<br>Make 4 armour stands |
| Protego Maxima | Prof. F. Flitwick, Charms | Know Protego<br>Defend a village from a raid and become its Hero<br>Block 30 attacks with a shield |
| Sectumsempra (Dark Orders) | The Half-Blood Prince | Know Levicorpus<br>Brew 5 different potions<br>Hit 15 creatures with Diffindo |
| Serpensortia (Dark Orders) | A Death Eater | Defeat 10 cave spiders<br>Defeat 2 Acromantulas<br>Bring 2 Acromantula Venom |

### Epic

| Spell | Teacher | Task |
|---|---|---|
| Apparition | W. Twycross, Ministry Apparition Instructor | Travel 5 times by the Floo Network<br>Visit a Black Lake ruin<br>Visit a Whomping Willow clearing<br>Visit a merfolk village |
| Avada Kedavra (Dark Orders) | A Death Eater | Know Crucio<br>Enter the Chamber of Secrets<br>Defeat 1 Basilisk |
| Crucio (Dark Orders) | A Death Eater | Know Imperio<br>Defeat 1 lycanthrope<br>Defeat 5 Iron Golems |
| Expecto Patronum | Prof. R. J. Lupin, Defence Against the Dark Arts | Know Protego<br>Survive 3 Dementor attacks<br>Defeat 3 Dementors<br>Eat 5 cookies afterwards. Chocolate. It really helps. |
| Fiendfyre (Dark Orders) | A Death Eater | Know Confringo<br>Defeat 1 Hungarian Horntail<br>Defeat 25 monsters while they burn |
| Imperio (Dark Orders) | A Death Eater | Know Confundo<br>Defeat 20 spiders<br>Defeat 1 Acromantula |
| Morsmordre (Dark Orders) | A Death Eater | Find a Death Eater camp<br>Bring 3 Cursed Galleons<br>Defeat 5 Iron Golems |
| Tenebris Flight (Dark Orders) | A Death Eater | Defeat 5 Phantoms<br>Stand above Y 200 for 30 seconds<br>Defeat 2 Ghasts |

## Mobs and Major Entities

### Whomping Willow

- Huge rooted hostile tree. Never moves; fights everything that comes within reach of its four branches.
- Dormant until approached or struck, then sweeps, slams, stomps its roots, grabs, and swats broom riders out of the air.
- Press the knot at its base, or feed a redstone signal to its roots, to hold it still.
- Rare in dark and old-growth forests; one stands guaranteed in a `willow_clearing`.
- Drops a sapling. Plant it, let it mature, then raise it by hand - a willow you raise ignores you and attacks everyone else.


### Death Eater

- Hostile spellcasting humanoid.
- Can cast offensive spells.
- Can cast Protego.
- Can use Tenebris Flight.
- May ride a broom.
- Can participate in raids.
- Some can become raid leaders and summon more Death Eaters.

### Hogwarts Student

- Friendly humanoid caster.
- Sleeps using custom student bed logic.
- Attacks hostile dark-aligned enemies.
- Can use spells in combat.
- Can self-shield with Protego.
- Supports flight behavior.

### Lord Voldemort

- Boss-tier dark wizard.
- Queues large spell windups.
- Uses defensive effects.
- Uses dark mobility.
- Drops the Elder Wand.
- Tied into a special summoning sequence.

### Voldemort Summoning Entity

- Ritual-style summoning effect.
- Builds up particles and atmosphere over time.
- Spawns Voldemort.
- Can also involve supporting dark wizard presence.

### Dementor

- Flying hostile monster.
- Actively avoids Patronuses.
- Uses a custom soul-sucking attack.
- Emits a cold dread aura.
- Slows nearby non-Dementor entities.
- Can apply wither and blindness.
- Pulls victims inward during the attack.

### Patronus

- Summoned magical guardian.
- Specifically hunts Dementors.
- Damages Dementors in a growing aura.
- Pushes Dementors away in expanding rings.
- Places temporary magical light blocks.
- Is very resistant to normal harm.

### Basilisk

- Giant hostile serpent creature.
- Repeatedly scans for victims nearby.
- Petrifies nearby living targets.
- Applies petrification, slowness, and weakness.
- Can instantly kill valid victims through petrification logic.
- Creates a terrifying area-control threat.

### Acromantula

- Large spider monster.
- Leaps at targets.
- Uses melee spider attack behavior.
- Hunts players.
- Naturally tied to dark forest content.

### Hippogriff

- Tamable mount creature.
- Can be fed rabbit.
- Can be bred.
- Can sit on command.
- Can attack hostile mobs.
- Can be ridden directly.
- Uses custom flying and mounted travel behavior.

### Owl

- Tamable flying companion.
- Can sit.
- Follows its owner.
- Performs route-based perch mail delivery.
- Can directly deliver items to players.
- Supports route assignment through Owl Route Tags.

### Centaur

- Hostile forest monster.
- Registered in code as `MinatorEntity`.
- Used as a dangerous magical creature in forest progression.

### Lycanthrope Villager

- Villager that transforms into a werewolf form.
- Has separate human and wolf AI goal sets.
- Automatically changes form based on time and full-moon logic.
- Gains aggressive wolf combat behavior in werewolf state.
- Has client morph animation states for transformation and locomotion.

### Broom Entity

- Rideable vehicle entity.
- Has three broom types: training broom, Nimbus 2000, Firebolt.
- Supports boost mode.
- Prevents fall damage while ridden.
- Has different speed tiers by broom type.
- Also supports wizard AI broom flight patterns.

### Fiendfyre Entity

- Main cursed-fire spreading controller.
- Grows over time.
- Spreads fire periodically.
- Has a limited lifetime.

### Fiendfyre Serpent

- Guided cursed-fire creature/projectile.
- Steers based on caster aim.
- Burns targets on contact.
- Produces dramatic trail and burst particles.

### Dark Mark

- Large floating dark sky symbol.
- Lasts a long time.
- Builds dramatic weather and ambience.
- Periodically buffs nearby dark wizards with damage boost and regeneration.

### Horcrux Soul

- Flying soul apparition used in the Horcrux sequence.
- Carries display copies of the player’s gear.
- Travels toward its target altar.
- Triggers the rebirth/death sequence on arrival.

### Petrified Remains

- Decorative aftermath entity for petrified victims.

### Spell Beam

- Colored projectile used by most spells.
- Has spell-specific particles and impact behavior.
- Supports near-miss sound effects.
- Supports beam clashes.
- Gives Elder Wand casts priority in beam clashes.
- Contains major block-hit logic for utility spells.

## Blocks

### Wandmaker's Bench

- Custom wandcraft workstation.
- Opens a dedicated GUI.
- Uses persistent block entity data.
- Crafts advanced wands from wand blank, core, and arcane catalyst.

### Broom Workbench

- Custom broomcraft workstation.
- Opens a dedicated GUI.
- Stores ingredients persistently.
- Does not finish crafting by itself.
- Requires `Alatus` to hit the bench while a valid recipe is loaded.

### Ward

- Physical anchor for Cave Inimicum and Protego Maxima.
- Can preview its outline.
- Can cycle configurable ward size while crouching.
- Can activate prepared modes.
- Displays detailed status to players.
- Emits barrier particles when the shield mode is active.

### Horcrux

- Forbidden ritual altar block.
- Tracks owner, active state, and primed state.
- Shows particles while primed or active.
- Can be primed by ritual setup.
- Can be fully bound into an active Horcrux.
- Can be shattered.
- Hostile destruction can summon a Dementor.

### Floo Fireplace

- Magical fireplace block.
- Always kept lit server-side.
- Stores a name.
- Stores linked destination fireplaces.
- Can ignite from dropped Floo Powder.
- Opens the Floo destination selection screen.
- Has active travel visual effects while engaged.

### Owl Perch

- Named owl-mail station.
- Stores one item.
- Links to another perch.
- Automatically dispatches a valid tamed owl if one is nearby and available.
- Can receive delivery from owls.

### Moving Portrait

- Decorative portrait block.
- Directional placement.
- Uses magical ambient particles for a living-painting feel.

### Candle Cluster

- Decorative candle block.
- Emits ambient magical flame particles.

### Enchanted Book Pile

- Decorative magical book-stack block.
- Emits ambient particles.

### Wizard Cauldron

- Decorative and atmospheric cauldron block.
- Supports magical potion-class ambience.

### Gillyweed Plant

- Custom underwater plant.
- Generates in swampy underwater environments.
- Can survive on mud, roots, moss, and similar wet natural substrates.

### Fine Wood Tree Set

- Fine Wood Log.
- Stripped Fine Wood Log.
- Fine Wood Leaves.
- Fine Wood Sapling.
- Fine Wood Planks.

Uses:

- Main wandcraft wood source.
- General magical building material set.

### Arcane Cluster

- Magical glowing crystal cluster.
- Used in crafting progression.

### House Banner Blocks

- Gryffindor Banner.
- Ravenclaw Banner.
- Hufflepuff Banner.
- Slytherin Banner.

These are decorative house-themed blocks.

### Patronus Light Block

- Invisible light block.
- No collision.
- No standard loot.
- Used internally by Patronus effects.

## Items and Materials

### Spellbooks

- Each spell has a dedicated spellbook item.
- Right-clicking a spellbook teaches the spell permanently.
- Spellbooks can appear in chest loot.
- In the same chests, a spell can come as a Hogwarts Assignment instead: a task that turns into the book (see Hogwarts Assignments).

### Brooms

#### Training Broom

- Basic flying broom.
- Slowest of the three broom tiers.

#### Nimbus 2000

- Faster broom tier.
- Better cruising and boosting speed.

#### Firebolt

- Fastest broom tier.
- Best cruise, chase, and boost performance.

### Wand Materials

- Wand Blank
  Base crafted wand body used for real wandcraft.

- Arcane Dust
  Magical ingredient used in early crafting and broommaking.

- Arcane Core
  Magical catalyst used in advanced crafting.

- Phoenix Feather Core
  Wand core material for phoenix wands.

- Unicorn Hair Core
  Wand core material for unicorn wands.

- Dragon Heartstring Core
  Wand core material for dragon wands.

### Creature Ingredients

- Werewolf Fang
  Used in Wizard Potion brewing.

- Centaur Talisman
  Magical creature drop/material.

- Acromantula Venom
  Magical creature drop/material.

### Gillyweed

- Eaten as a consumable.
- Grants the Gillyweed adaptation effect.
- Gives underwater utility themed around gills and aquatic movement.

### Sample Collector

- Used on a living target.
- Produces a filled Polyjuice sample.

### Polyjuice Sample

- Stores sampled target identity.
- Can represent player or mob targets.

### Polyjuice Potion

- Drinkable transformation potion.
- Requires a valid stored sample.
- Starts a delayed transformation sequence.
- Applies a Polyjuice effect for the potion duration.
- Can be extended with redstone.

### Floo Binding Token

- Links two Floo Fireplaces.
- Also opens naming support for fireplaces.

### Floo Powder

- Travel fuel for the Floo network.
- Must be dropped on an active Floo fireplace while standing in place.

### Owl Route Tag

- Links two owl perches.
- Can then be assigned to a tamed owl.
- Validates routes before assignment.

### Cave Inimicum Totem

- Binds itself to an active ward.
- If carried, it lets the player see truth inside the hidden ward from outside.

### Hogwarts Invite

- Acceptance letter item.
- Opens a custom reading screen.
- Is automatically delivered to first-year players by owl if possible.

### Sorting Hat

- Wearable helmet item.
- Starts the Hogwarts interview process.

### House Robes

- Gryffindor Robes.
- Ravenclaw Robes.
- Hufflepuff Robes.
- Slytherin Robes.

These are chest-slot house-themed robes.

### House Reveal Items

- Gryffindor.
- Ravenclaw.
- Hufflepuff.
- Slytherin.

These appear to be primarily thematic/collectible reveal items.

## Relics

Relics are split into two slot types:

- Capacity relic
- Regeneration relic

Only one relic of each type can be equipped.

### Minor Mana Charm

- Capacity relic.
- Grants +20 max mana.

### Refined Mana Charm

- Capacity relic.
- Grants +35 max mana.

### Arcane Focus

- Regeneration relic.
- Grants +18% mana regeneration.

### Ravenclaw Diadem Fragment

- Capacity relic.
- Grants +50 max mana.
- Found-only relic.

### Enchanted Inkwell

- Regeneration relic.
- Grants +28% mana regeneration.
- Found-only relic.

### Moonlit Phial

- Regeneration relic.
- Grants +36% mana regeneration.
- Found-only relic.

## Potions and Effects

### Wizard Potion

- Brewed from Awkward Potion + Werewolf Fang.
- Applies the Wizard Elixir effect.
- Improves mana regeneration.

### Polyjuice Potion

- Brewed from Wizard Potion + filled Polyjuice Sample.
- Applies the Polyjuice effect.
- Base disguise duration is about 3 minutes.

### Extended Polyjuice Potion

- Brewed by adding Redstone to Polyjuice Potion.
- Extended disguise duration is about 8 minutes.

### Major Custom Effects

- `Lumos`
  Tracks moving magical light on the caster.

- `Protego`
  Custom shield effect.

- `Tenebris Flight`
  Enables dark-flight state.

- `Incarcerous Chains`
  Custom marker effect for binding visuals and states.

- `Levicorpus`
  Custom marker effect used for upside-down render state.

- `Polyjuice`
  Maintains disguise state.

- `Gillyweed`
  Grants underwater adaptation.

- `Wizard Elixir`
  Improves mana regeneration.

- `Basilisk Petrification`
  Marks petrified victims.

## House Perks

### Gryffindor

- +10% spell damage.
- +10% melee damage.

### Ravenclaw

- +25 max mana.
- +20% mana regeneration.

### Slytherin

- 15% shorter cooldowns.
- 20% faster spell beams.

### Hufflepuff

- Uses survivability-focused perk handling.
- Includes special healing/sustain logic in the perk system.

## World Content and Generation

### Forbidden Forest

- Custom biome.
- Main dark magical wilderness biome for several themed creatures.

### Fine Wood Trees

- Custom tree generation.
- Primary magical wood source.

### Arcane Cluster Patches

- Magical crystal generation feature.

### Gillyweed Swamps

- Swamp-oriented underwater gillyweed generation.

### Gringotts

The wizarding bank, and the only multi-level structure in the mod. Roughly one village in three gets
a branch; `/findgringotts` locates the nearest, `/buildbank` forces one.

**Above ground.** A banking hall with goblin tellers behind gold-plated counters who will trade
Galleons, and guards on the floor who ignore you unless you give them a reason. Behind the counters
is a rotunda closed off by a goblin-forged screen.

**The gate.** The screen is a real gate, not a doorway. Polyjuiced into a goblin, a bare hand swings
it open for eight seconds and nobody looks up; otherwise it is about twelve seconds a block with a
pickaxe and the first swing brings every guard in the hall. Alohomora does not work on it, which is
canon and also the hint.

**The lift.** The rotunda island is a platform, not a stair head. Stand on the plinth and it takes
you down the shaft to the cart station.

**The ride.** A cart on a generated 3D spline, not vanilla rails, which is what lets it do sheer
drops, banked spirals and switchbacks. Around 700 blocks of track descending 96 to 180 blocks
depending on what the terrain above allows - a bank whose ground cannot give 96 gets no undercroft
and stays a bank. About a minute end to end. **W** launches it, **S** brakes. The line audits itself
at generation time for headroom, floor, gradient and self-intersection, so it is rideable end to end
or it is not built.

The corridor changes shape as it goes - enclosed bore, open ledge, trestle over the void, and
galleries where something happens - and no one profile runs more than about forty blocks. The stone
is banded by depth, from the bank's own dressed masonry at the top to raw blackstone at the bottom,
and the lamps turn from iron lanterns to soul lanterns as you pass out of the bank and into the
caves. Nothing is lit below the last ledge, which is what keeps the chasm floor invisible.

**The vaults.** Seven of them, cut into the rock side of the line. A vault is a door, a throat and a
chamber, so "the door is open" and "you have the gold" are two different moments. The door opens the
way the films open one: a bare hand on the leaf, then ten seconds of touch, ripple, mechanism, bolts
and parting. Opening one as yourself wakes the bank and starts a one-minute reseal; opening one as a
goblin does not.

**Set pieces.**

- **Thief's Downfall** - a curtain across the line you cannot ride around. It strips Polyjuice, the
  invisibility cloak and every potion effect, including the Fire Resistance you were relying on. It
  does not touch item enchantments. It also destroys cursed gold, so the trap on the way in is the
  cure on the way out.
- **The Ukrainian Ironbelly** - chained in front of the deepest vault, half-blind and hunting by
  hearing rather than by sight, so you can walk past it if you walk. **Clankers** cow it for five
  seconds, and the same noise brings every goblin guard within thirty-two blocks. Break the last
  anchor stub and it leaves.
- **Gemino and Flagrante** - the deepest hoard is cursed. Standing on it multiplies it, carrying it
  sets you alight and multiplies it in your pockets, and Fire Resistance does not help because it is
  a curse rather than a fire. Drop it, get in water, or ride back through the Downfall.
- **The chasm** - a stalactite cavern the whole descent spirals through, with waterfalls that fall
  into the dark and never arrive.

**Getting caught.** The bank keeps per-player heat from 0 to 4 within its own territory - opening
vaults, forcing the gate, killing staff - which escalates from every guard in the region searching
for you to squads sent to the way out, and decays a step every ten minutes you spend outside. Six
sentries stand below ground and are hostile on sight, unlike the guards upstairs. All of them let a
goblin walk past.

### Spawn Modifiers

- Owl biome spawns.
- Hippogriff biome spawns.
- Acromantula biome spawns.
- Gillyweed biome/worldgen placement.

## Dynamic Events and Progression Features

### Death Eater Raids

- Can be started by raid leaders.
- Can also be triggered by high-sky `Morsmordre`.
- Use a boss bar.
- Reward nearby/participating players when cleared.

### First-Year Owl Delivery

- New players can receive a Hogwarts invitation by owl.

### Insomnia Dementors

- Players who go too long without rest can trigger nighttime Dementor spawns in the Overworld.

### Structure-Themed Spawning

- Students can appear around village-like spaces.
- Death Eaters can appear around hostile structure regions.
- Special villager replacement can produce lycanthrope villagers.

### Advancements

The mod includes advancement progression for:

- Starting as a wizard.
- Wandcraft.
- Fine wood gathering.
- Arcane material collection.
- Core collection.
- Spellbook discovery.
- Potion brewing.
- Gillyweed collection.
- Polyjuice brewing.
- Broom workbench setup.
- First flight.
- Nimbus upgrade.
- Firebolt upgrade.
- Owl post.
- Floo network.
- Warded refuge creation.
- Sorting Hat.
- House robes.
- Defeating Death Eaters.
- Defeating Acromantulas.
- Defeating Centaurs.
- Defeating Dementors.
- Defeating Basilisks.
- Defeating Voldemort.

## Notes and Honest Caveats

- `Serpensortia` currently summons allied cave spiders, not a custom snake mob.
- Some spell behavior is split across several systems.
  Example: `Protego Maxima`, `Reparo`, `Alatus`, `Revelio`, and `Cave Inimicum` rely on event handlers, workbench logic, or block entities in addition to the spell enum.
- Several decorative items and house-reveal items are mostly cosmetic or thematic rather than deep active mechanics.
- The Gringotts undercroft is generated procedurally rather than pasted from a template, so no two
  branches have the same descent; what is fixed is the sequence of rooms and the audit it has to
  pass. It is dug across server ticks after the bank goes up, and picked up where it left off if the
  server stops mid-dig.
- This document describes what is implemented in the current codebase, even where the fantasy presentation is bigger than the underlying technical implementation.

