### Task 7: Apparition Master (known Floo fireplaces)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/ApparitionSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`OpenApparitionMapPacket` gains fireplaces; new `ChooseApparitionFireplacePacket`; `openFlooSelection` remembers)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`startApparitionChannel`, new `completeApparitionToFireplace`, Floo arrival remembers)
- Modify: `src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java` (fireplace list)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` (`openApparitionMap`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ApparitionRules.jumpsToFireplaces/MAX_KNOWN_FIREPLACES/MAX_LISTED_FIREPLACES` (Task 1); `PlayerMagicData.getModData/saveModData`, `SpellPackets.FlooDestinationData(BlockPos, String)`, `FlooFireplaceBlockEntity.getFireplaceName()` (existing)
- Produces:
  - `ApparitionSpell.rememberFireplace(ServerPlayer player, ServerLevel level, BlockPos pos, String name)`, `ApparitionSpell.isKnown(ServerPlayer, ServerLevel, BlockPos)` → `boolean`, `ApparitionSpell.forgetFireplace(ServerPlayer, ServerLevel, BlockPos)`, `ApparitionSpell.knownFireplaces(ServerPlayer, ServerLevel)` → `List<SpellPackets.FlooDestinationData>` (same dimension, nearest first, at most 8)
  - `SpellPackets.OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks, List<FlooDestinationData> fireplaces)`
  - `SpellPackets.ChooseApparitionFireplacePacket(BlockPos fireplacePos)` (C2S)
  - `ModEvents.completeApparitionToFireplace(ServerPlayer player, BlockPos fireplacePos)`
  - `ApparitionMapScreen(BlockPos origin, int radius, int durationTicks, List<SpellPackets.FlooDestinationData> fireplaces)`

- [ ] **Step 1: Create `ApparitionSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Floo fireplaces a player knows: the source and destination of every completed Floo trip and
 * every fireplace they open the Floo menu at. Kept in PlayerPersisted (survives death), at most 32,
 * oldest dropped first. A Master Apparates to any of them in the same dimension. Server only.
 */
public final class ApparitionSpell {
    private static final String KEY = "KnownFireplaces";

    private ApparitionSpell() {
    }

    public static void rememberFireplace(ServerPlayer player, ServerLevel level, BlockPos pos, String name) {
        if (name == null || name.isBlank()) {
            return; // an unnamed fireplace isn't on the network yet
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", level.dimension().location().toString());
        entry.putLong("Pos", pos.asLong());
        entry.putString("Name", name);
        list.add(entry);
        while (list.size() > ApparitionRules.MAX_KNOWN_FIREPLACES) {
            list.remove(0);
        }
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    public static boolean isKnown(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                return true;
            }
        }
        return false;
    }

    public static void forgetFireplace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    /** Known fireplaces in the player's dimension, nearest first, at most {@link ApparitionRules#MAX_LISTED_FIREPLACES}. */
    public static List<SpellPackets.FlooDestinationData> knownFireplaces(ServerPlayer player, ServerLevel level) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        List<SpellPackets.FlooDestinationData> found = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim"))) {
                found.add(new SpellPackets.FlooDestinationData(BlockPos.of(entry.getLong("Pos")), entry.getString("Name")));
            }
        }
        BlockPos here = player.blockPosition();
        found.sort(Comparator.comparingDouble(d -> d.pos().distSqr(here)));
        return found.size() > ApparitionRules.MAX_LISTED_FIREPLACES
                ? new ArrayList<>(found.subList(0, ApparitionRules.MAX_LISTED_FIREPLACES))
                : found;
    }

    private static void removeEntry(ListTag list, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                list.remove(i);
            }
        }
    }
}
```

