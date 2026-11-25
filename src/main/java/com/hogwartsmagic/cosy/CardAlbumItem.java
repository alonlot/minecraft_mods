package com.hogwartsmagic.cosy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The card album. Using it moves every card in your pack that the album does not already hold into the album (one
 * of each; duplicates stay in the pack), then opens a screen showing the collection. The collection is the
 * {@link CosyFood#ALBUM_CARDS} component on the album, so it travels with it.
 */
public class CardAlbumItem extends Item {
    public CardAlbumItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The ids the album holds. */
    public static List<Integer> collected(ItemStack album) {
        return album.getOrDefault(CosyFood.ALBUM_CARDS.get(), List.of());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack album = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(album, true);
        }
        int added = absorbFrom(player, album);
        if (added > 0) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.card_album.added", added), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        if (player instanceof ServerPlayer sp) {
            CosyNet.CHANNEL.send(new CosyNet.OpenAlbumPacket(collected(album)), PacketDistributor.PLAYER.with(sp));
        }
        return InteractionResultHolder.sidedSuccess(album, false);
    }

    /** Moves one copy of every new card from the pack into the album. Returns how many were added. */
    static int absorbFrom(Player player, ItemStack album) {
        List<Integer> before = collected(album);
        Set<Integer> have = new LinkedHashSet<>(before);
        List<Integer> take = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(CosyFood.WIZARD_CARD.get())) {
                continue;
            }
            WizardCards.Card card = WizardCardItem.cardOf(stack);
            if (card != null && !have.contains(card.id()) && !take.contains(card.id())) {
                take.add(card.id());
                stack.shrink(1);
            }
        }
        if (take.isEmpty()) {
            return 0;
        }
        List<Integer> after = new ArrayList<>(WizardCards.absorb(have, take));
        album.set(CosyFood.ALBUM_CARDS.get(), List.copyOf(after));
        return after.size() - before.size();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.card_album.progress", collected(stack).size(),
                WizardCards.ALL.size()).withStyle(ChatFormatting.GRAY));
    }
}
