# Cosy Home, Food and Kitchen: design

## Intent

The mod is mostly danger: Dementors, curses, Horcruxes, duels. This pack adds the other half: somewhere warm to
come back to, and things to eat. It is for solo, open-world play: a player's own house anywhere in the world,
not Hogwarts.

**Anti-goal:** household chores done by animated objects, such as knitting needles, self-washing dishes,
a sweeping broom or a family clock. The user rejected these because they don't fit Minecraft. Everything
here should feel like a Minecraft block or item with a magical twist, such as a campfire, smoker,
barrel, carpet or stairs seat, rather than a scripted household prop.

## Goals

### Part 1: Home

- **Hearth** (`hearth`): a single-block stone fireplace, built on `CampfireBlock` like the Floo hearth.
  - It is lit by default. A shovel or water puts it out; flint and steel, a fire charge or Incendio relights it.
  - It sends chimney smoke up and is a heat source for the cooking pot.
  - While lit, every 40 ticks it gives each player within 6 blocks **Warmed** for 200 ticks.
  - The Floo fireplace's hearth does the same, via a small hook in its block entity tick.
- **Warmed effect** (`warmed`, BENEFICIAL, warm orange):
  - Removes `GLACIUS_CHILL` each tick and sets ticks-frozen to 0, so powder snow can't freeze you.
  - Heals 1 HP every 80 ticks if the player wasn't hurt in the last 100 ticks.
  - Butterbeer gives it too.
- **Bobbing floating candles:** the existing `floating_candle` block gains a block entity and a renderer.
  - The three candles bob about 1.5 px, each with its own phase, and sway slightly.
  - The wick particles follow the same offset; compute it from game time in `animateTick`.
  - A new `LIT` property defaults to true, so old worlds keep lit candles. Right-click with an empty hand toggles it, and light is 14 when lit, 0 when not.
  - The castle's `KIND_CANDLES` ambience box is separate and stays untouched.
- **Furniture,** all in `ModDecorations`, using the existing `SeatBlock`, `DecorBlock` and `DecorShapes` patterns:
  - The armchair in house colours: `gryffindor/ravenclaw/hufflepuff/slytherin_armchair`. These use the existing
    armchair model with a different velvet texture; red velvet already exists. Also a `patchwork_armchair`.
  - `sofa`: a SeatBlock that connects to its left and right neighbours like stairs. Its LEFT/RIGHT/MIDDLE/SINGLE
    shape decides where the arms go.
  - `footstool`: a low seat.
  - `patchwork_quilt`: a 1 px carpet-like layer. When the block below is a bed, an `ON_BED` state drops the model
    7 px so it lies on the mattress. It has no collision, so it doesn't stop you sleeping.
  - Rugs: `round_rug` in four house variants plus `rag_rug`. Each is a 1 px carpet whose border runs only along
    edges with no matching rug next to it, so several rugs form one big rug.
  - `reading_nook_shelf`: a short bookshelf with a reading lamp on top (light 12).
- **Indoor ambience,** client only:
  - **Rain on the windows:** a looping sound while it rains at the player's position, the player has no sky
    above them, and a glass block or pane within 5 blocks has sky above it. The volume fades in and out over about 2 s.
  - **Hearth crackle:** a custom looping crackle near a lit hearth that fades with distance. It replaces the
    vanilla campfire crackle on the hearth.

### Part 2: Food

- **Chocolate** (`chocolate_bar`): food 4 / 0.6, quick to eat.
  - It clears Slowness, Blindness and Wither, which is everything a Dementor puts on you.
  - It gives **Chocolate Courage** (`chocolate_courage`, 600 ticks). While that lasts, `DementorEntity.isShieldedFromAura`
    counts the player as shielded, so the slowing aura skips them.
- **Chocolate Frog** (`chocolate_frog`): using the box opens it.
  - You always get a random **Famous Wizard Card**.
  - A small `chocolate_frog` entity jumps out and hops away for 5 s, then is gone with a puff.
  - Hit or right-click it before then to catch and eat it: food 3 plus the chocolate cure.
  - It doesn't despawn mid-hop on unload; it just discards.
- **Famous Wizard Card** (`wizard_card`): one item carrying a card-id data component.
  - There are 20 cards in three rarities (common 12, rare 6, legendary 2), and the icon frame is bronze, silver
    or gold to match. Use historical and founder figures from the books, each with a short original flavour line.
- **Card album** (`card_album`): using it moves every card in your inventory that isn't already collected into
  the album, then opens a client screen.
  - The screen shows a grid of 20 slots. Collected cards show name, frame and text; missing ones show a silhouette.
  - The collection lives in a data component on the album, so it moves with the album.
  - Duplicates stay in your inventory.
