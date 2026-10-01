# Spell Mastery & Spell Identity Design

Design spec for Hogwarts Magic (Forge 1.21). It covers two things:

1. **Spell Mastery:** each spell levels up from use.
2. **Spell Identity:** every spell gets one mechanic no other spell has, so the roster stops overlapping.

"Today" lines describe what the code does now (`spell/SpellType.java`, `event/ModEvents.java`, `entity/SpellBeamEntity.java`).

---

## 1. The Mastery System

### Ranks

| Rank | Name | XP needed (cumulative) |
|---|---|---|
| I | Novice | 0 (on learning the spell) |
| II | Adept | 30 |
| III | Expert | 120 |
| IV | Master | 350 |

### Earning XP

You only get XP for a *meaningful* cast. Firing into the air gives nothing.

- **Offensive spells:** you hit a living target that is hostile or neutral, or a player in PvP.
- **Utility spells:** the spell actually does its job (a door opened, a block repaired, a fall caught, an item pulled).

**XP per meaningful cast:** `clamp(baseCooldownSeconds / 3, 1, 20)`. Spells with long cooldowns would otherwise take forever to master, so this makes it take roughly the same real time for each spell. For example:

- Expelliarmus (2s) gives 1 XP.
- Fiendfyre (15s) gives 5 XP.
- Avada Kedavra (600s) gives 20 XP.

**Anti-farm rules:**

- Hitting the same entity again within 10s gives no XP.
- Passive animals give 25% XP.
- Mobs from spawners give 50% XP.
- An allied (`hogwartsmagic:allied`) or tamed target gives 0 XP.

### Every rank-up also gives

- **Cheaper and faster casts.** Each rank above Novice gives -8% mana cost and -8% cooldown, stacking to -24% at Master. This stacks multiplicatively with wand and house modifiers.
- **More max mana.** Every rank gained on any spell gives +1 permanent max mana. That's up to +114 if you master all 38 spells. This fixes the "max mana never grows" problem.
- **Feedback.** A chat line, a sound (reuse the level-up chime) and a particle burst from the wand.

### Global balance rules

These apply to every spell below.

- **Bosses** (Voldemort, Hungarian Horntail, Basilisk; add any future boss to the `hogwartsmagic:bosses` entity tag):
  - Crowd-control durations are halved.
  - Imperio, Obliviate, Confundo and Avada Kedavra's instant kill do not work on them. Avada instead deals 20% of max health.
- **Players (PvP):** hard crowd control lasts 60% as long (stun, petrify, bind, hang, levitate, freeze). Imperio on players is limited to what its Master rank says, and there's a config toggle.
- **Diminishing returns:** if the same target is hit by any hard crowd control again within 6s, the second one lasts 50% as long and a third does nothing. Without this, Stupefy into Petrificus into Incarcerous lets you lock someone down forever.

### Data and UI

- **Storage:** store mastery as `SpellMastery` (spellId → xp) in `PlayerPersisted`, next to `LearnedSpells`. It already survives death through the existing clone handler.
- **Grimoire:** show a rank badge (I to IV) and an XP bar for each spell. Also list what the next rank unlocks; that's the hook that keeps people casting.
- **Spell wheel:** add a small rank pip under each icon.
- **Config:** XP thresholds, XP multiplier, per-rank discount, PvP crowd-control multiplier and whether Imperio works on players.

### New shared effects needed

Several spells below rely on these. Build them once and reuse them.

| Effect | Behavior |
|---|---|
| `stunned` | No movement, attacks, item use or casting. Mobs: AI goals paused. Players: input zeroed server-side. |
| `disarmed` | Can't cast spells or pick up the dropped wand for the duration. Replaces the Weakness placeholder. |
| `bleeding` | Damage every second, blocks natural regeneration, healing received ×0.5. Stacks up to 3. |
| `cursed_wound` | Lowers max health by a set amount. Milk does **not** remove it; only Heal at Master does. |
| `brittle` | The next physical or explosion hit deals bonus damage and consumes the effect. Applied by freezing. |
| `silenced` | Can't cast. Player spell wheel greyed out. |
| `confused_aim` | The target's projectiles and spell beams veer randomly. |

---

## 2. Spells

