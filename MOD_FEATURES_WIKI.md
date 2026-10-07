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
  Players can collect identity samples, brew Polyjuice in the Wizard Cauldron, transform after a short delay, and maintain disguises for the potion duration.

- Cauldron brewing
  Potions are brewed step by step in the Wizard Cauldron over heat: add ingredients, stir and let it simmer, in order, and watch the liquid change colour. Seven recipes: Wiggenweld Potion, Pepperup Potion, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice Potion. The Hogwarts Guide (Potions tab) lists every recipe.

- Petrification
  A basilisk's full gaze turns a player to stone instead of killing them. A stone player waits for a friend with a Mandrake Restorative Draught or gives in and respawns.

- Dark Magic
  Dark spells darken your soul: a hidden Darkness score from 0 to 100 in five tiers, from Clean to Lost. The world reacts to each tier with costs and, from Corrupted, rewards. Time and healing bring you back.

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
- Cast into open sky by a player with a Lost soul, it calls a Death Eater escort for 5 minutes instead of a raid (see Dark Magic).

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
- Healing another hurt creature lifts 1 Darkness from the caster, at most once per 30 minutes of play (see Dark Magic).

### Tenebris Flight

- Applies a dark-flight effect.
- Enables black-smoke flight behavior.
- Used by players, Death Eaters, Voldemort, and summoned attackers.
- Supports aggressive aerial ram behavior in hostile AI.

### Expecto Patronum

- Held rather than tapped: the charm lasts only while the cast key is down.
- 15% of casts produce a corporeal Patronus (7.5% with a Shadowed soul, never from Tainted; see Dark Magic) — the stag, which hunts Dementors, burns them in an expanding aura, and lights the area. This is a one-shot summon and pays the full cooldown.
- The other 85% produce a non-corporeal shield: a broad fan of silver light thrown forward 20 blocks at a 25-degree half-angle — a mouth roughly 18 blocks across — tracking where you look.
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

### Riddikulus

- The charm against a Boggart. Pink beam with note and happy villager particles.
- Each hit on a Boggart makes it smaller and sillier and stops its attack for three seconds.
- Hits to banish a Boggart by rank: Novice 3, Adept 2, Expert 2, Master 1. The last hit bursts it into laughter and smoke, with no drops.
- Each hit also clears the Boggart's darkness and slowness from every player near it.
- On any other target it does no damage. The target glows for two seconds and gets a ring of notes.
- The spellbook is uncommon chest loot and a villager trade.

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
- Its gaze charges while a victim meets its eyes.
- The full gaze turns a survival or adventure player to stone: they wait for a Mandrake Restorative Draught or give in.
- Other creatures hit by the full gaze become statues (Petrified Remains). Bosses are excluded: they die as before, keep their loot, and their statue never comes back.
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
- Periodically buffs nearby dark wizards, and players with a Corrupted or Lost soul, with damage boost and regeneration.

### Horcrux Soul

- Flying soul apparition used in the Horcrux sequence.
- Carries display copies of the player’s gear.
- Travels toward its target altar.
- Triggers the rebirth/death sequence on arrival.

### Petrified Remains

- The statue a creature leaves when the Basilisk's gaze turns it to stone (bosses excepted).
- It keeps the whole mob (name, owner, gear, villager trades) and drops nothing.
- A Mandrake Restorative Draught (right-click or splash) brings the mob back where the statue stands.
- Breaking the statue, or pushing it into the void, loses the mob for good.

### Spell Beam

- Colored projectile used by most spells.
- Has spell-specific particles and impact behavior.
- Supports near-miss sound effects.
- Supports beam clashes.
- Gives Elder Wand casts priority in beam clashes.
- Contains major block-hit logic for utility spells.

### Boggart

- A shape-shifter with no shape of its own. It spawns in dark covered places in the Forbidden Forest and the Dementor Fen, at most one per 64 blocks.
- Hidden, it stands still as a dark violet shadow and gives off a little smoke.
- When a player comes within 8 blocks with line of sight, it takes the shape of the creature that has killed that player most often. A wizard killer gives a Dementor. No recorded deaths give an Acromantula.
- Revealed, it is a slow melee monster (24 health, 2 damage). Every two seconds it pulses fear: Darkness to players within 6 blocks and Slowness to players within 3.
- Riddikulus shrinks it per hit and banishes it after enough hits (see the spell). It cannot attack while it laughs.
- With no player within 16 blocks for ten seconds it hides again, heals to full and forgets its hits.
- The spawn egg is dark violet.

