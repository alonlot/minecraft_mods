# Spell Mastery Roadmap (revised design)

This is the version of `2026-10-02-spell-mastery-design.md` I recommend building. That doc is the
source of the ideas; this one decides **what ships, in what order, and what gets cut or changed**.
Where the two disagree, this doc wins.

Each phase below is a separate implementation plan in `docs/superpowers/plans/`. Only Phase 1 is
written out in full (`2026-10-02-spell-mastery-phase1.md`); each later phase gets its own plan once
the phase before it has been played and tuned. Writing all seven up front would bake in numbers
nobody has felt yet.

---

## What the code looks like today (the facts this plan is built on)

- **Spell data:** `spell/SpellType.java` is an enum of 39 spells. Each spell has a cooldown in ticks
  and a mana cost. `getManaCost()` already applies a global 75% discount.
- **Casting:** player casts go through `SpellPackets.CastSpellPacket.handle`
  (`network/SpellPackets.java:569`). Channelled spells (Protego Maxima, Patronus, Wingardium,
  Imperio, Fiendfyre, Apparition) tick in `event/ModEvents.java`.
- **Cost math is copy-pasted in about 20 places.** Each place multiplies by the wand multiplier, and
  the cooldown sites also multiply by the house multiplier. There's an inconsistency too: **mana
  cost ignores the house, cooldown includes it.** Mastery has to touch every one of these sites, so
  Phase 1 first gathers them into one helper.
- **Hits:** every beam resolves in `SpellBeamEntity.onHit` (`entity/SpellBeamEntity.java:487`),
  which sets a single `hitSuccessful` flag for both block hits and living hits. That's the one hook
  for awarding XP.
- **Player data:** stored under `PlayerPersisted/<modid>` (`util/PlayerMagicData.java`). Forge
  copies `PlayerPersisted` across death and respawn by itself, so mastery survives death for free.
  `LearnedSpells` lives there and syncs to the client with `SyncLearnedSpellsPacket`. Mana and max
  mana sync with `SyncManaPacket`, and the server tick already resends max mana whenever it changes.
- **Max mana** is 100 (config), plus 25 for Ravenclaw, plus relic bonuses.
- **UI:** the Grimoire is `client/SpellCompendiumScreen.java`, which already has a detail panel
  showing mana and cooldown. The spell wheel is `client/WandRadialMenuScreen.java`.
- **Confringo bug, confirmed:** `SpellBeamEntity.java:541` still explodes with
  `ExplosionInteraction.BLOCK` on a block hit.
- **There's no Dark Magic "corruption" system.** The design doc assumes one exists; it doesn't.
- **NPC casters** (Students, Death Eaters, Voldemort) have their own cooldown code. Mastery is
  **players only**, and NPC code stays untouched.

---

## Decisions that change the design doc

1. **Scope: each spell gets at most two bespoke rank perks in its first pass.** One is its Novice
   *identity* (the "only this spell does X" line); the other is its Master *capstone*. Adept and
   Expert get numeric scaling only (duration, radius, damage). This roughly halves the 150+
   behaviours in the doc. A spell gets more bespoke perks later only if playtesting says it feels
   flat.
2. **The max-mana bonus is capped.** It's +1 per rank gained on any spell, but capped at **+50**
   (configurable). Uncapped, it's +114 on a base of 100, more than doubling mana, which breaks every
   mana cost in the mod.
3. **The rank discount is additive, not compounding:** −8% per rank above Novice, so −24% at Master
   for both mana and cooldown. It multiplies with the wand and house multipliers.
4. **Keep the existing house rules:** the house multiplier applies to cooldown but not to mana
   cost. Changing that is a balance decision, not part of mastery.
5. **No XP in creative mode.** A debug command (`/spellmastery`) sets XP for testing instead.
6. **XP categories are explicit:**
   - **Living-hit spells** earn XP when the beam successfully hits a living target, with the
     anti-farm rules below.
   - **Block-utility spells** (Accio, Alohomora, Incendio, Aqua Eructo, Morsmordre, Cave Inimicum,
     Alatus, Reparo, Piertotum Locomotor, Serpensortia) earn XP when their block effect succeeds,
     at most once per `max(cooldown, 10s)` per spell.
   - **Self and channel spells** (Lumos, Protego, Protego Maxima, Tenebris Flight, Expecto
     Patronum, Apparition, Fiendfyre, Wingardium Leviosa, Imperio) earn XP when the cast completes
     and its cooldown starts, at most once per `max(cooldown, 10s)`. These spells never earn
     living-hit XP, so they can't be double-counted.
