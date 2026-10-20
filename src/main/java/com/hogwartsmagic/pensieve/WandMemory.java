package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.event.OllivandersCeremony;
import com.hogwartsmagic.pensieve.scene.ItemText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * The Wand memory's trigger: the first wand a player buys plays {@code wand_wall} in the Pensieve. The purchase is
 * already complete when this runs, so nothing here touches the inventory: the wand cannot be lost or duplicated, and
 * a refused start leaves the caller to fall back to the old ceremony.
 *
 * <p>The once-only flag is {@link OllivandersCeremony}'s. It is set when the memory is watched to its end or left
 * early; a yank, a disconnect or a death before that leaves it clear, so the memory plays again on the next wand.
 */
public final class WandMemory {
    public static final String SCENE = "wand_wall";

    private static boolean listening;

    private WandMemory() {
    }

    /** Hooks the home callback. Called once at common setup; repeat calls do nothing. */
    public static synchronized void register() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(WandMemory::onHome);
    }

    private static void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        if (!SCENE.equals(chainRoot)) return;
        if (marksDone(reason, sawStart)) OllivandersCeremony.markDone(player);
    }

    /** Watched to the end, or left once the memory had started: leaving during the build or fade shows nothing. */
    static boolean marksDone(ExitReason reason, boolean sawStart) {
        return reason == ExitReason.END || (reason == ExitReason.LEFT && sawStart);
    }

    /**
     * Starts the memory with {@code boughtWand} as the wand the boy holds, or does nothing and returns false when it
     * cannot start (the ceremony is running, the player is already a viewer or dead, the scene or the memory
     * dimension is missing). A stack that is not a wand falls back as {@link ItemText#viewerWand} says.
     */
    public static boolean tryStart(ServerPlayer player, ItemStack boughtWand) {
        if (OllivandersCeremony.isBusy(player)) return false;
        String id = boughtWand.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(boughtWand.getItem()).toString();
        return PensieveSessions.startSolo(player, SCENE, Map.of(ItemText.CONTEXT_WAND, id));
    }
}