## Blocks

### Wandmaker's Bench

- Custom wandcraft workstation.
- Opens a dedicated GUI.
- Uses persistent block entity data.
- Crafts advanced wands from a Fine Wood Stick, core, and arcane catalyst.

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

- Brewing cauldron. Fill it with a water bucket or three water bottles and put heat under it (a lit campfire or soul campfire, fire, soul fire, lava or a magma block).
- Right-click with ingredients in order, stir with a Fine Wood Stick (sneak for anticlockwise), and let simmer steps run on the heat. The liquid changes colour at every correct step; a wrong step ruins the brew.
- A finished brew gives 3 doses (glass bottles). Left on the heat more than 120 seconds after its last simmer, it is ruined. A ruined brew bottles as Failed Potion or is poured away with a bucket.
- Recipes: Wiggenweld Potion, Pepperup Potion, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice Potion (Hogwarts Guide, Potions). Each is described under Potions and Effects.
- Felix Felicis gives a single dose; every other brew gives 3.
- Crafting: seven iron ingots in a cauldron shape round one Arcane Dust. An Apothecary or a Cauldron Smith villager also sells one.
- Gunpowder after a finished Mandrake Restorative Draught makes every dose a splash potion. The Draught of Living Death always ends with Gunpowder: it is splash only.
- The Mini Hogwarts Potions classroom has seven of these cauldrons, full and already over heat.
- Cauldrons placed by structures start full and work like any other.
- Ingredients need a full cauldron (3 doses of water).
- Keep your other hand empty when you sneak-stir.

### Sneakoscope

- A small glass spinning top on a brass plate. It spins, glows and whistles when a Death Eater, Lord Voldemort or a Dementor is within 16 blocks, or the Dark Mark within 48 (both ranges are in the config).
- It also senses a player whose soul is Corrupted or Lost (Dark Magic) within 16 blocks, even under the Invisibility Cloak or Polyjuice. A Sneakoscope you carry never senses you (a placed one senses everyone, its owner too), and the config values `sneakoscopeDetectsPlayers` or `enableDarkMagic` turn player detection off. Creative and spectator players are never sensed.
- Carried anywhere in your inventory, it whistles to you alone, faster as the danger comes closer: every 2 seconds at the edge of the range, every half second up close.
- Placed, it is a redstone sensor: signal 1 at the edge of the range up to 15 at the source, on every side, readable by a comparator. The strongest signal from any detected source wins. It whistles once when it starts detecting and gives light while it spins.
- Crafting: a gold ingot on top, a glass pane in the middle, four gold nuggets round it and arcane dust below. The Curio Dealer sells one for 14 emeralds.

### Gillyweed Plant

- Custom underwater plant.
- Generates in swampy underwater environments.
- Can survive on mud, roots, moss, and similar wet natural substrates.

### Dittany Plant

- A small herb that grows wild in patches of two to four in the Forbidden Forest and the Horntail Peaks, on earth and on bare rock.
- Breaking a wild plant gives two Dittany; a plant you set down yourself gives back one.
- Dittany can be planted on earth or stone. It does not spread.

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

- Fine Wood Stick
  Base crafted wand body used for real wandcraft. It is also the stirring rod for the Wizard Cauldron: right-click to stir clockwise, sneak and right-click (other hand empty) to stir anticlockwise. Crafted from two stripped fine wood logs stacked (4 sticks) or three fine wood planks stacked (1 stick).

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
  Magical creature drop/material.

- Centaur Talisman
  Magical creature drop/material.

- Acromantula Venom
  Magical creature drop. Used in the Wizard Potion (brewing stand), and in Polyjuice Potion and Felix Felicis (Wizard Cauldron).

### Brewing Ingredients

- Dittany
  Picked from the Dittany plant, or bought from an apothecary (2 for 6 emeralds). Essence of Dittany.

- Asphodel
  Apothecary (second level), 1 for 8 emeralds. Mandrake Restorative Draught and Draught of Living Death.

- Wormwood
  Apothecary (second level), 2 for 6 emeralds. Draught of Living Death.

- Valerian Sprigs
  Apothecary, 2 for 4 emeralds; also in about a quarter of the Hogwarts ruin camp chests. Draught of Living Death.

