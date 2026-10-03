package com.hogwartsmagic.spell.support.seal;

import java.util.UUID;

/**
 * One Colloportus seal: every block within radius of (x, y, z) is sealed. ownerName is the caster's name when
 * the seal was made; createdAt is the game time then. Pure data.
 */
public record Seal(UUID owner, String ownerName, int rank, int x, int y, int z, int radius, long createdAt) {
}