The 38 spells are grouped by role, and each one has a single **Identity** line: the one thing only it does.

### A. Disabling spells (the overlap problem)

Today, Stupefy, Petrificus Totalus, Incarcerous, Glacius and Levicorpus all come down to "Slowness X". After this rework, each one disables in a different way:

| Spell | Disable type | Weakness |
|---|---|---|
| Stupefy | Short hard stun, fast to cast | Short duration |
| Petrificus Totalus | Long full-body lock | Breaks when the target takes damage |
| Incarcerous | Area tether: can't *leave* the spot but can still fight | Target can still fight back |
| Glacius | Slows down, then freezes and makes the target brittle | Fire breaks it |
| Levicorpus | Hangs the target in the air; they can't use items or block | Allies can cut them down |

---

#### Stupefy
**Identity:** the fastest *true* stun in the mod; it's the "interrupt" button.
**Today:** Slowness IV and Blindness for 3s, plus a cast interrupt.

| Rank | Effect |
|---|---|
| I Novice | `stunned` 1.5s. Interrupts any channel or windup the target is doing. |
| II Adept | `stunned` 2s. Small knockback (0.4). |
| III Expert | `stunned` 2.5s. A target hit mid-windup (Voldemort's Avada windup, a Death Eater's cast) is stunned for 4s. Punish their casts. |
| IV Master | **Chain stun:** jumps to 1 more enemy within 5 blocks for half the duration. Beam speed +25%. |

#### Petrificus Totalus
**Identity:** a long lockdown that **breaks on damage**. Use it to take one enemy out of a fight, not to combo on it.
**Today:** Slowness X, Jump 250 and Weakness X for 10s.

| Rank | Effect |
|---|---|
| I Novice | Target goes rigid and topples over (render it rotated 90°, like the Levicorpus render mixin). `stunned` 5s. Any damage ends it. |
| II Adept | 7s. Damage under 2 hearts no longer breaks it. |
| III Expert | 9s. A petrified mob in the air falls and takes normal fall damage. |
| IV Master | 12s. The first damage instance breaks it but deals +50% (a "shatter" hit). |

#### Incarcerous
**Identity:** an area **tether**. Bound targets can still attack and cast, but they can't move more than 2 blocks from where they were bound.
**Today:** Slowness X on the main target, Slowness IV and Weakness on everything within 2.6 blocks, chain visuals.

| Rank | Effect |
|---|---|
| I Novice | Main target only. Tethered with a 2-block leash for 5s; chains drawn to the ground. |
| II Adept | Also tethers enemies within 2.5 blocks for 3.5s. |
| III Expert | Radius 3.5. Tethered flyers (Dementors, Hippogriffs, broom riders) are pulled to the ground and can't take off. |
| IV Master | **Bind together:** all tethered targets are dragged toward the main target and chained to each other, so they share knockback. Leash 1 block, 7s. |

#### Glacius
**Identity:** **sets up combos.** Stacks a freeze, then makes the target brittle for burst damage.
**Today:** Slowness X, frozen ticks, longer in cold biomes, steam combo with fire.

| Rank | Effect |
|---|---|
| I Novice | Slowness II 4s and freeze build-up. Water at the target's feet turns to frosted ice. Steam combo kept. |
| II Adept | A second hit within 4s **freezes solid**: `stunned` 2s and `brittle`. |
| III Expert | `brittle` bonus +60%. Can freeze lava into obsidian (a single source block, a lifesaver in the Nether). |
| IV Master | **Glacius Tria:** a cone of 3 beams. Frozen targets that die shatter into ice shards that slow anything nearby. |

#### Levicorpus
**Identity:** the target hangs helpless in the air. They **can't use items or block**, and they take +25% damage.
**Today:** Levitation II, Weakness III and an upside-down render for 5s.

| Rank | Effect |
|---|---|
| I Novice | Hang 1.5 blocks up for 3s (hovering, not drifting upward). Can't use items or shields. |
| II Adept | 4s. Takes +25% damage while hanging. |
| III Expert | 5s. Shakes loose the main-hand item 25% of the time (lower odds than Expelliarmus, on purpose). |
| IV Master | 6s. **Liberacorpus:** re-casting on the hanging target drops it hard, dealing fall damage for 6 blocks. |