- Ashwinder Egg
  Rare: 1 Death Eater camp chest in 10, about 1 Chamber of Secrets chest in 7, and 1 Blaze in 20. It does not burn. Felix Felicis.

- Mandrake Root
  Pulled from a Mandrake. Pepperup Potion, Mandrake Restorative Draught and Polyjuice Potion.

- Everyday ingredients
  Honey Bottle and Glow Berries (Wiggenweld), Blaze Powder (Pepperup), Arcane Dust (Mandrake Draught), Gold Nugget (Felix Felicis), Gillyweed (Polyjuice), and Redstone Dust and Gunpowder to extend Polyjuice or make a splash potion.

- Where to buy
  Apothecaries sell Dittany and Valerian Sprigs from their first level and Asphodel and Wormwood from their second. Each apothecary stocks only some of them, so try another shop. The Potions classroom in Mini Hogwarts has two chests of supplies.

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
- Brewed only in the Wizard Cauldron. Redstone right after the sample extends it; gunpowder after that makes it splash.

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

- Brewed on the brewing stand from Awkward Potion + Acromantula Venom.
- Applies the Wizard Elixir effect.
- Improves mana regeneration.

### Polyjuice Potion

- Brewed in the Wizard Cauldron: add 1 Acromantula Venom, 2 Mandrake Roots and 1 Gillyweed, stir clockwise 4 times, simmer 8 minutes, then add a filled Polyjuice Sample. One brew gives 3 doses.
- The brewing stand no longer makes Polyjuice. Bottles brewed before this change still work; a leftover Stabilized Polyjuice Base has no use.
- Applies the Polyjuice effect.
- Base disguise duration is about 3 minutes.

### Extended and Splash Polyjuice

- Add Redstone in the cauldron right after the sample: the disguise lasts about 8 minutes.
- Add Gunpowder after that (or right after the sample): every dose is a splash potion.

### Wiggenweld Potion

- Brewed: 1 Honey Bottle, 2 Glow Berries, stir clockwise 3 times, simmer 20 seconds. 3 doses.
- Heals 8 health over about 2.4 seconds and stops Bleeding, which milk cannot.

### Pepperup Potion

- Brewed: 1 Blaze Powder, 1 Mandrake Root, stir anticlockwise 2 times, simmer 15 seconds. 3 doses.
- Restores 40 mana at once and gives 30 seconds of Fire Resistance. Steam pours from your ears.

### Essence of Dittany

