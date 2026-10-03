# Spell Mastery: final report (Phases 5b, 6a, 6b, 7b)

Branch `spell-mastery`, pushed to GitHub `alonlot/minecraft_mods` branch `claude/keen-lamport-g1ykt4`.
Phase 7a (Patronus forms, Serpensortia, Fiendfyre) was skipped on request.

Verified in the container at the last commit: 220 unit tests pass, 0 compiler warnings, the dedicated
server boots clean, all rule greps clean. Nothing was run on a real game client (the container has none),
so every in-game item below is still to be checked by a person.

Full per-task rulings are in `docs/superpowers/handoff/rulings.md`; full ledgers in `docs/superpowers/handoff/ledgers/`.

## Your instructions this run, and how they landed

| You said | What it became |
|---|---|
| Push to GitHub minecraft_mods | Pushed after each phase to `claude/keen-lamport-g1ykt4` |
| Avada must not be blockable by Protego | Avada passes every Protego (ranked, legacy, NPC); the bypass is per target, so Thorns and side damage still respect shields |
| Possession of players: "make it work" | Players are possessed by relaying the caster's keys to the victim's own client (no server-pushed movement) |
| Leave out 7a | Skipped |
| The puppet is blamed for kills | Already true on every path; nothing names the caster. The puppet still earns no XP |
| Arresto should not work on spells, and never send anything back | Arresto freezes only arrows, tridents, fireballs (all fireball-type projectiles) and Basilisk venom; never any spell beam; the Master redirect task was dropped; Master only widens the radius to 6 |

## Spec drifts for your sign-off

Phase 5b
- Accio never pulls the Elder Wand; side-along and Accio skip hung or chained victims; the Invisibility Cloak is never repaired by Reparo.
- Ascendio: a midair second cast only after a ground launch; Depulso deflects arrows and fireballs only (not tridents, not spell beams).
- Splinching stays as before (can kill), now config-toggleable.

Phase 6a (mind spells)
- Thralls show green particles instead of a glow; the nameplate hides on invisible or crouching thralls.
- Crucio stuns bosses for half the channel; does nothing to a controlled target (says "resists", no XP).
- A resisted Obliviate or Confundo still interrupts a boss's cast.
- Obliviating the last wave of a vanilla raid gives the vanilla victory (Hero reward); the forget is in memory only.
- Death Eaters can enthrall players for 3s.
- Per-victim Imperio lockout after a boss or player thrall ends; mob thralls can chain at Expert.
- Open question for you: should Stupefy, Petrificus, Levicorpus, Incarcerous and Glacius also respect PvP-off on players (the new mind spells do)?

Phase 6b (possession)
- Held spells (Crucio, Protego Maxima, Fiendfyre, Wingardium, Patronus) plus Imperio and Apparition cannot be cast through a puppet; Avada can (its windup runs on the puppet).
- Extra end conditions: over 48 blocks apart, the caster switching wand or spell, no mana, stun/silence/Stupefy on the caster. Only hits of 4+ damage on the frozen body eject you.
- No Nausea after a server stop or when the caster logs out.
- A possessed mob's own AI is paused; flying mobs get no vertical control; a stunned or silenced mob puppet idles.
- Possessed dragons, Basilisks and creepers refuse their special attack when a protected player is within 22 blocks; puppet spells fizzle on players the caster may not harm, and spare the caster's own thralls and allies.
- A possessed Voldemort casts a random one of Stupefy, Sectumsempra, Bombarda.
- A near-zero session still pays Imperio XP (cooldown bounds it).
- Only the first wand's spell selection is restored after a session.
- The freed victim earns no mastery XP for 5s after release (beams are handled exactly; this window covers other awards).
- The 2-second Master hold is enforced on the server too.

Phase 7b
- Arresto as above (your instruction). Tridents freeze and drop; a loyal trident flying home is not frozen. A frozen projectile drops after a save or restart. Arresto does not stop snowballs, potions, pearls, thrown blocks or thrown mobs.
- Expert Wingardium dropping a block as its own item is effectively free silk touch on blocks up to hardness 1.5 (ice, glass, bookshelves, glowstone, leaves). Plan-mandated, kept: say if you want this changed.
- Extra unliftable blocks: budding amethyst, infested blocks, cakes, flower pots, mandrake, powder snow, farmland, dirt path, frogspawn, chorus plant, and any block whose item is not the block itself (wall torches and similar).
- The Master's second held creature is never a player, armor stand, boss, ally, someone else's pet, a thrall, a possessed body, a mount or a rider. A player is never thrown when PvP or teams forbid it.
- Bosses casting Tenebris get Expert pass-through. Pass-through applies to anyone with the Expert effect, flying or not. The ram counts as a melee hit (house melee perks apply). Tenebris II-IV show in the inventory.
- Alatus infusion times follow the plan (5.5s/4s/3s/2s), not the design doc (10/7/5/3s). An upgraded broom keeps the better of its old quality and the new roll; a Master re-infusion is a true reroll and can come out worse.

## In-game checklists (still to run)

