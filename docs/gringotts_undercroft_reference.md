# Reference specification — the Gringotts undercroft

The films are the reference and they cannot be looked at from inside the build, so this is the
written transcription the undercroft is judged against. Same purpose as
`.claude/skills/rendering-minecraft-structures/textured/build/shots/REFERENCE_SPEC.md` serves for the
Hogwarts ruin camp: turn "does it look right" into a question with a checkable answer.

Every line below is either visible in *Philosopher's Stone* (the vault visit) or *Deathly Hallows
Part 2* (the break-in), or is a direct consequence of one of the decisions in the plan.

## The overall impression, in one sentence

**A cathedral of raw rock, entered from a marble bank, with a rickety iron railway pinned to its
walls and no visible bottom.**

If a render reads as "a mine", the build has failed. Mines are horizontal, evenly lit and
man-made-looking throughout. This is vertical, unevenly lit, and the *architecture stops* a little
way down — below the top galleries it is cave, not masonry.

## The three zones, in the order you meet them

### 1. Above: the bank
Marble. White quartz, gold banding, granite columns, polished floors, warm even light. This is
already built and must not be altered — it is the *contrast* the rest of the design depends on. The
undercroft's first job is to take that away.

### 2. The transition, roughly the first 25 blocks down
Masonry continues but degrades: the bank's stone-brick and andesite palette persists, courses get
rougher, the finish is gone. Lanterns still hang at regular intervals here and nowhere below.
**This is the only band where the two palettes touch**, and it should read as the bank running out of
money rather than as a seam.

### 3. Below: the rock
Deepslate and blackstone. No dressed stone except where something is *built* — a vault frame, a
trestle, a stair. The rock is the room.

## The chasm — the shot the whole build exists for

- **Vertical, not horizontal.** The eye should be drawn *down*, never along.
- **No visible floor.** You look over the edge and the track spirals away into black. This is
  achieved with a real floor far below the lowest light plus a fog plane, never with a hole.
- **The far wall is never fully visible.** A wall you can see all of is a wall you can measure, and a
  measurable chasm is a small one. Two overlapping lobes so a rock spur always interrupts the far
  side.
- **You can see track you have not ridden yet.** A lit thread of railway far below and across the
  void. This does more for perceived scale than another hundred blocks of width, because the eye
  reads the thread's angular size rather than the distance to the rock.
- **Water falls past and never arrives.** A handful of streams down the wall, passing into the fog.
  The single most convincing depth cue available.
- **Stalactites hang in clusters**, 3–7 with a shared base and graded lengths — never evenly spaced
  singles, which read as confetti rather than as rock.

## The railway

- **Pinned to the rock, not cut through it.** Wherever possible the track is a *ledge* with the void
  open on one side, or a *trestle* with void on both and no ceiling at all. Fully enclosed bore is
  the exception, used to get between places, and never for long.
- **The parapet is one block.** Two blocks reads as a rampart and hides the drop, which is the entire
  point of being up there.
- **It is old and under-maintained.** Occasional missing sleepers, a rail running over a patch of
  bare rock, chains and props holding a ledge up. Nothing pristine.
- **Rails are decorative.** The cart follows its own path; the rails exist so the eye believes it.
- **Junctions and dead ends.** Not every branch goes anywhere. A spur that ends at a bricked-up
  vault mouth says the bank is older than the player.

## The vault mouths

- **Cut into the cliff face**, at wildly different heights, so the wall reads as inhabited all the way
  down rather than at one level.
- **The doors contrast hard with their frames.** Dark stone surround, bright worked metal leaf. The
  Chamber of Secrets door is the cautionary example: chiseled tuff in a deepslate wall, ten points of
  luma apart, and the most important door in the mod reads as a smudge.
- **Big enough for the animation to happen in.** A subtle vault door is a wasted vault door.
- **Numbered, irregularly.** Vault 687 and vault 713 are not next to each other.

## Lighting

Darkness is the default and light is an event. Lit moments, with long unlit runs between them:

| Where | Colour | Why |
|---|---|---|
| Station | warm, bright | the last comfortable place |
| Vault mouths | warm, small | you are meant to notice them from across the void |
| **Thief's Downfall** | **cold blue-white — the only cold light in the build** | it should be visible from far above, and read as *wrong* |
| Goblin lamp clusters | warm, clustered | somebody works here |
| Dragon cavern | red, from below | uplighting a thing that big is what makes it read as big |
| Deep vault | gold | the payoff |
| The chasm floor | **nothing at all** | this is what makes it bottomless |

Never evenly spaced. Objects at equal intervals read as fence posts.

## Deliberate non-goals

- **Not a spiral.** Two of ten segments are recognisable spirals. A single spiral around a single
  axis is the failure mode this build is most likely to fall into, and a render of one is instantly
  boring.
- **Not symmetrical.** Nothing here should be mirrored or repeated at a fixed interval.
- **Not fully lit.** If a render shows the whole chasm clearly, the fog is wrong.
- **Not clean.** The bank above is clean. That is its job.

## How to check

Render textured with `cut=z` — the undercroft is sealed rock and shows nothing from outside. Then
read down this list. The failures that survive a passing block audit are, in order of how expensive
they have been historically:

1. density of loud blocks (cobweb, gold, stalactites) — check textured, never flat
2. something out of proportion eating the frame
3. a focal point with no presence
4. scatter where structure was intended
5. palette contrast at one-block frequency
6. mechanical regularity
