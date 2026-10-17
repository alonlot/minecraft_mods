# Sorting Memory, Stage 1 (Engine + Quiz): Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Putting on the Sorting Hat plays a (stub) memory with no basin. Afterwards, a voiced quiz (3 of 10 questions, 4 choices each, a tie lets you pick) sorts the player, replacing the Ollama AI sorting. This stage also adds the engine features the later content stages need: chained scenes, the viewer's own skin, the `{viewer}` name in text, actor scale, and eyelid effects.

**Architecture:**
- Pensieve scenes gain a `next` key. A server session pre-builds the next scene in a fresh slot while the current one plays, then moves its viewers across.
- A new `PensieveSessions.startSolo` starts a basin-less, non-preview memory that has a body. A home listener reports when a viewer is back in the world.
- Sorting becomes a pure, testable `SortingSession` state machine fed by a JSON question bank. `SortingHatManager` is the glue: hat on, then memory, then quiz, then house. The client side is one screen and a voice player, reached only through `ClientSideBridge`.

**Tech Stack:** Forge 1.21 (Java 21), JUnit 5 unit tests, Forge GameTests, Python 3 for scene generators, ffmpeg and the Fish Audio API for voices.

**Spec:** `docs/superpowers/specs/2026-10-17-sorting-memory-design.md`

## Global Constraints

- Server/client safety: nothing in `pensieve/`, `sorting/` or `network/` may import `net.minecraft.client.*` or `com.hogwartsmagic.client.*`. Client work goes through `ClientSideBridge.handlePacket("<name>", packet)`, the way `HouseRevealPacket` does it.
- Never run gradle while the dev Minecraft client is running (`tasklist | findstr java`; a GradleDaemon is fine).
- No `switch` on an enum in client or render code. Use `if` chains.
- Custom screens use `renderTransparentBackground`, never `renderBackground`.
- `ExitReason` is wire format: append only, never reorder.
- A scene JSON is at most 262,144 chars (`PensieveStartPacket.MAX_JSON`). Chaining is how a long memory fits.
- Spoken lines never contain a name. The player is always addressed as a boy.
- The quiz is server-authoritative: the client sends only `(step, choice)`, and the server owns the questions, the answer order and the house.
- Commit after each task with the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pushing is the user's job. All work stays on branch `sorting-memory` (cut from `locket-polish`).
- Subagent models: Sonnet 5.5 implements. Opus 5.5 reviews and takes Task 3 (client chain hand-over) and any retry.

## Review Focus

1. **The player takes the hat off during the memory or the quiz.** Expected: the quiz closes and nothing is saved. Putting the hat back on starts again from the memory. (Task 9 test `hatRemovedMidQuizCancels`.)
2. **The player presses X during a chained memory, mid-chain.** Expected: they go home, the pre-built next scene's slot is cleared (no leaked slot or blocks), and the quiz opens. (Task 2 gametest `leaveMidChainClearsStandby`.)
3. **The next scene is still building when the current one ends.** Expected: the viewer waits in the dark and the next scene starts once it is built. No crash, and no skipping to the end. (Task 2 gametest `handoverWaitsForBuild`.)
4. **A forged or stale packet arrives**: a choice out of range, a step already answered, or a tie pick that is not a tied house. Expected: ignored, and the state is unchanged. (Task 7 tests `rejectsOutOfRangeChoice`, `rejectsStaleStep`, `rejectsNonTiedPick`.)
5. **Dedicated server.** Expected: `runServer` starts clean with the new packets and screen classes present. (Task 11.)

---

