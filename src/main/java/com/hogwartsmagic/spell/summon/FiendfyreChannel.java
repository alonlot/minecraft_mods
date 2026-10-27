package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.dark.DarkMagicData;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The held Fiendfyre channel. Hold the cast key and serpents of cursed fire follow your aim; hold attack
 * to drive them further out. Rank sets how long the fire lasts, how many serpents answer, and how likely
 * one is to turn on its caster after glowing red for a second (FiendfyreRules). Releasing, switching off
 * the spell, running out of mana, a stun or silence, logging out, or the rank duration ends it and starts
 * the cooldown (which also awards the mastery cast); a rogue serpent outlives the channel until the
 * cast's time is up. Server only.
 */
public final class FiendfyreChannel {
    public static final String TAG_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    public static final String TAG_ADVANCE = "hogwartsmagic:fiendfyre_advance";
    private static final String TAG_LAST_DRAIN = "hogwartsmagic:fiendfyre_last_drain";
    private static final String TAG_LAST_SEEN = "hogwartsmagic:fiendfyre_last_seen";
    private static final String TAG_SERPENTS = "hogwartsmagic:fiendfyre_serpents";
    private static final String TAG_STARTED = "hogwartsmagic:fiendfyre_started";
    private static final String TAG_RANK = "hogwartsmagic:fiendfyre_rank";
    /** The single-serpent key used before the rank rules; cleared on stop so old saves don't keep it. */
    private static final String LEGACY_TAG_ENTITY = "hogwartsmagic:fiendfyre_entity";
    private static final int UPKEEP_INTERVAL = 4;
    private static final int HEARTBEAT_TIMEOUT = 4;

    private FiendfyreChannel() {
    }

    public static boolean isChanneling(Player player) {
        return player.getPersistentData().getBoolean(TAG_CHANNELING);
    }

    /** Every held tick: starts the channel on the first one (cooldown, startup mana, Darkness), then keeps it alive. */
    public static void heartbeat(ServerPlayer player, ItemStack wand, boolean advance) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        boolean creative = player.isCreative();
        if (!creative && MagicWandItem.isOnCooldown(wand, SpellType.FIENDFYRE, now)) {
            return;
        }
        if (data.getBoolean(TAG_CHANNELING)) {
            data.putLong(TAG_LAST_SEEN, now);
            data.putBoolean(TAG_ADVANCE, advance);
            return;
        }

