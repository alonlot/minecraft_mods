# Dark Magic Roadmap

This doc decides **what ships, in what order, and what gets cut** for a Dark Magic cost: a per-player
score that rises when you use dark spells and changes how the world treats you. It is a roadmap,
like `2026-10-02-spell-mastery-roadmap.md`. Each phase below gets its own implementation plan in
`docs/superpowers/plans/` (written under `handoff/standing-rules/planner-rules.md`) once the phase
before it has been played.

The spell mastery roadmap cut corruption (its decision 9) and said: "If you want corruption later,
it gets its own design doc." This is that doc.

---

## Why this feature

Today the Unforgivables, Fiendfyre and Sectumsempra cost only mana, a cooldown, and (for Avada) a
windup and a small backlash. Nothing in the world reacts to a player who uses them. Dark magic is
the theme of the series, and the mod already has the pieces that should react: Dementors, the
Patronus, the Horcrux, the Dark Mark, Death Eaters and students.

**The design rule for this doc: dark magic must be a temptation, not only a tax.** If corruption
only takes things away, players will not use dark spells, and the content built for them in the
mastery phases is wasted. So every tier gives a cost and, from tier 3, a reward.

---

## What the code looks like today (the facts this plan is built on)

Line numbers drift. Find each spot by its text.

- **No dark flag exists.** `SpellType` has no category field. Its constructor is
  `(id, color, r, g, b, trailParticle, impactParticle, cooldownTicks, manaCost, lootRarity)`.
  The only dark grouping in code is `ProtegoSpell`'s `unforgivable = spell == CRUCIO || spell == IMPERIO`.
- **The cast path.** `network/SpellPackets.CastSpellPacket.handle` calls `castAs`. Mana is checked
  and spent in one block (the "Not enough mana!" check before the self-cast branch). Exceptions:
  - **Fiendfyre** takes its startup mana in its own channel-start block, and starts its cooldown in
    `stopFiendfyreChannel`.
  - **Avada Kedavra** first starts `AvadaWindup.begin`. When the windup completes, it calls `castAs`
    again with the ready flag, and the mana is spent then.
  - **Imperio** starts its cooldown in `ImperioThralls` with `startCooldown(..., false)` and awards
    mastery itself.
  - Creative casts never reach `SpellCosts.startCooldown` (every call is guarded by `!creative`).
- **Puppets.** `MindControl` / `PossessionManager`: a possessed player's casts go through `castAs`
  as the victim. `MasteryAwards.eligible()` excludes forced casters. `SpellBeamEntity` knows the
  puppeteer (`MindControl.hitPuppeteer()`).
- **Successful hits** resolve in `SpellBeamEntity.onHitInner`. `hitSuccessful` and `masteryTarget`
  decide the award, and Avada then calls `tryPrimeHorcruxFromAvada`.
- **Player data** lives in `PlayerPersisted/<modid>` (`util/PlayerMagicData.getModData`). Forge
  copies `PlayerPersisted` across death, and `ModEvents.onPlayerClone` copies it again through
  `HorcruxData.copyPersistent`. Sync on login, respawn and dimension change happens in
  `ModEvents.onPlayerJoin`, `onPlayerRespawn` and `syncPersistentMagicState`.
  `SpellMasteryData` (a compound + `SyncSpellMasteryPacket`) is the model to copy.
  The network channel `hogwartsmagic:spells` is at protocol version 6.
- **The Patronus** (`SpellPackets`, `CORPOREAL_PATRONUS_CHANCE = 0.15f`): a cast is a corporeal
  `PatronusEntity` 15% of the time, otherwise the shield channel. The shield upkeep is
  `getPatronusShieldUpkeepCost` in `ModEvents`. Nothing about the caster's state is checked.
- **Dementors** (`entity/DementorEntity`): target the nearest player with no filter, plus Wizards
  and Students. The aura slows within 9 blocks. **Insomnia Dementors**
  (`ModEvents.trySpawnInsomniaDementor`) spawn at night after 5 days without rest (`INSOMNIA_TICKS`).
