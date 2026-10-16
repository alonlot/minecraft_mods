package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.assignment.AssignmentDef;
import com.hogwartsmagic.assignment.AssignmentProgress;
import com.hogwartsmagic.assignment.AssignmentRegistry;
import com.hogwartsmagic.assignment.AssignmentRules;
import com.hogwartsmagic.assignment.ObjectiveDef;
import com.hogwartsmagic.assignment.OpenAssignmentPacket;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Hogwarts Assignment (or Dark Orders): a task that, once done, turns into the spellbook it promises.
 * Right-click to accept it (binds the scroll to you), right-click again to read it, and right-click a
 * finished one to claim the book. Sneak-right-click twice to give it up. Progress lives on the player
 * (see AssignmentProgress); this stack only names the spell and its owner.
 */
public class AssignmentItem extends Item {
    public static final String TAG_SPELL_ID = "SpellId";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    /** Custom model data: 1..4 by rarity (Common..Epic), 5 for Dark Orders. */
    public static final int MODEL_DARK = 5;
    private static final Map<UUID, Long> ABANDON_ARMED = new HashMap<>();

    public AssignmentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(SpellType spell) {
        ItemStack stack = new ItemStack(ExampleMod.ASSIGNMENT.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_SPELL_ID, spell.getId()));
        AssignmentDef def = AssignmentRegistry.get(spell.getId());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelFor(spell, def != null && def.dark())));
        return stack;
    }

    /**
     * The client has no assignment data (it is server-side datapack content), so a scroll made there,
     * such as in the creative tab, may show the wrong seal. The server fixes it once the scroll is in a
     * player's inventory.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (level.isClientSide || entity.tickCount % 20 != 0) {
            return;
        }
        SpellType spell = getSpell(stack);
        AssignmentDef def = AssignmentRegistry.get(spell.getId());
        if (def == null) {
            return;
        }
        int wanted = modelFor(spell, def.dark());
        CustomModelData current = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (current == null || current.value() != wanted) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(wanted));
        }
    }

    public static int modelFor(SpellType spell, boolean dark) {
        return dark ? MODEL_DARK : spell.getLootRarity().ordinal() + 1;
    }

    public static SpellType getSpell(ItemStack stack) {
        String id = tag(stack).getString(TAG_SPELL_ID);
        return SpellType.fromId(id.isEmpty() ? SpellType.LUMOS.getId() : id);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        SpellType spell = getSpell(stack);
        String spellId = spell.getId();
        AssignmentDef def = AssignmentRegistry.get(spellId);
        if (def == null) {
            fail(player, Component.translatable("assignment.hogwartsmagic.faded"));
            return InteractionResultHolder.fail(stack);
        }
        CompoundTag data = tag(stack);
        if (data.hasUUID(TAG_OWNER) && !data.getUUID(TAG_OWNER).equals(player.getUUID())) {
            fail(player, Component.translatable("assignment.hogwartsmagic.bound", data.getString(TAG_OWNER_NAME)));
            return InteractionResultHolder.fail(stack);
        }
        boolean active = AssignmentProgress.isActive(player, spellId);
        if (!active && MagicBookItem.isSpellLearned(player, spell)) {
            fail(player, Component.translatable("gui.hogwartsmagic.spell_already_learned", spellName(spell)));
            return InteractionResultHolder.fail(stack);
        }

        if (active && player.isShiftKeyDown()) {
            return InteractionResultHolder.success(abandon(serverPlayer, stack, spell));
        }
        if (!active) {
            if (AssignmentProgress.activeCount(player) >= AssignmentRules.MAX_ACTIVE) {
                fail(player, Component.translatable("assignment.hogwartsmagic.too_many", AssignmentRules.MAX_ACTIVE));
                return InteractionResultHolder.fail(stack);
            }
            AssignmentProgress.start(player, spellId);
            bind(stack, player);
            player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.accepted", spellName(spell))
                    .withStyle(ChatFormatting.GOLD), false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (!data.hasUUID(TAG_OWNER)) {
            bind(stack, player);
        }

        if (active && AssignmentProgress.isComplete(serverPlayer, def)) {
            return InteractionResultHolder.success(claim(serverPlayer, stack, spell, def));
        }
        openScreen(serverPlayer, spell, def);
        return InteractionResultHolder.success(stack);
    }

    private static ItemStack claim(ServerPlayer player, ItemStack stack, SpellType spell, AssignmentDef def) {
        AssignmentProgress.consumeDeliveries(player, def);
        AssignmentProgress.remove(player, spell.getId());
        player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.claimed", spellName(spell))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.hogwartsmagic.ModSounds.SPELL_LEARN.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                    player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
        }
        ItemStack book = MagicBookItem.createBook(spell);
        if (stack.getCount() <= 1) {
            return book;
        }
        stack.shrink(1);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return stack;
    }

    private static ItemStack abandon(ServerPlayer player, ItemStack stack, SpellType spell) {
        long now = player.level().getGameTime();
        Long armedAt = ABANDON_ARMED.get(player.getUUID());
        if (armedAt == null || now - armedAt > AssignmentRules.ABANDON_CONFIRM_TICKS) {
            ABANDON_ARMED.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.abandon_confirm")
                    .withStyle(ChatFormatting.YELLOW), true);
            return stack;
        }
        ABANDON_ARMED.remove(player.getUUID());
        AssignmentProgress.remove(player, spell.getId());
        player.displayClientMessage(Component.translatable("assignment.hogwartsmagic.abandoned", spellName(spell))
                .withStyle(ChatFormatting.GRAY), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
        return ItemStack.EMPTY;
    }

    public static void forget(UUID player) {
        ABANDON_ARMED.remove(player);
    }

    private static void bind(ItemStack stack, Player player) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(TAG_OWNER, player.getUUID());
            tag.putString(TAG_OWNER_NAME, player.getGameProfile().getName());
        });
    }

    public static void openScreen(ServerPlayer player, SpellType spell, AssignmentDef def) {
        boolean active = AssignmentProgress.isActive(player, spell.getId());
        int[] progress = active ? AssignmentProgress.progress(player, def) : new int[def.objectives().size()];
        boolean[] live = AssignmentProgress.live(player, def);
        List<OpenAssignmentPacket.Line> lines = new ArrayList<>();
        for (int i = 0; i < def.objectives().size(); i++) {
            ObjectiveDef objective = def.objectives().get(i);
            boolean isLive = objective.type().isLive();
            int amount = isLive ? AssignmentProgress.liveAmount(player, objective, live[i]) : progress[i];
            boolean done = isLive ? live[i] : progress[i] >= objective.count();
            lines.add(new OpenAssignmentPacket.Line(def.objectiveKey(i), objective.count(), amount, isLive, done));
        }
        int state = !active ? OpenAssignmentPacket.STATE_OFFERED
                : AssignmentProgress.isComplete(player, def) ? OpenAssignmentPacket.STATE_COMPLETE
                : OpenAssignmentPacket.STATE_ACTIVE;
        SpellPackets.CHANNEL.send(new OpenAssignmentPacket(spell.getId(), def.teacherKey(), def.quoteKey(), def.dark(),
                        spell.getLootRarity().ordinal(), state, player.getGameProfile().getName(), lines),
                PacketDistributor.PLAYER.with(player));
    }

    private static void fail(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    private static Component spellName(SpellType spell) {
        return Component.translatable("spell.hogwartsmagic." + spell.getId());
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellType spell = getSpell(stack);
        boolean dark = tag(stack).contains(TAG_SPELL_ID) && isDark(stack);
        return Component.translatable(dark ? "item.hogwartsmagic.assignment.dark_named" : "item.hogwartsmagic.assignment.named",
                spellName(spell));
    }

    private static boolean isDark(ItemStack stack) {
        CustomModelData model = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return model != null && model.value() == MODEL_DARK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SpellType spell = getSpell(stack);
        SpellLootRarity rarity = spell.getLootRarity();
        tooltip.add(Component.translatable("assignment.hogwartsmagic.rarity." + rarity.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(rarityColor(rarity)));
        CompoundTag data = tag(stack);
        if (data.hasUUID(TAG_OWNER)) {
            tooltip.add(Component.translatable("assignment.hogwartsmagic.owner", data.getString(TAG_OWNER_NAME))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("assignment.hogwartsmagic.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static ChatFormatting rarityColor(SpellLootRarity rarity) {
        if (rarity == SpellLootRarity.UNCOMMON) {
            return ChatFormatting.YELLOW;
        }
        if (rarity == SpellLootRarity.RARE) {
            return ChatFormatting.AQUA;
        }
        if (rarity == SpellLootRarity.EPIC) {
            return ChatFormatting.LIGHT_PURPLE;
        }
        return ChatFormatting.WHITE;
    }
}