- Brewed: 3 Dittany, stir clockwise 5 times, simmer 30 seconds. 3 doses.
- Closes a Cursed Wound (Dolohov's Curse) and heals 4 health.

### Mandrake Restorative Draught

- Brewed: 2 Mandrake Roots, 1 Asphodel, stir clockwise 4 times, simmer 45 seconds, then 1 Arcane Dust. 3 doses.
- Add 1 Gunpowder after the Arcane Dust and every dose is a Splash Mandrake Restorative Draught.
- Right-click a player turned to stone, or a creature's statue, with the bottle to bring them back (one dose).
- Thrown, it frees every stone player and statue within 4 blocks, and gives everything else living there the same 8 seconds of gaze ward.
- Drunk, it clears a basilisk's building gaze, and for 8 seconds the gaze cannot build on you.

### Draught of Living Death

- Brewed: 1 Asphodel, 1 Wormwood, 2 Valerian Sprigs, stir anticlockwise 7 times, stir clockwise once (the brew turns clear as water), simmer 60 seconds, then 1 Gunpowder. 3 doses.
- Splash only. Everything within 4 blocks, the thrower too, is stunned for 20 seconds. The usual crowd-control rules apply: repeated stuns get shorter, bosses get half, and a player stunned by another player's throw gets 60% (config `pvpCrowdControlMultiplier`). Your own splash stuns you for the full 20 seconds.

### Felix Felicis

- Brewed: 1 Ashwinder Egg, 1 Acromantula Venom, 1 Gold Nugget, stir clockwise 3 times, simmer 10 minutes, stir anticlockwise once. One dose only.
- 3 minutes of Luck III (better loot) and a quarter more spell mastery XP.

### Failed Potion

- What a ruined brew bottles as. Drinking it gives 10 seconds of Nausea and 3 seconds of Poison.
- An empty bucket pours a ruined brew away instead.

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
  Holds a player turned to stone (see Petrification), and marks a creature whose death leaves a statue.

### Petrification

- A basilisk's full gaze turns a survival or adventure player to stone instead of killing them. Creative and spectator players are not affected, and bosses still die to it.
- While stone you cannot move, look around, attack, use items, cast or open your inventory. Nothing hurts you except the void and `/kill`, you do not get hungry or drown, and mobs ignore you. Chat still works, so you can call for help.
- A screen asks you to choose: Wait for rescue, or Give in. Giving in is a normal death (your items drop unless keepInventory is on, and a Horcrux still brings you back). Esc means wait; press J (it can be rebound) to open the screen again.
- A friend frees you with a Mandrake Restorative Draught: right-click you with the bottle, or throw the splash potion. You come back with 5 seconds of Slowness, and both of you get a message.
- Logging out does not end it: you log back in as stone. A server can set a time limit with the config value `petrificationMaxSeconds` (0, the default, means none); when it runs out you give in automatically. If another mod cancels that damage and you are still stone, the limit starts over.
- Creatures turned to stone leave Petrified Remains (see Mobs) that the Draught brings back.

## Dark Magic

Every player has a hidden score, Darkness, from 0 to 100. Dark spells raise it, and it falls slowly once you stop. It survives death and relogging. Only you see your own tier.

### Gaining Darkness

- A dark spell adds Darkness when you pay for the cast, hit or miss. An Avada Kedavra windup counts when it completes; a Fiendfyre tap or hold counts once.

| Spell | Darkness per cast |
|---|---|
| Avada Kedavra | 6 |
| Fiendfyre | 2.5 |
| Morsmordre | 2.5 |
| Imperio | 1.5 |
| Crucio | 1.2 |
| Tenebris Flight | 0.6 |
| Sectumsempra | 0.4 |
| Dolohov's Curse | 0.4 |
| Serpensortia | 0.3 |

- Ten Avada Kedavra casts take a Clean soul to Corrupted. Mastering an Unforgivable means a dark soul.
- Killing an innocent with Avada Kedavra (a villager, a Hogwarts Student, a House Elf or a peaceful animal) adds 3 more.
- Forging a Horcrux adds 30 and lifts you to at least 60. While your Horcrux exists and your Darkness is 60 or more, it cannot fall below 60; a value under 60 is never held. When the Horcrux shatters or is destroyed, that floor goes.
- A dark spell cast through a body you possess with Imperio darkens you, the puppeteer, never the victim. Creative and spectator players gain nothing. NPCs have no Darkness.
- The server setting `darkMagicGainMultiplier` scales every gain.

### Tiers

| Tier | Darkness |
|---|---|
| Clean | below 20 |
| Shadowed | 20 to under 40 |
| Tainted | 40 to under 60 |
| Corrupted | 60 to under 80 |
| Lost | 80 to 100 |

- A tier change sends you one chat line and a sound: a low bell going darker, a chime going lighter. Nobody else hears it.
- The Grimoire shows "Soul: <tier>" in its header, white to dark green. From Corrupted the mana bar turns a dark green-black.
- Apart from the operator command `/darkmagic`, no chat line or screen names another player's tier. Others can only guess it: from your smoke (Corrupted and Lost), a Sneakoscope, or how Students, House Elves and Death Eaters treat you.

### Costs

Each tier keeps the costs of the tiers below it, except where a harder cost replaces a softer one.

- Shadowed: a corporeal Patronus answers 7.5% of the time instead of 15%.
- Tainted: never a corporeal Patronus; the charm always answers as the shield. Insomnia Dementors come after 3 days without rest instead of 5.
- Corrupted: holding the Patronus shield costs 50% more mana. Dementors within their 48-block follow range that can see you choose you before anyone else. Hogwarts Students keep about 8 blocks away. Faint smoke rises around you, which other players can see (not while you are invisible or spectating; Polyjuice does not hide it).
- Lost: one heart less (-2 max health), shown in the health bar like the Horcrux cost. Students attack you on sight instead of keeping away. House Elves flee from you, even one bound to you, and your own elf stops following you.
- Students and Dementors that chase you for your tier drop you at once when you leave that tier or Dark Magic is turned off.
- The Patronus never fails. A dark wizard's Patronus is weaker, never gone.

### Rewards

- Corrupted: dark spells cost 15% less mana (after the wand and mastery multipliers), including the Fiendfyre and Imperio channels; the Grimoire shows the lower cost. A Dark Mark gives you Strength II and Regeneration as it gives Death Eaters.
- Lost: Death Eaters leave you alone until you strike one (a Fiendfyre or steam hit counts), and only the one you strike fights back. Death Eater base raids never come for you. Lord Voldemort, and the Death Eaters who guard him, still fight you.
- Lost: a Morsmordre cast into the open sky calls 4 to 6 Death Eaters to your side for 5 minutes instead of a raid. Each one lands only on safe ground near you (sturdy ground with no lava below and room to stand); one with no safe spot does not come. They follow you, fight whatever hurts you or whatever you strike in melee or with an arrow, and Apparate to you if you get more than 32 blocks away (once there is safe ground near you). They are still Death Eaters: they attack villagers, Students and players who are not Lost. Strike one and it turns on you. They leave when the 5 minutes end, or when you log out, die or change dimension. A new escort replaces the old one.

### The way back

- Decay: once you have cast no dark spell for 10 minutes, Darkness falls by 1 for every 30 minutes of online play, at every tier. From 100 to 0 takes 50 hours; each tier of 20 points takes 10.
- Redemption: a Heal that heals another hurt living thing (not yourself) removes 1 Darkness, at most once per 30 minutes of play. That at most doubles the pace: 100 to 0 in about 25 hours. A heal that cannot lighten you (at 0 Darkness, or at the Horcrux floor) does not use up the 30 minutes. A Heal cast in creative or through a possessed body redeems nobody.
- Decay and redemption both stop at the Horcrux floor, so a soul at 60 or more stays Corrupted or darker while its Horcrux exists.

### Sneakoscope

- A Sneakoscope spins and whistles at a player with a Corrupted or Lost soul within 16 blocks, even under the Invisibility Cloak or Polyjuice. One you carry never senses you; a placed one senses everyone, its owner too. Creative and spectator players are never sensed.

### Advancements

- A Torn Soul: your soul reaches Lost.
- Redemption: a soul that was Lost comes all the way back to Clean.
- Neither is announced in chat, so nobody learns your tier from them.

### Settings and commands

- `enableDarkMagic` (true): off means no gain, no decay, no redemption, and every tier reads as Clean, so every cost, reward and advancement is off. The stored number is kept for when it is turned back on.
- `darkMagicGainMultiplier` (1.0), `darkMagicDecayTicks` (36000, 30 minutes), `darkMagicRedemptionTicks` (36000, 30 minutes), and `sneakoscopeDetectsPlayers` (true).
- `/darkmagic get|set|add <player> [value]` (operators) reads or changes a player's Darkness. `set` ignores the Horcrux floor; `add` respects it.

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

### Dittany Patches

- Small patches of Dittany, two to four plants, on the ground of the Forbidden Forest and the Horntail Peaks.

### Mini Hogwarts

- A small castle raised in the Forbidden Forest the first time a player goes there (no two within 6000 blocks). `/spawnminihogwarts` builds one, `/findminihogwarts` finds the nearest.
- Students wander its floors, a few house elves keep below stairs, and themed chests wait in its library, spell study, dungeon, great hall and tower.
- **Potions classroom.** Climb the gatehouse stairs to the upper room with the bookshelves: the door in its south wall opens into a tall Potions classroom with seven Wizard Cauldrons over magma (six for students, one for the teacher), shelves, a lectern and two ingredient chests. Each chest holds enough for one brew of every common recipe, with bottles, a water bucket and a stirring rod; now and then one holds an Ashwinder Egg. The cauldrons are full and heated, so you can start brewing straight away.
- The chests are stocked once, when the castle is built (a chest whose chunk is not loaded at that moment stays empty). Castles that already stand are never rebuilt or changed, so only castles built after this update have the classroom.

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

- Players who go five days without rest (three with a Tainted or darker soul) can trigger nighttime Dementor spawns in the Overworld.

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
- Bottling your first finished brew (First Brew).
- Ruining a brew with a wrong step (Toil and Trouble).
- Holding Felix Felicis (Liquid Luck).
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
- Letting dark magic take your soul to Lost (A Torn Soul).
- Coming back from Lost to a Clean soul (Redemption).
- Laughing a Boggart away (Riddikulus!).

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

