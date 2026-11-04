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
- Adept: also opens iron doors and iron trapdoors, which swing shut again after 5 seconds.
- Expert: opens up to 12 doors, trapdoors and gates within 4 blocks at once, and picks a small piston door lock (up to 4 levers or buttons, one press).
- Master: picks any piston door lock (up to 20 switches and button orders of up to 4 presses). If no switch works, it forces a nearby comparator, observer or other redstone source on for 3 seconds. It also opens a Gringotts vault door from range (the alarm still goes off) and switches off a hostile Ward's barrier for 5 seconds.
- Piston locks are picked while you keep the cast held (right-click, or the slot key you cast with). The wand twists in first person, lock clicks and faint piston sounds play, sparks fly at the door, and the action bar shows a progress bar and the time left. Letting go stops it: no XP and nothing moves. A held pick may run 30 seconds.
- When the answer is found, the spell works the real levers and buttons itself. XP is paid when the last one lands. It never powers a dispenser, dropper or TNT on the way.
- It refuses a lock it cannot read safely: wiring that runs past what it can trace or into unloaded chunks, pistons used to move redstone blocks or observers, a lock that needs an item, or a clock that never stops. If someone flips a lever of the lock while it works, it stops ("Someone touched the lock while you read it.").
- Piston locks ignore claims and spawn protection. Only player casters pick them.
- A door sealed by Colloportus refuses it (see Colloportus).
- `/lockinfo [3|4]` (operators) reads the piston door you look at as an Expert or Master Alohomora would and prints the answer in chat without touching anything.

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
- Expert: shows how a piston door with a small lock (up to 4 switches) opens, while you keep the cast held as Alohomora does. The levers to flip and the buttons, plates and sculk sensors to work glow and get floating labels, with the presses numbered. Anyone nearby can see them for 15 seconds.
- Master: reads full piston locks and item locks too: which item frame to fill and how many times to turn it, and how many items to put in a chest or other container (up to 3 such keys per lock). Revelio never forces anything.
- At any rank, a lock under a Colloportus seal shows the seal instead: a sphere of white points and a chat line naming the sealer and rank.

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
- 15% of casts produce a corporeal Patronus — the stag, which hunts Dementors, burns them in an expanding aura, and lights the area. This is a one-shot summon and pays the full cooldown.
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

### Colloportus

- The locking charm. Cast it at a door, trapdoor, fence gate, or any block of a piston door (a piston within 6 blocks) to seal that lock against magic. A cast at anything else does nothing ("There is no lock here to seal.").
- A seal is a sphere around the struck block: 1, 2, 3 or 4 blocks by rank. A seal made at a piston door also covers its door pistons, so sealing at the button protects the whole door.
- You keep 1, 2, 4 or 8 seals by rank, across all dimensions. A new one past the cap replaces your oldest.
- Alohomora at a sealed lock is refused ("This lock is sealed by Colloportus."), before any piston lock is read. An Alohomora of a strictly higher rank breaks one seal instead of opening, and a Master Alohomora also breaks a Master seal. The sealer is told in chat if they are online.
- Your own Alohomora passes your own seals. Casting Colloportus inside your own seal lifts it (no XP).
- Only magic is stopped: hands, keys, buttons and redstone still work. Alohomora's Expert area skips sealed doors.
- Seals last until broken or lifted, are saved with the world, and stay when the door is broken.
- Revelio shows who sealed a lock, and `/lockinfo` prints `SEALED by <name> (rank N)`.
- `/colloportus clear [radius]` (operators, default 8, up to 64) removes every seal that reaches within the radius of you.
- Players only, never through an Imperio puppet or a reflected beam.

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

### Mountain Troll

- Big, slow, club-swinging brute (120 health). Hostile to players, Death Eaters and students.
- Roars once on first contact, then picks between an overhead ground slam (hits everything in a
  4-block circle in front of it and shakes nearby players), a sweeping swing in a cone in front,
  and a plain club hit.
- Below 40% health it flies into a rage: faster, harder hits, more slams.
- A single hit of 8 or more while it is not mid-attack staggers it for a moment.
- Spawns in the Forbidden Forest, Horntail Peaks, taiga and mountain biomes, and in the Goblin Undercity.
- Drops cobblestone and sometimes Arcane Dust.

### Giant

- Grawp: a five-and-a-half block giant with 250 health. Neutral until provoked.
- Provoked by being hit, or by a player who stands within 4 blocks of him for 3 seconds. The anger lasts
  30 seconds and is refreshed by every new provocation. He bullies other mobs regardless.
- Roars on first engagement (knockback and nausea within 8 blocks), then swipes, kicks or grabs.
- The kick breaks soft blocks in front of him (dirt, sand, logs, planks, leaves, glass, wooden doors and
  the like, never anything that needs a pickaxe) and throws some of the wreckage. Needs `mobGriefing`
  and is stopped by Cave Inimicum.
- The grab lifts a victim in his fist and hurls them. Below 35% health he rages and grabs more often.
- A single hit of 14 or more staggers him and makes him drop whatever he is holding.
- Rare in the Forbidden Forest, dark forests and old growth taigas. Egg or command giants never despawn.

### Hungarian Horntail

- Boss dragon with 320 health and a boss bar.
- On the ground it bites, spins its spiked tail through everything around it, gusts with its wings, and
  breathes a sweeping jet of fire that stops at walls and sets the ground alight (with `mobGriefing`).
- It also takes off, circles its prey, strafes it with fire from the air and finishes with a dive.
- Below 45% health it rages: faster, more fire, more flying. A hit of 14 or more staggers it on the ground.
- Lives on the open crags of Horntail Peaks (y 96 and up, open sky) and at Horntail nests. Wild ones can
  despawn; ones made by egg or `/summonhorntail` (op) never do.
