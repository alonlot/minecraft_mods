# Garden Gnomes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A garden-pest mob that uproots ripe crops and digs up lawns, and that the player de-gnomes by grabbing it, whirling it round and flinging it over the hedge.

**Architecture:** `GnomeEntity` (a `PathfinderMob`) with a synced `State` byte and goal classes copied from the Niffler's pattern. All throw, spin, bite and dig maths lives in the pure `GnomeRules` (JUnit-tested). Grabbing makes the gnome ride the player (`startRiding(player, true)`) and puts an invisible placeholder item, `HeldGnomeItem`, in the main hand; vanilla item use then carries "hold use" to the server with no new packet (`onUseTick` = spin, `releaseUsing` = throw). The gnome positions itself at the hand or on the spin orbit in its own `rideTick()`, on both sides, so the client draws it smoothly.

**Tech Stack:** Forge 51, Minecraft 1.21, Java 21, official mappings; Python + PIL texture/geometry generator; ElevenLabs SFX + ffmpeg.

**Spec:** `docs/superpowers/specs/2026-11-24-wizarding-world-additions-design.md`, section 1. Also read `C:/Users/alonl/hp-ww-tools/BUILDER_RULES.md` fully before starting.

## Global Constraints

- Worktree `C:/Users/alonl/hp-wizard-world`, branch `wizard-world`, base sha `a8f933fd`. Never touch the main checkout.
- Never run `gradlew`. Compile: `bash C:/Users/alonl/hp-vfx-tools/compile.sh C:/Users/alonl/hp-wizard-world` (timeout 600000) must print `COMPILE OK`. JUnit: `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "<pattern>"` must print `TESTS OK`.
- Size about 0.5 blocks tall: potato-shaped, bald, knobbly head, leathery brown-grey skin. Java `ModelPart` model, generated texture.
- Spawns by day, groups of 2–4, on `plains`, `sunflower_plains`, `meadow`, `forest`, `flower_forest`. Drawn to farmland and villages.
- Throw reach up to about 25 blocks. Thrown 12+ blocks: lands dazed, then leaves for good (despawns once out of sight). Held without spinning: bites for 1 heart (2.0 damage) a few seconds later and wriggles free.
- Uprooting and digging respect `GameRules.RULE_MOBGRIEFING` (check it the way `GiantEntity` does).
- Server/client safety: nothing under `entity/` or `item/` imports `client/` or `net.minecraft.client.*`. No `switch` on an enum in client code (use if/else). No entity event bytes 21/35/63.
- Shared files (`ExampleMod`, `ModEntities`, `ModSounds`, `sounds.json`, `en_us.json`, `ModEntityAttributes`, `ModSpawnPlacements`, `ClientModEvents`, creative tab): one compact block at the END of each list, under a `// Garden gnomes` comment.
- Javadoc says what the player sees, British spelling. One commit at the end (Task 6), not pushed.

## Review Focus

1. The player switches hotbar slot, drops (Q) or moves the placeholder, dies or logs out while carrying: the gnome must be let go and no `held_gnome` stack may survive in any inventory or on the ground. (Task 3 test `switchingTheHotbarSlotLetsGo`; the item self-removes in `inventoryTick` and `onEntityItemUpdate`.)
2. Two grabs at once: a gnome another player holds, or a second gnome for a player who already holds one, must be refused. (Task 3 test `aSecondGrabIsRefused`.)
3. `mobGriefing` off: no crop is pulled and no ground is dug. (Task 2 test `noGriefingMeansNoCropsAndNoHoles`.)
4. Digging must never open a hole over a cave, and never touch farmland or village paths. (Task 2 test `digTurnsGrassToCoarseDirtAndOnlyPitsOverSolidGround`.)
5. A thrown gnome must not take fall damage or be stuck in the THROWN state when it hits a wall or water. (Task 3 test `aLobLandsDazedNearbyAndStays` asserts full health; `travel` lands on `horizontalCollision`/`isInWater`/400-tick cap.)

---

### Task 1: GnomeRules (pure throw, spin, bite, dig and sight maths)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/GnomeRules.java`
- Test: `src/test/java/com/hogwartsmagic/entity/GnomeRulesTest.java`