- **Death Eaters** (`entity/WizardEntity`) and **Voldemort** target every player. The **Dark Mark**
  (`DarkMarkEntity.applyBuffs`) gives Strength II and Regeneration to Wizards within 50 blocks, never
  to players. The **Morsmordre sky raid** (`SpellBeamEntity.tryStartMorsmordreSkyRaid`) spawns 5 to 9
  Wizards and starts a raid against the caster's area.
- **Students** (`entity/StudentEntity`) attack monsters and Death Eaters, and turn on a player only
  after being hurt.
- **The Horcrux** (`block/entity/HorcruxBlockEntity`, `util/HorcruxData`): made by an Avada kill
  over the altar. It costs 2 max health while it exists, gives one rebirth, and then shatters.
  One per player.
- **Heal** (`spell/support/HealSpell`) heals by rank and cures Bleeding (rank 2+) and Cursed Wound
  (rank 4).
- **Houses:** `HousePerks.getHouse(Player)`. The house is not synced to the client.
- **UI:** the mana bar is `client/ManaOverlay` (shown only while holding a wand). The Grimoire is
  `client/SpellCompendiumScreen`, with a detail panel. `client/BasiliskGazeOverlay` and
  `client/MindControlOverlay` are vignette precedents.
- **Config:** `Config.java` (define, cache field, load in `onLoad`, getter). Server values reach the
  client through `SyncMasteryConfigPacket`, which is the pattern to copy.
- **Advancements** use only vanilla triggers. `VoldemortEntity.grantAllCriteria` is the one
  example of granting from code.
- **The original design** (`2026-10-02-spell-mastery-design.md`) wanted: corruption from Imperio,
  Avada ("heavy") and Fiendfyre; a Dark Mark that buffs a highly corrupted caster (Morsmordre
  Expert); and a Death Eater escort instead of a hostile raid for a highly corrupted caster
  (Morsmordre Master).

---

## Decisions

1. **One number per player: Darkness, 0 to 100** (a float). It is stored at
   `PlayerPersisted/<modid>/DarkMagic` with `DarkMagicLastDarkCast` (game time). It survives death,
   like mastery. A new `DarkMagicData` class owns read, write, add and sync.
2. **Which spells are dark** is a set in pure `spell/dark/DarkRules`, not a new `SpellType` field.
   (A field would touch all 40 constructor calls for nine spells.) Obliviate and Confundo are not
   dark in this pass.
3. **Darkness is gained when you pay for a dark cast,** hit or miss. Intent counts, not the result.
   The hook is the one block in `castAs` where mana is spent, plus the Fiendfyre startup block. This
   is two call sites, not one per spell.

   | Spell | Darkness per cast | Cooldown (ticks) | Casts from 0 to tier 3 (60) |
   |---|---|---|---|
   | Avada Kedavra | 10 | 12000 | 6 |
   | Fiendfyre | 4 | 300 | 15 |
   | Morsmordre | 4 | 1200 | 15 |
   | Imperio | 3 | 200 | 20 |
   | Crucio | 2 | 100 | 30 |
   | Tenebris Flight | 1 | 400 | 60 |
   | Sectumsempra | 0.75 | 60 | 80 |
   | Dolohov's Curse | 0.75 | 100 | 80 |
   | Serpensortia | 0.5 | 200 | 120 |

   Mastering an Unforgivable takes many casts, so **Master in Avada or Crucio means a dark
   player.** That is intended. It is also why the rewards in tier 3 and 4 exist.
4. **Extra Darkness for deeds:**
   - An Avada kill of an innocent (villager, Student, House Elf, any mob for which
     `MasteryAwards.isPassive` is true): +5.
   - Making a Horcrux: +30, and **a floor of 60 while the Horcrux exists** (Darkness cannot decay below
     it). When the Horcrux shatters or is destroyed, the floor goes.
