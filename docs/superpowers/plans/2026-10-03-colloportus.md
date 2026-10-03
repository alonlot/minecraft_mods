# Colloportus (the Locking Charm) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan (see the ledger's cloud rulings).

**Goal:** A new beam spell, Colloportus, that seals a lock against Alohomora. Cast at a wooden or iron door, a trapdoor, a fence gate, or any block of a piston door, it seals every block within a radius set by the caster's rank. While the seal stands:
- Alohomora at a block inside it (or, for a piston door, with any door piston inside it) is refused with "This lock is sealed by Colloportus." before any trace or solve starts. The mana of that cast is spent as usual.
- An Alohomora cast at a strictly higher rank than the seal breaks that seal instead of opening (that cast does nothing else). The next cast works normally.
- The sealer's own Alohomora passes. The sealer casting Colloportus inside their own seal lifts it.
- Revelio shows the seal (an outline of its sphere and a line naming the sealer and rank) instead of lock hints. `/lockinfo` prints `SEALED by <name> (rank N)`.
- Hands, keys, buttons, levers and redstone are untouched: the seal only stops magic.

The spell gets a spellbook like every other spell. The controller has already committed its texture (`magic_book_colloportus.png`, commit 5d716bc); this plan wires it.

**Architecture:**
- **Pure rules** (`spell/support/seal/SealRules`, `Seal`): radius and cap per rank, the sphere cover test, the break test, and the one decision an Alohomora faces against the seals over a lock (owner bypass, highest covering rank decides, break one seal per cast, the oldest of equals first). Minecraft-free and unit-tested first.
- **Storage** (`SealData extends SavedData`): one list of seals per dimension (`level.getDataStorage()`), saved with the world, oldest first. Seals are few (at most 8 per player), so the list is the index: a lookup is one integer distance test per seal. A dimension with no seals answers every query without reading the world.
- **The spell** (`ColloportusSpell`, server only): the beam's block hit seals, lifts or does nothing; `alohomoraGate` runs at the top of Alohomora's lock path; `revealSeals` for Revelio; `strongestSeal` for `/lockinfo`; `sealedAgainst` for Alohomora's Expert area.
- **"The lock" at a struck block** is the struck block plus every door piston `CircuitTracer` would trace from it (pistons within `LockRules.DOOR_RADIUS` = 6). That is the same 6-block rule that lets Colloportus seal a piston door in the first place, and it needs only the existing cheap piston scan (made public), never a trace.
- **Feedback** uses vanilla sounds and server particles only. The lines go to chat, because the client redraws the cooldown line on the action bar every tick right after a cast.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** No spec file. The binding inputs are the user's request and the controller's rulings, both copied below.

## Requirements (from the user)

1. Add a new spell.
2. Create its book icon like the other books (the controller made the texture; this plan wires it like every other book).
3. Cast on a lock, such as a door or an area of pistons, it makes that lock refuse Alohomora.

## Controller rulings (binding)