7. **Anti-farm rules** (kept from the doc, with two clarifications):
   - Hitting the same entity *with the same spell* within 10s gives no XP. Mixing spells in a combo
     still pays.
   - Allied, tamed or owned targets give 0 XP; passive animals give 25%; spawner mobs give 50%.
     If several apply, the lowest wins.
   - Spawner mobs are tagged when they spawn (`MobSpawnEvent.FinalizeSpawn` with
     `MobSpawnType.SPAWNER`), not guessed at later.
8. **Shared effects trimmed from 7 to 5:** `stunned`, `bleeding`, `cursed_wound`, `brittle` and
   `silenced`. "Disarmed" becomes silenced plus the existing drop-the-wand logic. `confused_aim` is
   dropped unless Phase 6 still wants Confundo to affect players.
9. **Corruption is out of scope.** Every rank that depends on it (Morsmordre III/IV, the
   corruption cost on Unforgivables) is cut. Unforgivables get a non-corruption cost instead
   (backlash and windup). If you want corruption later, it gets its own design doc.
10. **Splinching is configurable:** 10% at Novice by default (not 20%), and it can be turned off.
11. **Fiendfyre telegraphs before it turns.** A serpent about to go rogue glows red for 1s
    first. Without that, it just feels like a bug.
12. **Imperio works on players, mobs and bosses** (the user's call; see "Imperio: Thrall and
    Possession" below). At ranks I–III the victim acts as your tame dog: it follows you and
    fights whoever you fight or whoever attacks you (players and bosses for 3/4/5s, mobs much
    longer). At Master you hold right-click to possess it for up to 20 seconds (5 on a boss),
    with its POV and controls, while your own body freezes. A possessed player's hotbar and
    known spells are yours to use.

---

## Phases

### Phase 1: Mastery backbone (full plan written)
The Confringo terrain fix, central cost math, XP storage and sync, ranks, the generic rank bonuses,
the capped max-mana bonus, XP awarding with anti-farm, rank-up feedback, Grimoire and spell-wheel
UI, config and the debug command. **Every spell benefits, with zero per-spell work.**

**Done when:** you can play for 30 minutes, see ranks climb, feel spells get cheaper and faster,
watch max mana grow, survive death with ranks intact, and `runServer` boots clean on a dedicated
server.

### Phase 1b: Tomes of Mastery
An item that **raises a spell by one rank instantly**. It comes in the four spell rarities:

| Tome | Can raise |
|---|---|
| Common Tome of Mastery | Common spells |
| Uncommon Tome of Mastery | Uncommon and Common spells |
| Rare Tome of Mastery | Rare, Uncommon and Common spells |
| Epic Tome of Mastery | Any spell |

- **Using it:** right-click the tome to open a picker listing your learned spells that the tome
  can raise and that aren't Master yet. Click one: it goes up one rank, with the same chat line,
  chime and particles as a normal rank-up, and the +1 max mana. The tome is consumed.
- **One rank per tome, always:** a tome adds exactly one rank (I→II, II→III or III→IV), never
  more, and its tier never changes that. Going from Novice to Master by tomes alone takes three
  tomes. The spell's XP is set to the start of the next rank, so leftover progress isn't doubled.
- **Server checks everything:** spell learned, rarity allowed, not already Master. The picker is
  only a menu.
- **Tier colors:** the name is colored by tier (white, yellow, aqua, light purple), matching the
  Grimoire's tier colors, and each tier has its own book texture.
- **Tomes can take a spell to Master,** in survival as well, one rank per tome. What keeps
  that in check is **how rare tomes are**, and the higher the tier, the rarer:

  | Tome | Chance per eligible chest | Where it can appear |
  |---|---|---|
  | Common | 3% | Any chest that can roll a spell book |
  | Uncommon | 1.5% | Same chests |
  | Rare | 0.5% | Dungeon-tier chests and mod structures |
  | Epic | 0.15% | Endgame only: Gringotts vaults, the Horntail nest, the Chamber |

  As a rough feel, a player who explores a lot finds a few Common tomes per world, maybe one Rare,
  and is lucky to ever see an Epic. All four chances are config values. There's no crafting
  recipe, so tomes stay a treasure rather than a shortcut.
- **Needs:** Phase 1 storage (`SpellMasteryData.setXp`) and the rank-up feedback. A small
  follow-up plan after Phase 1 ships.

### Phase 2: Shared combat rules
- Five effects: `stunned` (server-side movement and attack lock, mob AI paused), `bleeding`
  (stacks, blocks regeneration, halves healing), `cursed_wound` (max-health modifier that milk
  can't remove), `brittle` (next hit +X%, then consumed), `silenced` (can't cast; spell wheel
  greyed out).
- **Diminishing returns** on hard crowd control: a second hit within 6s lasts 50%, a third does
  nothing.
- **Bosses:** a `hogwartsmagic:bosses` entity tag (Voldemort, Horntail, Ironbelly, Basilisk).
  Crowd control lasts half as long on them; Obliviate, Confundo and the instant kill don't work;
  Imperio works but is capped at short durations (see Imperio below).
- **PvP:** hard crowd control lasts 60% as long against players.

### Phase 3: Disabling spells (the biggest overlap fix)
| Spell | Novice identity | Master capstone |
|---|---|---|
| Stupefy | 1.5s true stun + interrupt | Chains to 1 more enemy |
| Petrificus Totalus | Topple + 5s lock that breaks on damage | Breaking hit deals +50% |
| Incarcerous | 2-block tether, target can still fight | Pulls flyers down; binds targets together |
| Glacius | Builds up freeze → second hit freezes solid + brittle | Shatter on death slows nearby |
| Levicorpus | Hang in place, no items or shields | Re-cast drops the target hard |

### Phase 4: Damage spells
| Spell | Novice identity | Master capstone |
|---|---|---|
| Diffindo | Partly ignores armor; cuts leaves, webs and wool, shears sheep | Cleaves a second target |
| Sectumsempra | Bleeding that shuts off healing | Detonates bleed stacks |
| Dolohov's Curse | `cursed_wound` max-health loss | Spreads on death |
| Confringo | Never breaks terrain (fixed in Phase 1); ignites | +50% vs brittle (the Glacius payoff) |
| Bombarda | Breaks blocks (respects `mobGriefing`, claims, spawn protection) | Larger radius, breaks obsidian |
| Incendio | Lights campfires, candles, TNT and furnaces | Spreads fire (respects `doFireTick`) |

The Incendio channel/flame jet is cut from this pass.

### Phase 5: Support and utility
Heal (cleanses bleeding and cursed wounds), Protego (parry window + reflect), Expelliarmus
(silenced + Master allegiance: disarming the Elder Wand's holder wins it), Revelio (reveals
Polyjuice and the Cloak at Master), Alohomora (iron doors; vault doors at Master), Reparo (repairs
items at Expert), Accio (pulls items; containers at Expert), Ascendio (self-launch), Depulso
(wall-slam damage), Lumos (fix the leftover-light-block bug **first**, then Master's damage to
undead), Apparition (range and channel time scale with rank; configurable splinching; Master jumps
to known Floo fireplaces).

### Phase 6: Mind spells and Unforgivables
Obliviate (mob forgets you and leaves you alone; silences players), Confundo (friendly fire,
contagious at Master), Imperio (ranks I–III turn the target into a tame-dog thrall; Master is possession, see below), Crucio
(a channel that locks both caster and target), Avada Kedavra (a windup that can be interrupted and
gets shorter and costs less backlash with rank; 20% max health against bosses). **Decide on
corruption before this phase starts.**

#### Imperio: Thrall (ranks I–III) and Possession (Master)

**Ranks I–III: Thrall.** The victim becomes your loyal "tame dog" for a while:
- **Following:** it follows you around and stays close (teleports to you if it falls far behind,
  like a wolf).
- **Attacking for you:** it attacks whatever *you* attack, and whatever attacks *you*. It never
  attacks you or your other thralls.
- **On players too:** a player victim loses control of their character and is driven the same
  way (follow, guard, fight for you) for a few seconds. They see a green "Imperius" vignette and
  their keys do nothing.
- **What you see:** thralls glow faintly green, and a small icon over the head shows who owns
  them.

| Rank | Mob thrall | Player or boss thrall | Max mob size (bosses ignore this) |
|---|---|---|---|
| I Novice | 20s, 1 thrall | 3s | up to 40 max health |
| II Adept | 30s, 1 thrall | 4s | up to 60, and the thrall gets Strength I |
| III Expert | 45s, 2 thralls | 5s | up to 100 |

**Bosses** (Voldemort, Hungarian Horntail, Ukrainian Ironbelly, Basilisk; the
`hogwartsmagic:bosses` tag) *can* be enthralled, but only for the player durations above
(3/4/5s). Turning Voldemort on his own Death Eaters for a few seconds is a great moment; forty
seconds of it would be a free win.

**Master: Possession.** The victim no longer acts as a dog. Instead **you take it over**:
1. **Hold right-click on the target to possess it for up to 20 seconds** (**5 seconds on a boss**).
   Your camera jumps to the victim's eyes and your controls drive *their* body: walking,
   looking, jumping, attacking and their held item. Letting go ends it early.
   - **Possessing a player:** you see **their hotbar** and can **scroll it** to pick their
     weapon, then attack as them. If you select **their wand**, your spell wheel and
     cast keys switch to **their learned spells**, at **their mastery ranks**, spending **their
     mana** and putting **their wand's cooldowns** on hold. The wheel shows a "Puppet" header so
     you know whose spells they are. Two spells can't be cast through a puppet:
     **Imperio** (no puppet chains) and **Apparition** (no teleporting the victim away). Forced
     casts earn the victim no mastery XP; you earn Imperio XP for the possession itself.
   - **Possessing a mob or boss:** you control its body and its own attack (melee, the Horntail's
     fire, and so on, through its existing attack method). Mobs have no hotbar or spells to use.
2. **Your own body stops working.** It stands frozen in a trance and doesn't respond to your
   keys. It can still be hurt, and a hit of 4 or more damage throws you out. That's the
   counterplay for everyone nearby.
3. **Resisting (players only):** they see *"Mash SPACE to resist!"* For the **first 8 seconds**
   resisting does nothing. After that, each half-second in which they press space fast enough
   (≥6 presses) gives a 20% chance to break free. Thrall mode (ranks I–III) and boss possession
   (5s) are too short to resist.
4. **When it ends** (you let go, 20s pass, they break free, your body is hit, either of you dies,
   logout or dimension change): the camera snaps back and both bodies get their controls back.
   A broken-free victim gets 3s of immunity to recasting; the caster gets Nausea for 3s.

**For every rank:** bosses are capped at the short durations above; the cooldown starts when the effect ends; you can scroll a
possessed player's hotbar but can't make them drop or throw items or open their inventory or
chests; a config toggle
(`imperioAffectsPlayers`, default on) controls player thralls and possession, and mobs are
always allowed. Mob AI is restored afterwards, and a thrall goes back to its old behaviour.

**How it works under the hood:**
- **Thrall on mobs:** temporary goals, either cloned from vanilla `FollowOwnerGoal`,
  `OwnerHurtByTargetGoal` and `OwnerHurtTargetGoal` or a mod version that works on any `Mob`
  rather than only `TamableAnimal`. They're added on cast and removed on expiry. This replaces
  today's "retarget a nearby hostile" behaviour, keeping the existing `hogwartsmagic:allied` tag
  and glow.
- **Thrall on players:** the victim's client gets a packet that zeroes its input. Each tick the
  server picks a simple goal (follow the caster, or path to and hit the caster's current target
  or attacker) and moves the player toward it, with the same sync caveats as possession.
- **Possession:** builds on the existing Imperio hold-channel in `ModEvents`
  (`TAG_IMPERIO_CHANNELING`, a heartbeat via `TAG_IMPERIO_LAST_SEEN`, forward input) and
  `ImperioControlPacket`. At Master, the server starts a possession session. Hotbar scrolling sends the victim's `setSelectedSlot`; while the victim holds a wand, the caster's client swaps its spell wheel and HUD to a snapshot of the victim's learned spells, ranks, mana and cooldowns (synced each second). Every cast still goes through the normal cast path *as the victim*, so the server checks learned spells, mana and cooldowns exactly as if the victim had cast it, with Imperio and Apparition refused. The caster's
  client calls `setCameraEntity(victim)` and streams its input; the caster's own body is zeroed
  server-side. The victim's client zeroes its input and reports space presses, which the server
  counts and caps so a macro can't escape instantly. Camera and input code live only in
  `client/`.
- **Watch out for players:** movement is client-authoritative in vanilla, so forced movement
  (thrall and possession) needs `hurtMarked`/teleport sync and may rubber-band slightly at high
  ping. Test it on the dedicated server early.

### Phase 7: The expensive features (each one a separate plan)
Patronus forms (tinted vanilla models, chosen by UUID + house), a Serpensortia snake entity
(replacing the cave spiders), Fiendfyre rogue AI with a telegraph and multiple serpents, Wingardium
block carrying, Arresto projectile freezing, Tenebris pass-through, Alatus broom quality rolls
(after fixing the broom bench slot filter).

---

## Global constraints (apply to every phase)

- **Forge 51.0.33, Minecraft 1.21, Java 21.**
- **Authoritative logic runs on the server.** Common and server code never touches `client.*`
  classes. Client screens only read synced data. Check it on `runServer` (dedicated) as well as
  `runClient`.
- **No `switch` on an enum in render or client code** (the synthetic `$1` class has been seen
  failing to load in this dev environment). Use `if` chains or `EnumSet`s.
- **Never run gradle while the client is running.**
- **Entity event bytes 21, 35 and 63 crash the client.** Use 61/62/64/66/67 for any new
  `broadcastEntityEvent`.
- **Terrain changes respect** `mobGriefing`, `BlockEvent.BreakEvent` (claims), spawn protection
  and Cave Inimicum wards.
- **Commit after each task; the user pushes** (pushes from the session hang on the credential
  dialog).
