# House Elf — design

A rare, solitary House Elf for the Hogwarts Magic mod, built to match the films:
the oversized bald head, tennis-ball eyes, drooping bat-ears and filthy pillowcase
of Dobby. The creature is never hostile. Its whole design is an arc — you find it
wild, you take it into service, and the interesting move is giving it up.

## 1. The creature

`HouseElfEntity extends PathfinderMob`, registered in `ModEntities` as
`hogwartsmagic:house_elf`, `MobCategory.CREATURE`, hitbox `0.6F x 0.85F`.

Attributes: 12 max health, 0.36 movement speed, 0 attack damage (it has no attack
goal against players in any state), 16 follow range.

It is `setPersistenceRequired()` once bound, so a servant never despawns.

### States

The state lives in a single synched `EntityDataAccessor<Integer>` (`WILD`,
`BOUND`, `FREE`) plus two booleans, `willing` and an owner UUID.

| State | Reached by | Behaviour |
|---|---|---|
| **Wild** | How it spawns | Not owned. Timid: backs away, wrings its hands, and Disapparates with a CRACK if a wizard it *notices* closes within 5 blocks or it takes damage. Cannot be led, bound or hit. |
| **Bound** | Offering food to a wild elf that cannot flee (§3) | Owned. Wears the pillowcase, follows its master at a servile 3–6 blocks, and **punishes itself** — bangs its head, boxes its own ears — when the master takes damage. Will not Disapparate away from its master. |
| **Free** | Giving a bound elf any clothing (§4) | No longer owned, but stays by choice *if* it was willing. Apparates to keep up when outpaced, and blasts anyone who attacks its friend. |

## 2. Noticing

The escape rule is one server-side predicate, `noticesWizard(Player)`. A wild elf
flees only from a player it notices. It does **not** notice a player who is:

- wearing the mod's Invisibility Cloak (`InvisibilityCloakItem` in the chest slot),
- under the vanilla `INVISIBILITY` effect with no giveaway armour equipped,
- Polyjuiced into anything that is not a player or a wizard — `PolyjuiceData`
  already stores the disguise's `EntityType`, so the check is that the stored type
  is neither `EntityType.PLAYER` nor the mod's wizard/student/Voldemort types,
- crouching **and** more than 2.5 blocks away (a route that needs no items at all,
  but demands real care).

This single predicate is the whole "approach unnoticed" family. Adding a future
disguise means adding one clause here.

## 3. Pinning

A noticed elf tries to Disapparate. The attempt fails — it strains, sparks, and
stays put — when any of the following holds. This is a second predicate,
`canDisapparate()`:

- the elf's position is inside a Cave Inimicum ward
  (`CaveInimicumManager.get(level).isWardedAt(blockPosition())`) — the spell's real
  canonical purpose,
- the elf is under Petrificus Totalus (the mod's petrification effect),
- the elf is chained by Incarcerous,
- the elf is frozen by Glacius,
- the elf is stunned by Stupefy,
- the elf is slowed by Arresto Momentum,
- the elf is under Imperio.

**Binding.** Right-clicking a wild elf that is not fleeing — because it either
never noticed you or cannot escape — while holding any `ItemStack` that
`isEdible()` binds it. The food is consumed, the elf bows, the bind sound plays,
its owner UUID is set, and it enters `BOUND`.

**Willing.** The bind records *how* it happened. Gentle routes — the elf never
noticed you, or was merely held by a Cave Inimicum ward — set `willing = true`.
Cruel routes — Imperio, Stupefy, Petrificus Totalus, Incarcerous, Glacius — set
`willing = false`.

## 4. Freeing

Right-clicking a bound elf with any item that is `Equipable` into a humanoid
armour slot (or a Sock, if one is ever added) frees it. The clothing is consumed
and worn.

- **Willing elf** → it shrieks with joy, leaps, and enters `FREE` *retaining the
  player as a friend*: it follows by choice, Apparates to close distance when left
  behind, and when its friend is attacked it hits the attacker with a knockback
  shockwave and modest magic damage on a cooldown.
- **Resentful elf** → it takes the clothes, says nothing, and Disapparates
  permanently. The entity is discarded. This is the cost of having taken it by force.

## 5. Spawning

- `data/hogwartsmagic/forge/biome_modifier/house_elf_spawns.json`:
  biome `hogwartsmagic:forbidden_forest`, weight **1**, `minCount` and `maxCount`
  both **1**.
- `ModSpawnPlacements`: `ON_GROUND` / `MOTION_BLOCKING_NO_LEAVES` with a
  `checkSpawnRules`-style predicate that additionally refuses to spawn if another
  House Elf already exists within 96 blocks. Rare *and* solitary, guaranteed.
- `MiniHogwartsManager` gains a `spawnHouseElves()` beside the existing
  `spawnStudents()`, placing 2–3 bound-looking elves inside the finished castle —
  the kitchens are their canon home. These spawn in `WILD` state so they can still
  be taken into service.

## 6. Model and animation

Authored in Blockbench via the MCP, exported to
`blockbench_models/house_elf.bbmodel`, and mirrored into
`client/model/HouseElfModel.java` + `client/model/HouseElfAnimations.java`,
following the Goblin pattern exactly. Rendered by
`client/renderer/HouseElfRenderer.java` extending `MobRenderer`, scaled down so
the model's built height matches the 0.85-block hitbox.

Proportions from the reference: head roughly a third of total height, eyes very
large and set wide, ears long and drooping to shoulder level, neck thin, torso
narrow under a draped pillowcase, arms and legs spindly with visible knees, feet
oversized and flat.

Animations: `idle` (weight shifting, ear twitch, hands wringing), `walk` (a
scurry with slapping feet), `cower` (wild, when a wizard is near), `punish` (bound,
striking its own head), `joy` (free, leaping with ears flapping), `disapparate`
(a fast crouch-and-vanish that plays just before removal).

Two textures share the geometry: `house_elf.png` (grey, filthy pillowcase) and
`house_elf_free.png` (cleaner skin, mismatched clothes).

Per project convention, no `switch` on an enum inside render code — the model
selects animations with `if`/`else` on the synched state int.

## 7. Sounds

Generated with ElevenLabs and converted to mono OGG, under
`assets/hogwartsmagic/sounds/house_elf/`, registered in `ModSounds` and
`sounds.json` with subtitles in `en_us.json`:

`ambient1`, `ambient2`, `ambient3` (squeaky, nervous, high-pitched), `crack`
(the Disapparition report), `hurt`, `death`, `bind`, `free` (a joyful shriek),
`punish` (a thud and a squeak).

## 8. Server/client safety

All state transitions, notice/pin checks, binding, freeing and the protective
blast are server-side. The client learns state through synched entity data and
entity events (`broadcastEntityEvent`) for the one-shot animations. Nothing in
`HouseElfEntity` touches a client-only class.

## 9. Out of scope

No elf-specific magic beyond the protective blast. No trading. No breeding. No
multi-elf households. No command system for chores.
