package com.hogwartsmagic.util;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Awards the mod's advancements for things only the code can see.
 *
 * <p>Most of the {@code hp/} tree runs on vanilla triggers - holding an item, killing a mob - and
 * needs nothing from here. The rest describe events no vanilla trigger knows about: a vault door
 * finishing its opening, a cart reaching the bottom, a duel being won. Each of those advancements
 * declares a single {@code minecraft:impossible} criterion, which nothing can ever satisfy by
 * itself, and this class is the only thing that completes it.
 *
 * <p>It awards <em>every remaining criterion</em> rather than naming one, so an advancement is
 * free to grow a second criterion later without every call site being revisited, and so the same
 * pattern {@code VoldemortEntity} uses for its own advancements has one home to move to.
 *
 * <p>Safe to call as often as the hook fires: a finished advancement has no remaining criteria, so
 * the repeat is a no-op, and an id with no advancement behind it (a datapack removed it) does
 * nothing rather than throwing.
 */
public final class ModAdvancements {

    private ModAdvancements() {
    }

    /**
     * Completes {@code hogwartsmagic:hp/<path>} for this player.
     *
     * @param path the advancement's path under {@code hp/}, e.g. {@code "duel_victor"}
     */
    public static void award(@Nullable ServerPlayer player, String path) {
        if (player == null || player.getServer() == null) {
            return;
        }
        AdvancementHolder advancement = player.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/" + path));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) {
            return;
        }
        for (String criterion : state.getRemainingCriteria()) {
            progress.award(advancement, criterion);
        }
    }
}