### Task 1: Scene format: `next`, actor `scale`, `viewer` skin

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/pensieve/scene/SceneDef.java`
- Modify: `src/main/java/com/hogwartsmagic/pensieve/scene/SceneParser.java`
- Modify: `src/main/java/com/hogwartsmagic/pensieve/scene/SceneValidator.java`
- Modify: `.claude/skills/writing-pensieve-memories/SKILL.md` (document the three keys)
- Test: `src/test/java/com/hogwartsmagic/pensieve/SceneParserTest.java`, `SceneValidatorTest.java`

**Interfaces:**
- Produces:
  - `SceneDef` gains a trailing component `String next` (nullable). The existing shorter constructors keep working and pass `null`.
  - `SceneDef.ActorDef` gains a trailing `float scale` (default 1). Add a constructor without it that passes 1.
  - `SceneDef.SkinRef(String texture, String username, boolean slim, boolean viewer)`, plus a 3-arg constructor passing `viewer=false`.
- JSON: top-level `"next": "<scene id>"`; actor `"scale": 0.75`; skin `{"viewer": true}`.

- [ ] **Step 1: Write the failing tests**

```java
// SceneParserTest
@Test void parsesNextScene() { SceneDef d = parse("{\"length\":20,\"next\":\"sorting_hut\"}"); assertEquals("sorting_hut", d.next()); }
@Test void nextDefaultsToNull() { assertNull(parse("{\"length\":20}").next()); }
@Test void parsesActorScale() { assertEquals(0.75f, actor("{\"id\":\"a\",\"type\":\"player\",\"scale\":0.75}").scale(), 1e-6); }
@Test void scaleDefaultsToOne() { assertEquals(1f, actor("{\"id\":\"a\",\"type\":\"player\"}").scale(), 1e-6); }
@Test void parsesViewerSkin() { assertTrue(actor("{\"id\":\"a\",\"type\":\"player\",\"skin\":{\"viewer\":true}}").skin().viewer()); }
// SceneValidatorTest
@Test void scaleOutOfRangeFails() { assertMessage(validate(actorScene("\"scale\":5")), "scale: must be 0.25 to 4"); }
@Test void viewerSkinWithTextureFails() { assertMessage(validate(actorScene("\"skin\":{\"viewer\":true,\"texture\":\"harry\"}")), "skin: viewer cannot be combined with texture or username"); }
@Test void viewerSkinOnMobFails() { assertMessage(validate(mobScene("\"skin\":{\"viewer\":true}")), "skin: only a player actor can have a skin"); }
@Test void nextMustBeValidId() { assertMessage(validate(scene("\"next\":\"Bad Id\"")), "next: must be a lowercase scene id"); }
@Test void nextCannotBeSelf() { assertMessage(validate(sceneWithId("a", "\"next\":\"a\"")), "next: a scene cannot chain to itself"); }
```
Use the helpers that already exist in these test classes, or add small ones with these names. `assertMessage` checks that some message contains the text.

- [ ] **Step 2: Run them and confirm they fail.** Run `./gradlew test --tests "*SceneParserTest*" --tests "*SceneValidatorTest*"`. Expected: compile errors or FAIL on the new tests.

- [ ] **Step 3: Implement.**
  - Parser: add `"next"` to the top-level `checkKeys`, `"scale"` to the actor keys and `"viewer"` to the skin keys.
  - Validator: scale must be in [0.25, 4]. `next` must match `[a-z0-9_]+` and must not equal the scene's own id. Keep the existing skin-on-mob rule if there is one.
  - Whether `next` names a loaded scene is checked in Task 2, at hand-over time, not here.

- [ ] **Step 4: Run them and confirm they pass**, along with `./gradlew test --tests "*SceneDataTest*"`, so the shipped scenes still parse.

- [ ] **Step 5: Commit** with message `Add next, actor scale and viewer skin to the scene format`.

---

### Task 2: Chained sessions on the server

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/pensieve/Session.java`
- Modify: `src/main/java/com/hogwartsmagic/pensieve/PensieveSessions.java`
- Test: `src/main/java/com/hogwartsmagic/gametest/PensieveGameTests.java`

**Interfaces:**
- Consumes: `SceneDef.next()` (Task 1).
- Produces:
  - `Session` gets these package-private fields:
    - `boolean standby`: built ahead, has no viewers, is not found by basin, and is not finished for being empty.
    - `@Nullable Session nextSession`
    - `String chainRoot`: the scene id that began the chain. A fresh session's `chainRoot` is its own id.
  - `Session.at()` returns false while `standby`.
  - Public getters `chainRoot()` and `standby()`.

Behaviour (the algorithm the tests pin):
1. When a session enters `PLAY` and `scene().next() != null`, look the next scene up (same `lookup`, or `MemoryRegistry.loadForPreview` for previews). If found, create it with `standby=true`, the same basin fields, `preview` and `chainRoot`, and build it in its own slot (the shared block budget builds standby sessions after active ones). Missing scene: log a warning and do not chain.
2. When the clock finishes and a `nextSession` exists:
   - If it is built, hand over:
     - for every viewer in `INSIDE`, move the same `Viewer` object into the next session and teleport it to the next anchor (same level, `connection.teleport`), keeping `sawStart`;
     - move `basinTicket`/`basinTicketLevel` from the old session to the new one (never add the ticket twice: a region ticket is a set, so adding it twice and removing it once loses it);
     - set `next.standby=false`, `next.phase=PLAY`, `next.startGameTime=now`, and `sendStart` to each viewer;
     - `finish(old)`, then `setPlaying(next, true)`.
   - If it is not built yet, hold the old session in a new `Phase.HANDOVER` (viewers stay pinned at the old anchor) and retry every tick.