**Interfaces:**
- Produces (no Minecraft imports; all `public static`):
  - Constants: `int FULL_SPIN_TICKS = 60`; `double MIN_THROW_SPEED = 0.35`; `double MAX_THROW_SPEED` (start at 1.35, tune until the reach test passes); `double GRAVITY = 0.06`; `double DRAG = 0.99`; `double THROW_HEIGHT = 1.2` (launch point above the thrower's feet); `double GONE_DISTANCE = 12.0`; `int BITE_DELAY_TICKS = 60`; `float LIFT_DEGREES = 15f, MIN_ELEVATION_DEGREES = 10f, MAX_ELEVATION_DEGREES = 50f`; `double SPIN_RADIUS = 1.2, SPIN_HEIGHT = 0.9`; `double MIN_SPIN_DEG_PER_TICK = 6, MAX_SPIN_DEG_PER_TICK = 24`; `double HAND_FORWARD = 0.6, HAND_RIGHT = 0.38, HAND_UP = 0.75`; `float PIT_CHANCE = 0.3f`; `int DIG_NONE = 0, DIG_COARSE = 1, DIG_PIT = 2`; `double VIEW_COS = 0.5`.
  - `double throwSpeed(int spinTicks)`: linear from MIN to MAX over `FULL_SPIN_TICKS`, capped.
  - `double launchElevationRadians(float xRotDegrees)`: Minecraft pitch (negative = looking up); `clamp(-xRot + LIFT, MIN, MAX)` in radians.
  - `double flightDistance(double speed, double elevationRad, double dropHeight)`: horizontal distance to landing `dropHeight` below the launch point.
  - `boolean goneForGood(double distance)`: `distance >= GONE_DISTANCE`.
  - `boolean biteDue(int carriedTicks, boolean everSpun)`.
  - `double spinAngleDegrees(double spinTicks)`: angular speed ramps from MIN to MAX over `FULL_SPIN_TICKS`, then holds. Closed form: `t <= F ? MIN*t + (MAX-MIN)*t*t/(2F) : angle(F) + MAX*(t-F)`.
  - `double[] handOffset(float yawDegrees)`: `{dx, dy, dz}` of the carried gnome's origin from the carrier's feet. Forward = `(-sin yaw, 0, cos yaw)`, right = `(-cos yaw, 0, -sin yaw)`.
  - `double[] spinOffset(float yawDegrees, double spinTicks)`: point on the orbit at angle `yaw + spinAngleDegrees(spinTicks)`: `(-sin a * R, SPIN_HEIGHT, cos a * R)`.
  - `int digOutcome(boolean grassOrDirt, boolean coarse, boolean sturdyBelow, float roll)`.
  - `boolean inView(double lookX, double lookY, double lookZ, double toX, double toY, double toZ)`: normalised dot `> VIEW_COS`.

`flightDistance` must step in exactly the order `GnomeEntity.travel` uses (Task 3): each tick `x += vx; y += vy;` then `vx *= DRAG; vy = (vy - GRAVITY) * DRAG;`. It lands when `y <= -dropHeight`, interpolating linearly inside the last tick. Cap at 400 ticks.

- [ ] **Step 1: Write the failing tests** (JUnit 5, `assertEquals(expected, actual, 1e-6)` unless stated)

```java
@Test void throwSpeedRisesWithSpinAndIsCapped()      // throwSpeed(0)==MIN; throwSpeed(FULL)==MAX; throwSpeed(10_000)==MAX;
                                                      // throwSpeed(t) strictly increasing for t in 0..FULL
@Test void aFullSpinFlingsAboutTwentyFiveBlocksAndNoFurther()
    // for e in 10..50 deg: flightDistance(throwSpeed(FULL), rad(e), THROW_HEIGHT) <= 26.0; the max over e >= 23.0
@Test void aBriefSpinIsNotEnoughToBeRidOfIt()
    // flightDistance(throwSpeed(20), rad(45), THROW_HEIGHT) < GONE_DISTANCE
    // flightDistance(throwSpeed(45), rad(45), THROW_HEIGHT) >= GONE_DISTANCE
    // flightDistance(throwSpeed(0), launchElevationRadians(0), THROW_HEIGHT) < 4.0   (Task 3's lob test relies on this)
@Test void launchElevationAddsLiftAndClamps()          // xRot 0 -> rad(15); -20 -> rad(35); -90 -> rad(50); 90 -> rad(10)
@Test void goneForGoodFromTwelveBlocks()               // 11.99 false; 12.0 true
@Test void onlyAnUnspunGnomeBitesAndOnlyAfterTheDelay() // biteDue(59,false) false; biteDue(60,false) true; biteDue(500,true) false
@Test void theSpinSpeedsUpThenHolds()                  // angle(0)==0; strictly increasing over 0..200;
                                                      // angle(FULL+1)-angle(FULL)==MAX_SPIN_DEG_PER_TICK (1e-6);
                                                      // angle(1)-angle(0) < MIN_SPIN_DEG_PER_TICK + 1
@Test void theOrbitKeepsItsRadiusAndStartsInFront()    // for yaw in {0,-90,37}, t in {0,13.5,90}: hypot(dx,dz)==SPIN_RADIUS, dy==SPIN_HEIGHT;
                                                      // spinOffset(0,0) == {0, SPIN_HEIGHT, SPIN_RADIUS}
@Test void theHandIsForwardAndToTheRight()             // handOffset(0) == {-0.38, 0.75, 0.6}; handOffset(-90) == {0.6, 0.75, 0.38}
@Test void digOutcomes()                               // (true,false,*,any)->COARSE; (false,true,true,0f)->PIT;
                                                      // (false,true,true,PIT_CHANCE)->NONE; (false,true,false,0f)->NONE; (false,false,true,0f)->NONE
@Test void inViewIsASixtyDegreeCone()                  // look (0,0,1): to (0,0,5) true; (0,0,-5) false; (5,0,5) true; (5,0,1) false
```

- [ ] **Step 2: Run, expect failure** `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "com.hogwartsmagic.entity.GnomeRulesTest"`. Expected: compile failure, `GnomeRules` missing.
- [ ] **Step 3: Implement `GnomeRules`** to the Interfaces above. Tune only `MAX_THROW_SPEED`.
- [ ] **Step 4: Run again.** Expected: `TESTS OK`.

---

### Task 2: GnomeEntity, its garden mischief, registration and spawning

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/GnomeEntity.java`
- Create: `src/main/java/com/hogwartsmagic/gametest/GnomeGameTests.java`
- Create: `src/main/resources/data/hogwartsmagic/forge/biome_modifier/gnome_spawns.json` (`forge:add_spawns`, biomes `#hogwartsmagic:has_gnome`, `hogwartsmagic:gnome` weight 6, minCount 2, maxCount 4)
- Create: `src/main/resources/data/hogwartsmagic/tags/worldgen/biome/has_gnome.json` (the five biomes in Global Constraints)
- Create: `src/main/resources/data/hogwartsmagic/tags/item/gnome_food.json` (carrot, potato, beetroot, wheat)
- Modify (append, `// Garden gnomes`): `entity/ModEntities.java` (`GNOME`: `MobCategory.CREATURE`, `.sized(0.4F, 0.5F)`, `.eyeHeight(0.42F)`, `.clientTrackingRange(10)`, `.updateInterval(2)`); `event/ModEntityAttributes.java`; `event/ModSpawnPlacements.java` (ON_GROUND, MOTION_BLOCKING_NO_LEAVES, `GnomeEntity::canSpawn`, REPLACE); `ExampleMod.java` (`GNOME_SPAWN_EGG`, `ForgeSpawnEggItem`, colours `0x7D6E5D`/`0x4B3F35`, plus creative tab line after the last spawn egg); `ModSounds.java` (`GNOME_GIGGLE` "gnome.giggle", `GNOME_SQUEAK` "gnome.squeak", `GNOME_BITE` "gnome.bite", `GNOME_WAIL` "gnome.wail"); `sounds.json` (giggle: `gnome/giggle1..3`; squeak: `gnome/squeak1..2`; bite: `gnome/bite`; wail: `gnome/wail`, each with a `subtitle`); `lang/en_us.json` (`entity.hogwartsmagic.gnome` "Garden Gnome", spawn egg, `item.hogwartsmagic.held_gnome` "Struggling Gnome", subtitles "Gnome giggles", "Gnome squeaks", "Gnome bites", "Gnome wails").

**Interfaces:**
- Consumes: `GnomeRules.digOutcome`, `DIG_*`, `PIT_CHANCE`.
- Produces:
  - `public class GnomeEntity extends PathfinderMob`
  - `public enum State { NONE(0), PULL(20), EAT(30), DIG(40), CARRIED(0), SPUN(0), THROWN(0), DAZED(60); public final int ticks; }`. Synced as a byte (`DATA_STATE`) like the Niffler's `Action`. Timed states return to NONE when `ticks` run out.
  - `public State state()`, `public void setState(State s)`, `public float stateTime(float partialTick)` (client, like `NifflerEntity.actionTime`)
  - `public static AttributeSupplier.Builder createAttributes()`: MAX_HEALTH 6, MOVEMENT_SPEED 0.3, FOLLOW_RANGE 16
  - `public static boolean canSpawn(EntityType<GnomeEntity>, ServerLevelAccessor, MobSpawnType, BlockPos, RandomSource)`
  - `@Nullable public BlockPos findRipeCrop()`: nearest `CropBlock` with `isMaxAge(state)` within 10 blocks horizontally and ±2 vertically. Null when `mobGriefing` is off.
  - `public int digAt(BlockPos ground, float roll)`: applies `digOutcome` to the block at `ground`. COARSE: `grass_block`/`dirt` → `coarse_dirt`. PIT: `coarse_dirt` → air, only when `ground.below()` is face-sturdy UP. Never touches farmland, `dirt_path` or anything else. Returns `DIG_NONE` when `mobGriefing` is off.
  - `public int cropsEaten()` (session counter, for tests)

Behaviour to build:
- `canSpawn`: returns true for `SPAWNER`. Otherwise needs all of: `grass_block` below; `level.getLevel().isDay()`; raw brightness > 8; `checkMobSpawnRules`; and either farmland or `dirt_path` within 8 blocks horizontally and ±3 vertically (`BlockPos.betweenClosed`, which stays inside the worldgen region), or `random.nextInt(3) == 0`. "Near villages" is covered by the `dirt_path` check. Do not call `isVillage` here: it reads POI data and is a worldgen deadlock risk.
- Goals:
  - 0 `FloatGoal`
  - 1 `BusyGoal`: any state but NONE roots it (MOVE, LOOK, JUMP), as the Niffler does.
  - 2 `LeaveGoal`: runs while `leaving` (Task 3). It runs away from the nearest player at speed 1.4, or in a random direction when there is none.
  - 3 `AvoidEntityGoal<Player>`: 7 blocks, speeds 1.0/1.35, predicate `EntitySelector.NO_CREATIVE_OR_SPECTATOR` and `!p.isDiscrete()`. A crouching player can creep up on it. Squeak when it bolts.
  - 4 `UprootCropGoal`: re-scans every 40–100 ticks, with no cooldown when freshly spawned. It walks to the crop. Within 1.5 blocks it enters PULL. When PULL ends it re-checks the crop is still ripe, then calls `level().destroyBlock(pos, true, this)`. Next tick it takes one item in `#hogwartsmagic:gnome_food` from an `ItemEntity` within 2 blocks via `take(item, 1)` + `shrink(1)`, enters EAT, sends item-crack particles (`ServerLevel.sendParticles`) and counts `cropsEaten++`. Then it giggles and waits 200–400 ticks. It gives up after 200 ticks.
  - 5 `DigHoleGoal`: on the ground, every 600–1800 ticks, no player within 6. It enters DIG, then calls `digAt(blockPosition().below(), random.nextFloat())`.
  - 6 `SeekFarmGoal`: when no ripe crop was found, every 400–800 ticks it samples 24 random columns within 24 blocks (surface from `Heightmap.Types.WORLD_SURFACE`) for farmland and walks there.
  - 7 `WaterAvoidingRandomStrollGoal(0.8)`, 8 `LookAtPlayerGoal(6)`, 9 `RandomLookAroundGoal`.
- Sounds: ambient `GNOME_GIGGLE` (`getAmbientSoundInterval` 160), hurt `GNOME_SQUEAK`, death `GNOME_SQUEAK` at pitch × 0.8, voice pitch × 1.1.
- Despawning: leave `removeWhenFarAway` true (not persistent). Gnomes are a recurring pest that thins out away from the player, not a population that piles up.
- Save `leaving`, the cooldowns, and nothing about carrying.

- [ ] **Step 1: Write the failing GameTests** in `GnomeGameTests` (`@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty9`, tick by hand as `NifflerGameTests` does, helper `step(g)` = `g.isPassenger() ? g.rideTick() : g.tick()`):

```java
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void aGnomeUprootsAndEatsARipeCarrot(GameTestHelper h)
    // dirt at y=1 across the pad; farmland at (5,1,4) with CARROTS age 7 at (5,2,4); farmland (5,1,6) with CARROTS age 3 at (5,2,6);
    // gnome spawned at (2,2,4); step up to 400 ticks until cropsEaten()==1
    // assert: (5,2,4) is air; cropsEaten()==1; FARMLAND still at (5,1,4); CARROTS still at (5,2,6) (unripe left alone)
@GameTest(template = EMPTY9, timeoutTicks = 20, batch = "gnome_griefing")
static void noGriefingMeansNoCropsAndNoHoles(GameTestHelper h)
    // same carrot layout + GRASS_BLOCK at (2,1,2); save rule, set mobGriefing false:
    // findRipeCrop()==null; digAt(abs(2,1,2), 0f)==DIG_NONE and GRASS_BLOCK still there;
    // restore the saved value; findRipeCrop() equals absolutePos(5,2,4); succeed. All synchronous, same tick.
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void digTurnsGrassToCoarseDirtAndOnlyPitsOverSolidGround(GameTestHelper h)
    // GRASS_BLOCK (2,1,2): digAt(.., 0.99f)==DIG_COARSE, block COARSE_DIRT
    // COARSE_DIRT (4,1,4) over STONE (4,0,4): digAt(.., 0f)==DIG_PIT, block AIR
    // COARSE_DIRT (6,1,6) over AIR (6,0,6): digAt(.., 0f)==DIG_NONE, still COARSE_DIRT
    // FARMLAND (6,1,2) and DIRT_PATH (2,1,6): digAt(.., 0f)==DIG_NONE, unchanged
```

- [ ] **Step 2: Compile** with `compile.sh`. Expected: fails until `GnomeEntity` exists.
- [ ] **Step 3: Implement** `GnomeEntity` and the registrations and data files above.
- [ ] **Step 4: Compile.** Expected: `COMPILE OK`. (The GameTests are run by the architect after the merge.)

---

### Task 3: Grab, spin, throw, bite, daze and leave

**Files:**
- Create: `src/main/java/com/hogwartsmagic/item/HeldGnomeItem.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/GnomeEntity.java`
- Modify (append, `// Garden gnomes`): `ExampleMod.java`: `HELD_GNOME = ITEMS.register("held_gnome", () -> new HeldGnomeItem(new Item.Properties().stacksTo(1)))`. NOT in any creative tab.
- Modify: `src/main/java/com/hogwartsmagic/gametest/GnomeGameTests.java`

**Interfaces:**
- Consumes: Task 1 `throwSpeed`, `launchElevationRadians`, `GRAVITY`, `DRAG`, `THROW_HEIGHT`, `goneForGood`, `biteDue`, `handOffset`, `spinOffset`, `inView`, `FULL_SPIN_TICKS`. Task 2 `State`, `setState`.
- Produces, on `GnomeEntity`:
  - `public boolean grab(Player p)`: needs main hand empty, state NONE/PULL/EAT/DIG, not leaving, not a passenger, `p.getPassengers().isEmpty()`, not spectator. Effects: `startRiding(p, true)`, state CARRIED, `carriedTicks = 0`, `everSpun = false`, main hand set to `new ItemStack(HELD_GNOME)`, squeak.
  - `public void spinTick()`: server; state SPUN, `everSpun = true`.
  - `public void throwFrom(Player p, int spinTicks)`: `stopRiding()`. Position = carrier feet + flat look × `HAND_FORWARD` + `(0, THROW_HEIGHT, 0)`. Velocity = `throwSpeed(spinTicks)` × `(cos e · flatLook, sin e)` with `e = launchElevationRadians(p.getXRot())`. State THROWN, remember `throwOrigin`, `hurtMarked = true`, play `GNOME_WAIL` entity-bound (`level.playSound(null, this, ...)`), `HeldGnomeItem.removeFrom(p)`.
  - `public void landAfterThrow(double distance)`: state DAZED, `lastThrowDistance = distance`, `leaving = goneForGood(distance)`, thump + squeak.
  - `public void wriggleFree(boolean bite)`: on a bite, `carrier.hurt(damageSources().mobAttack(this), 2.0F)` and `GNOME_BITE`. Then `stopRiding`, set position at the carrier's feet + forward 0.6, a little hop, state NONE, `HeldGnomeItem.removeFrom(carrier)`.
  - `public boolean isLeaving()`, `public double lastThrowDistance()`, `public int carriedTicks()`, `public float clientSpinTicks(float partialTick)`. It returns `spinTicks + partialTick`. `spinTicks` is a local counter on each side, incremented in `rideTick` while SPUN and reset otherwise. Spin start is not synced, only the state byte. The throw speed uses the item's authoritative `72000 - timeLeft`, not this counter.
  - Constants: `DAZE_TICKS = State.DAZED.ticks` (60), `LEAVE_MAX_TICKS = 400`, `SIGHT_RANGE = 64`.
- Produces, on `HeldGnomeItem extends Item`:
  - `@Nullable static GnomeEntity carriedBy(Entity e)`: first `GnomeEntity` passenger.
  - `static void removeFrom(Player p)`: clears every `HELD_GNOME` stack in the inventory, including the offhand.
  - `use`: `startUsingItem` + `consume` when `carriedBy(player) != null` (checked on both sides), else `fail`. `getUseDuration` 72000. `getUseAnimation` `UseAnim.NONE`. The vanilla use-slowdown reads as spinning on the spot.
  - `onUseTick`: server; `carriedBy(entity).spinTick()`.
  - `releaseUsing(stack, level, entity, timeLeft)`: server; `throwFrom(player, 72000 - timeLeft)`.
  - `inventoryTick`: server; removes itself when `carriedBy(entity) == null`.
  - `onEntityItemUpdate`: discards the `ItemEntity`, returns true.

Behaviour to build in `GnomeEntity`:
- `mobInteract(p, hand)`: when `hand == MAIN_HAND` and the main hand is empty, calls `grab(p)` on the server. On the client it returns success when the synced state looks grabbable. Returns `InteractionResult.sidedSuccess(client)`.
- `rideTick()`: calls `super.rideTick()`, then, when the vehicle is a `Player`, sets the position on both sides. CARRIED uses `carrier.position() + handOffset(carrier.getYHeadRot())`. SPUN uses `spinOffset(carrier.getYHeadRot(), spinTicks)`, and sets yRot/yBodyRot to face away from the carrier. Server only: `carriedTicks++`. If the carrier is dead or its main hand is not `HELD_GNOME`, `wriggleFree(false)`. Otherwise, if `biteDue(carriedTicks, everSpun)`, `wriggleFree(true)`.
- `aiStep`: if the state is CARRIED/SPUN but the gnome is not riding a `Player` (its carrier logged out, or the gnome was dismounted), reset to NONE. While DAZED on the client, every 4 ticks it adds an `ENCHANTED_HIT` particle circling the head.
- `travel(Vec3)`: when THROWN on the server it calls `move(MoverType.SELF, dm)`, then `setDeltaMovement(dm.x*DRAG, (dm.y-GRAVITY)*DRAG, dm.z*DRAG)`, and `resetFallDistance()`. After 2+ thrown ticks it lands on `onGround() || horizontalCollision || isInWater()`, or after 400 ticks: `landAfterThrow(horizontal distance from throwOrigin)`. Every other state calls `super.travel`.
- `isPickable()`: false while a passenger, so the carrier's crosshair and attacks pass through it.
- Leaving (server, every 10 ticks after DAZED ends): `leaveTicks += 10`. Call `discard()` when `leaveTicks >= LEAVE_MAX_TICKS`, or when no player within `SIGHT_RANGE` both `hasLineOfSight(this)` and has it `inView` of their look vector. Saved `leaving` → discard on the first tick after load.
- Class Javadoc: "Click to grab it by the ankle; keep holding use to whirl it round; let go to fling it. Hold it without whirling and it bites you."

- [ ] **Step 1: Write the failing GameTests** (mock players need `setGameMode(GameType.SURVIVAL)`; the mock defaults to creative):

```java
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void anUnspunGnomeBitesAndWrigglesFree(GameTestHelper h)
    // stone floor y=1; p at (1.5,2,4.5); gnome at (3,2,4); mobInteract(p, MAIN_HAND).consumesAction()
    // g.getVehicle()==p; p.getMainHandItem().is(HELD_GNOME); state()==CARRIED
    // step 30: still riding p, p.getHealth()==20
    // step 40 more: p.getHealth()==18.0F; !g.isPassenger(); no HELD_GNOME stack anywhere in p's inventory
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void aSecondGrabIsRefused(GameTestHelper h)
    // p1 grabs g1; g1.mobInteract(p2) leaves g1.getVehicle()==p1 and p2's hand empty;
    // g2.mobInteract(p1) leaves g2 not a passenger
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void switchingTheHotbarSlotLetsGo(GameTestHelper h)
    // p grabs g; p.getInventory().selected = 1; step(g) once
    // !g.isPassenger(); no HELD_GNOME in p's inventory; p.getHealth()==20; g.state()==NONE
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void aLobLandsDazedNearbyAndStays(GameTestHelper h)
    // stone floor 9x9 at y=1; p.moveTo(abs(1.5,2,4.5), yaw -90, pitch 0); p grabs g; g.throwFrom(p, 0)
    // step up to 100 while state()==THROWN; then state()==DAZED; !isLeaving(); lastThrowDistance() < GONE_DISTANCE;
    // g.getHealth()==g.getMaxHealth(); step DAZE_TICKS+5: state()==NONE and !g.isRemoved()
@GameTest(template = EMPTY9, timeoutTicks = 20)
static void aGnomeThrownTwelveBlocksLeavesForGood(GameTestHelper h)
    // g1.landAfterThrow(13.0): state()==DAZED, isLeaving(); step DAZE_TICKS+LEAVE_MAX_TICKS+20: g1.isRemoved()
    // g2.landAfterThrow(11.0): !isLeaving()
```

- [ ] **Step 2: Compile.** Expected: fails on the missing methods.
- [ ] **Step 3: Implement** `HeldGnomeItem` and the `GnomeEntity` additions above.
- [ ] **Step 4: Compile + run the Task 1 tests again.** Expected: `COMPILE OK`, `TESTS OK`.

---

### Task 4: Model, texture, renderer, item visuals

**Files:**
- Create: `tools/gnome/build_gnome.py`, the same shape as `tools/niffler/build_niffler.py`. It writes `src/main/java/com/hogwartsmagic/client/model/GnomeGeometry.java` (generated, "do not edit" header, `createBodyLayer()` + `bones(ModelPart)`) and `src/main/resources/assets/hogwartsmagic/textures/entity/gnome.png` (128×128). It also writes `tools/gnome/preview.png`: front and side orthographic views of the textured boxes, for you to look at with Read.
- Create: `src/main/java/com/hogwartsmagic/client/model/GnomeModel.java`, `src/main/java/com/hogwartsmagic/client/renderer/GnomeRenderer.java`
- Create: `models/item/gnome_spawn_egg.json` (parent `minecraft:item/template_spawn_egg`); `models/item/held_gnome.json` (`forge:separate_transforms`, like `elder_wand.json`; `base` = `item/generated` with fully transparent `hogwartsmagic:item/blank`, so nothing draws in the hand; `perspectives` gui/ground/fixed = `item/generated` with `hogwartsmagic:item/held_gnome`); `textures/item/blank.png` (16×16, transparent); `textures/item/held_gnome.png` (16×16 gnome face, drawn with the `texturing-minecraft-items` skill)
- Modify (append, `// Garden gnomes`): `event/ClientModEvents.java`: `GnomeModel.LAYER_LOCATION` layer + `GnomeRenderer::new`

**Interfaces:**
- Consumes: `GnomeEntity.state()`, `stateTime(pt)`, `clientSpinTicks(pt)`, `isLeaving()`; `State` constants.
- Produces: `GnomeModel extends EntityModel<GnomeEntity>` with `LAYER_LOCATION` and `public void pose(GnomeEntity.State s, float stateTime, float spinTicks, boolean leaving)`; `GnomeRenderer extends MobRenderer<GnomeEntity, GnomeModel>`, `SCALE = 0.5F`, shadow 0.25.

Geometry: 32 units a block before the renderer's 0.5 scale. The whole gnome is 16 units tall (= 0.5 blocks). Faces −z; ground at root y=24.
- Bones: root, body, head, jaw, ear_left/right, arm_left/right, leg_left/right.
- Head (about half the height): a 9×8×8 potato made of `rounded()` crossed boxes, plus 4–6 small knob boxes (brow ridge, cheek lumps, a crown knob, one off-centre lump so it isn't symmetric). Bulbous 2×2×2 nose, bead eyes, a jaw bone for the bite, eat and giggle animations.
- Ears: wide, thin and bat-like, sticking out sideways.
- Body: a small round 7×6×5. Arms: stubby 2×5×2 with knobbly fists. Legs: short and bowed, with oversized flat 4×1×5 feet.

Texture: paint it from the body the way the Niffler does (position and normal decide the colour, value-noise mottling). Leathery brown-grey palette:
- base `#7D6E5D`, shadow `#5A4E42`, highlight `#9A8B78`
- darker warts `#4B3F35` on the knobs, inner ears `#8F6A5E`, mouth `#2E2420`, eyes black with a 1-texel glint
- a ragged sackcloth loincloth band at the hips, `#6B5A3E`

Animation (`GnomeModel.pose`, if/else on the state, never `switch`):
- NONE: a fast waddle (legs swing, body rolls ±0.15, arms swing opposite) and ear twitches.
- PULL: leans back, both arms forward, jerking.
- EAT: hands to the mouth, jaw chomping.
- DIG: bent over, arms scrabbling.
- CARRIED: one leg straight up (the held ankle), the other kicking, arms flailing, head shaking.
- SPUN: limbs splayed outward.
- THROWN: limbs splayed and flailing.
- DAZED: sitting, legs forward, head lolling in a slow circle.
- Leaving: running with both arms up.

`GnomeRenderer.setupRotations(entity, pose, bob, yBodyRot, pt, scale)`:
- CARRIED and SPUN hang upside down from the hand, the Dinnerbone way: yaw, then `translate(0, bbHeight + 0.1, 0)`, then `ZP 180°`. SPUN also tilts about X by −70°, so the head swings outward.
- THROWN tumbles about X at 35°/tick around the body centre.
- Every other state uses `super`.

- [ ] **Step 1: Write and run** `python tools/gnome/build_gnome.py`. Expected: the three files are written. Read `preview.png` and fix the proportions until it reads as a bald potato head on a squat body.
- [ ] **Step 2: Write** `GnomeModel`, `GnomeRenderer`, the item JSON/PNGs and the `ClientModEvents` lines.
- [ ] **Step 3: Compile.** Expected: `COMPILE OK`. Grep `entity/GnomeEntity.java` and `item/HeldGnomeItem.java` for `client` imports. Expected: none.

---

### Task 5: Gnome sounds (ElevenLabs, mastered)

**Files:**
- Create: `build_scripts/generate_gnome_sounds.py`, modelled on `build_scripts/generate_house_elf_sounds.py`. It reads the key file at run time and never prints it. Then follow the BUILDER_RULES mastering pipeline exactly.
- Create: `src/main/resources/assets/hogwartsmagic/sounds/gnome/{giggle1,giggle2,giggle3,squeak1,squeak2,bite,wail}.ogg`

Prompts (loud, close-miked, "no silence"; never "quiet"):
- giggle, 1.2 s, ×3: "a tiny goblin-like creature giggling mischievously, high nasal cackle, close-miked, dry, no silence"
- squeak, 0.6 s, ×2: "a small creature's sharp indignant squeak, a high yelp, close-miked, no silence"
- bite, 0.5 s: "small sharp teeth snapping shut on skin, a quick chomp with a tiny growl, close-miked, no silence"
- wail, 1.6 s: "a tiny creature wailing as it is flung through the air, a high 'waaaah' that swoops away, close-miked, no silence"

- [ ] **Step 1: Run the script.** Print each raw take's 100 ms RMS envelope and choose a (start, end) window that opens on the event.
- [ ] **Step 2: Encode** mono 44.1 kHz vorbis q5, RMS between −11 and −26 dBFS, `alimiter=level=disabled:limit=0.65`, `-ss` before `-i` in both passes. Expected: 7 OGG files, each re-measured inside the range.
- [ ] **Step 3: Check** that every name in `sounds.json` (Task 2) has its file. Expected: no missing paths.

---

### Task 6: Verify and commit

- [ ] **Step 1:** `bash C:/Users/alonl/hp-vfx-tools/compile.sh C:/Users/alonl/hp-wizard-world`. Expected: `COMPILE OK`.
- [ ] **Step 2:** `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "com.hogwartsmagic.entity.GnomeRulesTest"`. Expected: `TESTS OK`.
- [ ] **Step 3:** Validate every new JSON file with `python -m json.tool`, and check the `en_us.json`/`sounds.json` appends parse. Expected: no errors.
- [ ] **Step 4: Commit** (message file + `git commit -F`; do not push). Subject: "Garden gnomes raid crops and dig lawns; grab, whirl and fling them away". End with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- [ ] **Step 5: Report** per BUILDER_RULES, flagging the shared files and these calls: placeholder-item grab, crouch to sneak up, despawning pest population, spawn weight 6, about 2 s of spin aimed upward to clear 12 blocks, the loincloth.
