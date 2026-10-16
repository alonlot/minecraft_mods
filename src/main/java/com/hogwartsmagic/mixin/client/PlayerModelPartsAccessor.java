package com.hogwartsmagic.mixin.client;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The synched byte of shown skin layers (hat, jacket, sleeves, pants); Pensieve puppet players turn them all on. */
@Mixin(Player.class)
public interface PlayerModelPartsAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> hogwartsmagic$modelParts() {
        throw new AssertionError();
    }
}