3. Viewers in `EXITING` at that moment exit normally.
4. Whenever a session finishes without handing over (everyone left, abort, death, logout, `endAll`), its standby `nextSession` is finished too, so its slot is cleared.
5. A session with no `next` behaves exactly as today.

- [ ] **Step 1: Write the failing gametests.** Register two test scenes through `overrideForTests`: `chain_a` (length 40, `next: chain_b`, a 3x1x3 stone floor) and `chain_b` (length 40, a 3x1x3 gold floor).

```java
@GameTest void chainHandsViewerToNextScene()   // startPreview(chain_a); after ~60+40 ticks the player's session sceneId()=="chain_b", chainRoot()=="chain_a", phase PLAY
@GameTest void handoverWaitsForBuild()         // force chain_b's builder to stall (blocks-per-tick budget 1 via a test hook); chain_a reaches HANDOVER and stays; after unstalling, chain_b plays
@GameTest void leaveMidChainClearsStandby()    // leave(LEFT) during chain_a's PLAY; within 200 ticks both slots are released (allocator has no reserved slot) and no gold blocks remain
@GameTest void unchainedSceneUnchanged()       // a scene with no next ends with ExitReason.END as before
```

- [ ] **Step 2: Run them and confirm they fail.** Run `./gradlew runGameTestServer`. Expected: the new tests FAIL (no `chainRoot()`, no `HANDOVER`).

- [ ] **Step 3: Implement the behaviour above.** Add `HANDOVER` to `Session.Phase` (server-only enum, not on the wire). Add a test hook `static void setBuildBudgetForTests(int)` if the stall test needs one.

- [ ] **Step 4: Run all the gametests and confirm they pass**, the existing Pensieve gametests included.

- [ ] **Step 5: Commit** with message `Chain Pensieve scenes: pre-build the next scene and hand viewers over`.

---

### Task 3: Client hand-over between chained scenes (Opus)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/PensieveClientState.java`
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/MemoryAudio.java` (one method)
- Test: in-game check (client-only code; the existing pure timeline tests must still pass)

**Interfaces:**
- Consumes: a `PensieveStartPacket` with a different scene id arriving while `phase == PLAY` (Task 2).
- Produces: `MemoryAudio.cut()`, which stops voices at once and fades music over 20 ticks, the same as the existing end-of-memory path, factored out.

Behaviour: in `onStart`, when `phase == PLAY` and `id` differs from `sceneId`:
- parse the new scene;
- clear the previous scene's state: `MemoryActors.clear()`, `EffectVisuals.clear()`, `MemoryAudio.cut()`, and whatever word or event cursor state `MemoryEvents`/`MemoryWords` keep;
- set `origin`, `startGameTime` and `scene`;
- call `MemoryActors.start(...)` and `beginReveal(PensieveTimeline.LATE_FADE_TICKS)`, so each scene fades in from black. The previous one already faded out at its `length`.

The time of day follows the new scene automatically through `syncTimeOfDay`.

- [ ] **Step 1: Write a two-scene dev chain**: `build_scripts/pensieve_scenes/dev_chain.py`, writing `dev_chain_a.json` (night, red wool floor, one actor) and `dev_chain_b.json` (noon, blue wool floor, a different actor), each 10 s long. Write both to the dev folder only, not to `memories/`.
- [ ] **Step 2: Implement the hand-over in `onStart`** as described.
- [ ] **Step 3: Run `./gradlew test`** with the client closed. Expected: PASS.
- [ ] **Step 4: Check in game.** Run `/pensieve preview dev_chain_a`. Expected: red scene, fade to black, then the blue scene fades in at noon with the second actor, no leftover actor or sound, and it ends home normally. Take a screenshot of each half.
- [ ] **Step 5: Commit** with message `Hand the client from one chained scene to the next`.

---

### Task 4: Solo memories and the home listener

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/pensieve/PensieveSessions.java`
- Create: `src/main/java/com/hogwartsmagic/pensieve/ViewerHomeListener.java`
- Test: `src/main/java/com/hogwartsmagic/gametest/PensieveGameTests.java`