- [ ] **Step 2: Packets.** In `SpellPackets.java`, replace the map packet registration:
```java
        CHANNEL.messageBuilder(OpenApparitionMapPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.origin);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.durationTicks);
                })
                .decoder(buf -> new OpenApparitionMapPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()))
                .consumerMainThread(OpenApparitionMapPacket::handle)
                .add();
```
with:
```java
        CHANNEL.messageBuilder(OpenApparitionMapPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.origin);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.durationTicks);
                    buf.writeVarInt(p.fireplaces.size());
                    for (FlooDestinationData fireplace : p.fireplaces) {
                        buf.writeBlockPos(fireplace.pos());
                        buf.writeUtf(fireplace.name());
                    }
                })
                .decoder(buf -> {
                    BlockPos origin = buf.readBlockPos();
                    int radius = buf.readVarInt();
                    int durationTicks = buf.readVarInt();
                    int size = clampDecodedSize(buf.readVarInt(),
                            com.hogwartsmagic.spell.support.ApparitionRules.MAX_LISTED_FIREPLACES);
                    List<FlooDestinationData> fireplaces = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        fireplaces.add(new FlooDestinationData(buf.readBlockPos(), buf.readUtf()));
                    }
                    return new OpenApparitionMapPacket(origin, radius, durationTicks, fireplaces);
                })
                .consumerMainThread(OpenApparitionMapPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseApparitionFireplacePacket.class)
                .encoder((p, buf) -> buf.writeBlockPos(p.fireplacePos))
                .decoder(buf -> new ChooseApparitionFireplacePacket(buf.readBlockPos()))
                .consumerMainThread(ChooseApparitionFireplacePacket::handle)
                .add();
```
Replace the record header:
```java
    public record OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks) {
```
with:
```java
    public record OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks, List<FlooDestinationData> fireplaces) {
```
Directly above:
```java
    /** Client → server: the player closed the map without choosing — cancel the channel now (full cooldown). */
```
add:
```java
    /** Client to server: a Master picked a known fireplace on the map. Re-validated against the server's own list. */
    public record ChooseApparitionFireplacePacket(BlockPos fireplacePos) {
        public static void handle(ChooseApparitionFireplacePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                com.hogwartsmagic.event.ModEvents.completeApparitionToFireplace(player, p.fireplacePos);
            }
        }
    }

```
In `openFlooSelection`, replace:
```java
        if (fireplace == null || !fireplace.isNamed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.needs_name"), true);
            return false;
        }
```
with:
```java
        if (fireplace == null || !fireplace.isNamed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.needs_name"), true);
            return false;
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel flooLevel) {
            // A fireplace you have stood at is one a Master can Apparate back to.
            com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, flooLevel,
                    fireplace.getBlockPos(), fireplace.getFireplaceName());
        }
```

