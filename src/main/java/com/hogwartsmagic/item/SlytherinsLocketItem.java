package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.locket.DespairRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public class SlytherinsLocketItem extends Item {
    public static final TagKey<Item> DARK_LORD_HORCRUXES =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dark_lord_horcruxes"));
    private static final int WHISPER_CHANCE = 6000; // about once per 5 minutes

    public SlytherinsLocketItem(Properties p) { super(p.stacksTo(1).fireResistant().rarity(Rarity.EPIC)); }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!level.isClientSide && holder instanceof ServerPlayer p && level.random.nextInt(WHISPER_CHANCE) == 0) {
            p.displayClientMessage(Component.literal(DespairRules.whisper(level.random.nextInt(6)))
                    .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), false);
        }
    }

    @Override public int getEntityLifespan(ItemStack stack, Level level) { return Integer.MAX_VALUE; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.hogwartsmagic.slytherins_locket.lore").withStyle(ChatFormatting.DARK_GREEN));
    }
}
