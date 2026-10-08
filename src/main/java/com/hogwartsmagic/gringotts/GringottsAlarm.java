package com.hogwartsmagic.gringotts;

import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How much trouble a given thief is in, and where the bank considers itself to be.
 *
 * <p>The mod already had a theft alarm and it was not one: any chest opened within twelve blocks of
 * any goblin guard <em>anywhere in the world</em> set them off, with no line of sight and no notion
 * of territory. Guards never despawn, so a player who led one home could never open their own chest
 * again - and it fired on ender chests, which is somebody's private storage. This replaces the idea
 * with a real one: the bank has <b>regions</b>, and being angry is a property of a <b>player</b>
 * rather than of whichever goblin happened to be standing nearby.
 *
 * <p>Heat decays a step every ten minutes spent outside, so a thief is hunted for roughly forty and
 * somebody who killed the dragon for the rest of the session. It can also be paid off, because
 * goblins are bankers before they are guards.
 */
public class GringottsAlarm extends SavedData {

    public static final String DATA_NAME = "gringotts_alarm";

    public static final int MAX_HEAT = 4;
    /** How long a step of heat survives once you are out of the bank. */
    private static final int DECAY_TICKS = 20 * 60 * 10;
    /** Guards sent in at heat 2 and above. */
    private static final int RESPONSE_SQUAD = 4;

    private final List<BlockPos> regions = new ArrayList<>();
    private final List<Integer> radii = new ArrayList<>();
    private final Map<UUID, Integer> heat = new HashMap<>();
    private final Map<UUID, Long> lastRaised = new HashMap<>();

    public static GringottsAlarm get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(GringottsAlarm::new, GringottsAlarm::load, null), DATA_NAME);
    }

    /** Records an undercroft as the bank's own ground. */
    public void addRegion(BlockPos centre, int radius) {
        for (int i = 0; i < this.regions.size(); i++) {
            if (this.regions.get(i).distSqr(centre) < 64.0D * 64.0D) {
                return;
            }
        }
        this.regions.add(centre.immutable());
        this.radii.add(radius);
        setDirty();
    }

    /** True if this is somewhere the bank cares what you do. */
    public boolean inside(BlockPos pos) {
        for (int i = 0; i < this.regions.size(); i++) {
            BlockPos centre = this.regions.get(i);
            int r = this.radii.get(i);
            if (Math.abs(pos.getX() - centre.getX()) <= r
                    && Math.abs(pos.getZ() - centre.getZ()) <= r
                    && pos.getY() <= centre.getY() + 8) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos regionOf(BlockPos pos) {
        for (int i = 0; i < this.regions.size(); i++) {
            BlockPos centre = this.regions.get(i);
            int r = this.radii.get(i);
            if (Math.abs(pos.getX() - centre.getX()) <= r
                    && Math.abs(pos.getZ() - centre.getZ()) <= r) {
                return centre;
            }
        }
        return null;
    }

    public int heatOf(ServerPlayer player) {
        return this.heat.getOrDefault(player.getUUID(), 0);
    }

    public void clear(ServerPlayer player) {
        this.heat.remove(player.getUUID());
        this.lastRaised.remove(player.getUUID());
        setDirty();
    }

    /**
     * Something was done that the bank objects to.
     *
     * <p>Does nothing outside a region, which is the territory check the old alarm never had.
     */
    public void raise(ServerLevel level, ServerPlayer player, BlockPos where, int steps,
                      String reasonKey) {
        BlockPos region = regionOf(where);
        if (region == null) {
            return;
        }
        int now = Math.min(MAX_HEAT, heatOf(player) + steps);
        this.heat.put(player.getUUID(), now);
        this.lastRaised.put(player.getUUID(), level.getGameTime());
        setDirty();

        player.displayClientMessage(Component.translatable(reasonKey)
                .withStyle(now >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD), true);
        level.playSound(null, region, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3.0F, 0.6F);

        // Everything already down there comes looking. A region query, not a blast radius: a guard
        // two chambers away should still be told, and a guard in a different bank should not.
        for (GoblinGuardEntity guard : level.getEntitiesOfClass(GoblinGuardEntity.class,
                new AABB(region).inflate(96.0D))) {
            guard.setTarget(player);
        }

        if (now >= 2) {
            // Reinforcements at the WAY OUT rather than on top of you: an ambush at the exit is a
            // chase, and an ambush where you stand is just damage.
            summon(level, region, player, RESPONSE_SQUAD);
        }
    }

    private void summon(ServerLevel level, BlockPos at, ServerPlayer after, int count) {
        for (int i = 0; i < count; i++) {
            GoblinGuardEntity guard = ModEntities.GOBLIN_GUARD.get().create(level);
            if (guard == null) {
                continue;
            }
            guard.moveTo(at.getX() + 0.5D + (i % 2 == 0 ? 1 : -1) * (1 + i),
                    at.getY() + 1.0D, at.getZ() + 0.5D + (i < 2 ? 1 : -1), 0.0F, 0.0F);
            guard.makeGuard("Vault Guard");
            guard.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(com.hogwartsmagic.ExampleMod.GOBLIN_SILVER_DAGGER.get()));
            guard.setTarget(after);
            level.addFreshEntity(guard);
        }
    }

    /** Called on a slow tick: bleeds heat off anyone who has left. */
    public void decay(ServerLevel level, ServerPlayer player) {
        int current = heatOf(player);
        if (current <= 0 || inside(player.blockPosition())) {
            return;
        }
        long since = level.getGameTime() - this.lastRaised.getOrDefault(player.getUUID(), 0L);
        if (since < DECAY_TICKS) {
            return;
        }
        this.heat.put(player.getUUID(), current - 1);
        this.lastRaised.put(player.getUUID(), level.getGameTime());
        if (current - 1 <= 0) {
            this.heat.remove(player.getUUID());
            this.lastRaised.remove(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("gringotts.alarm.cooled").withStyle(ChatFormatting.GRAY),
                    true);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag regionList = new ListTag();
        for (int i = 0; i < this.regions.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", this.regions.get(i).asLong());
            entry.putInt("R", this.radii.get(i));
            regionList.add(entry);
        }
        tag.put("Regions", regionList);

        ListTag heatList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : this.heat.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Id", entry.getKey());
            one.putInt("Heat", entry.getValue());
            one.putLong("At", this.lastRaised.getOrDefault(entry.getKey(), 0L));
            heatList.add(one);
        }
        tag.put("Heat", heatList);
        return tag;
    }

    public static GringottsAlarm load(CompoundTag tag, HolderLookup.Provider registries) {
        GringottsAlarm alarm = new GringottsAlarm();
        ListTag regionList = tag.getList("Regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < regionList.size(); i++) {
            CompoundTag entry = regionList.getCompound(i);
            alarm.regions.add(BlockPos.of(entry.getLong("Pos")));
            alarm.radii.add(entry.getInt("R"));
        }
        ListTag heatList = tag.getList("Heat", Tag.TAG_COMPOUND);
        for (int i = 0; i < heatList.size(); i++) {
            CompoundTag one = heatList.getCompound(i);
            alarm.heat.put(one.getUUID("Id"), one.getInt("Heat"));
            alarm.lastRaised.put(one.getUUID("Id"), one.getLong("At"));
        }
        return alarm;
    }
}