5. **Puppets.** A dark cast forced through Imperio possession charges the **puppeteer**, never the
   victim. Otherwise possession would be a free way to cast Avada. Creative and spectator players
   gain nothing (as mastery).
6. **Tiers.** All costs and rewards read the tier, never the raw number, so tuning stays in one table.

   | Tier | Range | Name |
   |---|---|---|
   | 0 | 0 to 19 | Clean |
   | 1 | 20 to 39 | Shadowed |
   | 2 | 40 to 59 | Tainted |
   | 3 | 60 to 79 | Corrupted |
   | 4 | 80 to 100 | Lost |

   A tier change sends one chat line and a sound (a low bell going up, a chime going down).
7. **Costs per tier** (each tier keeps the costs of the tiers below it):
   - **Shadowed:** the corporeal Patronus chance is halved (15% to 7.5%).
   - **Tainted:** no corporeal Patronus. Patronus shield upkeep +50%. Dementors in range choose you
     before other players. Insomnia Dementors come after 3 days without rest, not 5.
   - **Corrupted:** Expecto Patronum fails ("Only a thin silver mist leaves your wand."). The mana is
     spent and the cooldown starts. Students attack you on sight. Faint SMOKE particles around you,
     which other players can see.
   - **Lost:** -4 max health (the soul is torn), shown like the Horcrux health cost. Dementors may
     come at night even when you rest (5% per night check). House Elves flee from you even when
     bound.
