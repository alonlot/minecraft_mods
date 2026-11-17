# Voice Casting (speech to text) Plan and Spike Report

**Goal:** With a wand in the main hand, say a spell name. If the player knows that spell, it casts. Default mode: open mic while a wand is held (`voiceMode = WAND_OPEN_MIC`); the other mode is push-to-talk (`PUSH_TO_TALK`, hold V).

**User request (2026-11-15):** "if the player hold a wand with a spell he know and say it out loud it will cast it", then "i mean say as stt powered" (real speech to text, not typed chat), then "can i use the local and not make it push to talk?" / "its only when holding a wand so its makes sense" (open mic while a wand is held).

## Architecture

- **All on the client.** The microphone is recorded, transcribed and matched on the player's own computer. The result calls `ModEventsClientHooks.selectSpellAndCast(player, spell)`, the same function the radial menu uses. The server sees the same `SelectSpellPacket` and `CastSpellPacket` as a radial cast and checks them the same way (learned, cooldown, mana, stun, silence, mind control). No new packet, no protocol bump, no new trust: a modified client could already send these packets.
- **Hold spells** work as on the radial menu: keep right-click down after the spell is heard (Fiendfyre, Patronus shield, Wingardium Leviosa, Crucio, lock picks with the 1 s grace). Protego Maxima channels when the player looks up, as with the radial menu.
- **Engine:** whisper.cpp through `io.github.givimad:whisper-jni:1.7.1` (Apache 2.0; natives for win-amd64, macos-arm64, macos-amd64, linux amd64/arm64/armv7l inside its 4.6 MB jar), shipped inside the mod jar with Forge jar-in-jar.
- **Model:** `ggml-tiny.en-q5_1.bin` (31 MiB, MIT, SHA-1 `3fb92ec865cbbc769f08137f22470d6b66e071b6`, the official whisper.cpp value) in `src/main/resources/voice/`. Copied to `<game dir>/hogwartsmagic/voice/` on first use, because whisper.cpp reads a file path.
- **Matcher:** `spell/voice/IncantationMatcher` (pure Java, unit tested). Folds spelling to sound, weighted edit distance (vowel and voiced/unvoiced swaps cost half), max distance 0.34, the best spell must beat the second by 0.08, a part of the heard text that matches pays 0.15.
- **Files:** `client/voice/VoiceCastClient` (key, state machine, feedback), `client/voice/MicRecorder` (Java Sound, 16 kHz mono with 48/44.1/32 kHz fallback), `client/voice/WhisperEngine` (one low-priority worker thread), `spell/voice/VoiceCastConfig` (client config).

## Spike results (2026-11-15)

Test set: 37 spell names and 10 normal chat lines, each said by 5 ElevenLabs voices (2 British male, 1 British female, 1 US male, 1 US female), 16 kHz. Container CPU, 4 cores. Synthetic speech is cleaner than a real headset, so real accuracy will be lower.

| Engine | Correct | Wrong spell | No cast | False casts on chat lines | Time per take |
|---|---|---|---|---|---|
| Vosk small en-us, free text + fuzzy match | 35% | 0 | 120 | 0/50 | 550 ms |
| Vosk small en-us, spell grammar | 5% | 2 | 174 | 0/50 | 33 ms |
| Whisper tiny.en (Python binding), prompt | 90% | 0 | 18 | 0/50 | 670 ms |
| Whisper tiny.en, prompt, 10 dB noise | 89% | 1 | 20 | 0/50 | 670 ms |
| Whisper base.en (Python binding), prompt | 97% | 0 | 5 | 0/50 | 1320 ms |
| Whisper tiny.en q5_1 (whisper-jni, Java), prompt, plain edit distance | 92% | 0 | 13 | 0/50 | 860 ms (4 threads), 1690 ms (2 threads) |
| **Same transcripts, final IncantationMatcher** | **96%** | 0 | 6 | 0/50 | same |
| tiny.en q5_1, audio_ctx 512 | 56% | 0 | 80 | 0/50 | slower |
| tiny.en q5_1, GBNF spell grammar | 89% | 0 | 19 | 0/50 | 840 ms |


