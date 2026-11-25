package com.hogwartsmagic.locket;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** What the cave goblet holds. */
public enum GobletFill implements StringRepresentable {
    EMPTY("empty"),
    POTION("potion"),
    WATER("water");

    public static final Codec<GobletFill> CODEC = StringRepresentable.fromEnum(GobletFill::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, GobletFill> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)], GobletFill::ordinal).cast();

    private final String key;

    GobletFill(String key) {
        this.key = key;
    }

    @Override
    public String getSerializedName() {
        return key;
    }
}
