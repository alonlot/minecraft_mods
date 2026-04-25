package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.OwlEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class OwlPerchBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = new int[]{0};
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClients();
        }
    };

    private String perchName = "";
    private UUID owner;
    private BlockPos linkedPos;
    private boolean dispatchReady;
    private boolean owlDelivered;

    public OwlPerchBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.OWL_PERCH_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OwlPerchBlockEntity perch) {
        if (!level.isClientSide && level.getGameTime() % 20L == 0L) {
            perch.tryDispatch((ServerLevel) level);
        }
    }

    public void setOwner(@Nullable Player player) {
        this.owner = player == null ? this.owner : player.getUUID();
        setChanged();
        syncToClients();
    }

    public void setLinkedPos(BlockPos linkedPos) {
        this.linkedPos = linkedPos.immutable();
        setChanged();
        syncToClients();
    }

    @Nullable
    public BlockPos getLinkedPos() {
        return linkedPos;
    }

    public boolean hasQueuedMail() {
        return !inventory.getStackInSlot(0).isEmpty();
    }

    public boolean queueMail(ItemStack stack, Player player) {
        if (hasQueuedMail()) {
            return false;
        }
        setStoredMail(stack, true, false);
        setOwner(player);
        return true;
    }

    public boolean receiveDelivery(ItemStack stack) {
        if (hasQueuedMail() || stack.isEmpty()) {
            return false;
        }
        setStoredMail(stack, false, true);
        return true;
    }

    public ItemStack removeQueuedMail() {
        ItemStack stack = inventory.getStackInSlot(0);
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        dispatchReady = false;
        owlDelivered = false;
        return stack;
    }

    public ItemStack peekQueuedMail() {
        return inventory.getStackInSlot(0);
    }

    public boolean canAcceptMail(ItemStack stack) {
        return !stack.isEmpty() && (stack.getCount() >= 1);
    }

    public boolean hasDispatchReadyMail() {
        return hasQueuedMail() && dispatchReady;
    }

    public ItemStack takeStoredItem(Player player) {
        if (!hasQueuedMail()) {
            return ItemStack.EMPTY;
        }
        setOwner(player);
        return removeQueuedMail();
    }

    private void setStoredMail(ItemStack stack, boolean dispatchReady, boolean owlDelivered) {
        inventory.setStackInSlot(0, stack.copyWithCount(1));
        this.dispatchReady = dispatchReady;
        this.owlDelivered = owlDelivered;
        setChanged();
        syncToClients();
    }

    private void tryDispatch(ServerLevel level) {
        if (owner == null || linkedPos == null || !hasQueuedMail() || !dispatchReady) {
            return;
        }
        if (!level.isLoaded(linkedPos)) {
            return;
        }
        if (!(level.getBlockEntity(linkedPos) instanceof OwlPerchBlockEntity destination) || destination.hasQueuedMail()) {
            return;
        }

        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
        if (ownerPlayer == null) {
            return;
        }

        List<OwlEntity> nearbyOwls = level.getEntitiesOfClass(OwlEntity.class,
                new AABB(getBlockPos()).inflate(16.0),
                owl -> owl.isTame()
                        && owner.equals(owl.getOwnerUUID())
                        && !owl.isPostalBusy()
                        && owl.canServeRoute(this.worldPosition, linkedPos));
        Vec3 pickupPoint = Vec3.atCenterOf(this.worldPosition).add(0.0D, 1.0D, 0.0D);
        nearbyOwls.sort(Comparator.comparingDouble(owl -> owl.position().distanceToSqr(pickupPoint)));

        for (OwlEntity owl : nearbyOwls) {
            if (!owl.isCloseEnoughToPerch(this.worldPosition)) {
                continue;
            }

            ItemStack delivery = removeQueuedMail();
            if (delivery.isEmpty()) {
                return;
            }
            owl.beginPostalDelivery(this.worldPosition, linkedPos, delivery);
            return;
        }
    }

    public Component getStatusMessage(Player player) {
        String name = perchName.isBlank() ? "Unnamed perch" : perchName;
        if (linkedPos == null) {
            return Component.literal(name + " - not linked");
        }
        if (hasQueuedMail()) {
            return Component.literal(name + (dispatchReady ? " - mail queued" : " - delivery waiting"));
        }
        return Component.literal(name + " - linked and ready");
    }

    public void setPerchName(String perchName) {
        this.perchName = perchName;
        setChanged();
        syncToClients();
    }

    public String getPerchName() {
        return perchName;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putString("PerchName", perchName);
        tag.putBoolean("DispatchReady", dispatchReady);
        tag.putBoolean("OwlDelivered", owlDelivered);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (linkedPos != null) {
            tag.putLong("LinkedPos", linkedPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            try {
                inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
            } catch (RuntimeException exception) {
                inventory.setStackInSlot(0, ItemStack.EMPTY);
            }
        }
        perchName = tag.getString("PerchName");
        dispatchReady = tag.getBoolean("DispatchReady");
        owlDelivered = tag.getBoolean("OwlDelivered");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        linkedPos = tag.contains("LinkedPos") ? BlockPos.of(tag.getLong("LinkedPos")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putString("PerchName", perchName);
        tag.putBoolean("DispatchReady", dispatchReady);
        tag.putBoolean("OwlDelivered", owlDelivered);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (linkedPos != null) {
            tag.putLong("LinkedPos", linkedPos.asLong());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction side) {
        return slot == 0 && !hasQueuedMail() && canAcceptMail(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return slot == 0 && !stack.isEmpty() && owlDelivered;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return !hasQueuedMail();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? inventory.getStackInSlot(0) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = inventory.extractItem(0, amount, false);
        if (!removed.isEmpty() && inventory.getStackInSlot(0).isEmpty()) {
            dispatchReady = false;
            owlDelivered = false;
            setChanged();
            syncToClients();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }
        return removeQueuedMail();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        if (stack.isEmpty()) {
            removeQueuedMail();
            return;
        }
        setStoredMail(stack, true, false);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && !hasQueuedMail() && canAcceptMail(stack);
    }

    @Override
    public void clearContent() {
        removeQueuedMail();
    }
}
