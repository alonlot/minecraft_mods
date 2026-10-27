package com.hogwartsmagic.item;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.mastery.TomeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Tome of Mastery: raises one learned spell of this tome's tier or below by exactly one rank.
 * Right-click asks the server for the eligible spells, which opens the client picker; the
 * picker's choice comes back as {@link SpellPackets.UseTomePacket} and {@link #tryRaise}
 * re-checks everything before touching anything. One class, four registered items.
 */
public class TomeOfMasteryItem extends Item {
    private final SpellLootRarity tier;

    public TomeOfMasteryItem(SpellLootRarity tier, Properties properties) {
        super(properties.rarity(displayRarity(tier)));
        this.tier = tier;
    }

    public SpellLootRarity getTier() {
        return tier;
    }

    /** Vanilla rarity colours the name: white, yellow, aqua, light purple. */
    public static Rarity displayRarity(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return Rarity.UNCOMMON;
        }
        if (tier == SpellLootRarity.RARE) {
            return Rarity.RARE;
        }
        if (tier == SpellLootRarity.EPIC) {
            return Rarity.EPIC;
        }
        return Rarity.COMMON;
    }

    /** Registry path, e.g. {@code common_tome_of_mastery}; the lang key is {@code item.hogwartsmagic.<path>}. */
    public static String registryName(SpellLootRarity tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "_tome_of_mastery";
    }

    public static ItemStack stackFor(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return new ItemStack(ExampleMod.UNCOMMON_TOME_OF_MASTERY.get());
        }
        if (tier == SpellLootRarity.RARE) {
            return new ItemStack(ExampleMod.RARE_TOME_OF_MASTERY.get());
        }
        if (tier == SpellLootRarity.EPIC) {
            return new ItemStack(ExampleMod.EPIC_TOME_OF_MASTERY.get());
        }
        return new ItemStack(ExampleMod.COMMON_TOME_OF_MASTERY.get());
    }

    /** Learned, non-Master spells this tier can raise, in SpellType order. Reads only synced data. */
    public static List<SpellType> eligibleSpells(Player player, SpellLootRarity tier) {
        List<SpellType> eligible = new ArrayList<>();
        for (SpellType spell : SpellType.values()) {
            if (TomeRules.isEligible(tier, spell.getLootRarity(),
                    MagicBookItem.isSpellLearned(player, spell), SpellMasteryData.getRank(player, spell))) {
                eligible.add(spell);
            }
        }
        return eligible;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
            List<SpellType> eligible = eligibleSpells(serverPlayer, tier);
            if (eligible.isEmpty()) {
                serverPlayer.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".tome.nothing_to_raise")
                        .withStyle(ChatFormatting.RED), true);
            } else {
                List<String> ids = new ArrayList<>(eligible.size());
                for (SpellType spell : eligible) {
                    ids.add(spell.getId());
                }
                SpellPackets.CHANNEL.send(new SpellPackets.OpenTomePickerPacket(hand == InteractionHand.MAIN_HAND, tier.ordinal(), ids),
                        PacketDistributor.PLAYER.with(serverPlayer));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Server side of a picker click. Nothing from the client is trusted: the hand must still hold
     * a tome, the id must be a real spell, and the spell must be learned, allowed by this tome's
     * tier and below Master. On success the XP is set to the start of the next rank (exactly one
     * rank), the tome is consumed and the normal rank-up feedback plays.
     */
    public static boolean tryRaise(ServerPlayer player, boolean mainHand, String spellId) {
        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof TomeOfMasteryItem tome)) {
            return false;
        }
        SpellType spell = strictSpell(spellId);
        if (spell == null) {
            return false;
        }
        boolean learned = MagicBookItem.isSpellLearned(player, spell);
        int rank = SpellMasteryData.getRank(player, spell);
        if (!TomeRules.isEligible(tome.getTier(), spell.getLootRarity(), learned, rank)) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".tome.not_eligible")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float nextXp = TomeRules.xpForNextRank(SpellMasteryData.getXp(player, spell), Config.getMasteryThresholds());
        if (nextXp < 0f) {
            return false;
        }
        SpellMasteryData.setXp(player, spell, nextXp);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        MasteryAwards.celebrate(player, spell, SpellMasteryData.getRank(player, spell));
        return true;
    }

    /** SpellType.fromId falls back to Wingardium Leviosa on an unknown id; a packet must not. */
    private static SpellType strictSpell(String id) {
        if (id == null) {
            return null;
        }
        for (SpellType spell : SpellType.values()) {
            if (spell.getId().equals(id)) {
                return spell;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item." + ExampleMod.MODID + ".tome_of_mastery.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item." + ExampleMod.MODID + ".tome_of_mastery.works_on."
                + tier.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