**Interfaces:**
- Produces:
  - `public static boolean startSolo(ServerPlayer player, String sceneId)`: like `startOrJoin`, but with no basin. It is not a preview, so a body is left behind and damage is forwarded. The scene comes from `lookup(id)`. Returns false and does nothing when the player is already a viewer, is inside the memory dimension, the dimension is missing, or the scene is missing.
  - `@FunctionalInterface public interface ViewerHomeListener { void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart); }`
  - `public static void addHomeListener(ViewerHomeListener l)`, called from the `EXITING` branch of `tickViewer` right after `moveBack`, beside `applyEndAction`, for every reason that reaches it (END, LEFT, YANK, ABORT). Death, logout and `endAll` do not call it.

- [ ] **Step 1: Write the failing gametests.**

```java
@GameTest void soloStartLeavesBodyAndNoBasin()   // startSolo(player,"chain_b") -> true; session basinPos()==null, preview()==false; after crossing, viewer.bodyId()!=null
@GameTest void soloRefusesSecondStart()          // second startSolo while inside -> false
@GameTest void homeListenerGetsChainRootAndReason() // startSolo(chain_a); leave(LEFT) mid-play; listener records ("chain_a", LEFT, true) once
@GameTest void homeListenerSeesEndAfterChain()   // let chain_a -> chain_b run out; listener records ("chain_a", END, true)
```

- [ ] **Step 2: Run them and confirm they fail.** Run `./gradlew runGameTestServer`.
- [ ] **Step 3: Implement.** Factor the shared part of `startOrJoin` and `startPreview` rather than copying it.
- [ ] **Step 4: Run all the gametests and confirm they pass.**
- [ ] **Step 5: Commit** with message `Start solo memories without a basin and report viewers coming home`.

---

### Task 5: Viewer skin, `{viewer}` text, and actor scale on the client

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/ActorSkins.java`
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/MemoryWords.java`
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/MemoryActors.java` (render scale, eye height)
- Create: `src/main/java/com/hogwartsmagic/pensieve/scene/WordText.java` (pure)
- Test: `src/test/java/com/hogwartsmagic/pensieve/WordTextTest.java`

**Interfaces:**
- Consumes: `SkinRef.viewer()` and `ActorDef.scale()` (Task 1).
- Produces: `public static String WordText.fill(String text, String viewerName)`, which replaces every `{viewer}` with `viewerName`. A null name becomes `""`. Other braces are untouched.

- [ ] **Step 1: Write the failing test.**

```java
@Test void fillsViewer() { assertEquals("Mr. Steve, The Cupboard under the Stairs", WordText.fill("Mr. {viewer}, The Cupboard under the Stairs", "Steve")); }
@Test void fillsEveryOccurrence() { assertEquals("Steve Steve", WordText.fill("{viewer} {viewer}", "Steve")); }
@Test void leavesOtherBraces() { assertEquals("{x} Steve", WordText.fill("{x} {viewer}", "Steve")); }
@Test void nullNameIsEmpty() { assertEquals("Mr. ", WordText.fill("Mr. {viewer}", null)); }
```

- [ ] **Step 2: Run it and confirm it fails.** Run `./gradlew test --tests "*WordTextTest*"`.
- [ ] **Step 3: Implement.**
  - `WordText.fill`.
  - `MemoryWords`: pass every displayed text through `WordText.fill(text, mc.player.getGameProfile().getName())`.
  - `ActorSkins.skinFor`: when `ref.viewer()`, return the local player's `AbstractClientPlayer#getSkin()`. It brings its own slim/classic model.
  - `MemoryActors.renderAll`: for `scale != 1`, push the pose, translate to the actor, `poseStack.scale(s, s, s)`, render at 0,0,0, then pop. Apply this on both the normal and the stone path.
  - Everywhere `MemoryActors` computes an actor's eye position (camera `look_at "actor:"`, spell origins, light sampling), multiply the eye height by `scale`.
- [ ] **Step 4: Run the test and confirm it passes**, then check in game with a dev scene that has three actors: `{"viewer":true}` at scale 0.75, Hagrid-sized at 1.5, a normal one at 1, and a word `"Mr. {viewer}"`. Expected: your own skin, a visibly small child and a large adult standing on the floor (feet not sunk or floating), the camera looking at each one's eyes, and your name shown. Take a screenshot.
- [ ] **Step 5: Commit** with message `Show the viewer's own skin and name in memories, and scale actors`.

