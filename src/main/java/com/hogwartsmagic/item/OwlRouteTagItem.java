package com.hogwartsmagic.item;

import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.owlpost.OwlPost;
import com.hogwartsmagic.owlpost.OwlPostData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of owl perches. Use it on a perch to add that perch (use it again to take it off), then give it to a tamed
 * owl: the owl knows every perch on it and can carry post to any of them from its home perch.
 *
 * <p>Tags from before kept exactly two perches as RouteA/RouteB (no dimension); they read as a two-perch list in
 * whatever dimension they are used in, and are rewritten as a list on the first change.
 */
public class OwlRouteTagItem extends Item {
    public static final int MAX_PERCHES = 24;
    private static final String PERCHES = "Perches";
    private static final String ROUTE_A = "RouteA";
    private static final String ROUTE_B = "RouteB";
    private static final String FIRST_PERCH = "FirstPerch";

    public OwlRouteTagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The perches on this tag. Old two-perch tags have no dimension and are read in {@code legacyDimension}. */
    public static List<GlobalPos> getPerches(ItemStack stack, ResourceKey<Level> legacyDimension) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(PERCHES, Tag.TAG_LIST)) {
            return OwlPostData.readPosList(tag.getList(PERCHES, Tag.TAG_COMPOUND));
        }
        List<GlobalPos> out = new ArrayList<>();
        if (tag.contains(ROUTE_A) && tag.contains(ROUTE_B)) {
            out.add(GlobalPos.of(legacyDimension, BlockPos.of(tag.getLong(ROUTE_A))));
            GlobalPos b = GlobalPos.of(legacyDimension, BlockPos.of(tag.getLong(ROUTE_B)));
            if (!out.contains(b)) {
                out.add(b);
            }
        }
        return out;
    }

    public static boolean hasRoute(ItemStack stack, ResourceKey<Level> legacyDimension) {
        return !getPerches(stack, legacyDimension).isEmpty();
    }

    private static void setPerches(ItemStack stack, List<GlobalPos> perches) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(ROUTE_A);
        tag.remove(ROUTE_B);
        tag.remove(FIRST_PERCH);
        tag.put(PERCHES, OwlPostData.writePosList(perches));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof OwlPerchBlockEntity perch)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        GlobalPos here = GlobalPos.of(level.dimension(), context.getClickedPos());
        List<GlobalPos> perches = new ArrayList<>(getPerches(stack, level.dimension()));
        perch.registerNow((ServerLevel) level);
        String name = OwlPost.displayName(here, perch.getPerchName());
        if (perches.remove(here)) {
            setPerches(stack, perches);
            message(player, Component.translatableWithFallback("message.hogwartsmagic.owl_route.removed",
                    "%s taken off the route tag (%s perches).", name, perches.size()).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (perches.size() >= MAX_PERCHES) {
            message(player, Component.translatableWithFallback("message.hogwartsmagic.owl_route.full",
                    "This route tag is full (%s perches).", MAX_PERCHES).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        perches.add(here);
        setPerches(stack, perches);
        if (perch.getOwner() == null) {
            perch.setOwner(player);
        }
        message(player, Component.translatableWithFallback("message.hogwartsmagic.owl_route.added",
                "%s added to the route tag (%s perches).", name, perches.size()).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static void message(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        return assignToOwl(stack, player, interactionTarget);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        List<GlobalPos> perches = getPerches(stack, Level.OVERWORLD);
        if (perches.isEmpty()) {
            tooltip.add(Component.translatableWithFallback("tooltip.hogwartsmagic.owl_route.empty",
                    "Use on owl perches to add them.").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatableWithFallback("tooltip.hogwartsmagic.owl_route.count",
                "%s perches", perches.size()).withStyle(ChatFormatting.GOLD));
        boolean legacy = !tag.contains(PERCHES);
        int shown = 0;
        for (GlobalPos pos : perches) {
            if (shown++ >= 6) {
                tooltip.add(Component.literal("  ...").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            String where = pos.pos().getX() + ", " + pos.pos().getY() + ", " + pos.pos().getZ();
            if (!legacy && !pos.dimension().equals(Level.OVERWORLD)) {
                where += " (" + pos.dimension().location().getPath() + ")";
            }
            tooltip.add(Component.literal("  " + where).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatableWithFallback("tooltip.hogwartsmagic.owl_route.hint",
                "Give it to your owl.").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Gives the tag to the player's own owl. A tag the owl already carried comes back to the player. */
    public static InteractionResult assignToOwl(ItemStack stack, Player player, LivingEntity interactionTarget) {
        if (!(interactionTarget instanceof OwlEntity owl)) {
            return InteractionResult.PASS;
        }
        boolean client = player.level().isClientSide;
        if (!owl.isTame() || !owl.isOwnedBy(player)) {
            if (!client && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_owner_only"), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (client) {
            return InteractionResult.SUCCESS;
        }
        if (!hasRoute(stack, owl.level().dimension())) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.owl.route_missing"), true);
            return InteractionResult.SUCCESS;
        }
        if (owl.isOnErrand()) {
            player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.owl_busy",
                    "This owl is busy with a delivery."), true);
            return InteractionResult.SUCCESS;
        }
        ItemStack given = stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ItemStack old = owl.giveRouteTag(given);
        if (!old.isEmpty() && !player.addItem(old)) {
            player.drop(old, false);
        }
        owl.setOrderedToSit(false);
        GlobalPos home = owl.getHome();
        Component homeName = home == null
                ? Component.translatableWithFallback("message.hogwartsmagic.owl_post.no_home", "none yet")
                : Component.literal(OwlPost.displayName(OwlPostData.get(player.getServer()), home));
        player.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl.route_set_multi",
                "Your owl knows %s perches now. Home: %s. Sneak and use it with an empty hand to choose its home.",
                owl.getKnownPerches().size(), homeName).withStyle(ChatFormatting.GREEN), false);
        return InteractionResult.SUCCESS;
    }
}