Cloud services, same clips, final IncantationMatcher (2026-11-15):

| Engine | Correct | Wrong spell | No cast | False casts | Time per take | List price |
|---|---|---|---|---|---|---|
| ElevenLabs Scribe v2, 37 spell keyterms | 100% | 0 | 0 | 0/50 | 0.33 s median, 0.51 s p90 | $0.20 + $0.05 per audio hour |
| ElevenLabs Scribe v2, no keyterms | 98% | 0 | 3 | 0/50 | 0.35 s median | $0.20 per audio hour |
| Fish Audio transcribe-1 (takes no word list; detects Latin as Japanese and other languages) | 92% | 1 | 12 | 0/50 | 0.21 s median | $0.36 per audio hour |

Caution: the clips were made with ElevenLabs text to speech, so ElevenLabs speech to text has a home advantage. Real voices are needed before this table can decide anything.

Ruling: keep the local model as the default; a cloud engine (Scribe with keyterms) is at most an optional, off-by-default server setting with the server owner's key, sent through the server so the key never ships in a client - decided only after a real-voice test - cost if wrong: a few percent of missed casts until then.

Findings:
- Vosk cannot learn new words at run time: its grammar drops every out-of-vocabulary word, and Latin spell names are not in its vocabulary.
- whisper.cpp returns nothing for less than 1 s of audio. Takes are padded with silence to 1.5 s (this was the cause of an early 54% result).
- The grammar and audio_ctx options do not help. The prompt that lists the spell names does.
- With the final matcher, 4 of the 6 misses are "Reparo", which the synthetic voices say oddly (heard as "Hippado"). The other two are Incendio ("Insane you") and Glacius ("spacious").

## Rulings

Ruling: whisper.cpp tiny.en q5_1 on the client, not Vosk and not a server-side engine - 92% against 35%, no server CPU, works in single player and on any server - cost if wrong: swap the model file (base.en q5_1 is 57 MiB, about twice as slow).
Ruling: push-to-talk only, never always listening - the microphone is open only while the key is held (plus a 200 ms tail), so there is no idle CPU cost, no privacy question and no cast from talk on Discord - cost: a key press per cast.
Ruling: the model ships in the jar and in git (31 MiB), no download at run time - the mod never contacts the internet and works offline - cost: a larger repo and jar (resources were already 122 MB).
Ruling: match against every spell, then refuse a spell the player does not know with "You do not know X yet" - matching only known spells would turn an unknown spell into a wrong cast (Crucio into Accio) - small.
Ruling: every spell gets its display name as the incantation, including Dolohov's Curse, Tenebris Flight and Apparition - with push-to-talk a stray match costs nothing - small.
Ruling: main-hand wand only - the server casts only from the main hand - none.
Ruling: no voice cast while possessing (the puppet wheel casts for the puppet) or while a screen is open - none.
Ruling: silence (RMS below 0.004) skips the model - whisper invents "Thank you." from silence - none.
Ruling: default key V - unused in vanilla and in this mod; rebindable - may clash with other mods' push-to-talk keys.
Ruling: the engine failing to start (no native build, old macOS, missing runtime) turns voice casting off for the session with one chat line; it never crashes the game - none.

## Verified in the dev client (2026-11-15)

`runClient` under Xvfb with software OpenGL, and a temporary self-test (not committed) that fed PCM files through
`WhisperEngine` and the matcher during mod setup. whisper-jni loaded its native library inside Forge's module layer,
the model was copied out of the mod resources, and 36 of 37 spells (one voice) were heard right, with no cast from
14 normal sentences. The miss was Reparo ("Hey Pado"). A take took about 1.1 to 1.5 s on 4 container cores while
the title screen rendered in software. This run found two bugs, both fixed: a 0.2 s / 1.5 s silence pad made the
model loop ("Stupefyre, Stupefyre, ..."), and the matcher now drops a word that repeats the one before it.

## Not verified here (needs a real client)

