package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.capture.RecordingScene;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A memory drawn from a player's own head. The recording lives in the world save; the stack only carries its id
 * ({@code MemoryId}) and what the tooltip says: {@code Owner}, {@code OwnerName}, {@code Day}, {@code Seconds}.
 */
public class RecordedMemoryItem extends Item implements PensieveMemory {
    public static final String MEMORY_ID = "MemoryId";
    public static final String OWNER = "Owner";
    public static final String OWNER_NAME = "OwnerName";
    public static final String DAY = "Day";
    public static final String SECONDS = "Seconds";

    public RecordedMemoryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The stack for one recording, on {@code item}. */
    public static ItemStack create(Item item, UUID id, UUID owner, String ownerName, int day, int seconds) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(MEMORY_ID, id);
        tag.putUUID(OWNER, owner);
        tag.putString(OWNER_NAME, ownerName);
        tag.putInt(DAY, day);
        tag.putInt(SECONDS, seconds);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** The recording this stack holds, or null when its tag carries none. */
    @Nullable
    public static UUID memoryId(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag.hasUUID(MEMORY_ID) ? tag.getUUID(MEMORY_ID) : null;
    }

    @Override
    public String sceneId(ItemStack stack) {
        UUID id = memoryId(stack);
        // A stack with no id (a creative-tab copy) points at a recording that does not exist, so it fades quietly.
        return RecordingScene.sceneId(id == null ? new UUID(0L, 0L) : id);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (!tag.contains(OWNER_NAME)) {
            return super.getName(stack);
        }
        // An anvil rename (CUSTOM_NAME) wins before this is asked, as in vanilla.
        return Component.translatable("item.hogwartsmagic.memory.named", tag.getString(OWNER_NAME),
                String.valueOf(tag.getInt(DAY)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = tag(stack);
        if (tag.contains(DAY)) {
            tooltip.add(Component.translatable("item.hogwartsmagic.memory.day", String.valueOf(tag.getInt(DAY)))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (tag.contains(SECONDS)) {
            tooltip.add(Component.translatable("item.hogwartsmagic.memory.length", String.valueOf(tag.getInt(SECONDS)))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