Commands: `/spellmastery setrank @s <spell> <1-4>`, `/crowdcontrol reset @e`, `/imperio possess <target>`, `/imperio release`.

### Phase 5b (Reparo, Accio, Ascendio, Depulso, Apparition)
1. Break 10 blocks, Reparo Novice: 4 come back. Master: all 10, from up to 20 blocks.
2. Reparo Adept left-click with a damaged pickaxe in the off-hand: "Expert rank" message, no mana. Expert: +10% durability, a little more mana for diamond than wood.
3. Reparo Expert left-click with nothing damaged: "Nothing ... needs mending", no mana.
4. Accio Novice a cow: reeled in. A 30-health mob: "Too heavy". Voldemort at Master: never.
5. Accio Novice at the floor near dropped items: they fly to you.
6. Accio Expert a chest: the first stack flies to you. In spawn protection, another player's claim, or a locked chest: nothing. A Gringotts vault chest: the alarm goes off.
7. Accio Master a second player with PvP on: their held item flies to you; PvP off: nothing. The Elder Wand never moves.
8. Ascendio Novice at the floor: about 6 blocks up, no fall damage. Master: about 15.
9. Ascendio Expert: jump, cast again midair looking down: a smaller second boost; a third does nothing until you land.
10. Ascendio Master under water: you rocket up. Take-off next to two zombies: they are shoved away.
11. Depulso Novice a zombie a block from a wall: it slams for about 3. In the open: only knockback.
12. Depulso Expert the ground in a crowd: all within 2 blocks fly back. A skeleton arrow crossing the beam turns around.
13. Depulso Master a zombie into another zombie: both take the slam.
14. Apparition Novice: map size 48, 4s to choose. Master: map 200 that fits the screen, 1s.
15. With `apparitionSplinching = false`: never splinched in 20 Novice jumps.
16. Use a Floo fireplace, walk 500 blocks, Apparition Master: the fireplace is listed; clicking it lands you there. Break it, cast again, click it: "fireplace is gone" and it leaves the list.

### Phase 6a (Obliviate, Confundo, Crucio, Avada, Imperio thralls)
1. Obliviate I on a zombie chasing you: it wanders and ignores you for 5s; hitting it makes it attack again.
2. Obliviate a second player: silenced 2s, no blindness.
3. Obliviate IV on a raid pillager: the raid bar drops by one; a raid leader loses its banner.
4. Obliviate and Confundo on the Basilisk, Horntail, Ironbelly, Voldemort: smoke and "... resists the charm".
5. Confundo I on a skeleton next to a zombie: it shoots the zombie for 5s, never you.
6. Confundo IV in a crowd: whatever it hits fights the nearest other creature for 3s; no further spread.
7. Confundo a second player: Nausea and Slowness only.
8. Crucio I on a zombie, keep holding: neither of you moves, red sparks, 1.5 damage every half second; ends after 2s or on release.
9. Crucio a player channelling Protego Maxima: the barrier drops; stunned 60% of the channel.
10. Crucio IV to the end: a surviving zombie runs from you for 10s.
11. Crucio on a boss: it takes damage and is stunned for half the channel.
12. Avada I: green spiral 1.5s, then the beam kills a cow; you take 4. Master: 0.8s, no backlash.
13. Avada windup broken by Stupefy, a 4+ arrow, or Expelliarmus: "The Killing Curse breaks apart!", no mana, Avada locked 5s.
14. Avada on Voldemort or the Ironbelly: loses a fifth of max health, doesn't die. Protego on the target does not stop it.
15. Imperio I on a 20-health zombie: green sparkles, "Thrall of <you>"; it follows, teleports back past 12 blocks, defends you, attacks what you hit, never you; after 20s it ends and the cooldown starts then.
16. Imperio I on an iron golem: "too strong". Imperio III: works; a second thrall allowed; a third says every slot is taken, no mana.
17. Imperio II thrall has Strength I; a villager thrall punches for 2.
18. Imperio on a boss: a thrall for 3s (5s at Expert).
19. Imperio on a player (two clients); with `imperioAffectsPlayers=false` the cast says it's off.
20. Log out holding a thrall, log in: it is back to normal and Imperio is cooling down.
21. Imperio IV beam no longer makes a thrall (it starts possession, see 6b).

