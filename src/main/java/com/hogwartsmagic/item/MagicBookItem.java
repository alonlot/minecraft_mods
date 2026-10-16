package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

import java.util.List;

public class MagicBookItem extends Item {
    public static final String TAG_SPELL_ID = "SpellId";
    private static final String LEARNED_SPELLS_KEY = "LearnedSpells";
    private static final List<String> SPELL_MODEL_ORDER = List.of(
            "expelliarmus",
            "glacius",
            "confringo",
            "protego",
            "lumos",
            "accio",
            "wingardium_leviosa",
            "alohomora",
            "incendio",
            "stupefy",
            "revelio",
            "avada_kedavra",
            "crucio",
            "imperio",
            "fiendfyre",
            "sectumsempra",
            "morsmordre",
            "dolohov_curse",
            "incarcerous",
            "heal",
            "tenebris_flight",
            "expecto_patronum",
            "petrificus_totalus",
            "protego_maxima",
            "bombarda",
            "depulso",
            "diffindo",
            "aqua_eructo",
            "obliviate",
            "ascendio",
            "confundo",
            "cave_inimicum",
            "alatus",
            "serpensortia",
            "arresto_momentum",
            "reparo",
            "apparition",
            "piertotum_locomotor",
            "colloportus",
            "levicorpus"
    );

    public MagicBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide || stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return;
        }
        int v = customModelDataValue(getSpell(stack));
        if (v > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(v));
        }
    }

    public static ItemStack createBook(SpellType spell) {
        ItemStack stack = new ItemStack(ExampleMod.MAGIC_BOOK.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_SPELL_ID, spell.getId()));
        int cmd = customModelDataValue(spell);
        if (cmd > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(cmd));
        }
        return stack;
    }

    private static int customModelDataValue(SpellType spell) {
        int index = SPELL_MODEL_ORDER.indexOf(spell.getId());
        return index >= 0 ? index + 1 : 0;
    }

    public static SpellType getSpell(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String id = data.getUnsafe().getString(TAG_SPELL_ID);
        return SpellType.fromId(id.isEmpty() ? SpellType.ALOHOMORA.getId() : id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        SpellType spell = getSpell(book);

        if (!level.isClientSide) {
            if (isSpellLearned(player, spell)) {
                player.displayClientMessage(Component.translatable("gui.hogwartsmagic.spell_already_learned", 
                        Component.translatable("spell.hogwartsmagic." + spell.getId())).withStyle(ChatFormatting.RED), true);
            } else {
                learnSpell(player, spell);
                book.shrink(1);
                
                // Announcement
                player.displayClientMessage(Component.translatable("gui.hogwartsmagic.spell_learned", 
                        Component.translatable("spell.hogwartsmagic." + spell.getId())).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                
                // Sound
                level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        com.hogwartsmagic.ModSounds.SPELL_LEARN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
        return InteractionResultHolder.sidedSuccess(book, level.isClientSide());
    }

    public static boolean isSpellLearned(Player player, SpellType spell) {
        net.minecraft.nbt.CompoundTag modData = player.getPersistentData().getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
        String learned = modData.getString(LEARNED_SPELLS_KEY);
        // Match whole comma-separated tokens, not substrings: a bare contains() would treat
        // "protego" as learned the moment "protego_maxima" is known (and break on any future
        // id that is a prefix of another).
        if (learned.isEmpty()) {
            return false;
        }
        String target = spell.getId();
        for (String token : learned.split(",")) {
            if (token.equals(target)) {
                return true;
            }
        }
        return false;
    }

    public static boolean learnSpellIfAbsent(Player player, SpellType spell) {
        if (isSpellLearned(player, spell)) {
            return false;
        }
        learnSpell(player, spell);
        return true;
    }

    private static void learnSpell(Player player, SpellType spell) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        String learned = modData.getString(LEARNED_SPELLS_KEY);
        if (!learned.isEmpty()) learned += ",";
        learned += spell.getId();
        modData.putString(LEARNED_SPELLS_KEY, learned);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
        
        // Sync to client
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.hogwartsmagic.network.SpellPackets.CHANNEL.send(
                new com.hogwartsmagic.network.SpellPackets.SyncLearnedSpellsPacket(learned),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(serverPlayer)
            );
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellType spell = getSpell(stack);
        return Component.translatable("item.hogwartsmagic.magic_book.named", 
                Component.translatable("spell.hogwartsmagic." + spell.getId()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SpellType spell = getSpell(stack);
        if (spell != null) {
            tooltip.add(Component.translatable("spell.hogwartsmagic." + spell.getId() + ".desc").withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable("item.hogwartsmagic.magic_book.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
