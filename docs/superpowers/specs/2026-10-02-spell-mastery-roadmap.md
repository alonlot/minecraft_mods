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
12. **Imperio Master is full possession** (the user's call; see "Imperio Master: Possession" below).
    Hold right-click to possess a player or mob for up to 10 seconds: you get their POV and
    controls while your own body is frozen. Players can struggle free after 5 seconds.

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
- **The jump:** the spell's XP is set to the start of the next rank, so leftover progress isn't
  doubled.
- **Server checks everything:** spell learned, rarity allowed, not already Master. The picker is
  only a menu.
- **Tier colors:** the name is colored by tier (white, yellow, aqua, light purple), matching the
  Grimoire's tier colors, and each tier has its own book texture.
- **Where you find them:** chest loot only, rarer than spell books and weighted by tier like
  `SpellLootRarity`. Epic tomes only come from endgame loot (Gringotts vaults, the Horntail nest,
  the Chamber). No crafting recipe, so they stay a treasure rather than a shortcut.
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
  Crowd control lasts half as long on them, and mind spells and the instant kill don't work.
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
contagious at Master), Imperio (ranks I–III command more and bigger mobs; Master is possession, see below), Crucio
(a channel that locks both caster and target), Avada Kedavra (a windup that can be interrupted and
gets shorter and costs less backlash with rank; 20% max health against bosses). **Decide on
corruption before this phase starts.**

#### Imperio Master: Possession
Ranks I–III keep today's control: **hold right-click** with Imperio on a mob to steer it. At
**Master**, holding right-click on a player or non-boss mob becomes full possession:

1. **Hold to possess.** While you hold right-click (up to **10 seconds**), your camera jumps to the
   victim's eyes and your controls drive *their* body: walking, looking, jumping, attacking and
   their held item. Letting go ends it early.
2. **Your own body stops working.** It stands frozen in a trance and doesn't respond to your
   keys while you're inside the victim. It can still be hurt, and a hit of 4 or more damage
   throws you out. That's the counterplay for everyone nearby.
3. **The victim's side (players):** their controls are taken away, and they see a green
   "Imperius" vignette and the prompt *"Mash SPACE to resist!"* For the **first 5 seconds**
   resisting does nothing. After that, each half-second in which they press space fast enough
   (≥6 presses) gives a 20% chance to break free.
4. **When it ends** (you let go, 10s pass, they break free, your body is hit, either of you dies,
   logout or dimension change): the camera snaps back and both bodies get their controls back.
   A broken-free victim gets 3s of immunity to recasting; the caster gets Nausea for 3s.
5. **Limits:** bosses are immune; the Imperio cooldown starts when possession ends; you can't
   drop or throw the victim's items or open their inventory or chests; a config toggle
   (`imperioPossessPlayers`, default on) controls player possession, and mobs are always allowed.
6. **Mobs can't resist,** but their AI is switched off for the duration and restored after.

**How it works under the hood:** it builds on the existing Imperio channel in `ModEvents`, which
already tracks the hold (`TAG_IMPERIO_CHANNELING`, a heartbeat via `TAG_IMPERIO_LAST_SEEN`, and
forward input) and `ImperioControlPacket`.
- **Rank check:** at Master, the server starts a possession session (caster, victim, start tick,
  end tick) instead of the usual steering.
- **Caster's client:** calls `setCameraEntity(victim)` and sends its movement keys, rotation,
  jump, attack and use input every tick. The caster's own movement is zeroed server-side.
- **Victim's client:** gets a packet that zeroes its own input, shows the vignette and prompt,
  and reports space presses. The server counts the presses itself and caps them, so a macro
  can't escape instantly.
- **Client code stays client-side:** the camera and input capture live only in `client/`.
- **Watch out for players:** movement is client-authoritative in vanilla, so forced movement
  needs `hurtMarked`/teleport sync and may rubber-band slightly at high ping. Test it on the
  dedicated server early.

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
