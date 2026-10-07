package com.hogwartsmagic.brewing;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Grants the brewing advancements that no vanilla trigger can see (they use minecraft:impossible). Server side only.
 * Same approach as VoldemortEntity.grantAllCriteria and DarkAdvancements.grant.
 */
public final class BrewAdvancements {
    /** hogwartsmagic:hp/first_brew: bottled a dose of a finished brew. */
    public static final String FIRST_BREW = "first_brew";
    /** hogwartsmagic:hp/ruined_brew: a step of yours ruined a brew. */
    public static final String RUINED_BREW = "ruined_brew";

    private BrewAdvancements() {
    }

    /**
     * Completes hogwartsmagic:hp/name for a real server player. A client-side player, a fake player (a machine
     * of another mod; Forge 1.21 has no FakePlayer class, so anything the server player list does not hold for its
     * UUID counts as fake), a missing advancement or one already done is ignored.
     */
    public static void grant(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.server.getPlayerList().getPlayer(serverPlayer.getUUID()) != serverPlayer) {
            return;
        }
        AdvancementHolder advancement = serverPlayer.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/" + name));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = serverPlayer.getAdvancements();
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
