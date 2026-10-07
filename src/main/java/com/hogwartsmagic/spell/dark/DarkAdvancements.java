package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Grants the two Dark Magic advancements (Phase 4) from code. Both advancement files use the vanilla
 * {@code minecraft:impossible} criterion, so nothing but this class can complete them. Called only from
 * DarkMagicData.write, never on login, sync or a config reload. Server only.
 */
public final class DarkAdvancements {
    private DarkAdvancements() {
    }

    /** After a write: the effective tier it left, and whether Dark Magic is on (DarkAdvancementRules decides). */
    public static void afterWrite(ServerPlayer player, boolean enabled, int newTier) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        boolean wasLost = modData.getBoolean(DarkAdvancementRules.KEY_WAS_LOST);
        DarkAdvancementRules.Outcome outcome = DarkAdvancementRules.afterWrite(enabled, newTier, wasLost);
        if (outcome.flagChanged(wasLost)) {
            modData.putBoolean(DarkAdvancementRules.KEY_WAS_LOST, outcome.wasLost());
            PlayerMagicData.saveModData(player, modData);
        }
        if (outcome.grantTornSoul()) {
            grant(player, DarkAdvancementRules.TORN_SOUL);
        }
        if (outcome.grantRedemption()) {
            grant(player, DarkAdvancementRules.REDEMPTION);
        }
    }

    /** Completes every remaining criterion (as VoldemortEntity.grantAllCriteria); a done or missing one is skipped. */
    private static void grant(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        state.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            progress.award(advancement, criterion);
        }
    }
}