---

### B. Damage spells (stop being "same thing, different number")

Today, Diffindo (15 damage) out-damages Sectumsempra (10), and Sectumsempra, Dolohov and Crucio all use Wither as a "curse". Each one gets a distinct damage profile:

| Spell | Damage profile |
|---|---|
| Diffindo | Cheap, precise, partly ignores armor; also a **utility** cutting spell |
| Sectumsempra | Bleed over time that **stops healing** |
| Dolohov's Curse | Lowers **max health**, and milk can't fix it |
| Confringo | Combat explosion with fire; **never** breaks blocks |
| Bombarda | **Breaks blocks**; weaker against creatures |
| Incendio | Fire that spreads and lights blocks |

#### Diffindo
**Identity:** a precise severing charm. It deals honest damage and is also the "magic shears".
**Today:** 15 magic damage.

| Rank | Effect |
|---|---|
| I Novice | 6 damage, ignores 25% of armor. Hitting blocks cuts leaves, vines, cobwebs, wool, string and tall grass (they drop as items). Hitting a sheep shears it. |
| II Adept | 7 damage, ignores 40% of armor. Cuts leads and fishing lines, and breaks a mob off its leash. |
| III Expert | 8 damage. Hitting an armored target also deals 10% durability damage to the piece hit. |
| IV Master | 9 damage, ignores 60% of armor. **Cleave:** hits a second target up to 3 blocks behind the first. |

#### Sectumsempra
**Identity:** **bleeding**: damage over time that shuts off healing. The counter is Heal (Vulnera Sanentur).
**Today:** 10 magic damage and Wither III for 5s.

| Rank | Effect |
|---|---|
| I Novice | 5 damage and `bleeding` I for 6s (1 damage/s, no natural regeneration). |
| II Adept | 6 damage. Bleeding stacks up to 2. |
| III Expert | 7 damage. Bleeding stacks up to 3, and each stack also halves healing received. |
| IV Master | 8 damage. **Unseen blade:** hitting an already-bleeding target refreshes all stacks and deals 2 damage per stack instantly. |

#### Dolohov's Curse
**Identity:** a **lasting wound**. It lowers the target's *max* health, and milk doesn't remove it.
**Today:** 8 magic damage and Wither I for 10s.

| Rank | Effect |
|---|---|
| I Novice | 6 damage. `cursed_wound`: max health -2 hearts for 30s. |
| II Adept | -3 hearts, 45s. |
| III Expert | -4 hearts, 60s. A purple-flame scar renders on the target. |
| IV Master | -5 hearts, 90s. A target that dies while cursed releases a burst that curses enemies within 3 blocks for half the amount. |

#### Confringo
**Identity:** the **combat** explosion. It blasts and sets things on fire and **never** damages terrain.
**Today:** Ignite 5s and a 1.5 explosion, already set to `ExplosionInteraction.NONE` in `applyHitEffect`. The block-hit path in `SpellBeamEntity:527` still uses `BLOCK`, so it does damage terrain there; change that to `NONE`.

| Rank | Effect |
|---|---|
| I Novice | Explosion power 1.5 (no block damage) and ignite 4s. Steam combo kept. |
| II Adept | Power 1.8. Burning debris sets nearby *entities* on fire (not blocks). |
| III Expert | Power 2.1. Does +50% damage to `brittle` (frozen) targets, which is the Glacius payoff. |
| IV Master | **Delayed fuse:** you can re-cast within 1s to detonate a second blast at the same spot. |

#### Bombarda
**Identity:** the **terrain-breaking** charm, for blowing open doors, walls and mine shafts. It is weaker against creatures than Confringo.
**Today:** Power 2.5 with no block damage, plus 12 damage.

| Rank | Effect |
|---|---|
| I Novice | Breaks blocks up to stone hardness in a radius of 1.5. Deals 5 damage to entities. **Respects `mobGriefing`, spawn protection and `BlockEvent.BreakEvent`** (so claim mods work). |
| II Adept | Radius 2. Drops 100% of broken blocks (TNT normally loses some). |
| III Expert | Radius 2.5. Can break deepslate and harder blocks, up to obsidian at 25% chance per block. |
| IV Master | **Bombarda Maxima:** radius 3.5, and can break obsidian reliably. Never breaks bedrock, spawners or blocks in a Cave Inimicum ward. |