8. **Rewards per tier** (the temptation):
   - **Corrupted:** dark spells cost 15% less mana (in `SpellCosts.manaCost`, after the wand and
     mastery multipliers). A Dark Mark buffs you as it buffs Death Eaters (the design doc's
     Morsmordre Expert).
   - **Lost:** Death Eaters do not attack you unless you attack them first (Voldemort still does; the
     boss stays a fight). A Morsmordre sky raid that you start gives you a Death Eater escort that
     follows you for 5 minutes, instead of a raid near you (the design doc's Morsmordre Master).
9. **Decay.** While Darkness is below 80 and you have cast no dark spell for 10 minutes, it falls by
   1 every 2 minutes (2400 ticks). From 60 to 0 that is about 2 hours of play. At 80 and above it
   **does not decay with time.** A torn soul needs an act, not a wait.
10. **Redemption (the act):** a Heal that heals another living thing that was hurt (not yourself)
    removes 2 Darkness, at most once per Heal cooldown window (the `MasteryAwards` window). This
    works at every tier, and it is the only way down from Lost. It ties the cure to the "light"
    spell, and it does not need a new item.
11. **The client sees its own tier only.** One new packet `SyncDarkMagicPacket(float)` (the protocol
    goes from 6 to 7). Other players see only the smoke particles at tier 3+. No chat line or UI
    names another player's tier.
12. **UI:** the Grimoire shows a "Soul" line in its header (the tier name, with the colour going
    from white to dark green). The mana bar turns a dark green-black from tier 3. No new HUD bar.
13. **Config** (COMMON, synced like the mastery config): `enableDarkMagic` (default true; off means
    no gain, and all tiers read as 0), `darkMagicGainMultiplier` (1.0), `darkMagicDecayTicks` (2400).
14. **Existing players start at 0.** There is no record of past dark casts to backfill from.
15. **A debug command** `/darkmagic get|set|add <player> [value]` (op level 2), like `/spellmastery`.
16. **NPC casters have no Darkness.** Death Eaters cast Avada with no cost, as today.

---

## Phases

### Phase 1: The backbone

- Pure `spell/dark/DarkRules` (test-first): the dark set and gain table, the deed bonuses, the tier
  bounds, the decay step, the floor, the clamp, and the redemption amount.
- `DarkMagicData`: get, add (with floor and clamp), set, tier, and sync. The tier change message.
- The two cast hooks in `castAs` and the Fiendfyre start, with the puppeteer rule.
- Decay in `ModEvents.onPlayerTick` (one check every 200 ticks, not every tick).
- Sync on login, respawn and dimension change. `SyncDarkMagicPacket`, protocol 7.
- Config values and their sync. The `/darkmagic` command.
- The Grimoire "Soul" line and the mana bar tint.

**Done when:** casting dark spells moves you up the tiers with a message at each change, decay
brings you down, the number survives death and relog, the Grimoire shows your tier, the toggle turns
it all off, and `runServer` boots clean on a dedicated server.

### Phase 2: The costs

- Patronus: the corporeal chance by tier, the failure at Corrupted, the shield upkeep at Tainted.
- Dementors: a target goal that prefers the highest-tier player in range (ties go to the nearest),
  the insomnia threshold by tier, and the Lost night chance.
- Students: a target goal on players at Corrupted and above.
- Lost: the -4 max health modifier (applied and removed on tier change, login and respawn, like the
  Hufflepuff health modifier in `HousePerks`). House Elves flee.
- The smoke particles at tier 3+.

**Done when:** each cost in decision 7 can be seen in play, and each one turns off with the config
toggle.

### Phase 3: The rewards and the deeds

- The 15% dark mana discount in `SpellCosts.manaCost`.
- The Dark Mark buff for Corrupted players in `DarkMarkEntity.applyBuffs`.
- Death Eaters neutral to Lost players (a predicate on the player target goal, with the existing
  hurt-by goal unchanged), and the Morsmordre escort.
- The Avada innocent bonus (in `onHitInner` after the kill), the Horcrux +30 and floor (in
  `HorcruxData.startRitual` and when the Horcrux shatters or is reconciled away).
- Redemption through Heal.

**Done when:** a Lost player can walk through a Death Eater camp without a fight until they strike,
a Morsmordre cast at Lost gives an escort, and healing villagers brings a Lost player back down.

### Phase 4: Advancements and docs

- Advancements "A Torn Soul" (reach Lost) and "Redemption" (go from Lost to Clean), granted from
  code (there are no custom triggers yet; follow `VoldemortEntity.grantAllCriteria` or add one
  `CriterionTrigger`).
- `MOD_FEATURES_WIKI.md`, the web wiki, and the Hogwarts Guide.

### Later (not planned)

Revelio showing another player's tier at Master, Obliviate and Confundo as "grey" spells, a
Remorse potion (it can come from the brewing roadmap if both ship), house reactions (Slytherin
gains slower), and Dementors that ignore Lost players.

---

## Questions for the user before Phase 1 is planned

1. **Temptation:** are you OK that a dark player gets real rewards (cheaper dark spells, safe passage
   past Death Eaters, an escort)? Without them, my strong opinion is that players avoid dark spells
   and the system is only a tax.
2. **Speed:** with the table above, about 30 Crucio casts make you Corrupted, and it takes about two
   hours to decay back. Too fast, too slow?
3. **Lost has no time decay**, only Heal on others. Is that too harsh for a single-player world?

---

## Global constraints (apply to every phase)

- Forge 51.0.33, Minecraft 1.21, Java 21.
- Server authoritative. Common and server code never references `com.hogwartsmagic.client.*` or
  `net.minecraft.client.*`. Client code reads only its own synced value.
- No `switch` on an enum in client or render code. Use if-chains or `EnumSet`.
- Pure rules first: every number, tier bound and decision lives in Minecraft-free classes with
  JUnit 5 tests, indexed by tier, not by spell `switch`.
- NPC code paths change only where a phase says so (target goal predicates, the Dark Mark buff).
- Any new hurt, death or effect handler: LOWEST priority and an `isCanceled` guard.
- Entity event bytes 21, 35 and 63 crash the client. Use 61/62/64/66/67.
- `en_us.json` and every JSON file touched stays valid JSON.
- Build and test in the cloud: `cd /home/user/minecraft_mods && timeout 900 ./gradlew compileJava test --console=plain`,
  after checking `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`.
- Commit per task. Stage only named files.