- Name COLLOPORTUS, id `colloportus`. A beam spell aimed at a block, like Alohomora. Loot rarity UNCOMMON. Mana and cooldown in line with Alohomora.
- A seal is a sphere centred on the struck block, its radius set by mastery rank (about 2/4/6/8 blocks across for Novice/Adept/Expert/Master). Casting at a wooden or iron door, trapdoor or fence gate, or at any block within a piston door lock, creates a seal. A cast at a block with no door, trapdoor or gate and no door piston within Alohomora's piston-search range does nothing ("There is no lock here to seal.") and gives no XP.
- Any Alohomora (vanilla doors, and the piston-lock path including hold-to-pick, at every rank) whose struck block, or for piston locks any traced door piston, is inside an active seal is refused with "This lock is sealed by Colloportus." before any trace or solve starts. The mana of that cast is spent as usual.
- Revelio on a sealed lock reveals the seal (a brief particle outline of the sphere and a line naming the sealer and rank) instead of lock hints. `/lockinfo` prints SEALED (and the sealer and rank) as its single word line.
- An Alohomora cast at a rank strictly higher than the seal's rank breaks the seal instead of opening. That cast only breaks it, with a line and a sound; the next cast picks normally. Equal or lower rank is refused. The sealer's own Alohomora passes through their own seal. The sealer casting Colloportus again at a block inside their own seal removes it ("You lift your seal.").
- Seals are permanent until broken or lifted, stored in per-dimension SavedData (never per-tick `getPersistentData`). Per-player cap by rank (1/2/4/8); a new seal past the cap replaces that player's oldest. Seals from different players can overlap. Alohomora must beat every covering seal it does not own: the highest covering rank decides, and one seal (the highest) breaks per cast.
- It blocks only magic: hands, redstone, buttons and keys still work. No protection or claim checks (as Alohomora's requirement 4).
- Mastery XP once per new seal, players only, never through an Imperio puppet (as Alohomora P1). No XP for lifting.
- Vanilla sound events and particles only.
- A pure rules class (SealRules) built test-first. Seal lookups must be cheap.
- Code rules: server authoritative; no client classes in common code; if-chains, not enum switches; `isLoaded` before world reads; never load chunks; LOWEST priority plus `isCanceled` guards on any new hurt/death/effect handler (this plan adds none); mastery XP players only.

## Rulings made while planning (record them in the ledger)

Numbered as in `.superpowers/sdd/2026-10-03-colloportus/preflight.md`, where each has its cost.

1. **Numbers.** Base mana 15 (the global 0.75 tuning makes it 11; Alohomora is 10, so 8). Cooldown 60 ticks (Alohomora 40). Why: a seal lasts forever, so it costs one step more than the charm it counters. A longer cooldown also pays a little more XP per cast (`MasteryMath.xpPerCast` scales with cooldown).
2. **Radius 1/2/3/4** (2/4/6/8 blocks across). A block is covered when its centre is at most the radius from the seal's centre, tested on integers (`dx²+dy²+dz² <= r²`). A Novice seal on either half of a door covers the other half.
3. **Cap 1/2/4/8**, counted across all dimensions. The oldest by game time goes, even when it is in another dimension. All dimensions share one game time in vanilla.
4. **The lock at a struck block** is that block plus its door pistons (decision 4 in the pre-flight). This covers both "struck block" and "any traced door piston" without a trace. A side effect: a wooden door struck within 6 blocks of a piston whose block lies in someone's seal is refused too.
5. **Gate order.** The Diagon archway, Gringotts vault and hostile Ward branches of `AlohomoraSpell.onBlockHit` run first and ignore seals. The gate runs before `openOne`, the area opening and the piston path.
6. **Expert area.** Alohomora's 4-block area opening skips any door inside a seal the caster does not own. It never passes or breaks a seal; only the struck lock can break one.
7. **A break counts as Alohomora's block effect.** It returns true, so it pays Alohomora block XP through the usual gate. A refusal fizzles and pays nothing.
8. **Master seals cannot be broken.** Alohomora tops out at rank 4 and must be strictly higher, so a Master seal can only be lifted by its sealer or replaced by their cap. This is for the user's sign-off.
9. **Puppets.** Under a puppet's hit context (`MindControl.hitPuppeteer() != null`), Colloportus does nothing (it fizzles). An Alohomora gets no owner bypass and never breaks a seal (it is refused). Same as P1.
10. **A reflected Colloportus beam** does nothing.
11. **Lifting first.** A cast at a block inside one or more of the caster's own seals lifts every one of them, with no XP, and makes no new seal. To move a seal, lift it, then cast again.
12. **Seals are places, not blocks.** A seal stays when its door is broken or replaced. Nothing else removes a seal, and there is no admin command.
13. **A pick already running is not stopped** by a seal made after it started. Only new Alohomora casts are checked.
14. **Revelio at any rank** reveals every seal over the lock: one burst of END_ROD points on each sphere, and one chat line per seal (at most 4). When it reveals a seal it skips the piston-lock hints. Its other effects (ores, chests, invisible things) and its XP are unchanged.
15. **`/lockinfo`** prints one line, `SEALED by <name> (rank N)`, for the highest covering seal, and starts no read. This also happens for a sealed wooden door with no piston near.
16. **Lines go to chat.** `ModEventsClientHooks.onClientPlayerTick` rewrites the action bar every tick while the spell cools down, which it always is right after the cast. `LockPickStatePacket` is tied to picking state.
17. **The sealer is told** in chat when someone breaks one of their seals, if they are online.
18. **Where the book drops.** Loot pools STRONGHOLD_LIBRARY, DUNGEON_MIX and DEFAULT_POOL, which are Alohomora's pools minus the village. The Bookseller also sells it in its level-3 "advanced" list.
19. **Look and sound.** The cast sound reuses `SPELL_ALOHOMORA`. Colour bronze `0x8A6D3B`, trail ENCHANT, impact WAX_ON. Making a seal plays IRON_DOOR_CLOSE and CHAIN_PLACE with a WAX_ON ring at the radius. Lifting plays IRON_DOOR_OPEN and CHAIN_BREAK. A refusal plays CHAIN_HIT. A break plays CHAIN_BREAK and IRON_DOOR_OPEN with ELECTRIC_SPARK at the seal.
20. **The sealer's name is stored** at cast time. It is shown while they are offline; a renamed player shows the old name.
21. **The network protocol stays 6.** No packet is added, and beams and books carry the spell id as a string.
22. **Book custom model data 39** (appended to `SPELL_MODEL_ORDER`), with a model override added after the 38 entry.
23. **XP gate.** Seal XP goes through `MasteryAwards.onBlockEffect`, so it is gated like every block spell: at most once per `max(cooldown, 200)` ticks. This also stops seal-and-lift farming.
24. **NPCs never cast Colloportus.** No AI change is made. An NPC Alohomora faces seals like anyone (`casterRank`: rank 1, bosses 3).
25. **The Alohomora and Revelio descriptions are unchanged.** The Colloportus description explains the interaction.

## Global Constraints

- Server authoritative. `spell/`, `network/`, `event/ModEvents`, `util/`, `entity/`, `command/` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. This plan touches no client code.
- No `switch` on an enum in the new code. Use if-chains and `==`. The two existing `SpellType` switches only get one more `case`.
- Never load a chunk. The struck block is loaded (the beam hit it). `CircuitTracer.doorPistons` and `hasDoorPiston` check `isLoaded` per position. `playSound` and `sendParticles` load nothing.
- No new event handlers. (If one were added: LOWEST priority and an `isCanceled` guard.)
- Mastery XP only through the beam's existing player-only, not-reflected, not-puppet path (`SpellBeamEntity`, `masteryEligible`).
- Cloud build rules (`.superpowers/sdd/CLOUD-OVERRIDES.md`) replace the standing Windows rules:
  - Before any gradle run, check `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If another build is running, wait (poll every 30 s, at most 20 min).
  - Build and test: `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew compileJava test --console=plain`.
  - Server boot: the CLOUD-OVERRIDES block (with `run/eula.txt` containing `eula=true`).
- Commit per task:
  - Stage only the named files. Never `.claude/settings.local.json` or `.superpowers/`.
  - `git commit -m "<subject>" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`.
  - No apostrophes in heredoc bodies. Never push.
- `en_us.json` stays valid JSON. Validate it with `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`, and validate every JSON file you touch the same way.
- `RankChangeTextTest` fails unless every `SpellType` constant has `spell.hogwartsmagic.<id>.rank.2..4`. So Task 2 adds the constant and its lang lines in the same commit.
- Line numbers drift. Every edit names the exact text to find. The text is quoted from HEAD 926d4d1, and 5d716bc (texture only) changes none of it.
- There are 319 tests at 926d4d1. They stay unchanged and green.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/support/seal/Seal.java` | Pure record: one seal | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/support/seal/SealRules.java` | Pure: radius, cap, cover, break, decide, evictions | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/support/seal/SealRulesTest.java` | 14 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/support/seal/SealData.java` | Per-dimension SavedData list of seals | 2 |
| Create `src/main/java/com/hogwartsmagic/spell/support/seal/ColloportusSpell.java` | Seal, lift, gate, reveal, strongest, area test | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java` | `doorPistons` becomes public | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Constant, hit-effect case, sound case | 2 |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Colloportus block branch; living hit fizzles (T2); Revelio reveal (T3) | 2, 3 |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | COLLOPORTUS in BLOCK_UTILITY | 2 |
| Modify `src/main/java/com/hogwartsmagic/item/MagicBookItem.java` | `colloportus` in SPELL_MODEL_ORDER (CMD 39) | 2 |
| Create `src/main/resources/assets/hogwartsmagic/models/item/magic_book_colloportus.json` | Book model on the committed texture | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/models/item/magic_book.json` | Override for CMD 39 | 2 |
| Modify `src/main/java/com/hogwartsmagic/loot/MagicBookChestLoot.java` | Three pools | 2 |
| Modify `src/main/java/com/hogwartsmagic/entity/ModVillagers.java` | Bookseller level 3 | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Name, description, rank lines, messages | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java` | Gate before opening; area skips seals | 3 |
| Modify `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java` | SEALED line | 3 |

Untouched on purpose: `MagicBookLootModifier` (reads the pools), `SpellLootRarity` (UNCOMMON exists), `SpellCosts` (reads `SpellType`), the radial menu, Grimoire and creative tab (all loop over `SpellType.values()`), `ModSounds` and `sounds.json` (a reused sound), recipes (books are not crafted), and the network protocol version.

## Pre-flight table (the controller writes it to the ledger)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2, T3 | `Seal(UUID owner, String ownerName, int rank, int x, int y, int z, int radius, long createdAt)`; `SealRules.radius(int)`, `cap(int)`, `covers(Seal, int, int, int)`, `breaks(int, int)`, `decide(List<Seal>, UUID, int, boolean)`, `toEvict(int, int)`, `MAX_RADIUS`, `Verdict {PASS, REFUSE, BREAK}`, `Decision(Verdict verdict, int target)` | names identical |
| T2 → T3 | `SealData.get(ServerLevel)`, `isEmpty()`; `ColloportusSpell.Gate {PASS, REFUSED, BROKEN}`, `alohomoraGate(ServerLevel, BlockPos, Entity, int)`, `bypassId(Entity)`, `sealedAgainst(SealData, BlockPos, UUID)`, `revealSeals(ServerLevel, BlockPos, ServerPlayer)`, `strongestSeal(ServerLevel, BlockPos)` | identical; all written in T2 |
| T2 | `CircuitTracer.doorPistons(ServerLevel, BlockPos)` made public (it was private) | only visibility and javadoc change |
| T2 → T3 | `SpellBeamEntity` is edited by both: T2 in the block-hit chain and the living-hit fizzle, T3 in the Revelio branch | T3 anchors are quoted from HEAD and untouched by T2 |
| T2 | the texture `textures/item/magic_books/magic_book_colloportus.png` exists (5d716bc) | `test -f` in T2 step 1 |

---

### Task 1: SealRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/seal/Seal.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/seal/SealRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/seal/SealRulesTest.java`

**Interfaces.** Consumes: nothing. Produces: the record and methods in the pre-flight table.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/spell/support/seal/SealRulesTest.java`:

```java
package com.hogwartsmagic.spell.support.seal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SealRulesTest {
    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);
    private static final UUID CARA = new UUID(0L, 3L);

    private static Seal seal(UUID owner, int rank) {
        return new Seal(owner, "p", rank, 0, 64, 0, SealRules.radius(rank), 0L);
    }

    @Test
    void radiusGrowsWithRank() {
        assertEquals(1, SealRules.radius(1));
        assertEquals(2, SealRules.radius(2));
        assertEquals(3, SealRules.radius(3));
        assertEquals(4, SealRules.radius(4));
        assertEquals(SealRules.MAX_RADIUS, SealRules.radius(4));
    }

    @Test
    void capDoublesWithRank() {
        assertEquals(1, SealRules.cap(1));
        assertEquals(2, SealRules.cap(2));
        assertEquals(4, SealRules.cap(3));
        assertEquals(8, SealRules.cap(4));
    }

    @Test
    void ranksOutsideOneToFourClamp() {
        assertEquals(1, SealRules.radius(0));
        assertEquals(4, SealRules.radius(9));
        assertEquals(1, SealRules.cap(-3));
        assertEquals(8, SealRules.cap(5));
    }

    @Test
    void coversASphereAroundTheTarget() {
        Seal s = new Seal(ALICE, "p", 2, 10, 64, -5, 2, 0L);
        assertTrue(SealRules.covers(s, 10, 64, -5));
        assertTrue(SealRules.covers(s, 12, 64, -5)); // straight out to the radius
        assertTrue(SealRules.covers(s, 11, 65, -4)); // 1 + 1 + 1 = 3 <= 4
        assertFalse(SealRules.covers(s, 12, 65, -5)); // 4 + 1 = 5 > 4
        assertFalse(SealRules.covers(s, 10, 67, -5));
        Seal novice = new Seal(ALICE, "p", 1, 0, 64, 0, 1, 0L);
        assertTrue(SealRules.covers(novice, 0, 65, 0)); // the other half of a door
        assertFalse(SealRules.covers(novice, 1, 65, 0));
    }

    @Test
    void breaksOnlyAtAStrictlyHigherRank() {
        assertTrue(SealRules.breaks(2, 1));
        assertTrue(SealRules.breaks(4, 3));
        assertFalse(SealRules.breaks(1, 1));
        assertFalse(SealRules.breaks(3, 4));
        assertFalse(SealRules.breaks(4, 4)); // nothing breaks a Master seal
    }

    @Test
    void noSealPasses() {
        SealRules.Decision d = SealRules.decide(List.of(), ALICE, 1, true);
        assertEquals(SealRules.Verdict.PASS, d.verdict());
        assertEquals(-1, d.target());
    }

    @Test
    void theSealerPassesTheirOwnSeal() {
        assertEquals(SealRules.Verdict.PASS, SealRules.decide(List.of(seal(ALICE, 4)), ALICE, 1, true).verdict());
    }

    @Test
    void anEqualOrLowerRankIsRefused() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2)), ALICE, 2, true);
        assertEquals(SealRules.Verdict.REFUSE, d.verdict());
        assertEquals(0, d.target());
        assertEquals(SealRules.Verdict.REFUSE, SealRules.decide(List.of(seal(BOB, 3)), ALICE, 1, true).verdict());
        assertEquals(SealRules.Verdict.REFUSE, SealRules.decide(List.of(seal(BOB, 2)), null, 2, true).verdict()); // an NPC
    }

    @Test
    void aHigherRankBreaksTheSeal() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2)), ALICE, 3, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(0, d.target());
        assertEquals(SealRules.Verdict.BREAK, SealRules.decide(List.of(seal(BOB, 1)), null, 3, true).verdict()); // a boss NPC
    }

    @Test
    void theHighestCoveringSealDecides() {
        List<Seal> covering = List.of(seal(BOB, 1), seal(CARA, 3), seal(BOB, 2));
        SealRules.Decision expert = SealRules.decide(covering, ALICE, 3, true);
        assertEquals(SealRules.Verdict.REFUSE, expert.verdict());
        assertEquals(1, expert.target());
        SealRules.Decision master = SealRules.decide(covering, ALICE, 4, true);
        assertEquals(SealRules.Verdict.BREAK, master.verdict());
        assertEquals(1, master.target());
    }

    @Test
    void ownSealsAreIgnoredAmongOthers() {
        SealRules.Decision d = SealRules.decide(List.of(seal(ALICE, 4), seal(BOB, 1)), ALICE, 2, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(1, d.target());
    }

    @Test
    void theOldestOfEqualSealsBreaksFirst() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2), seal(CARA, 2)), ALICE, 3, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void aPuppetCastNeitherPassesNorBreaks() {
        // Through a puppet the caster counts as nobody (null) and may not break.
        SealRules.Decision d = SealRules.decide(List.of(seal(ALICE, 1)), null, 4, false);
        assertEquals(SealRules.Verdict.REFUSE, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void evictionsKeepTheCap() {
        assertEquals(0, SealRules.toEvict(0, 1));
        assertEquals(1, SealRules.toEvict(1, 1));
        assertEquals(0, SealRules.toEvict(3, 4));
        assertEquals(1, SealRules.toEvict(4, 4));
        assertEquals(2, SealRules.toEvict(9, 8)); // more than the cap after a rank drop
        assertEquals(2, SealRules.toEvict(2, 1));
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Run the gradle check, then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew test --tests "*SealRulesTest" --console=plain`. Expected: compilation failure (no `Seal`, no `SealRules`).

- [ ] **Step 3: Write `Seal`.** Create `src/main/java/com/hogwartsmagic/spell/support/seal/Seal.java`:

```java
package com.hogwartsmagic.spell.support.seal;

import java.util.UUID;

/**
 * One Colloportus seal: every block within radius of (x, y, z) is sealed. ownerName is the caster's name when
 * the seal was made; createdAt is the game time then. Pure data.
 */
public record Seal(UUID owner, String ownerName, int rank, int x, int y, int z, int radius, long createdAt) {
}
```

- [ ] **Step 4: Write `SealRules`.** Create `src/main/java/com/hogwartsmagic/spell/support/seal/SealRules.java`:

```java
package com.hogwartsmagic.spell.support.seal;

import java.util.List;
import java.util.UUID;

/**
 * Colloportus: how far a seal reaches and how many a caster keeps at each rank, which blocks a seal covers, and
 * what an Alohomora does against the seals over a lock. Pure numbers.
 */
public final class SealRules {
    public static final int MAX_RADIUS = 4;

    private static final int[] RADIUS = {1, 2, 3, 4};
    private static final int[] CAP = {1, 2, 4, 8};

    public enum Verdict { PASS, REFUSE, BREAK }

    /** target: the index, in the covering list, of the seal that refuses or breaks; -1 for PASS. */
    public record Decision(Verdict verdict, int target) {
    }

    private SealRules() {
    }

    public static int radius(int rank) {
        return RADIUS[index(rank)];
    }

    /** Seals one caster may keep at once (across all dimensions). */
    public static int cap(int rank) {
        return CAP[index(rank)];
    }

    /** Block (x, y, z) lies in the seal: its centre is at most the radius from the seal's centre. */
    public static boolean covers(Seal seal, int x, int y, int z) {
        long dx = x - seal.x();
        long dy = y - seal.y();
        long dz = z - seal.z();
        long r = seal.radius();
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** Only a strictly higher Alohomora breaks a seal, so nothing breaks a Master seal. */
    public static boolean breaks(int alohomoraRank, int sealRank) {
        return alohomoraRank > sealRank;
    }

    /**
     * An Alohomora at rank against the seals covering its lock, oldest first. caster: the casting player, or null
     * for an NPC or a puppet cast (they own no seal). The caster's own seals let it through. Of the others the
     * highest rank decides, the oldest of equals first: a strictly higher Alohomora breaks that one seal when
     * mayBreak, otherwise the cast is refused.
     */
    public static Decision decide(List<Seal> covering, UUID caster, int rank, boolean mayBreak) {
        int target = -1;
        for (int i = 0; i < covering.size(); i++) {
            Seal seal = covering.get(i);
            if (caster != null && caster.equals(seal.owner())) {
                continue;
            }
            if (target < 0 || seal.rank() > covering.get(target).rank()) {
                target = i;
            }
        }
        if (target < 0) {
            return new Decision(Verdict.PASS, -1);
        }
        if (mayBreak && breaks(rank, covering.get(target).rank())) {
            return new Decision(Verdict.BREAK, target);
        }
        return new Decision(Verdict.REFUSE, target);
    }

    /** How many of a caster's oldest seals must go so one more fits under cap (more than one after a rank drop). */
    public static int toEvict(int existing, int cap) {
        return Math.max(0, existing - Math.max(1, cap) + 1);
    }

    private static int index(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

- [ ] **Step 5: Run it and see it pass.** Run `timeout 900 ./gradlew test --tests "*SealRulesTest" --console=plain`. Expected: PASS (14 tests). Then run `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, 333 tests (319 + 14), all green.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/seal/Seal.java src/main/java/com/hogwartsmagic/spell/support/seal/SealRules.java src/test/java/com/hogwartsmagic/spell/support/seal/SealRulesTest.java
git commit -m "Add the Colloportus seal rules: radius, cap, cover test and the Alohomora decision" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The Colloportus spell, its seals and its book

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/seal/SealData.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/seal/ColloportusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`
- Modify: `src/main/java/com/hogwartsmagic/item/MagicBookItem.java`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/magic_book_colloportus.json`
- Modify: `src/main/resources/assets/hogwartsmagic/models/item/magic_book.json`
- Modify: `src/main/java/com/hogwartsmagic/loot/MagicBookChestLoot.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/ModVillagers.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: Task 1. Produces: `SealData` (`get`, `isEmpty`, `seals`, `covering`, `ownedBy`, `add`, `remove`), `ColloportusSpell` (`Cast`, `Gate`, `onBlockHit`, `lockPositions`, `alohomoraGate`, `bypassId`, `sealedAgainst`, `revealSeals`, `strongestSeal`), public `CircuitTracer.doorPistons`, and `SpellType.COLLOPORTUS`.

This task adds the spell and its book, and makes and lifts seals. Nothing reads the seals yet (Task 3), so Alohomora is still unaffected when this task ends.

- [ ] **Step 1: Check the texture is there.** Run `test -f src/main/resources/assets/hogwartsmagic/textures/item/magic_books/magic_book_colloportus.png && echo ok`. Expected: `ok` (committed in 5d716bc). If it is missing, stop and report BLOCKED.

- [ ] **Step 2: Make the piston scan public.** In `CircuitTracer.java`, find:

```java
    private static List<BlockPos> doorPistons(ServerLevel level, BlockPos hit) {
```

Replace with:

```java
    /** The loaded pistons near hit that count as the door, nearest first, at most MAX_DOOR_PISTONS. Also read by Colloportus. */
    public static List<BlockPos> doorPistons(ServerLevel level, BlockPos hit) {
```

- [ ] **Step 3: `SealData`.** Create `src/main/java/com/hogwartsmagic/spell/support/seal/SealData.java`:

```java
package com.hogwartsmagic.spell.support.seal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The Colloportus seals of one dimension, oldest first, saved with the world. Seals are few (at most 8 per player),
 * so this plain list is the index: a lookup is one integer distance test per seal. Server thread only.
 */
public final class SealData extends SavedData {
    public static final String DATA_NAME = "hogwartsmagic_colloportus_seals";

    private final List<Seal> seals = new ArrayList<>();

    public static SealData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SealData::new, SealData::load, null), DATA_NAME);
    }

    public boolean isEmpty() {
        return seals.isEmpty();
    }

    /** Every seal in this dimension, oldest first (read only). */
    public List<Seal> seals() {
        return Collections.unmodifiableList(seals);
    }

    /** The seals that cover at least one of positions, oldest first. */
    public List<Seal> covering(Collection<BlockPos> positions) {
        List<Seal> found = new ArrayList<>();
        for (Seal seal : seals) {
            for (BlockPos pos : positions) {
                if (SealRules.covers(seal, pos.getX(), pos.getY(), pos.getZ())) {
                    found.add(seal);
                    break;
                }
            }
        }
        return found;
    }

    public List<Seal> ownedBy(UUID owner) {
        List<Seal> found = new ArrayList<>();
        for (Seal seal : seals) {
            if (seal.owner().equals(owner)) {
                found.add(seal);
            }
        }
        return found;
    }

    public void add(Seal seal) {
        seals.add(seal);
        setDirty();
    }

    public void remove(Seal seal) {
        if (seals.remove(seal)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Seal seal : seals) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Owner", seal.owner());
            one.putString("Name", seal.ownerName());
            one.putInt("Rank", seal.rank());
            one.putInt("X", seal.x());
            one.putInt("Y", seal.y());
            one.putInt("Z", seal.z());
            one.putInt("R", seal.radius());
            one.putLong("At", seal.createdAt());
            list.add(one);
        }
        tag.put("Seals", list);
        return tag;
    }

    public static SealData load(CompoundTag tag, HolderLookup.Provider registries) {
        SealData data = new SealData();
        ListTag list = tag.getList("Seals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (!one.hasUUID("Owner")) {
                continue;
            }
            int rank = Math.max(1, Math.min(4, one.getInt("Rank")));
            int radius = Math.max(1, Math.min(SealRules.MAX_RADIUS, one.getInt("R")));
            data.seals.add(new Seal(one.getUUID("Owner"), one.getString("Name"), rank,
                    one.getInt("X"), one.getInt("Y"), one.getInt("Z"), radius, one.getLong("At")));
        }
        return data;
    }
}
```

- [ ] **Step 4: `ColloportusSpell`.** Create `src/main/java/com/hogwartsmagic/spell/support/seal/ColloportusSpell.java`:

```java
package com.hogwartsmagic.spell.support.seal;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.support.lock.CircuitTracer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Colloportus, the locking charm. A beam at a door, trapdoor or fence gate, or at any block of a piston door (a
 * door piston within LockRules.DOOR_RADIUS), seals every block within the rank's radius against magic. An
 * Alohomora there is refused, or, cast at a strictly higher rank, breaks the seal instead of opening. The sealer
 * passes their own seal and lifts it by casting Colloportus inside it again. Hands, keys and redstone are
 * untouched. Seals last until broken or lifted (SealData). Players only, never through a puppet. No protection
 * checks, as Alohomora. Server only.
 */
public final class ColloportusSpell {
    /** What a Colloportus beam did: made a seal (pays XP), lifted the caster's own (no XP), or nothing (fizzle). */
    public enum Cast { SEALED, LIFTED, NOTHING }

    /** What the seals over a lock do to one Alohomora: let it through, refuse it, or lose one seal to it. */
    public enum Gate { PASS, REFUSED, BROKEN }

    private static final int MAX_REVEAL_LINES = 4;

    private ColloportusSpell() {
    }

    /** The Colloportus beam struck pos (a loaded block). */
    public static Cast onBlockHit(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster, boolean reflected) {
        if (reflected || MindControl.hitPuppeteer() != null
                || !(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return Cast.NOTHING; // players only, never a reflected beam, never through a puppet
        }
        SealData data = SealData.get(level);
        List<Seal> mine = new ArrayList<>();
        for (Seal seal : data.covering(List.of(pos))) {
            if (seal.owner().equals(player.getUUID())) {
                mine.add(seal);
            }
        }
        if (!mine.isEmpty()) {
            mine.forEach(data::remove);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.3f);
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
            tell(player, "lifted", ChatFormatting.GRAY);
            return Cast.LIFTED;
        }
        if (!isLock(level, pos, state)) {
            tell(player, "no_lock", ChatFormatting.GOLD);
            return Cast.NOTHING;
        }
        MinecraftServer server = level.getServer();
        int rank = DisableSupport.casterRank(player, SpellType.COLLOPORTUS);
        int cap = SealRules.cap(rank);
        int evicted = evictOldest(server, player.getUUID(), SealRules.toEvict(countAll(server, player.getUUID()), cap));
        Seal seal = new Seal(player.getUUID(), player.getGameProfile().getName(), rank,
                pos.getX(), pos.getY(), pos.getZ(), SealRules.radius(rank), level.getGameTime());
        data.add(seal);
        sealEffects(level, seal);
        player.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus." + (evicted > 0 ? "replaced" : "sealed"),
                countAll(server, player.getUUID()), cap).withStyle(ChatFormatting.AQUA));
        return Cast.SEALED;
    }

    /** A door, trapdoor or fence gate, or a block with a door piston near it (the piston-lock search). */
    private static boolean isLock(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || CircuitTracer.hasDoorPiston(level, pos);
    }

    /** The lock an Alohomora, Revelio or /lockinfo at hit works on: hit itself and the door pistons near it. */
    public static List<BlockPos> lockPositions(ServerLevel level, BlockPos hit) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(hit.immutable());
        positions.addAll(CircuitTracer.doorPistons(level, hit));
        return positions;
    }

    /**
     * Before an Alohomora at hit touches the lock (its mana is already spent). PASS: no seal, or only the caster's
     * own. REFUSED: the caster was told. BROKEN: the highest seal it beat is gone, and this cast does nothing else.
     * Through a puppet there is no owner bypass and no break.
     */
    public static Gate alohomoraGate(ServerLevel level, BlockPos hit, @Nullable Entity caster, int rank) {
        SealData data = SealData.get(level);
        if (data.isEmpty()) {
            return Gate.PASS; // no seal in this dimension: no piston scan at all
        }
        List<Seal> covering = data.covering(lockPositions(level, hit));
        if (covering.isEmpty()) {
            return Gate.PASS;
        }
        boolean puppet = MindControl.hitPuppeteer() != null;
        Entity responsible = CombatEffects.responsible(caster);
        SealRules.Decision decision = SealRules.decide(covering, bypassId(caster), rank, !puppet);
        if (decision.verdict() == SealRules.Verdict.PASS) {
            return Gate.PASS;
        }
        ServerPlayer player = responsible instanceof ServerPlayer sp ? sp : null;
        if (decision.verdict() == SealRules.Verdict.REFUSE) {
            level.playSound(null, hit, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 0.6f);
            if (player != null) {
                tell(player, "refused", ChatFormatting.GOLD);
            }
            return Gate.REFUSED;
        }
        Seal seal = covering.get(decision.target());
        data.remove(seal);
        BlockPos centre = new BlockPos(seal.x(), seal.y(), seal.z());
        level.playSound(null, centre, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.playSound(null, centre, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.7f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5,
                24, 0.5, 0.5, 0.5, 0.15);
        if (player != null) {
            tell(player, "broken", ChatFormatting.AQUA);
        }
        ServerPlayer sealer = level.getServer().getPlayerList().getPlayer(seal.owner());
        if (sealer != null && sealer != player) {
            sealer.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus.broken_yours",
                    responsible != null ? responsible.getDisplayName() : Component.literal("?")).withStyle(ChatFormatting.GOLD));
        }
        return Gate.BROKEN;
    }

    /** The player whose own seals let this cast through; null for an NPC or a puppet cast. */
    @Nullable
    public static UUID bypassId(@Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        return MindControl.hitPuppeteer() == null && responsible instanceof Player p ? p.getUUID() : null;
    }

    /** Alohomora's Expert area: a seal not owned by bypass covers pos (the area never passes or breaks one). */
    public static boolean sealedAgainst(SealData data, BlockPos pos, @Nullable UUID bypass) {
        for (Seal seal : data.seals()) {
            if (!seal.owner().equals(bypass) && SealRules.covers(seal, pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** Revelio at hit: outlines every seal over this lock and names its sealer and rank. False if there is none. */
    public static boolean revealSeals(ServerLevel level, BlockPos hit, ServerPlayer viewer) {
        SealData data = SealData.get(level);
        if (data.isEmpty()) {
            return false;
        }
        List<Seal> covering = data.covering(lockPositions(level, hit));
        if (covering.isEmpty()) {
            return false;
        }
        for (int i = 0; i < covering.size(); i++) {
            Seal seal = covering.get(i);
            outline(level, seal);
            if (i < MAX_REVEAL_LINES) {
                viewer.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus.revealed",
                        seal.ownerName(), Component.translatable(MasteryAwards.rankKey(seal.rank())))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        level.playSound(null, hit, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.4f);
        return true;
    }

    /** The highest seal over the lock at hit (the oldest of equals), or null: for /lockinfo. */
    @Nullable
    public static Seal strongestSeal(ServerLevel level, BlockPos hit) {
        SealData data = SealData.get(level);
        if (data.isEmpty()) {
            return null;
        }
        Seal best = null;
        for (Seal seal : data.covering(lockPositions(level, hit))) {
            if (best == null || seal.rank() > best.rank()) {
                best = seal;
            }
        }
        return best;
    }

    private static int countAll(MinecraftServer server, UUID owner) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += SealData.get(level).ownedBy(owner).size();
        }
        return count;
    }

    /** Removes the owner's count oldest seals, in any dimension (all dimensions share one game time). */
    private static int evictOldest(MinecraftServer server, UUID owner, int count) {
        int removed = 0;
        for (int i = 0; i < count; i++) {
            SealData oldestData = null;
            Seal oldest = null;
            for (ServerLevel level : server.getAllLevels()) {
                SealData data = SealData.get(level);
                for (Seal seal : data.ownedBy(owner)) {
                    if (oldest == null || seal.createdAt() < oldest.createdAt()) {
                        oldest = seal;
                        oldestData = data;
                    }
                }
            }
            if (oldest == null) {
                break;
            }
            oldestData.remove(oldest);
            removed++;
        }
        return removed;
    }

    /** A new seal: an iron door shuts and a chain settles, and a ring shows how far it reaches. */
    private static void sealEffects(ServerLevel level, Seal seal) {
        BlockPos pos = new BlockPos(seal.x(), seal.y(), seal.z());
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9f, 0.8f);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 0.7f);
        double cx = seal.x() + 0.5;
        double cy = seal.y() + 0.5;
        double cz = seal.z() + 0.5;
        int points = 16 + 8 * seal.radius();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            level.sendParticles(ParticleTypes.WAX_ON, cx + Math.cos(a) * seal.radius(), cy, cz + Math.sin(a) * seal.radius(),
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, cx, cy, cz, 20, 0.3, 0.4, 0.3, 0.4);
    }

    /** Revelio: one burst of points spread evenly over the seal's sphere (a Fibonacci sphere). */
    private static void outline(ServerLevel level, Seal seal) {
        int r = seal.radius();
        int points = 24 + 12 * r * r;
        double cx = seal.x() + 0.5;
        double cy = seal.y() + 0.5;
        double cz = seal.z() + 0.5;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < points; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / points;
            double ring = Math.sqrt(1.0 - y * y);
            double a = golden * i;
            level.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * ring * r, cy + y * r, cz + Math.sin(a) * ring * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
        player.sendSystemMessage(Component.translatable("message.hogwartsmagic.colloportus." + key).withStyle(colour));
    }
}
```

Notes for the implementer:
- `seal.owner().equals(bypass)` is false when `bypass` is null, as an NPC or puppet cast needs.
- Alohomora is not wired to `alohomoraGate`, `sealedAgainst`, `revealSeals` or `strongestSeal` yet; Task 3 does that.

- [ ] **Step 5: The spell type.** In `SpellType.java`, find:

```java
    PIERTOTUM_LOCOMOTOR("piertotum_locomotor", 0x9BA0A4, 0.61f, 0.63f, 0.64f, ParticleTypes.SMOKE, ParticleTypes.CAMPFIRE_COSY_SMOKE, 600, 90, SpellLootRarity.RARE);
```

Replace with:

```java
    PIERTOTUM_LOCOMOTOR("piertotum_locomotor", 0x9BA0A4, 0.61f, 0.63f, 0.64f, ParticleTypes.SMOKE, ParticleTypes.CAMPFIRE_COSY_SMOKE, 600, 90, SpellLootRarity.RARE),
    /** The locking charm: seals a door or a piston door lock against Alohomora (see spell.support.seal.ColloportusSpell).
     *  Everything happens on a block hit, so it has no entity hit effect; a living target is a miss. */
    COLLOPORTUS("colloportus", 0x8A6D3B, 0.54f, 0.43f, 0.23f, ParticleTypes.ENCHANT, ParticleTypes.WAX_ON, 60, 15, SpellLootRarity.UNCOMMON);
```

Then find:

```java
            case PIERTOTUM_LOCOMOTOR -> {
                // No hit effect: the charm animates masonry, not creatures. A living target is a miss, and
                // SpellBeamEntity.onHit fizzles the beam for it rather than routing through here.
            }
```

Replace with:

```java
            case PIERTOTUM_LOCOMOTOR -> {
                // No hit effect: the charm animates masonry, not creatures. A living target is a miss, and
                // SpellBeamEntity.onHit fizzles the beam for it rather than routing through here.
            }
            case COLLOPORTUS -> {
                // No hit effect: the charm seals locks, not creatures (SpellBeamEntity fizzles it on a living target).
            }
```

Then find:

```java
            case PIERTOTUM_LOCOMOTOR -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
        };
```

Replace with:

```java
            case PIERTOTUM_LOCOMOTOR -> ModSounds.SPELL_WINGARDIUM_LEVIOSA.get();
            case COLLOPORTUS -> ModSounds.SPELL_ALOHOMORA.get(); // the counter-charm shares the unlocking charm's voice
        };
```

- [ ] **Step 6: The beam.** In `SpellBeamEntity.java`, find:

```java
                        hitSuccessful = com.hogwartsmagic.util.StatueMultiblock.tryRaise(sl, pos,
                                getOwner() instanceof ServerPlayer caster ? caster : null);
                    }
                }
            } else if (result instanceof EntityHitResult entityHit && livingBehind(entityHit.getEntity()) instanceof LivingEntity living) {
```

Replace with:

```java
                        hitSuccessful = com.hogwartsmagic.util.StatueMultiblock.tryRaise(sl, pos,
                                getOwner() instanceof ServerPlayer caster ? caster : null);
                    }
                } else if (spell == SpellType.COLLOPORTUS) {
                    // Seals the lock here against Alohomora; inside the caster's own seal it lifts it instead (no XP).
                    if (this.level() instanceof ServerLevel sealLevel) {
                        com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast cast =
                                com.hogwartsmagic.spell.support.seal.ColloportusSpell.onBlockHit(
                                        sealLevel, pos, state, getOwner(), this.reflected);
                        hitSuccessful = cast != com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast.NOTHING;
                        masteryEligible = cast == com.hogwartsmagic.spell.support.seal.ColloportusSpell.Cast.SEALED;
                    }
                }
            } else if (result instanceof EntityHitResult entityHit && livingBehind(entityHit.getEntity()) instanceof LivingEntity living) {
```

Then find:

```java
                        if (spell == SpellType.MORSMORDRE || spell == SpellType.PIERTOTUM_LOCOMOTOR) {
```

Replace with:

```java
                        if (spell == SpellType.MORSMORDRE || spell == SpellType.PIERTOTUM_LOCOMOTOR
                                || spell == SpellType.COLLOPORTUS) {
```

(XP: the existing `if (getOwner() instanceof ServerPlayer masteryCaster && !this.reflected && this.puppeteer == null)` block pays `onBlockEffect` when `masteryEligible`. That is players only, never reflected, and never a puppet beam.)

- [ ] **Step 7: Mastery.** In `MasteryAwards.java`, find:

```java
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA, SpellType.BOMBARDA, SpellType.DIFFINDO);
```

Replace with:

```java
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA, SpellType.BOMBARDA, SpellType.DIFFINDO,
            SpellType.COLLOPORTUS);
```

- [ ] **Step 8: The book.** In `MagicBookItem.java`, find:

```java
            "apparition",
            "piertotum_locomotor"
    );
```

Replace with:

```java
            "apparition",
            "piertotum_locomotor",
            "colloportus"
    );
```

Create `src/main/resources/assets/hogwartsmagic/models/item/magic_book_colloportus.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/magic_books/magic_book_colloportus"
  }
}
```

In `models/item/magic_book.json`, find:

```json
    {
      "predicate": {
        "custom_model_data": 38.0
      },
      "model": "hogwartsmagic:item/magic_book_piertotum_locomotor"
    }
  ]
}
```

Replace with:

```json
    {
      "predicate": {
        "custom_model_data": 38.0
      },
      "model": "hogwartsmagic:item/magic_book_piertotum_locomotor"
    },
    {
      "predicate": {
        "custom_model_data": 39.0
      },
      "model": "hogwartsmagic:item/magic_book_colloportus"
    }
  ]
}
```

(Vanilla tests overrides from the last entry backwards, so 39 must come after 38.)

- [ ] **Step 9: Loot and the Bookseller.** In `MagicBookChestLoot.java`, find:

```java
            SpellType.PIERTOTUM_LOCOMOTOR
    };
```

Replace with:

```java
            SpellType.PIERTOTUM_LOCOMOTOR,
            // The locking charm sits on the shelf beside the unlocking one.
            SpellType.COLLOPORTUS
    };
```

Find:

```java
            SpellType.INCENDIO, SpellType.EXPELLIARMUS, SpellType.ALOHOMORA, SpellType.ARRESTO_MOMENTUM
    };
```

Replace with:

```java
            SpellType.INCENDIO, SpellType.EXPELLIARMUS, SpellType.ALOHOMORA, SpellType.ARRESTO_MOMENTUM,
            SpellType.COLLOPORTUS
    };
```

Find:

```java
            SpellType.AQUA_ERUCTO,
            SpellType.REPARO
    };
```

Replace with:

```java
            SpellType.AQUA_ERUCTO,
            SpellType.REPARO,
            SpellType.COLLOPORTUS
    };
```

In `ModVillagers.java`, find:

```java
                    SpellType.REPARO, SpellType.STUPEFY);
```

Replace with:

```java
                    SpellType.REPARO, SpellType.STUPEFY, SpellType.COLLOPORTUS);
```

(This is the Bookseller's level-3 `advanced` list. The Wandmaker's list ends `SpellType.EXPELLIARMUS, SpellType.STUPEFY, SpellType.REPARO);`, so the anchor is unique.)

- [ ] **Step 10: Text.** In `en_us.json`, find the last entry:

```
  "spell.hogwartsmagic.piertotum_locomotor.rank.4": "Only the rank discount applies: less mana and a faster recharge."
}
```

Replace with:

```
  "spell.hogwartsmagic.piertotum_locomotor.rank.4": "Only the rank discount applies: less mana and a faster recharge.",
  "spell.hogwartsmagic.colloportus": "Colloportus",
  "spell.hogwartsmagic.colloportus.desc": "The locking charm. Cast at a door, trapdoor, fence gate or any block of a piston door to seal it against magic: Alohomora is refused there, and one cast at a higher rank only breaks the seal. Hands, keys, buttons and redstone still work, and Revelio shows who sealed it. A seal lasts until it is broken or you lift it by casting Colloportus inside it again. Novice: the seal reaches 1 block around the target and you keep one at a time.",
  "spell.hogwartsmagic.colloportus.rank.2": "Seals reach 2 blocks and you can keep 2; only an Expert or Master Alohomora breaks them.",
  "spell.hogwartsmagic.colloportus.rank.3": "Seals reach 3 blocks and you can keep 4; only a Master Alohomora breaks them.",
  "spell.hogwartsmagic.colloportus.rank.4": "Seals reach 4 blocks and you can keep 8, and no Alohomora can break them.",
  "message.hogwartsmagic.colloportus.no_lock": "There is no lock here to seal.",
  "message.hogwartsmagic.colloportus.sealed": "The lock seals shut (%s of %s seals).",
  "message.hogwartsmagic.colloportus.replaced": "The lock seals shut, and your oldest seal fades (%s of %s seals).",
  "message.hogwartsmagic.colloportus.refused": "This lock is sealed by Colloportus.",
  "message.hogwartsmagic.colloportus.broken": "Your Alohomora breaks the Colloportus seal.",
  "message.hogwartsmagic.colloportus.broken_yours": "%s broke one of your Colloportus seals.",
  "message.hogwartsmagic.colloportus.revealed": "Sealed by Colloportus: %s, %s.",
  "message.hogwartsmagic.colloportus.lifted": "You lift your seal."
}
```

Validate:
- `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`
- `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/models/item/magic_book.json',encoding='utf-8'))"`
- `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/models/item/magic_book_colloportus.json',encoding='utf-8'))"`

Expected: no output from any of them.

- [ ] **Step 11: Build, greps and the boot.**
- Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, 333 tests, all green. `RankChangeTextTest` now also covers `colloportus`.
- Run `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch" src/main/java/com/hogwartsmagic/spell/support/seal/`. Expected: nothing.
- Run `grep -c "custom_model_data" src/main/resources/assets/hogwartsmagic/models/item/magic_book.json`. Expected: `38`.
- Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, with no `Exception` or `ERROR` naming `Seal`, `Colloportus` or `SpellType`. Afterwards no server java is left.

- [ ] **Step 12: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/seal/SealData.java src/main/java/com/hogwartsmagic/spell/support/seal/ColloportusSpell.java src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/item/MagicBookItem.java src/main/resources/assets/hogwartsmagic/models/item/magic_book_colloportus.json src/main/resources/assets/hogwartsmagic/models/item/magic_book.json src/main/java/com/hogwartsmagic/loot/MagicBookChestLoot.java src/main/java/com/hogwartsmagic/entity/ModVillagers.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add Colloportus: seal doors and piston door locks, lift your own seal, and its spellbook" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Seals refuse Alohomora; Revelio and /lockinfo see them

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (the Revelio branch only)
- Modify: `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java`

**Interfaces.** Consumes: the `ColloportusSpell` and `SealData` methods from Task 2. Produces: nothing new.

- [ ] **Step 1: Imports in `AlohomoraSpell`.** Find:

```java
import com.hogwartsmagic.spell.mastery.MasteryAwards;
```

Replace with:

```java
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.support.seal.ColloportusSpell;
import com.hogwartsmagic.spell.support.seal.SealData;
```

- [ ] **Step 2: The gate.** In `onBlockHit`, find:

```java
        if (state.is(ExampleMod.WARD.get()) && level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
            return owner instanceof ServerPlayer player && suppressWard(level, ward, player, rank);
        }
        boolean opened = openOne(level, pos, state, owner, rank);
```

Replace with:

```java
        if (state.is(ExampleMod.WARD.get()) && level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
            return owner instanceof ServerPlayer player && suppressWard(level, ward, player, rank);
        }
        // Colloportus: a sealed lock (this block or its door pistons) refuses the charm before anything opens or
        // is traced; a strictly higher rank breaks one seal instead, and that cast does nothing else.
        ColloportusSpell.Gate gate = ColloportusSpell.alohomoraGate(level, pos, caster, rank);
        if (gate != ColloportusSpell.Gate.PASS) {
            return gate == ColloportusSpell.Gate.BROKEN;
        }
        boolean opened = openOne(level, pos, state, owner, rank);
```

This one gate covers the vanilla doors and the piston path: `PistonLockSpell.begin(... Use.OPEN)` is only reached below it, so a refused cast never traces, solves or starts a hold. The hold-to-pick client latch still waits for its release, but no job exists for it.

- [ ] **Step 3: The Expert area skips other casters' seals.** Find:

```java
    private static int openAround(ServerLevel level, BlockPos center, int radius, @Nullable Entity owner, int rank) {
        int opened = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (opened >= AlohomoraRules.MAX_AREA_OPENINGS) {
                break;
            }
            if (pos.equals(center) || !level.isLoaded(pos)) {
                continue;
            }
```

Replace with:

```java
    private static int openAround(ServerLevel level, BlockPos center, int radius, @Nullable Entity owner, int rank) {
        int opened = 0;
        SealData seals = SealData.get(level);
        UUID bypass = ColloportusSpell.bypassId(owner);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (opened >= AlohomoraRules.MAX_AREA_OPENINGS) {
                break;
            }
            if (pos.equals(center) || !level.isLoaded(pos)) {
                continue;
            }
            if (!seals.isEmpty() && ColloportusSpell.sealedAgainst(seals, pos, bypass)) {
                continue; // the area never opens, passes or breaks another caster's seal
            }
```

(`java.util.UUID` is already imported in `AlohomoraSpell`.)

- [ ] **Step 4: Revelio shows a seal instead of the lock's hints.** In `SpellBeamEntity.java`, find:

```java
                        if (getOwner() instanceof ServerPlayer revealer
                                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(revelioRank)
                                && com.hogwartsmagic.spell.mind.MindControl.hitPuppeteer() == null) {
```

Replace with:

```java
                        // A Colloportus seal over this lock is shown (any rank) instead of how the lock opens.
                        boolean sealShown = getOwner() instanceof ServerPlayer sealViewer
                                && com.hogwartsmagic.spell.support.seal.ColloportusSpell.revealSeals(sl, pos, sealViewer);
                        if (!sealShown && getOwner() instanceof ServerPlayer revealer
                                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(revelioRank)
                                && com.hogwartsmagic.spell.mind.MindControl.hitPuppeteer() == null) {
```

- [ ] **Step 5: `/lockinfo` names a seal.** In `LockInfoCommand.java`, find:

```java
        PistonLockSpell.Begin begin = PistonLockSpell.begin(player.serverLevel(), blockHit.getBlockPos(), player, rank,
                PistonLockSpell.Use.INSPECT);
```

Replace with:

```java
        com.hogwartsmagic.spell.support.seal.Seal seal = com.hogwartsmagic.spell.support.seal.ColloportusSpell
                .strongestSeal(player.serverLevel(), blockHit.getBlockPos());
        if (seal != null) {
            // One plain line the user can paste back; nothing is read while a seal holds the lock.
            player.sendSystemMessage(Component.literal("SEALED by " + seal.ownerName() + " (rank " + seal.rank() + ")"));
            return 1;
        }
        PistonLockSpell.Begin begin = PistonLockSpell.begin(player.serverLevel(), blockHit.getBlockPos(), player, rank,
                PistonLockSpell.Use.INSPECT);
```

Also update the class javadoc. Find:

```java
/** /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing. */
```

Replace with:

```java
/**
 * /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing. A lock
 * under a Colloportus seal prints SEALED with its sealer and rank instead.
 */
```

- [ ] **Step 6: Build and greps.**
- Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, 333 tests, all green.
- Run `grep -n "alohomoraGate\|sealedAgainst" src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java`. Expected: one line each.
- Run `grep -n "revealSeals" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`. Expected: one line.

- [ ] **Step 7: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/command/LockInfoCommand.java
git commit -m "Colloportus seals refuse Alohomora, break to a higher rank, show under Revelio and /lockinfo" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Final verification and jar

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew clean build --console=plain`. Expected: BUILD SUCCESSFUL, 333 tests (319 + 14), all green, and no new warnings in the report.
- [ ] **Step 2:** Validate the three JSON files (the commands in Task 2 step 10).
- [ ] **Step 3:** Run the safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell src/main/java/com/hogwartsmagic/command src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`. Expected: the same lines as at 926d4d1 (compare with `git grep -n "net.minecraft.client\|com.hogwartsmagic.client" 926d4d1 -- src/main/java/com/hogwartsmagic/spell src/main/java/com/hogwartsmagic/command src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`), no new lines.
  - `grep -rn "getPersistentData" src/main/java/com/hogwartsmagic/spell/support/seal/`. Expected: nothing.
  - `grep -rn "SubscribeEvent" src/main/java/com/hogwartsmagic/spell/support/seal/`. Expected: nothing (no new handlers).
- [ ] **Step 4:** Run the dedicated-server boot (the CLOUD-OVERRIDES block). Expected: `Done (`, with no mixin, registry or class-loading errors.
- [ ] **Step 5:** Report the jar path `build/libs/hogwartsmagic-1.0.0.jar` for the user to copy into `C:\Users\alonl\curseforge\minecraft\Instances\hogwarts\mods\` with Minecraft closed. No commit.

---

### Task 5: In-game verification (with the user)

This can't be automated. Install the jar from Task 4. Set ranks with `/spellmastery`. Unless a row says otherwise, player A casts Colloportus and player B casts Alohomora.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | creative tab, Grimoire | Find the Colloportus Spellbook, read it | Its own book icon (not the plain book); "Colloportus Spellbook"; reading it learns the spell; it is in the radial menu; the Grimoire shows its description, rank lines and "Stronghold libraries", "Dungeons...", "Common chests" |
| 2 | wooden door; A Novice | A casts at the door | Iron door clank and chain sound, a ring of wax sparkles; chat "The lock seals shut (1 of 1 seals)."; A gains XP |
| 3 | row 2; B Novice | B casts Alohomora at the door, then at its upper half | Both: "This lock is sealed by Colloportus.", the beam fizzles, mana spent, door stays shut |
| 4 | row 2 | B opens the door by hand; wire a button to an iron door sealed the same way and press it | Both open: the seal only stops magic |
| 5 | row 2; B Adept | B casts Alohomora | "Your Alohomora breaks the Colloportus seal.", chain break and sparks; the door stays shut; A sees "B broke one of your Colloportus seals."; B's next cast opens it |
| 6 | row 2 | A casts Alohomora (Novice) | The door opens (own seal passes) |
| 7 | row 2 | A casts Colloportus at the door again | "You lift your seal."; no XP; B's Novice Alohomora now opens it |
| 8 | stone wall, no door or piston within 6 | A casts Colloportus | "There is no lock here to seal."; fizzle; no XP |
| 9 | A Novice, two doors | Seal door 1, then door 2 | "...your oldest seal fades (1 of 1 seals)."; B's Novice Alohomora opens door 1 but not door 2 |
| 10 | 2×2 sticky piston door with a lever lock; A Expert | A casts at a block of the door frame; B (Expert) holds Alohomora at the door | Refused at once: no progress bar, no clicks, no sparks; nothing moves |
| 11 | row 10; B Master | B holds Alohomora | The seal breaks (no picking that cast); the next held cast picks and opens normally |
| 12 | row 10 with A Master | B Master casts Alohomora | The Master seal breaks (a Master Alohomora breaks a Master seal); the next cast picks normally |
| 13 | row 10 | B casts Revelio (any rank) at the door | A sphere of white points around the seal; chat "Sealed by Colloportus: A, Expert."; no lock hints (no glowing levers) |
| 14 | row 10 | Op runs `/lockinfo` at the door | One line `SEALED by A (rank 3)`; no "Reading the lock..." |
| 15 | wooden door; A Novice and C Adept both seal it; B Expert | B casts Alohomora three times | 1st breaks C's seal, 2nd breaks A's, 3rd opens |
| 16 | three wooden doors within 4 blocks; one sealed by A; B Expert | B casts Alohomora at an unsealed door | Every door opens except the sealed one |
| 17 | row 2 | Restart the server | B's Alohomora is still refused; Revelio still shows the seal |
| 18 | a seal in the Nether | B's Alohomora at an Overworld door at the same coordinates | Opens (seals belong to their dimension) |
| 19 | a zombie | A casts Colloportus at it | Fizzles, nothing happens |
| 20 | A possesses B with Imperio Master | Cast Colloportus through B at a door | Fizzles, no seal, no XP |
| 21 | dedicated server and a client | Join, then rows 2 and 3 | No registry or packet mismatch |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Requirement coverage.**
  - 1 (a new spell): Task 2 (`SpellType.COLLOPORTUS`, beam branch, lang, mastery); rows 1, 2 and 8.
  - 2 (the book like the others): Task 2 steps 1 and 8 (model, override 39, model order), and step 9 (loot); row 1.
  - 3 (a sealed lock refuses Alohomora, doors and pistons): Task 3 steps 2 and 3; rows 3, 10, 15 and 16.
- **Controller rulings.**
  - Name, id, beam, UNCOMMON, mana and cooldown: Task 2 step 5 and decision 1.
  - Sphere radius per rank, what can be sealed, no-lock line and no XP: Task 1 (`radius`), Task 2 (`isLock`, `no_lock`, `Cast.NOTHING`); rows 2 and 8.
  - Refusal before any trace, every rank and path, mana spent: Task 3 step 2 (the gate sits above `openOne` and `PistonLockSpell.begin`); rows 3 and 10.
  - Revelio and /lockinfo: Task 3 steps 4 and 5; rows 13 and 14.
  - Break at a strictly higher rank, one seal per cast (the highest), owner bypass, lifting: `SealRules.decide` (Task 1), `alohomoraGate` and `onBlockHit` (Task 2); rows 5, 6, 7, 11, 12 and 15.
  - Permanent per-dimension SavedData, cap with oldest replaced, overlap: `SealData`, `evictOldest`; rows 9, 15, 17 and 18.
  - Only magic: no interaction hooks are added; row 4.
  - XP once per new seal, players only, no puppet, none for lifting: beam `masteryEligible`, `onBlockHit` puppet and reflected guards; rows 2, 7 and 20.
  - Vanilla sounds and particles only: Task 2 step 4.
  - Pure rules test-first: Task 1.
  - Cheap lookups: the list index, and the `isEmpty` fast path before any piston scan.
  - Code rules: Global Constraints and the greps in Task 2 step 11 and Task 4 step 3.
- **Type consistency.** `Seal` has 8 components in every task. `Decision(verdict, target)`. `Gate {PASS, REFUSED, BROKEN}` against `Verdict {PASS, REFUSE, BREAK}` is deliberate: one is the world outcome, the other the pure rule. `alohomoraGate(ServerLevel, BlockPos, Entity, int)` is called with `(level, pos, caster, rank)` in Task 3.
- **Placeholder scan:** none.
- **Counts:** Task 1 adds 14 tests. 319 + 14 = 333.