- [ ] **Step 3: Server side in `ModEvents`.** In `startApparitionChannel`, replace (Task 6's version):
```java
        int apparitionRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(),
                        com.hogwartsmagic.spell.support.ApparitionRules.range(apparitionRank),
                        com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(apparitionRank)),
                PacketDistributor.PLAYER.with(player));
```
with:
```java
        int apparitionRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        java.util.List<SpellPackets.FlooDestinationData> fireplaces =
                com.hogwartsmagic.spell.support.ApparitionRules.jumpsToFireplaces(apparitionRank)
                        && player.level() instanceof ServerLevel apparitionLevel
                        ? com.hogwartsmagic.spell.support.ApparitionSpell.knownFireplaces(player, apparitionLevel)
                        : java.util.List.of();
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(),
                        com.hogwartsmagic.spell.support.ApparitionRules.range(apparitionRank),
                        com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(apparitionRank),
                        fireplaces),
                PacketDistributor.PLAYER.with(player));
```
Directly above:
```java
    /** Cancel for any reason (timeout, hit while channeling, lost wand, closed map): clears state and applies the full cooldown. */
```
add:
```java
    /**
     * Master Apparition: straight to a known Floo fireplace in this dimension, at any distance. Lands on
     * the block above it, like Floo. May load the destination chunk once (no range limit on a Master's
     * jump, on a one-minute cooldown). A fireplace that is gone is forgotten.
     */
    public static void completeApparitionToFireplace(ServerPlayer player, BlockPos fireplacePos) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (!playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!(player.level() instanceof ServerLevel level)
                || !com.hogwartsmagic.spell.support.ApparitionRules.jumpsToFireplaces(
                        com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION))
                || !com.hogwartsmagic.spell.support.ApparitionSpell.isKnown(player, level, fireplacePos)) {
            cancelApparitionChannel(player, wand);
            return;
        }
        level.getChunkAt(fireplacePos);
        if (!(level.getBlockEntity(fireplacePos) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity)) {
            com.hogwartsmagic.spell.support.ApparitionSpell.forgetFireplace(player, level, fireplacePos);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.fireplace_gone").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            cancelApparitionChannel(player, wand);
            return;
        }
        BlockPos exit = fireplacePos.above();
        if (com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level).isWardedAt(exit)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.blocked").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            cancelApparitionChannel(player, wand);
            return;
        }

        playerData.remove(TAG_APPARITION_CHANNELING);
        playerData.remove(TAG_APPARITION_START);
        clearImplodeScale(player);
        SpellPackets.CHANNEL.send(new SpellPackets.CloseApparitionMapPacket(), PacketDistributor.PLAYER.with(player));

        // Side-along: anyone within a block comes with you. Gather BEFORE moving.
        java.util.List<LivingEntity> passengers = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(APPARITION_SIDEALONG_RADIUS), e -> e != player && e.isAlive());
        Vec3 from = player.position();
        double x = exit.getX() + 0.5;
        double y = exit.getY();
        double z = exit.getZ() + 0.5;
        crackEffect(level, from.x, from.y, from.z, true);
        player.teleportTo(x, y, z);
        player.fallDistance = 0.0f;
        crackEffect(level, x, y, z, false);
        for (LivingEntity passenger : passengers) {
            passenger.teleportTo(x + (player.getRandom().nextDouble() - 0.5) * 1.5,
                    y, z + (player.getRandom().nextDouble() - 0.5) * 1.5);
            passenger.fallDistance = 0.0f;
        }
        revealInvisibilityCloak(player);
        applyApparitionCooldown(player, wand, true);
    }

```
In `tickPendingFlooTravel`, replace:
```java
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.hogwartsmagic.ModSounds.FLOO_ARRIVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        destination.ignite(serverLevel);
```
with:
```java
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.hogwartsmagic.ModSounds.FLOO_ARRIVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        destination.ignite(serverLevel);
        // Both ends of a completed trip are fireplaces a Master can Apparate to.
        com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, serverLevel, sourcePos, source.getFireplaceName());
        com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, serverLevel, destinationPos, destination.getFireplaceName());
```

- [ ] **Step 4: The fireplace list on the map.** In `ApparitionMapScreen.java` (Task 6's file):

Replace:
```java
    private final int durationTicks;
    private final RandomSource shakeRand = RandomSource.create();
```
with:
```java
    private final int durationTicks;
    private final java.util.List<SpellPackets.FlooDestinationData> fireplaces;
    private final RandomSource shakeRand = RandomSource.create();
    private static final int ROW = 11;
```
Replace:
```java
    private int panelHeight;
```
with:
```java
    private int panelHeight;
    private int listTop;
```
Replace the constructor:
```java
    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
    }
```
with:
```java
    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks,
                               java.util.List<SpellPackets.FlooDestinationData> fireplaces) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
        this.fireplaces = fireplaces == null ? java.util.List.of() : java.util.List.copyOf(fireplaces);
    }

    /** Header plus one row per known fireplace; 0 when there are none (below Master). */
    private int fireplaceListHeight() {
        return fireplaces.isEmpty() ? 0 : 12 + ROW * fireplaces.size();
    }

    /** Index of the fireplace row under the mouse, or -1. */
    private int fireplaceRowAt(double mouseX, double mouseY) {
        if (fireplaces.isEmpty()) {
            return -1;
        }
        int mapSize = tiles * TILE;
        if (mouseX < mapLeft || mouseX >= mapLeft + mapSize) {
            return -1;
        }
        int row = Math.floorDiv((int) mouseY - (listTop + 12), ROW);
        return row >= 0 && row < fireplaces.size() ? row : -1;
    }
```
In `init()`, replace:
```java
        panelHeight = mapSize + 56;
```
with:
```java
        panelHeight = mapSize + 56 + fireplaceListHeight();
```
and replace:
```java
        mapTop = panelTop + 40;
        buildMap();
```
with:
```java
        mapTop = panelTop + 40;
        listTop = mapTop + mapSize + 16;
        buildMap();
```
In `render`, directly above the last `        g.pose().popPose();`, insert:
```java
        // Known Floo fireplaces (Master): click one to Apparate there at any distance.
        if (!fireplaces.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.fireplaces"),
                    mapLeft, listTop, COLOR_GOLD);
            int hovered = fireplaceRowAt(mouseX, mouseY);
            for (int i = 0; i < fireplaces.size(); i++) {
                SpellPackets.FlooDestinationData fireplace = fireplaces.get(i);
                int rowY = listTop + 12 + i * ROW;
                if (i == hovered) {
                    g.fill(mapLeft - 2, rowY - 1, mapLeft + mapSize + 2, rowY + ROW - 1, 0x50FFE08A);
                }
                int distance = (int) Math.sqrt(origin.distSqr(fireplace.pos()));
                String label = this.font.plainSubstrByWidth(fireplace.name() + " (" + distance + "m)", mapSize);
                g.drawString(this.font, label, mapLeft, rowY, COLOR_BODY);
            }
        }

```
In `mouseClicked`, replace:
```java
        if (button == 0 && !selectionSent) {
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
```
with:
```java
        if (button == 0 && !selectionSent) {
            int row = fireplaceRowAt(mouseX, mouseY);
            if (row >= 0) {
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionFireplacePacket(fireplaces.get(row).pos()),
                        PacketDistributor.SERVER.noArg());
                selectionSent = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
```

- [ ] **Step 5: The bridge passes the list.** In `ClientRuntimeBridge.java`, replace:
```java
                new ApparitionMapScreen(packet.origin(), packet.radius(), packet.durationTicks()));
```
with:
```java
                new ApparitionMapScreen(packet.origin(), packet.radius(), packet.durationTicks(), packet.fireplaces()));
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "message.hogwartsmagic.apparition.splinched": "You've been Splinched!",` insert:
```json
  "gui.hogwartsmagic.apparition.fireplaces": "Known fireplaces (any distance):",
  "message.hogwartsmagic.apparition.fireplace_gone": "That fireplace is gone. You can no longer picture it.",
```

- [ ] **Step 7: Build, test and boot a dedicated server** (changed packet layout, new C2S packet)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `ApparitionSpell`, `ChooseApparitionFireplacePacket` or any `client` class.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/ApparitionSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master Apparate to any Floo fireplace they have used in the same dimension" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

