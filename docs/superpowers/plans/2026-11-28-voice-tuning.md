# Voice Casting Tuning (2026-11-28)

**User request:** "the stt still not that good. is there a way making him better for the spells he has?", then "Build all four".

## What the logs showed

Every "Voice casting: heard" line in run/logs (tiny.en, the player's real voice): long spells were heard right (Bombarda 7/7, Fiendfyre, Imperio, Expelliarmus, Tenebris Flight, Avada Kedavra mostly). Short or odd ones were not: "That's Sandio" (Ascendio), "Aqua, Opto" (Aqua Eructo), "Alamo" (Alohomora), "Outros" (Alatus?), "Umbarata" / "Unbada", "In the medium of theosa" (Wingardium Leviosa), "Show us lemon" (the Sherbet Lemon password). "Refillio" and "Ophelio" already matched Revelio.

## The four changes

1. **Take recorder** (`client/voice/TakeLog`, config `voiceSaveTakes`, off by default): each take as a 16 kHz WAV plus a row in `hogwartsmagic/voice/takes/takes.tsv` (mode, practised spell, heard, matched, distance, margin, model, ms, beam). Newest 1000 kept. Practice takes carry their spell, so they are a labelled set of the player's own voice for `tools/spell_voice/run_bench.sh takes=...`.
2. **Base model** (`voiceModel` BASE | TINY, default BASE; both ship in `src/main/resources/voice/`; ggml-base.en-q5_1.bin 59,721,011 bytes, SHA-256 `4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f`, the Hugging Face ggerganov/whisper.cpp value). The engine swaps model on the next take when the config changes. `voiceBeamSize` (default 1 = greedy).
3. **Voice practice** (`/voicepractice`, or the Voice Practice button in the grimoire): say each known spell 3 times; a take the model already gets right is counted, a mishearing is kept as a personal alias (`spell/voice/VoiceAliases`, stored per model in `hogwartsmagic/voice/aliases.json`). Refused: empty, all everyday English words ("Have a video"), close to another spell, or making any other spell's own incantation stop matching. The matcher takes the aliases (margin is between spells, so an alias never competes with its own spell).
4. **Small fixes:** `VoiceAudio.normalise` brings the loud 30% of 20 ms frames to about -22 dBFS (gain at most 10x, peak at most 0.95) before the model; the prompt lists only the spells the player knows (`VoiceCastClient.PROMPT_KNOWN_ONLY`), matching still runs against every spell.

## Benchmark (tools/spell_voice/run_bench.sh, the 42 incantation voice lines)

The voice lines were made to pass tiny (CheckSpellVoice), so "clean" favours tiny; "quiet" is the clip at -26 dB with hiss, a weak microphone. First eight rows ran beside a gradle build (times inflated); the last two did not.

| Variant | Push-to-talk | Open mic | Wrong | ms/take |
|---|---|---|---|---|
| tiny greedy, clean | 42 | 42 | 0 | (1416) |
| base greedy, clean | 40 | 40 | 0 | (3010) |
| tiny beam 5, clean | 42 | 41 | 0 | (1131) |
| base beam 5, clean | 42 | 42 | 0 | (2465) |
| tiny, quiet, no gain | 33 | 30 | 0 | (1427) |
| tiny, quiet, gain | 37 | 32 | 0 | (1289) |
| base, quiet, no gain | 33 | 33 | 0 | (2466) |
| base, quiet, gain | 33 | 33 | 1 | (2019) |
| tiny, quiet, gain, known-spells prompt | 35 | 35 | 0 | 626 |
| **base, quiet, gain, known-spells prompt** | **38** | **38** | 0 | 1335 |

Ruling: base + gain + known-spells prompt + greedy - best on the weak-microphone set and the spike's real-accent gap (97% against 90%) - cost: about 1.3 s a take against 0.6 s; `voiceModel = TINY` is the way back.
Ruling: beam search stays off by default - no clear gain on these clips - cheap to revisit with real takes.
Ruling: takes are not saved unless the player turns it on - the open mic hears talk near a held wand - the practice screen has the toggle.

## Next

Run practice with recordings on, then `run_bench.sh <models dir> takes=<game dir>/hogwartsmagic/voice/takes model=tiny,... model=base,...` on the player's own voice before changing any default again.
