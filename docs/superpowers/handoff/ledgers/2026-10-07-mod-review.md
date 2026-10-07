# Ledger: full mod review, 2026-10-07 night window

Base: 2d2bce4. Eleven parallel reviewers, one per package group, then a second wave of four on items the first wave could not reach. Branch ccr-7b6fcfa4-2vnkm0.

## Verification limit
No Forge toolchain was available in the container (no Gradle cache, no network for the Minecraft decompile). Every Java file passed a javac parse-only check and every JSON file loads, but NOTHING WAS COMPILED OR RUN. Before merge: `./gradlew build`, run the unit tests, boot a dedicated server and a client, and play-test the items marked "play-test" below.

## Play-test list (behaviour changed)
- Cast a spell with no mana: mana must now go down (free casts were possible before).
- Death Eater base raids: they must now start near player bases (the gate never fired before).
- Log out while channelling Fiendfyre, Wingardium, Protego Maxima or Apparition: nothing must stay in the world.
- Student and Wizard Protego: their shield must lose a charge per hit.
- Whomping Willow: save and reload while it is awake; it must still attack.
- Dig under the Forbidden Forest: the biome name must not change.
- Break ward, horcrux (inactive), willow sapling, broom workbench and archway brick: they must drop.
- Villagers: apothecary, bookseller, owl merchant, broom merchant must take their job sites.
- Creative tab must show the name "Hogwarts Magic".
- Floo: name a fireplace with a 40-character name; it must be refused.

## ModEvents.java (agent 1)
FIXED: base raid gate on gameTime (never fired before); logout stops all channels; ServerStopped clears static maps; raid member map leak; ground probe floor at min build height; Math.random -> entity random; redundant null check.
NOT FIXED: Tenebris first-tick ink uses tickCount<5; 40 particles/tick per flier; raid "cleared" on unload grants Hero of Village; duplicate onEntityInteract handlers; 111k block scan per player per 30s; channelTicks div guard at 1818; onLeftClickBlock client/server desync; Name Tag block naming not consumed.
## network/SpellPackets.java (agent 2)
FIXED: central sender!=null guard for all client-bound packets (guest could spoof host mana/relics/screens on LAN); Floo destination/name packets validate loaded pos + 32 block distance + name <=32 chars (chunk-load DoS, remote Floo); mana deduction wrote to a detached PlayerPersisted compound -> free casts, now PlayerMagicData.saveModData; Floo list encoder capped at 256 to match decoder.
NOT FIXED: redundant enqueueWork in broom packets; redundant per-record sender checks; SelectSpell no learned check; CaveInimicum region sync length check; dead clamp in Apparition map decoder.
## command/, root, event/ (agent 3)
FIXED: Gemino dropped galleon never burned out (setUnlimitedLifetime pinned age); Mandrake pull map leaked on logout and left PULLING state; ExampleMod.onServerStarting was static on an instance registration so it never fired; OllivandersCeremony.endAll now called on ServerStopping; Config.onLoad made public.
NOT FIXED: Find* commands and /ollivanders have no permission check; cart camera config read per frame; Ollivanders markDone on replay.
## worldgen/ (agent 4)
FIXED: Forbidden Forest biome only had the surface climate point so it flipped to another biome underground; DiagonAlley and MiniHogwarts tick() had no exception guard (one bad tag rethrew every tick forever); static build jobs survived server stop and wrote into the previous world's unloaded level (new WorldgenLifecycle resets them); Undercroft FOGGED per-player map never cleared.
NOT FIXED: CarveSink chasm can write >48 blocks from station (sync chunk loads); DiagonAlleyView sync getChunk in tick; schematic sampling step could be 0; Undercroft completion may re-run spawns after an exception.
## block/item/menu/misc (agent 5)
FIXED: wand workbench shift-click granted Lumos before the move (double grant); Floo token and Owl route tag mutated stacks and block entities on the client too (desync) and dereferenced a null player; Cave Inimicum ward odd size step threw IllegalArgumentException every tick; snapshot load of a bad edge length threw during chunk load; duplicate update packet build.
NOT FIXED: config max edge 256 vs snapshot cap 64; DuelManager leaks on logout; ReparoManager leaks on dimension unload; Broom workbench invalid menu mayPlace; SortingHat async callback writes to stale player; Floo fireplace re-lights every tick.
## entity/ part B (agent 6)
FIXED: Basilisk venom hurt/effects on client (ghost effects); Fiendfyre and Dark Mark client mutated synched counters; Dementor 16-block patronus scan every tick throttled to every 5 ticks.
NOT FIXED: StudentModel static maps leak on client; Dementor suck goal 24-block scan per tick; BroomEntity TEMPORARY DEBUG WARN log spam; WizardEntity hurt without client guard and dead client particle branch; HorcruxSoul moves/discards on both sides; empty FlyingMobRenderer.java file in entity package.
## spell/mind,damage,disable,combat,motion,mastery (agent 7)
FIXED: Crucio FLEEING map leaked for despawned mobs; duplicate ROOTED removal in Crucio end().
NOT FIXED: IncarcerousChainAnimation phases collapse into one tick (TickTask runs early when server has idle time); four identical RankIndex copies; Ascendio nearby entities missing hurtMarked; Stupefy interrupts fully-immune targets; MASTER_CAST_AT never expires while online.
Checked OK: possession/thrall cleanup, mastery math, CC pruning, NBT keys.
## entity/ part A (agent 8)
FIXED: Student Protego charge decrement was a no-op (addEffect never lowers amplifier) so NPC shields had unlimited charges; Whomping Willow saved awake reloaded as DORMANT with awake flag (stuck forever); Hippogriff sit toggle flipped synched data on client.
NOT FIXED: Horntail breath damage unattributed (no death message/credit); Willow and Student per-tick scans; Horntail/Giant write synched state tick every tick; dead Arresto homing code in Student; WizardEntity has the same Protego no-op bug (line ~100).
## client/ + mixin/ (agent 9)
FIXED: Fiendfyre trail cache leak; WizardModel static cast maps leak; Polyjuice disguises and surrogates leaked ClientLevel across worlds; CaveInimicum shouldHideEntity allocated a list per entity per frame; mixin formatting.
NOT FIXED: unregistered dead LevicorpusLivingEntityRendererMixin (delete, do not register: would double-flip); dead SpellHUD/OwlAnimation/WhulvkLycanIdleToWolfAnimations; FlyingMobModel in wrong directory for its package; CaveInimicum sound mixins run on netty thread; mixed remap=false convention.
## spell/support (agent 10)
FIXED: CircuitTracer clamps world signal reads to 0..15 (modded block >15 would throw mid-trace on the server thread).
NOT FIXED: FORCE hint label; /lockinfo never lists item keys; STEPS not cleared on logout; per-dimension queues linger after dimension unload.
## resources (agent 11)
FIXED: 30 missing en_us lang keys; patronus_light_block had no blockstate/model; 5 blocks had no loot table and dropped nothing; acquirable_job_site tag only listed the wand workbench POI so 4 villager professions could never take their job.
NOT FIXED: 8 whomping_willow sound events point to missing .ogg files; flying_mob.png texture missing; 18 of 23 mob effects have no icon; lumos_light blockstate references minecraft:block/air which may not exist as a model.
