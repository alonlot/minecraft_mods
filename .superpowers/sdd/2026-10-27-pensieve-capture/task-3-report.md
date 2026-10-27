# Task 3 report

Commit a188d921 "Turn recordings into Pensieve scenes", pushed to origin/sorting-memory.

Files: capture/RecordingScene.java, capture/RecordedTrackSampler.java (new); scene/SceneDef.java (javadoc only, documents recorded_beam; SceneValidator untouched); RecordingSceneTest (6 tests), RecordedTrackSamplerTest (5 tests).

Verification: gradle was NOT run, because another session had `gradlew runGameTestServer` running. The new tests were compiled with plain javac (JDK 21) against the gradle-cached JUnit 5.10.2 and gson jars, and run with the JUnit launcher: 11 tests, 11 passed. SceneDataTest and SceneValidatorTest were not run (they need Minecraft classes); the SceneDef change is javadoc only, so they are unaffected.

Calls made:
- actorId(Track) has no Recording argument, so the owner is identified as the player track with actor 0 (capture puts the owner first, EntitySelect). Repeated player names are disambiguated inside toScene (extra `_actor` suffix), since a single-track function cannot see repeats.
- Extra tests added: repeatedPlayerNamesGetTheActorNumber, patchesUpToStopsAfterTick.
- patchesUpTo accepts DataPatch, EquipPatch and Act (anything else throws).
- Actions are sorted by t (cast cues merged with acts).
- Beam event `at` is the start; sound/beam events sorted by t.

## Fix report (review round)
- Owner now found by `t.uuid().equals(r.owner())`; the actor-0 assumption is gone. `actorId(Track)` replaced by public `RecordingScene.actorIds(Recording)` returning an ordered `Map<String, Track>` (owner / p_name[_actor] / eN), used by toScene so Task 7 can resolve `trackFor(actorId)`.
- Actor `name`: players get their name (escaped with the chat `{` + U+200B rule); mobs get "". The Track has no custom-name field, so Task 4 must store a custom name on the track (and Task 3 can then pass it through escape) if custom-named mobs should show tags.
- `pose` returns 7 zeros for empty arrays.
- Tests added: ownerIsFoundByUuidNotPosition, mobsAreUnnamedAndPlayerNamesEscaped, emptyArraysGiveZeros.
- Command: `./gradlew test --tests "com.hogwartsmagic.pensieve.*"` in hp-pensieve -> BUILD SUCCESSFUL (includes SceneDataTest, SceneValidatorTest).
