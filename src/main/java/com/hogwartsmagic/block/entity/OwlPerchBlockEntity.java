package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.owlpost.OwlPost;
import com.hogwartsmagic.owlpost.OwlPostData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An owl post station. Slot 0 is the outbox: one stack waiting for an owl that lives here to carry it to
 * {@link #outboxDest}. Slots 1-9 are the inbox, where stacks other owls bring are left.
 *
 * <p>Hoppers: anything fed in from above or the sides goes to the outbox, but only when the perch has a default
 * destination, and is sent there once the stack has stopped growing for two seconds (or is full). A hopper
 * underneath empties the inbox.
 */
public class OwlPerchBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int OUTBOX = 0;
    public static final int INBOX_SIZE = 9;
    private static final int[] SLOTS_IN = new int[]{OUTBOX};
    private static final int[] SLOTS_OUT = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    /** A hopper-fed stack leaves once it has not grown for this long (game ticks), or once it is full. */
    private static final int SETTLE_TICKS = 40;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1 + INBOX_SIZE, ItemStack.EMPTY);
    private String perchName = "";
    @Nullable
    private UUID owner;
    @Nullable
    private GlobalPos outboxDest;
    @Nullable
    private UUID outboxSender;
    @Nullable
    private GlobalPos defaultDest;
    /** A perch saved before owl post had destinations: its one link, resolved to this dimension on the first tick. */
    @Nullable
    private BlockPos legacyLinked;
    private boolean legacyOutbox;

    private boolean registered;
    private int lastOutboxCount;
    private int settleTicks;

    public OwlPerchBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.OWL_PERCH_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OwlPerchBlockEntity perch) {
        if (level instanceof ServerLevel serverLevel) {
            perch.serverTick(serverLevel);
        }
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(this.level.dimension(), this.worldPosition);
    }

    private void serverTick(ServerLevel level) {
        if (legacyLinked != null) {
            if (defaultDest == null && !legacyLinked.equals(worldPosition)) {
                defaultDest = GlobalPos.of(level.dimension(), legacyLinked);
            }
            if (legacyOutbox && outboxDest == null) {
                outboxDest = defaultDest;
            }
            legacyLinked = null;
            legacyOutbox = false;
            setChanged();
        }
        long time = level.getGameTime();
        if (!registered || time % 1200L == 0L) {
            registerNow(level);
        }
        if (time % 10L != 0L) {
            return;
        }
        ItemStack outbox = items.get(OUTBOX);
        if (outbox.isEmpty()) {
            lastOutboxCount = 0;
            settleTicks = 0;
            return;
        }
        if (outboxDest == null) {
            if (defaultDest == null) {
                return;
            }
            outboxDest = defaultDest;
            setChanged();
        }
        if (outbox.getCount() != lastOutboxCount) {
            lastOutboxCount = outbox.getCount();
            settleTicks = 0;
            if (outboxSender == null && outbox.getCount() < outbox.getMaxStackSize()) {
                return; // a hopper is still filling it
            }
        }
        settleTicks += 10;
        if (outboxSender == null && settleTicks < SETTLE_TICKS && outbox.getCount() < outbox.getMaxStackSize()) {
            return;
        }
        OwlPost.tryDispatch(level, this);
    }

    /** Puts this perch (and its name) in the post office's list, so owls can be sent to it. */
    public void registerNow(ServerLevel level) {
        OwlPostData.get(level.getServer()).putPerch(globalPos(), perchName);
        registered = true;
    }

    // ------------------------------------------------------------------ owner and name

    public void setOwner(@Nullable Player player) {
        if (player != null) {
            this.owner = player.getUUID();
            setChanged();
        }
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    /** Whether {@code player} may take this perch's post and set where its hoppers send: owner or op (anyone on an unowned perch). */
    public boolean mayManage(net.minecraft.world.entity.player.Player player) {
        return owner == null || owner.equals(player.getUUID())
                || (player instanceof net.minecraft.server.level.ServerPlayer sp && sp.hasPermissions(2));
    }

    public void setPerchName(String perchName) {
        this.perchName = perchName == null ? "" : perchName;
        setChanged();
        syncToClients();
        if (level instanceof ServerLevel serverLevel) {
            registerNow(serverLevel);
        }
    }

    public String getPerchName() {
        return perchName;
    }

    // ------------------------------------------------------------------ outbox

    public boolean hasOutbox() {
        return !items.get(OUTBOX).isEmpty();
    }

    public ItemStack peekOutbox() {
        return items.get(OUTBOX);
    }

    @Nullable
    public GlobalPos getOutboxDest() {
        return outboxDest;
    }

    @Nullable
    public UUID getOutboxSender() {
        return outboxSender;
    }

    /** A stack handed over at the destination screen. Only into an empty outbox. */
    public boolean setOutbox(ItemStack stack, GlobalPos dest, @Nullable UUID sender) {
        if (hasOutbox() || stack.isEmpty()) {
            return false;
        }
        items.set(OUTBOX, stack.copy());
        outboxDest = dest;
        outboxSender = sender;
        lastOutboxCount = 0;
        settleTicks = 0;
        setChanged();
        return true;
    }

    /** Empties the outbox (an owl took it, or the player took it back). */
    public ItemStack takeOutbox() {
        ItemStack stack = items.get(OUTBOX);
        items.set(OUTBOX, ItemStack.EMPTY);
        outboxDest = null;
        outboxSender = null;
        lastOutboxCount = 0;
        settleTicks = 0;
        setChanged();
        return stack;
    }

    @Nullable
    public GlobalPos getDefaultDest() {
        return defaultDest;
    }

    public void setDefaultDest(@Nullable GlobalPos dest) {
        this.defaultDest = dest;
        setChanged();
    }

    // ------------------------------------------------------------------ inbox

    public boolean hasInbox() {
        for (int i = 1; i <= INBOX_SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int inboxStacks() {
        int n = 0;
        for (int i = 1; i <= INBOX_SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Lays a delivered stack in the inbox; returns what did not fit (empty if all of it did). */
    public ItemStack acceptDelivery(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 1; i <= INBOX_SIZE && !rest.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
                int room = Math.min(slot.getMaxStackSize(), getMaxStackSize()) - slot.getCount();
                if (room > 0) {
                    int moved = Math.min(room, rest.getCount());
                    slot.grow(moved);
                    rest.shrink(moved);
                }
            }
        }
        for (int i = 1; i <= INBOX_SIZE && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest.split(Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), getMaxStackSize()))));
            }
        }
        if (rest.getCount() != stack.getCount()) {
            setChanged();
        }
        return rest;
    }

    /** Takes every stack out of the inbox. */
    public List<ItemStack> takeInbox() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 1; i <= INBOX_SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                out.add(items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }
        if (!out.isEmpty()) {
            setChanged();
        }
        return out;
    }

    // ------------------------------------------------------------------ sync and NBT

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putString("PerchName", perchName);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (outboxDest != null) {
            tag.put("OutboxDest", OwlPostData.writePos(outboxDest));
        }
        if (outboxSender != null) {
            tag.putUUID("OutboxSender", outboxSender);
        }
        if (defaultDest != null) {
            tag.put("DefaultDest", OwlPostData.writePos(defaultDest));
        }
        if (legacyLinked != null) {
            tag.putLong("LinkedPos", legacyLinked.asLong());
            tag.putBoolean("LegacyOutbox", legacyOutbox);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        perchName = tag.getString("PerchName");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        outboxDest = tag.contains("OutboxDest") ? OwlPostData.readPos(tag.getCompound("OutboxDest")) : null;
        outboxSender = tag.hasUUID("OutboxSender") ? tag.getUUID("OutboxSender") : null;
        defaultDest = tag.contains("DefaultDest") ? OwlPostData.readPos(tag.getCompound("DefaultDest")) : null;
        legacyLinked = tag.contains("LinkedPos") ? BlockPos.of(tag.getLong("LinkedPos")) : null;
        legacyOutbox = tag.getBoolean("LegacyOutbox");
        if (tag.contains("Items")) {
            ContainerHelper.loadAllItems(tag, items, registries);
        } else if (tag.contains("Inventory")) {
            // Saved before destinations: one slot, either mail waiting to go (to LinkedPos) or mail an owl brought.
            ListTag legacy = tag.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                CompoundTag entry = legacy.getCompound(i);
                if (entry.getInt("Slot") != 0) {
                    continue;
                }
                ItemStack stack = ItemStack.parseOptional(registries, entry);
                if (stack.isEmpty()) {
                    continue;
                }
                if (tag.getBoolean("OwlDelivered") || !tag.getBoolean("DispatchReady")) {
                    items.set(1, stack);
                } else {
                    items.set(OUTBOX, stack);
                    legacyOutbox = true;
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("PerchName", perchName);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ container (hoppers, breaking)

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_OUT : SLOTS_IN;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return side != Direction.DOWN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return side == Direction.DOWN && slot >= 1 && slot <= INBOX_SIZE;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot != OUTBOX || stack.isEmpty() || defaultDest == null) {
            return false;
        }
        ItemStack outbox = items.get(OUTBOX);
        if (outbox.isEmpty()) {
            return true;
        }
        // Topping up a stack already waiting: only one meant for the default destination, not a hand-sent one.
        return outboxSender == null && defaultDest.equals(outboxDest) && ItemStack.isSameItemSameComponents(outbox, stack);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == OUTBOX && items.get(OUTBOX).isEmpty()) {
                outboxDest = null;
                outboxSender = null;
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (slot == OUTBOX) {
            outboxDest = null;
            outboxSender = null;
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        items.set(slot, stack);
        if (slot == OUTBOX) {
            if (stack.isEmpty()) {
                outboxDest = null;
                outboxSender = null;
            } else if (outboxDest == null) {
                outboxDest = defaultDest;
                outboxSender = null;
            }
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        outboxDest = null;
        outboxSender = null;
    }
}