- **Bertie Bott's Every Flavour Beans** (`bertie_botts_beans`): a box holding 10 beans (a data component).
  - Each eat takes one bean, which is quick, and rolls a weighted effect: good (speed, regen, jump, night vision,
    saturation), neutral, or silly (nausea, a few seconds of levitation, a burp). Each comes with an action-bar flavour line
    such as "Earwax!" or "Toffee.".
  - The box is gone when it's empty.
- **Meals:** `pumpkin_pasty` (6 / 0.7), `treacle_tart` (7 / 0.6), `cauldron_cake` (5 / 0.8). These come from the pot.
- **Butterbeer:**
  - `butterbeer_keg`: a barrel-like block entity. Add 1 sugar, 1 honey bottle and 1 wheat (with the honey bottle
    returned as a glass bottle), and the batch ages for 6000 ticks into 8 servings. While it ages, the barrel
    shows a fill state and bubbles now and then.
  - Right-click with an empty `tankard` to get a `tankard_of_butterbeer`. That drinks like a potion: food 4,
    Warmed for 3600 ticks and a little saturation, and it gives the tankard back.
  - `tankard` is crafted from planks and an iron nugget.

### Part 3: Kitchen

- **Enchanted stove** (`enchanted_stove`): a cast-iron range that runs on fuel.
  - Right-click with any furnace fuel to add burn time, up to 8 items' worth.
  - When lit it glows and has flames, and it is a heat source.
- **Self-stirring cooking pot** (`cooking_pot`): a block placed on top of any heat source. The heat sources are
  the stove when lit, campfires, the hearth, the Floo hearth, fire, lava and magma, all listed in a
  `hogwartsmagic:heat_sources` block tag.
  - Right-click to put in up to 6 ingredients. They float and bob in the pot (renderer).
  - The pot only cooks while it is being stirred.
    - Right-clicking with an empty hand gives it 3 s of manual stirring.
    - Tapping it with a wand enchants the ladle to stir itself until the dish is done.
  - The ladle turns in the renderer, the pot bubbles and makes a sound.
  - Recipes are a data-driven `hogwartsmagic:pot_cooking` recipe type: shapeless ingredients, cook ticks,
    and the result. Take the result out with a right-click.
  - Shift-right-clicking with an empty hand takes the ingredients back out.
  - Recipes:
    - chocolate_bar ×3 (cocoa ×2, sugar, milk bucket → bucket returned)
    - chocolate_frog ×2 (chocolate_bar ×2, slime ball)
    - pumpkin_pasty ×3 (pumpkin, wheat ×2, egg, sugar)
    - treacle_tart ×2 (wheat ×2, honey bottle, egg, sugar)
    - cauldron_cake ×3 (wheat, cocoa, sugar, egg, milk bucket)
    - bertie_botts_beans ×1 (sugar ×2, sweet berries, slime ball, spider eye, glow berries)
- Crafting recipes for stove, pot, hearth, keg, tankard, album and furniture use vanilla materials.

## Constraints

- Forge 1.21 (Forge 51), the existing `ExampleMod` registers. Each part gets its own registry class
  (`cosy/CosyHome`, `cosy/CosyFood`, `cosy/CosyKitchen`, or furniture in `ModDecorations`) hooked by one line, so
  parts can be built in parallel and merged.
- Dedicated-server safe: screens, renderers and ambience sounds are client-only classes registered from client
  setup. Effects, cooking, the keg and frog catching are server-authoritative.
- Never switch on an enum in render code. Never use entity-event bytes 21, 35 or 63. New Fx kinds go in the
  `ClientModEvents` call list. Screens use `renderTransparentBackground`.
- 1.21 API: `FoodProperties` via `DataComponents.FOOD`; save data public; no `MobType`.
- Item textures are 16x16 hand-drawn grids in `tools/item_textures/` (the texturing skill). Block textures are
  16x16 and generated by script under `tools/`. Sounds come from the ElevenLabs pipeline (see memory): envelope-trimmed,
  level-matched, mono OGG.
- Every new block and item gets a lang entry, model, loot table (blocks), creative tab entry and a recipe where
  the design names one.

## Approaches

1. **Three registry classes built in parallel and merged in an integration worktree** (picked). Home is independent;
   Food and Kitchen share recipes and items, so they are built together. This is fastest, and the merge conflicts are
   limited to `ExampleMod` hook lines, lang, `sounds.json` and `ModSounds`.
2. One builder, all in sequence: no merging, but about twice as long.
3. Fold food into the existing Wizard Cauldron brewing system: reuses UI, but brewing is potion-shaped
   (quality, stirring rod rules) and would make meals feel like potions; rejected.

## Left to the builder

Exact model geometry, texture palettes, card names and lines, bean table weights, and sound prompts, all within the
rules above.