        CompoundTag modData = PlayerMagicData.getModData(player);
        float maxMana = PlayerMagicData.getMaxMana(player);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float startup = startupCost(player, wand);
        if (!creative && mana < startup) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!creative) {
            mana -= startup;
            modData.putFloat("Mana", mana);
            PlayerMagicData.saveModData(player, modData);
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
        }

        // Dark Magic: the channel start is the paid Fiendfyre cast (its startup mana was just taken).
        DarkMagicData.onDarkCastPaid(player, SpellType.FIENDFYRE);
        int rank = SpellMasteryData.getRank(player, SpellType.FIENDFYRE);
        data.putBoolean(TAG_CHANNELING, true);
        data.putLong(TAG_LAST_DRAIN, now);
        data.putLong(TAG_LAST_SEEN, now);
        data.putBoolean(TAG_ADVANCE, advance);
        data.putLong(TAG_STARTED, now);
        data.putInt(TAG_RANK, rank);
        spawnSerpents(player, rank);

        ModEvents.revealInvisibilityCloak(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SpellType.FIENDFYRE.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.86f);
    }

    /** The cast key was let go (or the caster was interrupted): end it and start the cooldown. */
    public static void release(ServerPlayer player, ItemStack wand) {
        if (isChanneling(player)) {
            stop(player, wand, true);
        }
        player.getPersistentData().remove(TAG_ADVANCE);
    }

    public static void stop(ServerPlayer player, ItemStack wand, boolean startCooldown) {
        CompoundTag data = player.getPersistentData();
        for (FiendfyreSerpentEntity serpent : serpents(player)) {
            if (!serpent.isRogue()) {
                serpent.discard(); // a rogue serpent is nobody's to dismiss
            }
        }
        if (data.hasUUID(LEGACY_TAG_ENTITY)
                && player.serverLevel().getEntity(data.getUUID(LEGACY_TAG_ENTITY)) instanceof FiendfyreSerpentEntity old) {
            old.discard();
        }
        data.remove(TAG_CHANNELING);
        data.remove(TAG_LAST_DRAIN);
        data.remove(TAG_LAST_SEEN);
        data.remove(TAG_ADVANCE);
        data.remove(TAG_SERPENTS);
        data.remove(TAG_STARTED);
        data.remove(TAG_RANK);
        data.remove(LEGACY_TAG_ENTITY);
        if (startCooldown && !player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellCosts.startCooldown(player, wand, SpellType.FIENDFYRE, player.level().getGameTime());
        }
    }

    /** Player tick: wand and heartbeat checks, the rank duration, lost serpents, and the mana upkeep. */
    public static void tick(ServerPlayer player, CompoundTag modData) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (selected != SpellType.FIENDFYRE || !player.isAlive()) {
            stop(player, wand, true);
            return;
        }
        long now = player.level().getGameTime();
        if (now - data.getLong(TAG_LAST_SEEN) > HEARTBEAT_TIMEOUT) {
            stop(player, wand, true);
            return;
        }
        int rank = Math.max(1, data.getInt(TAG_RANK));
        if (FiendfyreRules.expired(data.getLong(TAG_STARTED), now, rank)) {
            stop(player, wand, true);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.burnt_out")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int controlled = 0;
        for (FiendfyreSerpentEntity serpent : serpents(player)) {
            if (!serpent.isRogue()) {
                serpent.setCaster(player);
                controlled++;
            }
        }
        if (controlled == 0) {
            stop(player, wand, true); // the only serpent went rogue, or the fire was lost in an unloaded chunk
            return;
        }

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = upkeepCost(player, wand);
        long lastDrain = data.getLong(TAG_LAST_DRAIN);
        if (!player.isCreative() && now - lastDrain >= UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stop(player, wand, true);
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.collapses")
                        .withStyle(ChatFormatting.GOLD), true);
                return;
            }
            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            PlayerMagicData.saveModData(player, modData);
            data.putLong(TAG_LAST_DRAIN, now);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }
    }

    /** The serpents this channel spawned that are still loaded and alive in the caster's level (rogue ones included). */
    static List<FiendfyreSerpentEntity> serpents(ServerPlayer player) {
        List<FiendfyreSerpentEntity> out = new ArrayList<>();
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_SERPENTS)) {
            return out;
        }
        ServerLevel level = player.serverLevel();
        ListTag ids = data.getList(TAG_SERPENTS, Tag.TAG_INT_ARRAY);
        for (Tag id : ids) {
            Entity entity = level.getEntity(NbtUtils.loadUUID(id));
            if (entity instanceof FiendfyreSerpentEntity serpent && serpent.isAlive()) {
                out.add(serpent);
            }
        }
        return out;
    }

    /** One roll per cast decides whether a serpent will turn, which one, and when. */
    private static void spawnSerpents(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        int count = FiendfyreRules.serpentCount(rank);
        int duration = FiendfyreRules.durationTicks(rank);
        int rogueIndex = -1;
        int turnTick = -1;
        if (FiendfyreRules.goesRogue(rank, random.nextDouble())) {
            rogueIndex = FiendfyreRules.rogueIndex(count, random.nextDouble());
            turnTick = FiendfyreRules.rogueTurnTick(duration, random.nextDouble());
        }
        ListTag ids = new ListTag();
        for (int i = 0; i < count; i++) {
            FiendfyreSerpentEntity serpent = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), level);
            serpent.setCaster(player);
            serpent.configure(i, count, i == rogueIndex ? turnTick : -1, duration);
            serpent.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
            level.addFreshEntity(serpent);
            ids.add(NbtUtils.createUUID(serpent.getUUID()));
        }
        player.getPersistentData().put(TAG_SERPENTS, ids);
    }

    private static float startupCost(ServerPlayer player, ItemStack wand) {
        return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE) * 0.16f
                * SpellCosts.darkMultiplier(player, SpellType.FIENDFYRE));
    }

    private static float upkeepCost(ServerPlayer player, ItemStack wand) {
        return Math.max(1.25f, (SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE)
                * SpellCosts.darkMultiplier(player, SpellType.FIENDFYRE)) / 14.0f);
    }
}