- Ringing Clankers near one only makes it angry.

### Ukrainian Ironbelly

- The dragon chained in front of the deepest Gringotts vault (200 health, heavy armour). It never flies.
- Nearly blind: it finds you by noise (sprinting, breaking blocks) or when you come within 8 blocks.
  Walking past it, wall-side, works.
- Held within 11 blocks of its anchor; past the chain it stops following and breathes fire instead.
- Clankers cow it for 5 seconds. Break every lightning-rod anchor stub around it and it climbs out of the
  cavern and is gone.

### Gringotts Goblin

- Gringotts teller. Neutral, never despawns, and trades against Galleons: a gold ingot for 2 Galleons,
  3 Galleons for an ingot, a diamond for 6, and Galleons for a fine wood stick, a broom and a goblin-forged
  silver dagger. Each offer has a limited number of uses.
- Refuses to trade while it is angry. Striking one brings every vault guard within 24 blocks.
- Also found deep underground (below y 40) anywhere in the Overworld, and in the Goblin Undercity.

### Goblin Vault Guard

- Armoured goblin with a silver dagger (30 health). Neutral: it only attacks someone it is angry at.
- Angered by hitting a goblin or guard, by opening containers inside the bank, by Clankers, and by the
  bank's alarm.
- Undercroft sentries are hostile on sight, except to a player Polyjuiced into a goblin.
- May drop its dagger.

### House Elf

- Never hostile. A few live in the Hogwarts castle; in the wild they are rare and solitary (a natural
  spawn never lands within 96 blocks of another elf), found only in the Forbidden Forest.
- A wild elf Disapparates with a crack when a wizard it notices comes within 5 blocks, or when it is hurt.
  It does not notice a player in an Invisibility Cloak or under invisibility, one Polyjuiced into a
  creature, or one crouching more than 1.5 blocks away.
- It cannot Disapparate inside a Cave Inimicum ward, or while stunned, petrified, chained, slowed,
  frozen, hung by Levicorpus or under Imperio.
- Offer food to a wild elf that is not fleeing and it is bound to you. It follows you, Apparates to you
  when left far behind, and punishes itself when you are hurt.
- Give a bound elf any piece of clothing (armour) to free it. An elf you took gently (unseen, or held only
  by a ward) stays as a friend and blasts whoever attacks you. One taken by force takes the clothes and
  leaves forever.

### Merperson

- Black Lake selkies with spears (30 health). Territorial, not hostile.
- Swim within 11 blocks and one rears up and screeches a warning. Stay within 5.5 blocks for 5 seconds and
  the pod attacks. Strike one and every merperson within 20 blocks turns on you.
- Mermish is per listener: under water you hear their song and speech, above the surface only screeching.
- Each natural pod has a merchieftainess: larger, paler, crowned, 52 health. She leads the chorus.
- Out of water they flounder and, after five minutes, slowly dry out.
- Opening or breaking any container in the merfolk village turns the whole village on you for two
  minutes and wakes the grindylow nest around the hoard.
- Drops kelp, prismarine shards, and sometimes its spear.

### Grindylow

- Small (8 health) Black Lake water demon. A trap rather than a fighter.
- Lies dormant in the weed (its green eyes glow) until something swims within 4.5 blocks, then shrieks
  and comes.
- It takes hold: your movement slows, you are dragged down, and you lose air far faster. Several stack.
- Any hit on it breaks its grip (brittle fingers), and a grip lapses on its own after ten seconds.
- A nest lies around the merfolk hoard; free-swimming groups live in the Black Lake and its shallows.

### Golden Snitch

- Released from the Golden Snitch item (crafted, or bought from the broom merchant). It flits around the
  spot where it was let go and darts away from anyone within 9 blocks.
- It cannot be hurt. It can only be caught by touching it while riding a broom, and not in the first
  3 seconds after release.
- Catching it gives 50 XP, returns the item and shows how long the chase took.

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

### Mandrake Plant

- Grows wild in swamps, mangrove swamps, dark forests, the Forbidden Forest and the Dementor Fen.
- Cannot simply be broken. With an empty hand, right-click it (or try to break it) to pull: three
  seconds of tugging while it screams, giving nausea and weakness to players within 5 blocks.
- Walk away, switch to an item or let it out of reach and you lose your grip. Even a full pull fails
  about three times in ten, and it twists back into the soil.
- A successful pull drops a Mandrake Root, used to brew the Polyjuice base.

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

### Merperson's Spear

- Iron-tier spear: a little slow and unremarkable on land, 4 extra damage while you are under water.
- Dropped now and then by merpeople, and found in the merfolk hoard.

### Merchieftainess's Crown

- Found only in the merfolk hoard. Worn, it gives Conduit Power for as long as it stays on: breathe,
  see and work under water.
- Every merperson that sees you wearing it attacks on sight, with no warning.

### Clankers

- The goblins' noisemaker (24 uses, 3 second cooldown). Sold by the cauldron smith.
- Cows every Ironbelly within 20 blocks for 5 seconds, and brings every goblin guard within 32 blocks.
  Rung near a Hungarian Horntail, it only makes it angry.

### Golden Snitch (item)

- Crafted from gold, Arcane Dust and Magical Silk, or bought from the broom merchant.
- Right-click to release it. See the Golden Snitch mob entry for the catch.

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
and stays a bank. About a minute end to end. **W** launches it, **S** brakes. At the bottom, let go
of **W** and press it again to ride the same line back up to the station. The line audits itself
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
- Mountain Troll, Giant, Gringotts Goblin and House Elf biome spawns.
- Mandrake patches in swamps, dark forests, the Forbidden Forest and the Dementor Fen.

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