---

### Task 6: Eyelid effects

**Files:**
- Create: `src/main/java/com/hogwartsmagic/pensieve/scene/Eyelids.java` (pure curve)
- Modify: `src/main/java/com/hogwartsmagic/pensieve/scene/SceneNames.java` (`EFFECTS` += `eyes_open`, `eyes_close`)
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/EffectVisuals.java` (running effect and a query, like `GreyEdges`)
- Modify: `src/main/java/com/hogwartsmagic/client/pensieve/PensieveOverlay.java` (draw the lids)
- Modify: `.claude/skills/writing-pensieve-memories/SKILL.md` (document both)
- Test: `src/test/java/com/hogwartsmagic/pensieve/EyelidsTest.java`

**Interfaces:**
- Produces: `public static float Eyelids.closed(boolean opening, float age, int duration)`. 1 means fully shut (black) and 0 means fully open.
  - `opening`: shut until 30% of `duration`, then a half-open blink that closes again (peak open 0.5 at 45%, shut again at 55%), then fully open by 100% (ease-out). After `duration` it is 0.
  - closing: open, then shut by 100% (ease-in). After `duration` it stays 1 until the scene ends.
  - The overlay draws two black bands from top and bottom, each covering `closed * 50%` of the screen height, with a soft 24-px gradient edge.

- [ ] **Step 1: Write the failing test.**

```java
@Test void openingStartsShut() { assertEquals(1f, Eyelids.closed(true, 0, 100), 1e-4); }
@Test void openingBlinksHalfOpen() { assertEquals(0.5f, Eyelids.closed(true, 45, 100), 0.02); }
@Test void openingShutAgainMidBlink() { assertEquals(1f, Eyelids.closed(true, 55, 100), 0.02); }
@Test void openingEndsOpen() { assertEquals(0f, Eyelids.closed(true, 100, 100), 1e-4); assertEquals(0f, Eyelids.closed(true, 500, 100), 1e-4); }
@Test void closingEndsShutAndStays() { assertEquals(0f, Eyelids.closed(false, 0, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 40, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 400, 40), 1e-4); }
```

- [ ] **Step 2: Run it and confirm it fails.** Run `./gradlew test --tests "*EyelidsTest*"`.
- [ ] **Step 3: Implement** the curve and the overlay drawing. When several lid effects overlap, the most-closed one wins. `EffectVisuals.clear()` drops them, which the Task 3 hand-over relies on.
- [ ] **Step 4: Run the tests and confirm they pass** (including `SceneDataTest`, which checks `EFFECTS`). Then check in game with a dev scene that has `eyes_open` at t 0 for 100 ticks and `eyes_close` at t 8 s for 40 ticks. Take screenshots of the half-open blink and of fully shut.
- [ ] **Step 5: Commit** with message `Add eyes_open and eyes_close screen effects to memories`.

---

### Task 7: The question bank and the quiz state machine

**Files:**
- Create: `src/main/resources/data/hogwartsmagic/sorting/questions.json`
- Create: `src/main/java/com/hogwartsmagic/sorting/SortingQuestion.java` (record)
- Create: `src/main/java/com/hogwartsmagic/sorting/SortingQuestionBank.java` (parse plus load from the classpath)
- Create: `src/main/java/com/hogwartsmagic/sorting/SortingSession.java` (pure state machine)
- Test: `src/test/java/com/hogwartsmagic/sorting/SortingQuestionBankTest.java`, `SortingSessionTest.java`

**Interfaces:**
- Produces:
  - `public record SortingQuestion(String id, String text, String voice, Map<HogwartsHouse, String> answers)`, with exactly 4 answers, one per house.
  - `public static List<SortingQuestion> SortingQuestionBank.parse(JsonObject root) throws IllegalArgumentException` and `public static List<SortingQuestion> SortingQuestionBank.load()`, which reads `/data/hogwartsmagic/sorting/questions.json` from the classpath once and caches it.
  - `public final class SortingSession`:
    - `SortingSession(List<SortingQuestion> bank, java.util.Random rng)` draws 3 distinct questions and a shuffled house order per question.
    - `int step()`: 0..2 for the questions, 3 for a tie pick, 4 when done.
    - `SortingQuestion question()` and `List<HogwartsHouse> order()`: the houses in display order for the current question.
    - `Outcome answer(int step, int choice)`.
    - `List<HogwartsHouse> tied()`.
    - `HogwartsHouse result()`.
  - `public enum Outcome { NEXT_QUESTION, TIE, SORTED, REJECTED }`. On a tie step, `choice` indexes into `tied()`.

JSON shape:
```json
{"questions":[{"id":"q1","text":"...","voice":"hogwartsmagic:sorting.voice.q1",
  "answers":{"GRYFFINDOR":"...","RAVENCLAW":"...","HUFFLEPUFF":"...","SLYTHERIN":"..."}}]}