- Microphone capture on Windows and macOS (macOS asks for microphone permission for the launcher; if it is refused, no audio comes in).
- whisper-jni loading from jar-in-jar in a production install (the dev client loads it from the classpath; the jar's jarjar metadata is correct). A dedicated-server boot check was not run: the server stops at the EULA before mods load, and accepting the EULA is the user's decision.
- Latency on a gaming PC while the game renders.
- Accuracy with real voices, accents and headset microphones.

## Open mic mode (2026-11-15)

- `client/voice/OpenMicListener` opens the microphone while a wand is in the main hand (1 s grace after it leaves, so scrolling past does not churn; a failed open retries after 10 s, not every tick). The V key does nothing in this mode.
- `spell/voice/SpeechSegmenter` (pure, unit tested) cuts the stream into utterances: 20 ms frames, loud = RMS over 3x an adaptive noise floor (minimum 0.006), 3 loud frames start, 400 ms quiet ends, 300 ms pre-roll. An utterance over 4 s is talk or noise: dropped, and nothing starts again until 400 ms of quiet, so the tail of a long sentence never casts on its own.
- One utterance at a time goes to the engine; one that ends while the engine is busy is dropped (no queue, no late casts).
- Strict matching, `IncantationMatcher.match(text, true)`: no partial runs; 0.08 per extra word; a sentence is refused when a shorter run of its words is the best spell itself (and closer than the whole text) or any other spell that is not one of the best spell's words. "Protego Maxima", "Pier totum locomotor" and "Sector Sempa" still cast; "Oh Depulso", "Protego him!" and "Did you see his Stupefy?" do not.
- Nothing is shown for talk that matches no spell (it would show on every sentence).

Strict matching on the saved transcripts (tiny local 185 + Scribe 185) and on 600 sentences that name a spell (40 spells x 15 templates) plus 24 chat lines:

| Rule | Spells correct | Sentences naming a spell that cast | Chat lines that cast |
|---|---|---|---|
| Push-to-talk (lenient) | 97.6% | 547 of 600 | 2 of 24 |
| Open mic (strict, final) | 97.3% | 0 of 600 | 0 of 24 |

End-to-end open mic test (no microphone): every clip placed alone in a stream with 0.8 to 1.5 s gaps and Gaussian noise (sigma 0.002), fed in 50 ms chunks through SpeechSegmenter, whisper-jni (tiny.en q5_1, 4 threads, the engine's padding) and strict matching. Plus 20 runs of 3 chat clips joined by 150 ms gaps.

| Result | Value |
|---|---|
| Spells cast right | 176 of 185 (95.1%) |
| Wrong spell cast | 0 |
| Events with no spell that cast (50 chat clips, 20 talk runs) | 0 of 70 |
| Whisper calls / mean time | 295 / 957 ms |

Misses: Reparo 4 times ("Hippado", "Hit bottom"), Incendio 2, Expecto Patronum ("He's back to Patronum"), Sectumsempra, Crucio ("Good luck"). Against push-to-talk (96%), the open mic loses about one point to the speech detector's cut and the noise.

Ruling: open mic while a wand is in the main hand is the default; push-to-talk stays as a setting - the user's choice, and the wand gate keeps the microphone closed the rest of the time - cost: CPU while talking with a wand out, and a friend's voice from speakers can cast (headphones advised in the config comment and the wiki).
Ruling: no on-screen line for unmatched talk in open mic - it would flash on every sentence - small: a player gets no feedback on a miss.
Ruling: the V key is ignored in open mic mode - two microphone lines on one device fail on some systems - small.

## Hold window after a voice cast and the timing log (2026-11-15)

User request: "spells that need holding will let you have a small time window before you hold right click to keep it held", and a log line with the real time per take.

- After a voice cast that starts a hold, `ClientModEvents.startVoiceHoldGrace()` gives right-click 30 ticks (1.5 s) to go down. The words are heard about a second after they end, so right-click cannot already be down.
- Running spells keep their heartbeats during the window: Crucio, the Protego Maxima dome (still only while looking up), lock picks (their 1 s radial grace is raised to 1.5 s). Spells that start on the press wait: Fiendfyre, the Patronus, Wingardium Leviosa (the server keeps its target 40 ticks after the hit), Imperio at Master.
- When the window ends with right-click up, each resolves as a tap: Fiendfyre fires its tap cast, Crucio and Protego Maxima release, the Patronus and Wingardium do nothing more.
- Right-click inside the window takes over the hold and never casts a second beam: the wand use handler now also skips Wingardium and Imperio while their hold waits (Fiendfyre, Patronus, Crucio and locks were already guarded).
- `WhisperEngine` logs `Voice casting: heard "<text>" in <ms> ms (<s> s of speech, <n> threads)` for every take, at INFO, to `logs/latest.log`.

Ruling: 1.5 s, not the radial menu's 1 s - the player hears the cast later than they see a wheel close - small.
Ruling: no action-bar hint for the window - the cooldown line redraws the action bar every tick after a cast; the wiki describes the window - small.
Ruling: the radial menu keeps its old behaviour (only lock picks get a grace there) - the user asked for voice casts - none.
Not verified in game: the hold window needs a world and a held key; it is compile and unit tested only (all 362 tests pass).

## voiceOnlySpells gamerule (2026-11-15)

User request: "add a gamerule that forces only voice activated spells".

- `/gamerule voiceOnlySpells true` (default false, category PLAYER). The server then casts a player's spell only after the client sent a `VoiceCastStampPacket` for it (`spell/voice/VoiceOnlyRule`, rules in the pure, unit-tested `VoiceStamp`).
- One cast per spoken spell. Heartbeats for the spoken spell pass and keep the stamp fresh, so voice-started holds and the 1.5 s right-click take-over work and never time out. A stamp unused for 60 ticks is spent; a release always passes and ends it. The Imperio Master hold start needs a fresh spoken Imperio.
- The check sits in `CastSpellPacket.handle`, so the server's own re-entry (Avada wind-up) and puppet casts (possession) are not affected.
- The value is synced to clients (`VoiceOnlyRulePacket`, on login and change; protocol version 7). Clients then refuse clicks, left-click self-casts, the radial wheel and slot keys with "Only spoken spells work here" (the wheel and slot keys still select).
- New for every voice user: sneak while saying a self-cast spell (Protego, Episkey, Lumos, Arresto Momentum, Reparo) to cast it on yourself. Without this, voice-only mode would have no self-casts.

Ruling: possession puppet casts are exempt - they go through their own wheel and voice casting is off while possessing - cost: a possessing Master can puppet-cast without speaking.
Ruling: a game rule, not anti-cheat - a modified client can send its own stamps, which gives it nothing it could not already do with cast packets - none.
Ruling: sneak + spoken spell = self-cast, in every mode - the only way to self-cast under the rule - small: sneaking players cannot beam those five spells by voice.

## In-game checklist

1. Hold a wand, hold V, say "Lumos", release: the wand lights within about a second.
2. Say a spell you have not learned: "You do not know <spell> yet".
3. Say "hello": "Heard "Hello.": no spell" (turn off with voiceShowHeard=false).
4. Say "Fiendfyre" and hold right-click: the channel starts; release ends it.
5. Look up, say "Protego Maxima" and hold right-click: the dome channels.
6. No wand in hand: V does nothing, the microphone stays closed.
7. voiceCasting=false in hogwartsmagic-client.toml: V does nothing.
8. Stunned or silenced: the usual "cannot cast" line.
9. Open mic (default): hold a wand and say "Stupefy!" without any key: it casts. Say "did you see his Stupefy": nothing.
10. Open mic: put the wand away: the system microphone light goes off after about a second.
11. Open mic: talk for a while with the wand out: no casts, and no visible FPS loss on your machine (report it if there is).
12. Say "Fiendfyre", then press and hold right-click within about 1.5 s: the channel starts. Do not press: a single tap cast.
13. Say "Expecto Patronum" and hold right-click within 1.5 s: the shield. Say "Wingardium Leviosa" at a mob, then hold right-click: it lifts, with no second beam.
14. Open logs/latest.log: one "Voice casting: heard ... in ... ms" line per take shows the speed on your PC.
15. /gamerule voiceOnlySpells true: right-click, left-click, the wheel and slot keys cast nothing ("Only spoken spells work here"); saying a spell casts; saying Fiendfyre then holding right-click channels.
16. Under the rule, sneak and say "Protego": the shield goes on you.