#### Incendio
**Identity:** **fire as a tool.** It lights blocks, spreads flame and burns targets. It's the only fire spell that's useful outside combat.
**Today:** Ignite 5s (2s in rain), steam combo, lights blocks on impact.

| Rank | Effect |
|---|---|
| I Novice | Ignites the target for 4s. On blocks, it lights campfires, candles, TNT and furnaces (gives a furnace 10s of fuel). |
| II Adept | Ignites for 5s and sets fire to flammable blocks where it lands (respects `doFireTick` and `mobGriefing`). |
| III Expert | **Flame jet:** hold to channel a short 5-block flame cone (costs mana each tick). |
| IV Master | **Incendio Duo:** the jet is 8 blocks long and ignored by rain. Burns cobwebs and Devil's Snare-style blocks instantly. |

---

### C. Mind spells (Obliviate, Confundo, Imperio)

Today, all three come down to "clear the target and add Nausea". Each one now controls the mind in its own way:

| Spell | What it does to the mind |
|---|---|
| Obliviate | **Disengage and silence.** The target forgets you exist |
| Confundo | **Chaos.** The target hits the wrong people and its aim goes wild |
| Imperio | **Direct control.** You give the orders |

#### Obliviate
**Identity:** the **escape and stealth** spell. Mobs forget you completely; players lose their spells for a while.
**Today:** Clears the mob's target, Blindness 5s, Nausea 10s.

| Rank | Effect |
|---|---|
| I Novice | The mob drops its target and ignores you for 5s unless you hit it. On a player: `silenced` 2s. |
| II Adept | Ignores you for 10s. Silence 3s. |
| III Expert | Ignores you for 20s. Also hits mobs within 4 blocks. On a villager: resets trade prices that went up after you hurt them, and clears the "Gossip" against you. |
| IV Master | Ignores you for 30s. **Memory charm:** the mob also forgets its home, which pulls raiders out of a raid and makes a Death Eater lose its raid leader. Silence 4s. |

#### Confundo
**Identity:** **chaos and friendly fire.** The target turns on its friends and its aim goes wild.
**Today:** Nausea, Slowness, and the mob targets a random nearby entity.

| Rank | Effect |
|---|---|
| I Novice | For 5s the mob attacks the nearest *other* creature. On a player: `confused_aim` (beams and arrows veer by up to 15°). |
| II Adept | 7s. A confused mob's attacks on its allies deal +25% damage. |
| III Expert | 9s. **Contagious:** if the confused mob hits another mob, that one is confused for 3s too. |
| IV Master | 12s, aim veer up to 30°. A confused NPC caster's spells fire in a random direction. |

#### Imperio
**Identity:** **direct control.** Pick the mob's target, or at Master rank briefly puppet a player.
**Today:** Retargets the mob onto a nearby hostile, gives Strength, Glowing and the allied tag. `ImperioControlPacket` already exists.

| Rank | Effect |
|---|---|
| I Novice | Controls 1 mob up to 40 max health for 20s. It attacks whatever you look at and press the control key on. |
| II Adept | Up to 60 max health, 30s. The controlled mob gets Strength I. |
| III Expert | Controls **2 mobs** at once, up to 100 max health, 45s. Controlled mobs follow you between targets. |
| IV Master | 3 mobs, 60s. **On players** (config toggle): their hotbar is locked and they walk toward a point you choose for 2s. |

Doesn't work on bosses. Using it adds Dark Magic corruption (see section 3.6 in the main review).

---

### D. The Unforgivables

#### Avada Kedavra
**Identity:** the **instant kill**, at a steep price. Ranks make it more usable but never cheap.
**Today:** Max damage, 2 damage back to the caster, 600s cooldown.

| Rank | Effect |
|---|---|
| I Novice | Kills non-boss targets outright. You take 4 damage back. 1.5s windup with a green glow that others can Stupefy-interrupt. |
| II Adept | Backlash 3 damage. Windup 1.2s. |
| III Expert | Backlash 2 damage. Windup 1s. Against bosses it deals 20% of max health. |
| IV Master | No backlash. Windup 0.8s. **The killing curse can't be blocked by Protego**, but it can be dodged, and two Avada beams that collide cancel each other out (Priori Incantatem moment). |