```

- [ ] **Step 1: Write the 10 questions** in the spirit of the books, original wording (not the Wizarding World quiz). Each has four answers that each lean clearly to one house, written for an 11-year-old boy about to start school, and no names. **Show them to the user and wait for approval before Task 10 voices them.**
- [ ] **Step 2: Write the failing tests.**

```java
// SortingQuestionBankTest
@Test void shippedBankHasTenValidQuestions() { var b = SortingQuestionBank.load(); assertEquals(10, b.size()); b.forEach(q -> assertEquals(EnumSet.allOf(HogwartsHouse.class), q.answers().keySet())); }
@Test void rejectsMissingHouse() { assertThrows(IllegalArgumentException.class, () -> SortingQuestionBank.parse(json("one question with 3 answers"))); }
@Test void rejectsDuplicateIds() { assertThrows(IllegalArgumentException.class, () -> SortingQuestionBank.parse(json("two questions id q1"))); }
// SortingSessionTest (bank = 10 synthetic questions, seeded Random)
@Test void drawsThreeDistinctQuestions()
@Test void majorityWins()            // answer houses G,G,R -> SORTED, result()==GRYFFINDOR
@Test void threeWayTieAsksPick()     // answers G,R,H -> TIE, tied()==[GRYFFINDOR,RAVENCLAW,HUFFLEPUFF] (enum order), step()==3
@Test void tiePickSorts()            // after TIE, answer(3, index of RAVENCLAW in tied()) -> SORTED, result()==RAVENCLAW
@Test void rejectsOutOfRangeChoice() // answer(0, 4) and answer(0,-1) -> REJECTED, step() still 0
@Test void rejectsStaleStep()        // answer(0,x) twice -> second REJECTED, step()==1
@Test void rejectsNonTiedPick()      // in TIE with 3 tied, answer(3, 3) -> REJECTED
@Test void orderIsShuffledPerQuestion() // over 50 seeds, order() for question 0 is not always enum order
```
Note: with 3 one-point answers, the only tie is three different houses (1-1-1). The tied list is the three answered houses, in enum order.

- [ ] **Step 3: Run them and confirm they fail.** Run `./gradlew test --tests "*Sorting*Test*"`.
- [ ] **Step 4: Implement** `SortingQuestion`, `SortingQuestionBank` and `SortingSession`.
- [ ] **Step 5: Run the tests and confirm they pass.**
- [ ] **Step 6: Commit** with message `Add the Sorting Hat question bank and quiz state machine`.

---

### Task 8: Quiz packets, screen and hat voice player

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (four packets)
- Create: `src/main/java/com/hogwartsmagic/client/sorting/SortingQuizScreen.java`
- Create: `src/main/java/com/hogwartsmagic/client/sorting/HatVoice.java`
- Modify: `src/main/java/com/hogwartsmagic/util/ClientSideBridge.java` (and/or `client/ClientRuntimeBridge.java`, wherever `"showHouseReveal"` is dispatched)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SortingSession` (Task 7) on the server, for what goes into the packets.
- Produces these records, registered like `HouseRevealPacket`:
  - `SortingQuestionPacket(int step, String text, List<String> answers /*4, display order*/, String voice)`: S2C. Opens or updates the screen and plays the voice.
  - `SortingTiePacket(List<HogwartsHouse> houses, String voice)`: S2C. The screen shows only these houses (house names in house colours), with the prompt `screen.hogwartsmagic.sorting.tie`.
  - `SortingClosePacket()`: S2C. Closes the screen if it is open.
  - `SortingVoicePacket(String voice)`: S2C. Plays one hat line (greeting, a reaction, a house shout).
  - `SortingChoicePacket(int step, int choice)`: C2S. Handled by `SortingHatManager.onChoice(ServerPlayer, int, int)` (Task 9).
