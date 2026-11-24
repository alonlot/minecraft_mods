package com.hogwartsmagic.portkey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * What a Portkey remembers, kept on the stack's custom data as the Floo binding token keeps its fireplace: where it is
 * bound (dimension, feet block, the way you were facing), when it is ready to go again, and whether an Old Portkey
 * has spent itself.
 */
public final class PortkeyData {

    private static final String DIM = "PortkeyDim";
    private static final String POS = "PortkeyPos";
    private static final String YAW = "PortkeyYaw";
    private static final String READY_AT = "PortkeyReadyAt";
    private static final String SPENT = "PortkeySpent";

    private PortkeyData() {
    }

    /** Where a Portkey goes: the feet block in a dimension, and the way to face on landing. */
    public record Binding(ResourceKey<Level> dim, BlockPos pos, float yaw) {
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void put(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static Optional<Binding> binding(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(DIM) || !tag.contains(POS)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(DIM));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new Binding(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong(POS)),
                tag.getFloat(YAW)));
    }

    public static void bind(ItemStack stack, ResourceKey<Level> dim, BlockPos pos, float yaw) {
        CompoundTag tag = tag(stack);
        tag.putString(DIM, dim.location().toString());
        tag.putLong(POS, pos.asLong());
        tag.putFloat(YAW, yaw);
        put(stack, tag);
    }

    /** The game time the Portkey may go again (0 when it never went). */
    public static long readyAt(ItemStack stack) {
        return tag(stack).getLong(READY_AT);
    }

    public static void setReadyAt(ItemStack stack, long readyAt) {
        CompoundTag tag = tag(stack);
        tag.putLong(READY_AT, readyAt);
        put(stack, tag);
    }

    public static boolean spent(ItemStack stack) {
        return tag(stack).getBoolean(SPENT);
    }

    public static void markSpent(ItemStack stack) {
        CompoundTag tag = tag(stack);
        tag.putBoolean(SPENT, true);
        put(stack, tag);
    }
}