Every cast adds heavy corruption.

#### Crucio
**Identity:** a **channeled** torture curse. While you hold it, you and the victim are both locked in place.
**Today:** Wither II, Slowness III and Weakness III for 5s.

| Rank | Effect |
|---|---|
| I Novice | Hold for up to 2s: target `stunned` and takes 1.5 damage per 0.5s. You can't move while channeling. |
| II Adept | Up to 3s. When the channel ends, the target gets Weakness II for 8s. |
| III Expert | Up to 4s. You can move at 30% speed while channeling. |
| IV Master | Up to 5s. The target's armor loses durability each tick, and a mob that survives flees from you for 10s. |

Bosses take damage but don't get stunned.

---

### E. Movement and force spells

| Spell | Direction | Unique hook |
|---|---|---|
| Accio | Toward you | Also pulls **items and blocks** |
| Depulso | Away (horizontal) | **Wall-slam** damage |
| Ascendio | Upward | **Self-launch** mobility |
| Wingardium Leviosa | You hold it | **Carry and throw** |
| Arresto Momentum | Stop | **Freezes projectiles** |

#### Accio
**Identity:** the **summoning charm**. It pulls creatures *and* items.
**Today:** Reels the struck target in continuously.

| Rank | Effect |
|---|---|
| I Novice | Reels in mobs up to 20 max health, plus dropped items within 24 blocks. |
| II Adept | Up to 40 max health. Pulls all dropped items within 3 blocks of the impact point. |
| III Expert | Up to 80 max health. Hitting a **container** pulls its first stack to you (only if you could open it, i.e. not locked or claim-protected). |
| IV Master | Any non-boss mob. Hitting a player pulls their main-hand item to you (PvP only; replaces Expelliarmus's drop). |

#### Depulso
**Identity:** the banishing charm. Its knockback **slams targets into walls** for damage.
**Today:** Strong push away from the caster.

| Rank | Effect |
|---|---|
| I Novice | Knockback 1.5. Hitting a wall within 0.5s deals 3 damage. |
| II Adept | Knockback 1.8. Wall slam 5 damage and `stunned` 0.5s. |
| III Expert | Also pushes enemies within 2 blocks of the impact point. Hitting an incoming **projectile** sends it back. |
| IV Master | Wall slam 8 damage. A target slammed into another mob damages both. |

#### Ascendio
**Identity:** **mobility.** It launches *you* (self-cast is canon) and also tosses enemies up.
**Today:** Launches the target and nearby entities upward.

| Rank | Effect |
|---|---|
| I Novice | Self-cast (aim down or press the cast key while crouching): launches you 6 blocks up with no fall damage for 5s. Casting at a target still tosses it up. |
| II Adept | 9 blocks. Self-cast keeps your forward momentum, so you can leap across gaps. |
| III Expert | 12 blocks. **Double cast:** a second cast in midair gives another boost, half as strong. |
| IV Master | 15 blocks. Self-cast pushes away anything within 2 blocks when you take off. Cast underwater, it shoots you to the surface (canon, Triwizard Task 2). |

#### Wingardium Leviosa
**Identity:** **telekinesis.** You hold, carry and throw things.
**Today:** Channeled grab-and-hold of the target (`ModEvents.java:1224`).

| Rank | Effect |
|---|---|
| I Novice | Hold a mob up to 20 max health in front of you while channeling (mana drain per second). |
| II Adept | Up to 50 max health. Releasing while swinging **throws** it, dealing fall-style damage on impact. |
| III Expert | Can lift **blocks** (non-block-entity, up to stone hardness, respects claims). The block becomes a falling block you carry and can place or throw. |
| IV Master | Up to 100 max health. A thrown mob or block deals 6 damage to whatever it hits. You can hold 2 small items or mobs at once. |

#### Arresto Momentum
**Identity:** **stops motion.** It catches falls and freezes projectiles in midair.
**Today:** Slow Falling, damps motion, resets fall distance, homes onto falling targets.

| Rank | Effect |
|---|---|
| I Novice | Catches yourself or a falling ally: slow fall 4s, fall damage reset. |
| II Adept | Also slows a hostile target's movement 50% for 3s. |
| III Expert | **Freeze projectiles:** arrows, fireballs, tridents and spell beams within 4 blocks of the impact point stop in midair for 2s, then drop. |
| IV Master | Radius 6. Frozen beams can be **redirected**: your next cast within 2s sends them where you're aiming. |

---

### F. Defense and support

#### Protego
**Identity:** **parry.** Well-timed blocks reflect spells.
**Today:** Custom shield effect that reduces damage for 5s.

| Rank | Effect |
|---|---|
| I Novice | Shield for 3s that blocks 40% damage from the front. |
| II Adept | 50%. Spell beams hitting the shield within the first 0.4s are **reflected** at a random angle. |
| III Expert | 60%, and the parry window is 0.6s. Reflected beams fly back **at the caster**. |
| IV Master | 75%. A parry refunds 50% of the shield's mana. Melee attackers get knocked back. Unforgivables still can't be reflected. |

#### Protego Maxima
**Identity:** the **area dome**: a team defense you hold.
**Today:** Channeled skyward barrier with mana drain; activates the Ward's barrier mode.

| Rank | Effect |
|---|---|
| I Novice | Dome with radius 3 for as long as you hold it. Blocks projectiles and beams from outside. |
| II Adept | Radius 4. Drains mana 20% slower. |
| III Expert | Radius 5. Hostile mobs that touch the dome are pushed out. |
| IV Master | Radius 6. Once cast, the dome **holds itself up for 8s** after you let go. |

#### Heal (Episkey → Vulnera Sanentur)
**Identity:** the **only cleanse** for the mod's curses. It counters Sectumsempra and Dolohov's Curse.
**Today:** Basic heal.

| Rank | Effect |
|---|---|
| I Novice (Episkey) | Heal 3 hearts on yourself or a target. |
| II Adept | 4 hearts. Removes `bleeding`. |
| III Expert | 5 hearts plus Regeneration I for 4s. Removes Poison and Wither. |
| IV Master (Vulnera Sanentur) | 6 hearts. Removes `cursed_wound`. Can heal tamed animals and Hippogriffs at double strength. |

#### Expelliarmus
**Identity:** **disarm.** It takes away the *wand*, not just the item.
**Today:** Drops the main-hand item, knockback, Weakness II for 3s (the code admits it's a placeholder). NPCs chase their dropped wand.

| Rank | Effect |
|---|---|
| I Novice | Drops the main-hand item. NPC casters go into "retrieve wand" mode (already works). If it was a wand: `disarmed` 2s. |
| II Adept | `disarmed` 3s. Knockback 0.8. |
| III Expert | The dropped item **flies to you** instead of landing on the ground (mobs only, or PvP with a config option). |
| IV Master | `disarmed` 4s. Beats any non-Elder beam it clashes with (today only the Elder Wand wins clashes). **Wand allegiance:** disarming the Elder Wand's holder makes it yours. Canon, and it gives players a second way to get it besides killing Voldemort. |

#### Expecto Patronum
**Identity:** the **anti-Dementor guardian**, and the mod's best story progression.
**Today:** Summons a generic Patronus entity that damages Dementors in a growing aura.

| Rank | Effect |
|---|---|
| I Novice | **Incorporeal:** a silver mist shield in front of you for 4s that pushes Dementors back. No animal. (Canon: first attempts are mist.) |
| II Adept | **Corporeal:** a full Patronus for 10s that hunts Dementors. |
| III Expert | 20s. The Patronus takes **your form**: an animal chosen once from player UUID + house (stag, otter, hare, phoenix, doe, wolf, cat, owl…), and you keep it forever. That needs ~6 more models; reuse vanilla animal models tinted silver to keep it cheap. |
| IV Master | 30s. Lights up dark areas and **repels all undead** within 6 blocks. It can also carry a **message**: send a chat message to a player within 500 blocks, delivered by the Patronus. |

#### Lumos
**Identity:** a **light source**, and at Master rank a weapon against darkness.
**Today:** A moving light block above the caster for 10 minutes.

| Rank | Effect |
|---|---|
| I Novice | Moving light (level 12) above the caster. Re-cast to turn it off (Nox). |
| II Adept | Light level 15. |
| III Expert | **Lumos Maxima:** casting at a surface leaves a floating light orb there for 5 minutes, up to 3 orbs. |
| IV Master | **Lumos Solem:** a beam that deals 6 damage to undead, spiders and Acromantulas and blinds them for 3s. |

Fix the existing bug where light blocks get left behind on unload before adding orbs.

#### Revelio
**Identity:** **information.** It reveals what's hidden: ores, chests, invisible things, disguises.
**Today:** Removes invisibility, ore and chest highlight at radius 12, exposes Cave Inimicum.

| Rank | Effect |
|---|---|
| I Novice | Highlights chests and invisible mobs within 8 blocks for 8s. |
| II Adept | Radius 12, and adds ores (coal through diamond). |
| III Expert | Radius 16, and adds ancient debris and mod blocks (arcane clusters, Horcrux altars). Exposes Cave Inimicum wards. |
| IV Master | Radius 20. **Reveals Polyjuice disguises** (shows the true name over the head) and players wearing the **Invisibility Cloak**, for 5s. |

#### Alohomora
**Identity:** the **unlocking charm**.
**Today:** Opens doors, trapdoors and fence gates.

| Rank | Effect |
|---|---|
| I Novice | Opens wooden doors, trapdoors and fence gates. |
| II Adept | Opens **iron** doors and trapdoors (stays open 5s, then closes again). |
| III Expert | Opens every door within 4 blocks of the impact point. |
| IV Master | Opens vault and locked-chest blocks in mod structures (adds a locked Gringotts vault as a reason to use it), and turns off a hostile Ward's barrier for 5s. |

#### Reparo
**Identity:** the **fixing charm**: blocks *and* items.
**Today:** Restores recently broken blocks within range using matching items; has a cinematic rubble effect.

| Rank | Effect |
|---|---|
| I Novice | Restores up to 4 recently broken blocks within 6 blocks. |
| II Adept | Up to 10 blocks, radius 10. Covers blocks broken by explosions (Creeper damage). |
| III Expert | Up to 24 blocks, radius 14. **Repairs items:** casting on yourself while holding a damaged tool or armor restores 10% durability per cast (mana scales with item tier; no Mending conflict). |
| IV Master | Up to 48 blocks, radius 20, and restores glass and block-entity blocks that were empty (an empty chest or furnace). |

---

### G. Fire and dark spells

#### Fiendfyre
**Identity:** **cursed fire that is hard to control.** At low rank it can turn on you.
**Today:** Ignite 20s, 8 damage, spawns a Fiendfyre controller and serpent that you steer by aiming.

| Rank | Effect |
|---|---|
| I Novice | One serpent for 6s. **15% chance it goes rogue** and targets the nearest creature, *including you*. |
| II Adept | 8s. Rogue chance 8%. |
| III Expert | 10s. Rogue chance 3%. Splits into 2 serpents. |
| IV Master | 12s. Never goes rogue. 3 serpents that you can **send at separate targets** (each new cast-key press picks the next target). Can destroy a Horcrux block (canon). |

Must respect `mobGriefing` and claims for block fire. Every cast adds corruption.

#### Morsmordre
**Identity:** the **Dark Mark**: a buff zone for dark wizards and a fear zone for everyone else.
**Today:** Spawns the Dark Mark, applies Blindness; casting it high enough into open sky triggers a Death Eater raid.

| Rank | Effect |
|---|---|
| I Novice | Mark lasts 60s. Gives nearby dark wizards Strength I. |
| II Adept | 2 minutes. Villagers in range flee indoors and students go into "alarm" mode. |
| III Expert | 3 minutes. **A caster with high corruption also gets the buff.** |
| IV Master | 4 minutes. A raid you trigger gives **you** a Death Eater escort instead of turning the raid on you. Only possible for highly corrupted players. |

#### Tenebris Flight
**Identity:** **smoke flight**: fast, evasive and hard to hit.
**Today:** Applies the dark-flight effect to the caster and target for 2s.

| Rank | Effect |
|---|---|
| I Novice | 3s of smoke flight. |
| II Adept | 5s, +20% speed. |
| III Expert | 7s. **Incorporeal:** projectiles and beams pass through you 30% of the time. |
| IV Master | 10s, 50% pass-through. Ramming into an enemy deals 5 damage and Blindness 2s. |

#### Serpensortia
**Identity:** **conjures snakes**, and Parseltongue lets you command them.
**Today:** Spawns 3 allied **cave spiders**. That needs a real snake entity (a small model; use the Basilisk's animation code as reference).

| Rank | Effect |
|---|---|
| I Novice | 1 snake for 20s; it attacks your target, and its bite applies Poison I 3s. |
| II Adept | 2 snakes, 30s. |
| III Expert | 3 snakes, 40s. Poison II. |
| IV Master | **Parseltongue:** your snakes follow commands (attack, guard, follow), last 60s, and hostile snakes (if added later) ignore you. |

---

### H. Special-purpose spells

#### Cave Inimicum
**Identity:** an **illusion ward**. Outsiders see a fake snapshot of the area.
**Today:** Prepares a Ward for illusion mode; outsiders see the snapshot and insiders see reality.

| Rank | Effect |
|---|---|
| I Novice | Max ward size 8³. |
| II Adept | 12³. Bind up to 2 totems. |
| III Expert | 16³. Hostile mobs outside the ward lose track of anyone inside it. |
| IV Master | 24³. Hostile mobs **can't path into** the ward, and it hides you from Revelio cast by anyone below Master. |

#### Alatus
**Identity:** **broom infusion**; better rank means better brooms.
**Today:** Starts infusion on a Broom Workbench that has a valid recipe loaded.

| Rank | Effect |
|---|---|
| I Novice | Infusion takes 10s. |
| II Adept | 7s. 15% chance for a **"Fine"** broom (+5% speed, stored as an item component). |
| III Expert | 5s. Fine 30%, plus a 5% chance for **"Masterwork"** (+10% speed, +15% boost stamina). |
| IV Master | 3s. Fine 40%, Masterwork 15%. Can **re-infuse** a broom to reroll its quality (costs arcane dust). |

Fix the broom bench slot filter first, or Nimbus and Firebolt still can't be crafted at all.

#### Apparition
**Identity:** **minimap teleport**, and it's risky until mastered. Splinching is canon.
**Today:** 4s channel, 100 mana, 48-block range, side-along already exists.

| Rank | Effect |
|---|---|
| I Novice | Range 48, channel 4s. **20% splinch chance:** take 3 hearts damage and drop one random hotbar item at the origin. |
| II Adept | Range 80, channel 3s, splinch 10%. |
| III Expert | Range 128, channel 2s, splinch 3%. Side-along takes 1 extra entity (a tamed pet or player). |
| IV Master | Range 200, channel 1s, no splinching. Can apparate to any **named Floo fireplace or Owl Perch** you've used, at any distance in the same dimension. |

---

## 3. Spells needing new entities or effects (implementation cost flag)

| Needed | For | Effort |
|---|---|---|
| `stunned`, `disarmed`, `bleeding`, `cursed_wound`, `brittle`, `silenced`, `confused_aim` effects | Many spells | Medium (one-time) |
| Snake entity | Serpensortia | Medium |
| Patronus animal forms | Expecto Patronum III+ | Medium-High (models) |
| Light orb entity | Lumos Maxima | Low |
| Falling-block carry | Wingardium III | Medium |
| Projectile freeze | Arresto Momentum III | Medium |
| Topple render | Petrificus | Low (copy the Levicorpus render mixin) |

## 4. Suggested build order

1. **Mastery backbone:** XP storage, ranks, the generic -8%/-8%/+1 mana per rank, Grimoire UI. Every spell benefits from this with zero per-spell work.
2. **Shared effects** (`stunned`, `bleeding`, `cursed_wound`, `brittle`, `disarmed`, `silenced`, `confused_aim`).
3. **Overlap fixes:** the disabling group (A) and damage group (B). These change the most gameplay.
4. **Utility rank unlocks** (E, F). They're cheap and make the spells feel great.
5. **The expensive stuff:** Patronus forms, snakes, Fiendfyre rogue AI, corruption hooks.