### Phase 6b (possession: needs a dedicated server and two clients for the player items)
1. Imperio IV hit a zombie and keep holding 2s: your view jumps to its eyes; your body freezes; WASD walks it, space jumps, the mouse aims; left-click bites what it looks at.
2. Let go: view snaps back, 3s Nausea, Imperio cooldown and XP start now.
3. Possess player B (holding a sword): you see B's hotbar; scroll and number keys change B's item (B can't change it back); left-click swings B's sword; B's own keys and mouse do nothing.
4. Possess B for 20s: it ends by itself; both HUDs clear.
5. B mashes SPACE: nothing before 8s ("Mash SPACE to resist!"); then fast mashing breaks free and B sees "You broke free!"; holding SPACE does not count; your Imperio on B within 3s just puffs.
6. A third player or skeleton hits your frozen body for 4+: thrown out; under 4: not.
7. Ends when B dies, you die, B logs out, you log out (you rejoin unfrozen), you walk B 48+ blocks away, you stop holding the Imperio wand, someone Stupefies your body, you run out of mana.
8. B holds a wand with Stupefy and Avada: the puppet wheel shows B's spells; Stupefy fires from B where you aim; B's mana and cooldown are used; B earns no XP. Crucio, Wingardium, Protego Maxima, Fiendfyre, Patronus, Imperio, Apparition are greyed and refused. With a sword: "Select their wand first". Wheel-cast several spells in one session with right-click held: the session stays up; release right-click with the wheel open: it ends.
9. `/imperio possess` Voldemort: 5s cap, no resist prompt, left-click fires his spells. Horntail: fire breath where you look. Basilisk: venom.
10. Possess a skeleton: it shoots; a creeper: it lights its fuse.
11. `imperioAffectsPlayers=false`: holding Imperio on B says it's off; mobs still work.
12. Close your client while possessing: B regains control within half a second; reconnect: normal camera, not frozen.
13. Scroll or macro spam never changes B's slot more than 10 times a second.
14. Start possessing mid-jump and wait 5s: no kick for flying, the body lands.
15. With toggle-sprint on, B never sprints while possessed.
16. Milk or `/effect clear` on your frozen body keeps you frozen; Crucio then possess keeps you frozen; a possessed piglin or zoglin does not fight you after release.
17. Kill a cow through a possessed player: the death message names the puppet.
18. With PvP off, a possessed Death Eater's beam fizzles on other players and its Bombarda splash spares them; your own body and your thralls are never hit by puppet spells.
19. Stupefy a possessed zombie: it stops moving and attacking while stunned.
20. Watch for rubber-banding on B over a few minutes of normal play; a 0.5s network hiccup ends the session as released (heartbeat).

### Phase 7b (broom bench, Alatus, Wingardium, Arresto, Tenebris)
1. Broom Workbench: Phantom Membrane, Netherite, Elytra fit their slots by click and shift-click; Feather and Gold Block don't. Nimbus and Firebolt craft. The screen dims once.
2. Alatus I: 5.5s, no prefix. Alatus IV: 2s; over a dozen Master infusions some come out Fine (aqua tooltip) and some Masterwork (purple).
3. A Masterwork Firebolt is visibly faster with a longer boost; still Masterwork after relogging while riding. Upgrading a Masterwork broom never comes out worse than Masterwork.
4. Re-infusion (broom in Base, 4+ Arcane Dust in Magic, Boost and Catalyst empty): at III a message and nothing consumed; at IV the same broom with a new quality and exactly 4 dust gone. Swapping the layout mid-infusion cancels it.
5. Wingardium II on stone: nothing. III on stone within 6 blocks, keep holding: it floats in front of you; let go: it falls and places. Chests, obsidian, doors, grass, powder snow: nothing.
6. Wingardium III holding a block, left-click: it flies, hits a zombie for 3, drops; the click does not also hit the held thing. Holding a zombie: it flies and hurts what it hits and itself. A thrown block at a wall drops at its foot and never hits something behind a 1-block wall.
7. In a claim, spawn protection, or a Cave Inimicum ward: can't lift. A block let fall into one drops as an item. A double slab or a 4-candle block that drops gives 2 or 4 items.
8. Wingardium IV on a zombie next to a pig: both float; a throw sends both for 6 each. Dirt with sand on top: both rise. A horse with a rider is never picked as the second.
9. Arresto III self-cast with a skeleton shooting: arrows within 4 blocks hang 2s, then drop and can be picked up. Blaze fireballs stop, then fall and burst. Stupefy and Avada beams keep flying. A trident freezes and drops; a loyal trident still returns. Arrows stuck in nearby walls are not affected.
10. Arresto IV: same with radius 6. Nothing is ever sent back.
11. Tenebris III with a skeleton shooting: about a third of arrows pass through (smoke puff). IV: about half; flying into a zombie deals 5 and blinds 2s, once a second; no ram on players with PvP off, allies, pets, or while possessed or enthralled.
12. Dedicated server, two clients: one player throws the other with Wingardium Expert: one smooth flight, no rubber-banding while held. Frozen arrows look frozen on both screens.
13. Freeze arrows, `/stop` within 2s, restart: the arrows are on the ground.
14. A carried block does not stop arrows or spell beams.
15. A Polyjuice splash potion that passes through a Tenebris flyer does not splash.

## Known leftovers (not done)
- Bootstrap-dependent tests (item codec round trip, recipe slot filters) need a Minecraft test harness the project doesn't have.
- `BroomEntity` debug code is switched off but not deleted; a placed broom can never be turned back into an item (pre-existing).
- A carried block that falls into the void is lost, like a dropped item.
- Pre-existing, outside these phases: an enum switch in `SpellBeamRenderer`; `ModEvents.onLivingHurt` runs at NORMAL priority without an isCanceled guard; some ClientModEvents calls inside a ModEvents client-only path; four unused imports in `SpellPackets`.
- The two-client rubber-band test for possession and Wingardium is still owed.