- `HatVoice.play(String soundId)`: a non-positional sound on `SoundSource.VOICE`, at volume 1 and pitch 1, that stops the previous hat line. An unknown id is ignored with a debug log.
- `SortingQuizScreen`:
  - `renderTransparentBackground`, then a parchment panel with the title `screen.hogwartsmagic.sorting.title` ("The Sorting Hat"), the question text wrapped, and 4 full-width buttons.
  - `shouldCloseOnEsc()` returns false, and `isPauseScreen()` returns false.
  - Clicking sends `SortingChoicePacket` and disables all buttons until the next packet arrives.

- [ ] **Step 1: Write the packets and the bridge entries** (`"sortingQuestion"`, `"sortingTie"`, `"sortingClose"`, `"sortingVoice"`) and the client classes as specified.
- [ ] **Step 2: Build with the client closed.** Run `./gradlew compileJava`. Expected: BUILD SUCCESSFUL. Then run `grep -rn "client\." src/main/java/com/hogwartsmagic/network/SpellPackets.java`; it must show no new direct client imports.
- [ ] **Step 3: Commit** with message `Add the Sorting quiz screen, packets and hat voice player`. In-game behaviour is checked in Task 9.

---

### Task 9: The new SortingHatManager flow; remove the AI

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/sorting/SortingHatManager.java` (rewrite the flow)
- Modify: `src/main/java/com/hogwartsmagic/event/SortingHatEvents.java` (drop the chat handler)
- Delete: `src/main/java/com/hogwartsmagic/sorting/OllamaSortingService.java`, and `SortingResult.java` if nothing else uses it (grep first)
- Modify: `Config.java` and the config toml defaults, removing any Ollama entries (grep for `ollama`, ignoring case)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (remove `.wait`, `.empty` and the AI-only lines; keep `.greeting` and `.final`)
- Create: `src/main/resources/data/hogwartsmagic/memories/sorting.json` (stub) and `build_scripts/pensieve_scenes/sorting.py`
- Test: `src/main/java/com/hogwartsmagic/gametest/SortingGameTests.java`

**Interfaces:**
- Consumes:
  - `PensieveSessions.startSolo`, `addHomeListener`, `isViewer` (Task 4)
  - `SortingSession` and `SortingQuestionBank` (Task 7)
  - the packets (Task 8)
- Produces:
  - `public static final String MEMORY_SCENE = "sorting"`. Later stages chain the full film from this id through `next`.
  - `public void onChoice(ServerPlayer player, int step, int choice)`
  - `public void tick(ServerPlayer player)`, replacing `maybeStartSession`.

Flow (server, per player):
- `tick` runs every 20 ticks:
  - The hat is off, and a quiz or a pending memory is open: send `SortingClosePacket`, drop the state, save nothing.
  - The hat is on, the player is unsorted, is not a viewer, and has no quiz or pending memory: `startSolo(player, MEMORY_SCENE)`. If that returns true, mark the player memory-pending. If it returns false (no dimension or no scene), start the quiz straight away.
- Home listener with `chainRoot == MEMORY_SCENE`:
  - END or LEFT: clear the pending mark. If the player still wears the hat and is unsorted, start the quiz. Send `SortingVoicePacket(greeting)` then question 0.
  - YANK or ABORT: clear the pending mark only. The next `tick` replays the memory.
- Death or logout during the memory: the listener is not called. Clear the pending mark on logout (`PlayerLoggedOutEvent`) and on death. The next time the hat is worn, the memory starts from the beginning.
- `onChoice`: ignore it unless the player has a quiz and is wearing the hat. Pass it to `SortingSession.answer`:
  - `NEXT_QUESTION`: a reaction voice (random 1 of 3), then the next `SortingQuestionPacket`.
  - `TIE`: `SortingTiePacket` with the tie voice.
  - `SORTED`: save the house (the existing `saveHouse`), send `HouseRevealPacket`, play `SORTED_SOUND`, send `SortingVoicePacket(house shout)` and `SortingClosePacket`, and the existing `.final` chat line.
  - `REJECTED`: nothing.
- Stub scene `sorting.json`: about 12 s. A dark stone hall floor and one `{"viewer":true}` actor at scale 0.75 on a stool, `eyes_open` at the start, the narration word `"{viewer}..."`, and `eyes_close` over the last 2 s. This exercises Tasks 1, 5 and 6 in one place and is replaced in stage 2.

- [ ] **Step 1: Write the failing gametests.** Use `PensieveSessions.overrideForTests` with the stub and a mock player (`makeMockServerPlayerInLevel`).

```java
@GameTest void hatStartsMemoryThenQuiz()     // equip hat, unsorted -> after tick: isViewer; force leave(LEFT) -> after return: manager has a quiz at step 0
@GameTest void threeAnswersSortPlayer()      // drive onChoice through a majority -> persisted house set, quiz cleared
@GameTest void hatRemovedMidQuizCancels()    // quiz at step 1, unequip hat, tick -> no quiz, no house; re-equip, tick -> isViewer again (memory replays)
@GameTest void sortedPlayerNothingHappens()  // house already saved + hat on -> tick -> not a viewer, no quiz
@GameTest void yankReplaysMemory()           // memory pending, leave(YANK) -> no quiz; next tick -> isViewer again
@GameTest void forgedChoiceIgnored()         // onChoice(step 2) while at step 0 -> still step 0
```
Give `SortingHatManager` package-private test accessors `hasQuiz(UUID)` and `quizStep(UUID)`.

- [ ] **Step 2: Run them and confirm they fail.** Run `./gradlew runGameTestServer`.
- [ ] **Step 3: Implement the flow**, delete the Ollama code, the chat handler, the config entries and the lang lines, and write the stub scene through its generator.
- [ ] **Step 4: Run all the tests and confirm they pass.** Run `./gradlew test` and `./gradlew runGameTestServer`. Expected: everything passes, with `SceneDataTest` validating `sorting.json`.
- [ ] **Step 5: Check in game.** Put the hat on: the stub memory plays (eyes open, your skin, small, your name, eyes close), you come back, the quiz opens, and three clicks sort you, with the house reveal shown. Pressing X mid-memory goes straight to the quiz. Taking the hat off mid-quiz closes the screen, and putting it back on replays the memory. Take screenshots of the quiz screen and the reveal.
- [ ] **Step 6: Commit** with message `Sort with a fixed voiced quiz after the Sorting memory; remove the Ollama sorting`.

---

### Task 10: Voice the Sorting Hat (needs the user)

**Files:**
- Create: `src/main/resources/assets/hogwartsmagic/sounds/sorting/*.ogg`
- Modify: `src/main/resources/assets/hogwartsmagic/sounds.json`, `src/main/java/com/hogwartsmagic/ModSounds.java`
- Modify: `questions.json`, so each `voice` matches a registered id

Lines, all ids `hogwartsmagic:sorting.voice.<name>`:
- `greeting`
- `q1`..`q10`
- `react1`..`react3`
- `tie`
- `gryffindor`, `ravenclaw`, `hufflepuff`, `slytherin`: the shouts

That is 19 lines in all. The greeting and the reactions are written here, original, with no names.

- [ ] **Step 1: Find a voice.** Search Fish Audio's voice library for a Sorting Hat voice: old, warm, sly, theatrical. List the best 2-3 candidates with links. **The user picks one.**
- [ ] **Step 2: Generate the 19 lines.** Use the pipeline in the `reference-fish-audio-voices` memory: the key from the local key file (never committed), MP3, envelope-trim and level-match, mono OGG, `ffmpeg`. Deliver each line cinematically: pauses, a drawn-out "Hmm...", and shouted houses.
- [ ] **Step 3: Register the 19 sounds** in `ModSounds` (`register("sorting.voice.<name>")`) and `sounds.json` (`"stream": false`, with a subtitle key per line), and point `questions.json` at them.
- [ ] **Step 4: Check in game.** Every question and reaction is heard, the tie line and a house shout play, and no "missing sound" warnings appear in the log.
- [ ] **Step 5: Commit** with message `Voice the Sorting Hat`.

---

### Task 11: Stage 1 check: dedicated server and the whole branch

- [ ] **Step 1:** With the client closed, run `./gradlew test` and `./gradlew runGameTestServer`. Expected: everything passes.
- [ ] **Step 2:** Run `./gradlew runServer` and wait for "Done". Expected: no exceptions mentioning `client`, `Screen` or `ClassNotFound`. Then stop it.
- [ ] **Step 3:** Run a whole-branch review on Opus against this plan and the spec, then fix what it finds.
- [ ] **Step 4:** Write the progress note in the `project-sorting-memory` memory, then ask the user to push `sorting-memory`.
