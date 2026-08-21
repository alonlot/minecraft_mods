package com.hogwartsmagic.gringotts;

import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * Asks the one question the whole break-in turns on: <b>are you currently a goblin?</b>
 *
 * <p>The films open a vault with a goblin drawing a finger down the door, and this mod has had a
 * complete Polyjuice system - samples, brewing, an active disguise held in player persistent data -
 * with nowhere in particular to point it. Gringotts is where it was always meant to end up: the
 * gate opens quietly for a goblin, the vault door does not scream for one, and the guards below
 * ground let one walk past.
 *
 * <p>It is deliberately never a <em>gate</em> on progress, only on noise. A player who never brewed
 * Polyjuice can still get through every door in here; they simply cannot do it quietly. Making the
 * disguise mandatory would put the whole feature behind a potion chain, and making the Thief's
 * Downfall - which strips it - a wall the deep vaults sit behind.
 */
public final class GringottsDisguise {

    /** Both goblin entity types count; a bank teller and a vault guard have the same hands. */
    private static final Set<String> GOBLIN_TYPES = Set.of(
            "hogwartsmagic:goblin", "hogwartsmagic:goblin_guard");

    private GringottsDisguise() {
    }

    public static boolean isGoblin(Player player) {
        CompoundTag active = PolyjuiceData.getActive(player);
        if (active.isEmpty() || !PolyjuiceData.isMobSample(active)) {
            return false;
        }
        return GOBLIN_TYPES.contains(active.getString(PolyjuiceData.ENTITY_TYPE));
    }
}
