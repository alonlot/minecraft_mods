package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.SneakoscopeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Sneakoscope anywhere in a player's inventory (main, armour, off hand). Called every server tick from
 * ModEvents.onPlayerTick. Once a second (phased by entity id) it measures the strength, turns the spin on or off on
 * every Sneakoscope stack (only when it changes) and schedules the whistle; every tick it plays a due whistle, which
 * only the holder hears. Server thread only.
 */
public final class SneakoscopeCarried {
    private static final class State {
        int strength;
        long nextWhistle = Long.MAX_VALUE;
    }

    /** Weak keys: a player who logs out or respawns (a new ServerPlayer) leaves nothing behind. */
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private SneakoscopeCarried() {
    }

    public static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (SneakoscopeRules.dueToCheck(now, player.getId())) {
            check(player, now);
        }
        State state = STATES.get(player);
        if (state != null && state.strength > 0 && now >= state.nextWhistle) {
            SneakoscopeSensor.whistleTo(player);
            state.nextWhistle = now + SneakoscopeRules.whistleInterval(state.strength);
        }
    }

    private static void check(ServerPlayer player, long now) {
        Item sneakoscope = ExampleMod.SNEAKOSCOPE_ITEM.get();
        Inventory inventory = player.getInventory();
        boolean carries = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(sneakoscope)) {
                carries = true;
                break;
            }
        }
        if (!carries) {
            STATES.remove(player);
            return;
        }
        int strength = 0;
        if (player.isAlive() && !player.isSpectator()) {
            strength = SneakoscopeSensor.strength(player.serverLevel(), player.getBoundingBox().getCenter(), player);
        }
        boolean spinning = SneakoscopeRules.spinning(strength);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(sneakoscope) && SneakoscopeItem.isSpinning(stack) != spinning) {
                SneakoscopeItem.setSpinning(stack, spinning);
            }
        }
        State state = STATES.get(player);
        int old = state == null ? 0 : state.strength;
        if (strength <= 0) {
            STATES.remove(player);
            return;
        }
        if (state == null) {
            state = new State();
            STATES.put(player, state);
        }
        state.nextWhistle = SneakoscopeRules.nextWhistle(now, state.nextWhistle, old, strength);
        state.strength = strength;
    }
}
