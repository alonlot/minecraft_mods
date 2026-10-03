package com.hogwartsmagic.network;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpellPackets {
    private static final int MAX_FLOO_DESTINATIONS = 256;
    private static final int MAX_CAVE_INIMICUM_EDGE_LENGTH = 64;
    private static final int MAX_CAVE_INIMICUM_STATE_COUNT = MAX_CAVE_INIMICUM_EDGE_LENGTH
            * MAX_CAVE_INIMICUM_EDGE_LENGTH
            * MAX_CAVE_INIMICUM_EDGE_LENGTH;
    private static final int MAX_ACTIVE_WARDS = 512;

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ExampleMod.MODID + ":spells")
            .networkProtocolVersion(3)
            .simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(SelectSpellPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.spellId))
                .decoder(buf -> new SelectSpellPacket(buf.readUtf()))
                .consumerMainThread(SelectSpellPacket::handle)
                .add();
        CHANNEL.messageBuilder(CastSpellPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.selfCastProtego);
                    buf.writeBoolean(p.releaseChannel);
                    buf.writeBoolean(p.channelHeartbeat);
                    buf.writeBoolean(p.fiendfyreAdvance);
                })
                .decoder(buf -> new CastSpellPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()))
                .consumerMainThread(CastSpellPacket::handle)
                .add();
        CHANNEL.messageBuilder(ImperioControlPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.startChannel);
                    buf.writeBoolean(p.stopChannel);
                    buf.writeFloat(p.forward);
                    buf.writeFloat(p.strafe);
                    buf.writeBoolean(p.jump);
                    buf.writeBoolean(p.attack);
                    buf.writeFloat(p.yaw);
                    buf.writeFloat(p.pitch);
                })
                .decoder(buf -> new ImperioControlPacket(
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readFloat(),
                        buf.readFloat(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readFloat(),
                        buf.readFloat()))
                .consumerMainThread(ImperioControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetSpellSlotPacket.class)
                .encoder((p, buf) -> {
                    buf.writeInt(p.slotIndex);
                    buf.writeUtf(p.spellId);
                })
                .decoder(buf -> new SetSpellSlotPacket(buf.readInt(), buf.readUtf()))
                .consumerMainThread(SetSpellSlotPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncLearnedSpellsPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.learnedSpells))
                .decoder(buf -> new SyncLearnedSpellsPacket(buf.readUtf()))
                .consumerMainThread(SyncLearnedSpellsPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncSpellMasteryPacket.class)
                .encoder((p, buf) -> buf.writeNbt(p.mastery))
                .decoder(buf -> {
                    net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                    return new SyncSpellMasteryPacket(tag == null ? new net.minecraft.nbt.CompoundTag() : tag);
                })
                .consumerMainThread(SyncSpellMasteryPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncMasteryConfigPacket.class)
                .encoder((p, buf) -> {
                    buf.writeVarInt(p.adept);
                    buf.writeVarInt(p.expert);
                    buf.writeVarInt(p.master);
                    buf.writeDouble(p.rankDiscount);
                    buf.writeDouble(p.manaPerRank);
                    buf.writeDouble(p.manaCap);
                })
                .decoder(buf -> new SyncMasteryConfigPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()))
                .consumerMainThread(SyncMasteryConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncManaPacket.class)
                .encoder((p, buf) -> { buf.writeFloat(p.mana); buf.writeFloat(p.maxMana); })
                .decoder(buf -> new SyncManaPacket(buf.readFloat(), buf.readFloat()))
                .consumerMainThread(SyncManaPacket::handle)
                .add();
        CHANNEL.messageBuilder(BroomBoostPacket.class)
                .encoder((p, buf) -> buf.writeBoolean(p.isBoosting))
                .decoder(buf -> new BroomBoostPacket(buf.readBoolean()))
                .consumerMainThread(BroomBoostPacket::handle)
                .add();
        CHANNEL.messageBuilder(BroomTrickPacket.class)
                .encoder((p, buf) -> buf.writeByte(p.trickType))
                .decoder(buf -> new BroomTrickPacket(buf.readByte()))
                .consumerMainThread(BroomTrickPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncRelicsPacket.class)
                .encoder((p, buf) -> {
                    buf.writeUtf(writeItemId(p.capacityRelic));
                    buf.writeUtf(writeItemId(p.regenRelic));
                })
                .decoder(buf -> new SyncRelicsPacket(readItemStack(buf.readUtf()), readItemStack(buf.readUtf())))
                .consumerMainThread(SyncRelicsPacket::handle)
                .add();
        CHANNEL.messageBuilder(EquipRelicPacket.class)
                .encoder((p, buf) -> buf.writeInt(p.inventorySlot))
                .decoder(buf -> new EquipRelicPacket(buf.readInt()))
                .consumerMainThread(EquipRelicPacket::handle)
                .add();
        CHANNEL.messageBuilder(UnequipRelicPacket.class)
                .encoder((p, buf) -> buf.writeInt(p.slotIndex))
                .decoder(buf -> new UnequipRelicPacket(buf.readInt()))
                .consumerMainThread(UnequipRelicPacket::handle)
                .add();
        CHANNEL.messageBuilder(HouseRevealPacket.class)
                .encoder((p, buf) -> buf.writeEnum(p.house))
                .decoder(buf -> new HouseRevealPacket(buf.readEnum(HogwartsHouse.class)))
                .consumerMainThread(HouseRevealPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncPolyjuiceDisguisePacket.class)
                .encoder((p, buf) -> {
                    buf.writeUUID(p.playerId);
                    buf.writeNbt(p.disguise);
                })
                .decoder(buf -> new SyncPolyjuiceDisguisePacket(buf.readUUID(), buf.readNbt()))
                .consumerMainThread(SyncPolyjuiceDisguisePacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenFlooScreenPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.sourcePos);
                    buf.writeInt(p.destinations.size());
                    for (FlooDestinationData destination : p.destinations) {
                        buf.writeBlockPos(destination.pos());
                        buf.writeUtf(destination.name());
                    }
                })
                .decoder(buf -> {
                    BlockPos source = buf.readBlockPos();
                    int size = clampDecodedSize(buf.readInt(), MAX_FLOO_DESTINATIONS);
                    List<FlooDestinationData> destinations = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        destinations.add(new FlooDestinationData(buf.readBlockPos(), buf.readUtf()));
                    }
                    return new OpenFlooScreenPacket(source, destinations);
                })
                .consumerMainThread(OpenFlooScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenFlooNamingScreenPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.fireplacePos);
                    buf.writeUtf(p.currentName);
                })
                .decoder(buf -> new OpenFlooNamingScreenPacket(buf.readBlockPos(), buf.readUtf()))
                .consumerMainThread(OpenFlooNamingScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenHogwartsInviteScreenPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.recipient))
                .decoder(buf -> new OpenHogwartsInviteScreenPacket(buf.readUtf()))
                .consumerMainThread(OpenHogwartsInviteScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenHogwartsGuideScreenPacket.class)
                .encoder((p, buf) -> {})
                .decoder(buf -> new OpenHogwartsGuideScreenPacket())
                .consumerMainThread(OpenHogwartsGuideScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetFlooNamePacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.fireplacePos);
                    buf.writeUtf(p.name);
                })
                .decoder(buf -> new SetFlooNamePacket(buf.readBlockPos(), buf.readUtf()))
                .consumerMainThread(SetFlooNamePacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseFlooDestinationPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.sourcePos);
                    buf.writeBlockPos(p.destinationPos);
                })
                .decoder(buf -> new ChooseFlooDestinationPacket(buf.readBlockPos(), buf.readBlockPos()))
                .consumerMainThread(ChooseFlooDestinationPacket::handle)
                .add();
        CHANNEL.messageBuilder(CaveInimicumRegionSyncPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.minPos);
                    buf.writeVarInt(p.edgeLength);
                    buf.writeVarInt(p.stateIds.length);
                    for (int stateId : p.stateIds) {
                        buf.writeVarInt(stateId);
                    }
                })
                .decoder(buf -> {
                    BlockPos minPos = buf.readBlockPos();
                    int edgeLength = clampDecodedSize(buf.readVarInt(), MAX_CAVE_INIMICUM_EDGE_LENGTH);
                    int[] stateIds = new int[clampDecodedSize(buf.readVarInt(), MAX_CAVE_INIMICUM_STATE_COUNT)];
                    for (int i = 0; i < stateIds.length; i++) {
                        stateIds[i] = buf.readVarInt();
                    }
                    return new CaveInimicumRegionSyncPacket(minPos, edgeLength, stateIds);
                })
                .consumerMainThread(CaveInimicumRegionSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(CaveInimicumBlockDeltaPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeVarInt(p.stateId);
                })
                .decoder(buf -> new CaveInimicumBlockDeltaPacket(buf.readBlockPos(), buf.readVarInt()))
                .consumerMainThread(CaveInimicumBlockDeltaPacket::handle)
                .add();
        CHANNEL.messageBuilder(UndercroftFogPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.centre);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.topY);
                    buf.writeVarInt(p.bottomY);
                })
                .decoder(buf -> new UndercroftFogPacket(buf.readBlockPos(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()))
                .consumerMainThread(UndercroftFogPacket::handle)
                .add();
        CHANNEL.messageBuilder(CaveInimicumActiveWardsPacket.class)
                .encoder((p, buf) -> {
                    buf.writeVarInt(p.wards.size());
                    for (CaveInimicumActiveWardData ward : p.wards) {
                        buf.writeBlockPos(ward.center());
                        buf.writeVarInt(ward.edgeLength());
                    }
                })
                .decoder(buf -> {
                    int size = clampDecodedSize(buf.readVarInt(), MAX_ACTIVE_WARDS);
                    List<CaveInimicumActiveWardData> wards = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        wards.add(new CaveInimicumActiveWardData(buf.readBlockPos(), buf.readVarInt()));
                    }
                    return new CaveInimicumActiveWardsPacket(wards);
                })
                .consumerMainThread(CaveInimicumActiveWardsPacket::handle)
                .add();
        CHANNEL.messageBuilder(StartHorcruxDeathSequencePacket.class)
                .encoder((p, buf) -> {
                    buf.writeInt(p.soulEntityId);
                    buf.writeDouble(p.startPos.x);
                    buf.writeDouble(p.startPos.y);
                    buf.writeDouble(p.startPos.z);
                    buf.writeBlockPos(p.targetPos);
                })
                .decoder(buf -> new StartHorcruxDeathSequencePacket(
                        buf.readInt(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readBlockPos()))
                .consumerMainThread(StartHorcruxDeathSequencePacket::handle)
                .add();
        CHANNEL.messageBuilder(FinishHorcruxDeathSequencePacket.class)
                .encoder((p, buf) -> {})
                .decoder(buf -> new FinishHorcruxDeathSequencePacket())
                .consumerMainThread(FinishHorcruxDeathSequencePacket::handle)
                .add();
        CHANNEL.messageBuilder(PatronusShieldStatePacket.class)
                .encoder((p, buf) -> {
                    buf.writeInt(p.casterId);
                    buf.writeBoolean(p.active);
                })
                .decoder(buf -> new PatronusShieldStatePacket(buf.readInt(), buf.readBoolean()))
                .consumerMainThread(PatronusShieldStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(CloakTransitionPacket.class)
                .encoder((p, buf) -> {
                    buf.writeUUID(p.playerId);
                    buf.writeBoolean(p.hiding);
                })
                .decoder(buf -> new CloakTransitionPacket(buf.readUUID(), buf.readBoolean()))
                .consumerMainThread(CloakTransitionPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenApparitionMapPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.origin);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.durationTicks);
                    buf.writeVarInt(p.fireplaces.size());
                    for (FlooDestinationData fireplace : p.fireplaces) {
                        buf.writeBlockPos(fireplace.pos());
                        buf.writeUtf(fireplace.name());
                    }
                })
                .decoder(buf -> {
                    BlockPos origin = buf.readBlockPos();
                    int radius = buf.readVarInt();
                    int durationTicks = buf.readVarInt();
                    // Read every encoded entry (so the buffer is fully consumed) up to a hard cap, keep the first 8.
                    int rawSize = buf.readVarInt();
                    if (rawSize > MAX_APPARITION_MAP_WIRE_ENTRIES) {
                        throw new io.netty.handler.codec.DecoderException("Too many apparition fireplaces: " + rawSize);
                    }
                    int size = clampDecodedSize(rawSize, MAX_APPARITION_MAP_WIRE_ENTRIES);
                    int keep = com.hogwartsmagic.spell.support.ApparitionRules.MAX_LISTED_FIREPLACES;
                    List<FlooDestinationData> fireplaces = new ArrayList<>(Math.min(size, keep));
                    for (int i = 0; i < size; i++) {
                        FlooDestinationData entry = new FlooDestinationData(buf.readBlockPos(), buf.readUtf());
                        if (i < keep) {
                            fireplaces.add(entry);
                        }
                    }
                    return new OpenApparitionMapPacket(origin, radius, durationTicks, fireplaces);
                })
                .consumerMainThread(OpenApparitionMapPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseApparitionFireplacePacket.class)
                .encoder((p, buf) -> buf.writeBlockPos(p.fireplacePos))
                .decoder(buf -> new ChooseApparitionFireplacePacket(buf.readBlockPos()))
                .consumerMainThread(ChooseApparitionFireplacePacket::handle)
                .add();
        CHANNEL.messageBuilder(CloseApparitionMapPacket.class)
                .encoder((p, buf) -> {})
                .decoder(buf -> new CloseApparitionMapPacket())
                .consumerMainThread(CloseApparitionMapPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseApparitionDestinationPacket.class)
                .encoder((p, buf) -> {
                    buf.writeInt(p.targetX);
                    buf.writeInt(p.targetZ);
                })
                .decoder(buf -> new ChooseApparitionDestinationPacket(buf.readInt(), buf.readInt()))
                .consumerMainThread(ChooseApparitionDestinationPacket::handle)
                .add();
        CHANNEL.messageBuilder(CancelApparitionPacket.class)
                .encoder((p, buf) -> {})
                .decoder(buf -> new CancelApparitionPacket())
                .consumerMainThread(CancelApparitionPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenTomePickerPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.mainHand);
                    buf.writeVarInt(p.tierOrdinal);
                    buf.writeVarInt(p.spellIds.size());
                    for (String id : p.spellIds) {
                        buf.writeUtf(id, 64);
                    }
                })
                .decoder(buf -> {
                    boolean mainHand = buf.readBoolean();
                    int tierOrdinal = buf.readVarInt();
                    int size = clampDecodedSize(buf.readVarInt(), SpellType.values().length);
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ids.add(buf.readUtf(64));
                    }
                    return new OpenTomePickerPacket(mainHand, tierOrdinal, ids);
                })
                .consumerMainThread(OpenTomePickerPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseTomePacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.mainHand);
                    buf.writeUtf(p.spellId, 64);
                })
                .decoder(buf -> new UseTomePacket(buf.readBoolean(), buf.readUtf(64)))
                .consumerMainThread(UseTomePacket::handle)
                .add();
        CHANNEL.messageBuilder(RevealDisguisePacket.class)
                .encoder((p, buf) -> {
                    buf.writeUUID(p.playerId);
                    buf.writeVarInt(p.ticks);
                })
                .decoder(buf -> new RevealDisguisePacket(buf.readUUID(), buf.readVarInt()))
                .consumerMainThread(RevealDisguisePacket::handle)
                .add();
    }

    public static void syncRelics(ServerPlayer player) {
        net.minecraftforge.items.ItemStackHandler relics = PlayerMagicData.getRelicHandler(player);
        CHANNEL.send(new SyncRelicsPacket(relics.getStackInSlot(0), relics.getStackInSlot(1)),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
    }

    public static void syncCloakTransition(ServerPlayer player, boolean hiding) {
        CHANNEL.send(new CloakTransitionPacket(player.getUUID(), hiding),
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
    }

    public static boolean openFlooSelection(ServerPlayer player, FlooFireplaceBlockEntity fireplace) {
        if (fireplace == null || !fireplace.isNamed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.needs_name"), true);
            return false;
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel flooLevel) {
            // A fireplace you have stood at is one a Master can Apparate back to.
            com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, flooLevel,
                    fireplace.getBlockPos(), fireplace.getFireplaceName());
        }
        List<FlooDestinationData> destinations = new ArrayList<>();
        for (BlockPos linkedPos : fireplace.getLinkedPositions()) {
            if (!player.level().isLoaded(linkedPos)) {
                continue;
            }
            if (player.level().getBlockEntity(linkedPos) instanceof FlooFireplaceBlockEntity linked && linked.isNamed()) {
                destinations.add(new FlooDestinationData(linkedPos, linked.getFireplaceName()));
            }
        }
        if (destinations.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.no_destinations"), true);
            return false;
        }
        CHANNEL.send(new OpenFlooScreenPacket(fireplace.getBlockPos(), destinations),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
        return true;
    }

    private static String writeItemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ItemStack readItemStack(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                    .map(ItemStack::new)
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static void enqueueClientWork(CustomPayloadEvent.Context ctx, Runnable work) {
        ctx.enqueueWork(work);
        ctx.setPacketHandled(true);
    }

    private static final int MAX_APPARITION_MAP_WIRE_ENTRIES = 64;

    private static int clampDecodedSize(int rawSize, int maxSize) {
        if (rawSize <= 0) {
            return 0;
        }
        return Math.min(rawSize, maxSize);
    }

    public record BroomBoostPacket(boolean isBoosting) {
        public static void handle(BroomBoostPacket p, CustomPayloadEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer player = ctx.getSender();
                if (player != null && !com.hogwartsmagic.spell.mind.MindControl.isControlled(player) && player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                    broom.isBoosting = p.isBoosting;
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record BroomTrickPacket(byte trickType) {
        public static void handle(BroomTrickPacket p, CustomPayloadEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                net.minecraft.server.level.ServerPlayer player = ctx.getSender();
                if (player != null && !com.hogwartsmagic.spell.mind.MindControl.isControlled(player) && player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                    broom.tryStartTrick(p.trickType);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record HouseRevealPacket(HogwartsHouse house) {
        public static void handle(HouseRevealPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("showHouseReveal", p));
        }
    }

    public record SyncPolyjuiceDisguisePacket(UUID playerId, CompoundTag disguise) {
        public static void handle(SyncPolyjuiceDisguisePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncPolyjuiceDisguise", p));
        }
    }

    public record SyncManaPacket(float mana, float maxMana) {
        public static void handle(SyncManaPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncMana", p));
        }
    }

    public record SyncRelicsPacket(ItemStack capacityRelic, ItemStack regenRelic) {
        public static void handle(SyncRelicsPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncRelics", p));
        }
    }

    public record EquipRelicPacket(int inventorySlot) {
        public static void handle(EquipRelicPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null && com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a controlled player acts through no mod packet
            }
            if (player == null || p.inventorySlot < 0 || p.inventorySlot >= player.getInventory().getContainerSize()) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(p.inventorySlot);
            com.hogwartsmagic.relic.RelicType type = com.hogwartsmagic.relic.RelicItem.getRelicType(stack);
            if (type == null) {
                return;
            }

            net.minecraftforge.items.ItemStackHandler relics = PlayerMagicData.getRelicHandler(player);
            int slotIndex = type.getSlotType().getIndex();
            ItemStack previous = relics.getStackInSlot(slotIndex);
            ItemStack equipped = stack.copyWithCount(1);
            relics.setStackInSlot(slotIndex, equipped);
            player.getInventory().removeItem(p.inventorySlot, 1);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                player.drop(previous, false);
            }

            PlayerMagicData.saveRelicHandler(player, relics);
            float maxMana = PlayerMagicData.getMaxMana(player);
            CompoundTag modData = PlayerMagicData.getModData(player);
            float currentMana = Math.min(modData.contains("Mana") ? modData.getFloat("Mana") : maxMana, maxMana);
            modData.putFloat("Mana", currentMana);
            modData.putFloat("MaxMana", maxMana);
            PlayerMagicData.saveModData(player, modData);
            CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
            syncRelics(player);
        }
    }

    public record UnequipRelicPacket(int slotIndex) {
        public static void handle(UnequipRelicPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null && com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a controlled player acts through no mod packet
            }
            if (player == null || p.slotIndex < 0 || p.slotIndex >= PlayerMagicData.RELIC_SLOT_COUNT) {
                return;
            }
            net.minecraftforge.items.ItemStackHandler relics = PlayerMagicData.getRelicHandler(player);
            ItemStack equipped = relics.getStackInSlot(p.slotIndex);
            if (equipped.isEmpty()) {
                return;
            }
            relics.setStackInSlot(p.slotIndex, ItemStack.EMPTY);
            if (!player.getInventory().add(equipped.copy())) {
                player.drop(equipped.copy(), false);
            }
            PlayerMagicData.saveRelicHandler(player, relics);
            float maxMana = PlayerMagicData.getMaxMana(player);
            CompoundTag modData = PlayerMagicData.getModData(player);
            float currentMana = Math.min(modData.contains("Mana") ? modData.getFloat("Mana") : maxMana, maxMana);
            modData.putFloat("Mana", currentMana);
            modData.putFloat("MaxMana", maxMana);
            PlayerMagicData.saveModData(player, modData);
            CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
            syncRelics(player);
        }
    }

    public record SyncLearnedSpellsPacket(String learnedSpells) {
        public static void handle(SyncLearnedSpellsPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncLearnedSpells", p));
        }
    }

    public record SyncSpellMasteryPacket(net.minecraft.nbt.CompoundTag mastery) {
        public static void handle(SyncSpellMasteryPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncSpellMastery", p));
        }
    }

    /** Server to client: the server's mastery numbers, so client-side ranks and costs match the server. */
    public record SyncMasteryConfigPacket(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap) {
        public static void handle(SyncMasteryConfigPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                // Server-to-client only: ignore anything a client sends (e.g. a LAN guest).
                ctx.setPacketHandled(true);
                return;
            }
            // MasteryConfig is common code with no client imports, so no bridge hop is needed.
            enqueueClientWork(ctx, () -> com.hogwartsmagic.spell.mastery.MasteryConfig.acceptSynced(
                    new com.hogwartsmagic.spell.mastery.MasteryConfig.Values(
                            p.adept, p.expert, p.master, p.rankDiscount, p.manaPerRank, p.manaCap)));
        }
    }

    public record SelectSpellPacket(String spellId) {
        public static void handle(SelectSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) return;
            ItemStack wand = player.getMainHandItem();
            if (MagicWandItem.isAnyWand(wand)) {
                SpellType spell = SpellType.fromId(p.spellId);
                MagicWandItem.setSelectedSpell(wand, spell);
            }
        }
    }

    public record SetSpellSlotPacket(int slotIndex, String spellId) {
        public static void handle(SetSpellSlotPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a controlled player acts through no mod packet
            }
            if (p.slotIndex < 0 || p.slotIndex >= MagicWandItem.SPELL_SLOT_COUNT) {
                return;
            }
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand)) {
                return;
            }

            SpellType spell = null;
            if (p.spellId != null && !p.spellId.isBlank()) {
                spell = SpellType.fromId(p.spellId);
                if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                    return;
                }
            }

            MagicWandItem.setSpellSlot(wand, p.slotIndex, spell);
        }
    }

    public record CastSpellPacket(boolean selfCastProtego, boolean releaseChannel, boolean channelHeartbeat, boolean fiendfyreAdvance) {
        private static final String TAG_PROTEGO_MAXIMA_CHANNELING = "hogwartsmagic:protego_maxima_channeling";
        private static final String TAG_PROTEGO_MAXIMA_LAST_DRAIN = "hogwartsmagic:protego_maxima_last_drain";
        private static final String TAG_PROTEGO_MAXIMA_LAST_SEEN = "hogwartsmagic:protego_maxima_last_seen";
        private static final int PROTEGO_MAXIMA_UPKEEP_INTERVAL = 10;
        private static final String TAG_FIENDFYRE_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
        private static final String TAG_FIENDFYRE_LAST_DRAIN = "hogwartsmagic:fiendfyre_last_drain";
        private static final String TAG_FIENDFYRE_LAST_SEEN = "hogwartsmagic:fiendfyre_last_seen";
        private static final String TAG_FIENDFYRE_ENTITY = "hogwartsmagic:fiendfyre_entity";
        private static final String TAG_FIENDFYRE_ADVANCE = "hogwartsmagic:fiendfyre_advance";
        private static final String TAG_PATRONUS_SHIELD_CHANNELING = com.hogwartsmagic.entity.DementorEntity.TAG_PATRONUS_SHIELD_CHANNELING;
        private static final String TAG_PATRONUS_SHIELD_LAST_DRAIN = "hogwartsmagic:patronus_shield_last_drain";
        private static final String TAG_PATRONUS_SHIELD_LAST_SEEN = "hogwartsmagic:patronus_shield_last_seen";
        /**
         * Game time a cast last resolved into a corporeal stag. Heartbeats keep refreshing it
         * while the key is held, so the hold can't re-roll; it lapses on its own once released,
         * which means a missed release packet can never wedge the spell shut.
         */
        private static final String TAG_PATRONUS_CAST_SPENT = "hogwartsmagic:patronus_cast_spent_at";
        private static final long PATRONUS_CAST_SPENT_GRACE = 10L;
        /** How often the charm answers in full rather than as a bare shield of light. */
        private static final float CORPOREAL_PATRONUS_CHANCE = 0.15f;
        private static final String TAG_WINGARDIUM_CHANNELING = "hogwartsmagic:wingardium_channeling";
        private static final String TAG_WINGARDIUM_TARGET = "hogwartsmagic:wingardium_target";
        private static final String TAG_WINGARDIUM_LAST_SEEN = "hogwartsmagic:wingardium_last_seen";
        private static final String TAG_WINGARDIUM_LAST_DRAIN = "hogwartsmagic:wingardium_last_drain";

        public static void handle(CastSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // A controlled player's own cast keys do nothing; casts made on their behalf call castAs directly.
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) return;
            castAs(player, p);
        }

        /**
         * The whole cast path, run as {@code player}: learned spell, stun and silence gates, cooldown, mana,
         * then the spell. The packet handler and (Phase 6b) puppet casts come through here, so every cast is
         * checked the same way.
         */
        public static void castAs(ServerPlayer player, CastSpellPacket p) {
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand)) return;
            boolean creative = player.isCreative();

            // Determine selected spell and check per-spell cooldown stored on the wand itself
            SpellType spell = MagicWandItem.getSelectedSpell(wand);

            if (spell == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.no_spell_selected"), true);
                return;
            }

            // Check if spell is learned
            if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.spell_not_learned"), true);
                return;
            }

            // The client's self-cast flag is only honoured for spells that are legitimately self-castable;
            // otherwise a modified client could fire any learned spell (Bombarda...) at its own caster.
            if (p.selfCastProtego && !com.hogwartsmagic.event.ModEvents.isLeftClickSelfCast(spell)) {
                return;
            }

            // Stunned or silenced: no casts, no channel starts and no heartbeats (a running channel
            // lapses on its own timeout). Releasing a channel is always allowed.
            if (!p.releaseChannel && com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                if (!p.channelHeartbeat) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                }
                return;
            }

            // Apparition is its own channel: it opens a destination minimap rather than casting immediately.
            if (spell == SpellType.APPARITION && !p.selfCastProtego && !p.releaseChannel && !p.channelHeartbeat) {
                com.hogwartsmagic.event.ModEvents.startApparitionChannel(player, wand);
                return;
            }

            long now = player.level().getGameTime();
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();

            if (p.releaseChannel) {
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
                    stopProtegoMaximaChannel(player, wand, now, creative);
                }
                if (playerData.getBoolean(TAG_FIENDFYRE_CHANNELING)) {
                    stopFiendfyreChannel(player, wand, now, creative);
                }
                if (playerData.getBoolean(TAG_PATRONUS_SHIELD_CHANNELING)) {
                    com.hogwartsmagic.event.ModEvents.stopPatronusShieldChannel(player, wand);
                }
                if (playerData.getBoolean(TAG_WINGARDIUM_CHANNELING)) {
                    com.hogwartsmagic.event.ModEvents.stopWingardiumChannel(player, wand);
                }
                playerData.remove(TAG_FIENDFYRE_ADVANCE);
                playerData.remove(TAG_PATRONUS_CAST_SPENT);
                return;
            }

            boolean shouldChannelProtegoMaxima = spell == SpellType.PROTEGO_MAXIMA && player.getXRot() <= -55.0F;
            if (p.channelHeartbeat && shouldChannelProtegoMaxima) {
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
                    playerData.putLong(TAG_PROTEGO_MAXIMA_LAST_SEEN, now);
                    return;
                }
                float upkeepCost = getProtegoMaximaUpkeepCost(player, wand);
                net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
                if (!creative && currentMana < upkeepCost) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }

                playerData.putBoolean(TAG_PROTEGO_MAXIMA_CHANNELING, true);
                playerData.putLong(TAG_PROTEGO_MAXIMA_LAST_DRAIN, now - PROTEGO_MAXIMA_UPKEEP_INTERVAL);
                playerData.putLong(TAG_PROTEGO_MAXIMA_LAST_SEEN, now);
                playerData.putFloat("hogwartsmagic:protego_maxima_health", 100.0f);
                playerData.putLong("hogwartsmagic:protego_maxima_last_hit", now);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.92f);
                return;
            }

            if (p.channelHeartbeat && spell == SpellType.FIENDFYRE) {
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

                net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                float maxMana = PlayerMagicData.getMaxMana(player);
                float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
                float startupCost = getFiendfyreStartupCost(player, wand);

                if (playerData.getBoolean(TAG_FIENDFYRE_CHANNELING)) {
                    playerData.putLong(TAG_FIENDFYRE_LAST_SEEN, now);
                    playerData.putBoolean(TAG_FIENDFYRE_ADVANCE, p.fiendfyreAdvance);
                    ensureFiendfyreExists(player);
                    return;
                }

                if (!creative && currentMana < startupCost) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }

                if (!creative) {
                    currentMana -= startupCost;
                    modData.putFloat("Mana", currentMana);
                    player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
                    CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
                }

                playerData.putBoolean(TAG_FIENDFYRE_CHANNELING, true);
                playerData.putLong(TAG_FIENDFYRE_LAST_DRAIN, now);
                playerData.putLong(TAG_FIENDFYRE_LAST_SEEN, now);
                playerData.putBoolean(TAG_FIENDFYRE_ADVANCE, p.fiendfyreAdvance);
                ensureFiendfyreExists(player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.86f);
                return;
            }

            if (p.channelHeartbeat && spell == SpellType.WINGARDIUM_LEVIOSA) {
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

                net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                float maxMana = PlayerMagicData.getMaxMana(player);
                float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;

                if (playerData.getBoolean(TAG_WINGARDIUM_CHANNELING)) {
                    playerData.putLong(TAG_WINGARDIUM_LAST_SEEN, now);
                    return;
                }

                Entity target = com.hogwartsmagic.event.ModEvents.consumeRecentWingardiumTarget(player);
                if (target == null) {
                    return;
                }

                float startupCost = com.hogwartsmagic.event.ModEvents.getWingardiumStartCost(player, wand);
                if (!creative && currentMana < startupCost) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }

                if (!creative) {
                    currentMana -= startupCost;
                    modData.putFloat("Mana", currentMana);
                    player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
                    CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
                    MagicWandItem.rollManaRefund(player, wand, Math.max(1, Math.round(startupCost)));
                }

                playerData.putBoolean(TAG_WINGARDIUM_CHANNELING, true);
                playerData.putUUID(TAG_WINGARDIUM_TARGET, target.getUUID());
                playerData.putLong(TAG_WINGARDIUM_LAST_SEEN, now);
                playerData.putLong(TAG_WINGARDIUM_LAST_DRAIN, now);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.08f);
                return;
            }

            if (p.channelHeartbeat && spell == SpellType.EXPECTO_PATRONUM) {
                // Already holding the shield up: just keep it alive.
                if (playerData.getBoolean(TAG_PATRONUS_SHIELD_CHANNELING)) {
                    playerData.putLong(TAG_PATRONUS_SHIELD_LAST_SEEN, now);
                    return;
                }
                // A stag already answered this cast; it hunts on its own and ignores the held key.
                if (now - playerData.getLong(TAG_PATRONUS_CAST_SPENT) <= PATRONUS_CAST_SPENT_GRACE) {
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    return;
                }
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

                net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                float maxMana = PlayerMagicData.getMaxMana(player);
                float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;

                if (player.getRandom().nextFloat() < CORPOREAL_PATRONUS_CHANCE) {
                    int fullCost = SpellCosts.manaCost(player, wand, spell);
                    if (!creative && currentMana < fullCost) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                        return;
                    }
                    if (!creative) {
                        currentMana -= fullCost;
                        modData.putFloat("Mana", currentMana);
                        playerData.getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
                        CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
                        MagicWandItem.rollManaRefund(player, wand, fullCost);
                    }
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    summonCorporealPatronus(player);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                    return;
                }

                float upkeepCost = com.hogwartsmagic.event.ModEvents.getPatronusShieldUpkeepCost(player, wand);
                if (!creative && currentMana < upkeepCost) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }

                startPatronusShieldChannel(player, now);
                return;
            }

            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)
                    && !(spell == SpellType.ASCENDIO && com.hogwartsmagic.spell.support.AscendioSpell.canDoubleCast(player))) return;

            // Reparo cast on yourself mends the off-hand item or worn armour (Expert and up). It
            // prices itself by the item, so it runs before the flat mana charge below.
            if (p.selfCastProtego && spell == SpellType.REPARO) {
                com.hogwartsmagic.spell.support.ReparoSpell.castOnSelf(player, wand, now);
                return;
            }

            // Avada Kedavra winds up first (Spell Mastery Phase 6). AvadaWindup re-enters castAs when the windup
            // completes, with the ready flag set, and only then is the mana taken and the beam fired.
            if (spell == SpellType.AVADA_KEDAVRA && !p.selfCastProtego
                    && !com.hogwartsmagic.spell.mind.AvadaWindup.consumeReady(player)) {
                com.hogwartsmagic.spell.mind.AvadaWindup.begin(player);
                return;
            }

            // Mana Check
            net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
            float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
            int cost = SpellCosts.manaCost(player, wand, spell);

            if (!creative && currentMana < cost) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }

            // Deduct mana only outside creative mode
            if (!creative) {
                currentMana -= cost;
            } else {
                currentMana = modData.contains("MaxMana") ? modData.getFloat("MaxMana") : PlayerMagicData.getMaxMana(player);
            }
            modData.putFloat("Mana", currentMana);
            player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
            
            // Sync new mana to client
            float currentMaxMana = PlayerMagicData.getMaxMana(player);
            CHANNEL.send(new SyncManaPacket(currentMana, currentMaxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
            if (!creative) {
                MagicWandItem.rollManaRefund(player, wand, cost);
            }

            if (p.selfCastProtego) {
                spell.applyHitEffect(player, player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
                
                // Set per-spell cooldown on the wand
                if (!creative) {
                    SpellCosts.startCooldown(player, wand, spell, now);
                }
                return;
            }

            if (spell == SpellType.LUMOS && !com.hogwartsmagic.spell.support.LumosRules.firesBeam(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell))) {
                // Novice and Adept: right-click toggles the wand light (Lumos / Nox). From Expert this
                // falls through to the beam below and the toggle moves to left-click (self-cast).
                com.hogwartsmagic.spell.support.LumosSpell.toggle(player, player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);

                // Set per-spell cooldown on the wand
                if (!creative) {
                    SpellCosts.startCooldown(player, wand, spell, now);
                }
                return;
            }

            if (spell == SpellType.ASCENDIO && com.hogwartsmagic.spell.support.AscendioSpell.wantsSelfCast(player)) {
                // Aimed down or crouch-cast: Ascendio launches you instead of firing a beam.
                boolean doubleCast = com.hogwartsmagic.spell.support.AscendioSpell.launchSelf(player, now);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
                if (!creative && !doubleCast) {
                    SpellCosts.startCooldown(player, wand, spell, now);
                }
                return;
            }

            if (spell == SpellType.TENEBRIS_FLIGHT) {
                // Tenebris Flight is self-cast only for players
                spell.applyHitEffect(player, player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);

                // Set per-spell cooldown on the wand
                // Set per-spell cooldown on the wand
                if (!creative) {
                    SpellCosts.startCooldown(player, wand, spell, now);
                }
                return;
            }

            if (spell == SpellType.EXPECTO_PATRONUM) {
                // Full mana was already taken above, so either outcome is paid for here.
                if (player.getRandom().nextFloat() < CORPOREAL_PATRONUS_CHANCE) {
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    summonCorporealPatronus(player);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                } else {
                    startPatronusShieldChannel(player, now);
                }
                return;
            }

            SpellBeamEntity beam = new SpellBeamEntity(player.level(), player, spell);
            float beamSpeed = 1.5f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
            if (spell == SpellType.AQUA_ERUCTO) {
                beamSpeed = 5.75f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
            }
            if (spell == SpellType.STUPEFY) {
                beamSpeed *= com.hogwartsmagic.spell.disable.StupefyRules.beamSpeedMultiplier(
                        com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell));
            }
            // Simple "charge tier": synced with the player's attack strength meter.
            float chargeTier = (0.6f + player.getAttackStrengthScale(0.0f)) * MagicWandItem.getSpellPowerMultiplier(wand);
            beam.setCharge(chargeTier);
            beam.setElderWandCast(wand.getItem() == ExampleMod.ELDER_WAND.get());
            if (spell == SpellType.ARRESTO_MOMENTUM || spell == SpellType.REVELIO) {
                Entity aimedEntity = spell == SpellType.REVELIO
                        ? findAimedInvisibleLivingTarget(player, 48.0D)
                        : findAimedLivingTarget(player, 48.0D);
                if (aimedEntity != null) {
                    beam.setHomingTarget(aimedEntity);
                }
            }
            beam.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, beamSpeed, 1);
            com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
            player.level().addFreshEntity(beam);
            // Play the spell sound so nearby players hear it
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);

            // Imperio's control phase applies its cooldown when the channel ends.
            if (!creative && spell != SpellType.IMPERIO && spell != SpellType.WINGARDIUM_LEVIOSA) {
                SpellCosts.startCooldown(player, wand, spell, now);
            }
        }

        private static float getProtegoMaximaUpkeepCost(ServerPlayer player, ItemStack wand) {
            return Math.max(0.75f, (SpellType.PROTEGO_MAXIMA.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                    * SpellCosts.masteryMultiplier(player, SpellType.PROTEGO_MAXIMA)) / 24.0f);
        }

        private static Entity findAimedLivingTarget(ServerPlayer player, double range) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(range));
            AABB searchBox = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.5D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    player.level(),
                    player,
                    start,
                    end,
                    searchBox,
                    candidate -> candidate instanceof LivingEntity && candidate.isPickable() && !candidate.isSpectator() && candidate != player
            );
            return hit != null ? hit.getEntity() : null;
        }

        private static Entity findAimedInvisibleLivingTarget(ServerPlayer player, double range) {
            Vec3 start = player.getEyePosition();
            Vec3 end = start.add(player.getLookAngle().scale(range));
            AABB searchBox = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(2.0D);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    player.level(),
                    player,
                    start,
                    end,
                    searchBox,
                    candidate -> candidate instanceof LivingEntity living
                            && candidate.isPickable()
                            && !candidate.isSpectator()
                            && candidate != player
                            && (living.isInvisible() || living.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY))
            );
            return hit != null ? hit.getEntity() : null;
        }

        /** The charm answering in full: a corporeal stag that hunts Dementors down. */
        private static void summonCorporealPatronus(ServerPlayer player) {
            com.hogwartsmagic.entity.PatronusEntity patronus = new com.hogwartsmagic.entity.PatronusEntity(
                    ModEntities.PATRONUS.get(), player.level());
            patronus.setPos(player.getX(), player.getY(), player.getZ());
            player.level().addFreshEntity(patronus);

            com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        /** The weaker half of the charm: a wisp of silver held between you and them. */
        private static void startPatronusShieldChannel(ServerPlayer player, long now) {
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
            playerData.putBoolean(TAG_PATRONUS_SHIELD_CHANNELING, true);
            playerData.putLong(TAG_PATRONUS_SHIELD_LAST_DRAIN, now);
            playerData.putLong(TAG_PATRONUS_SHIELD_LAST_SEEN, now);

            // Everyone who can see the caster needs to draw the light, not just the caster.
            CHANNEL.send(new PatronusShieldStatePacket(player.getId(), true),
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));

            com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.22f);
        }

        private static float getFiendfyreStartupCost(ServerPlayer player, ItemStack wand) {
            return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                    * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE) * 0.16f);
        }

        private static void ensureFiendfyreExists(ServerPlayer player) {
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
            if (playerData.hasUUID(TAG_FIENDFYRE_ENTITY)) {
                net.minecraft.world.entity.Entity existing = player.serverLevel().getEntity(playerData.getUUID(TAG_FIENDFYRE_ENTITY));
                if (existing instanceof FiendfyreSerpentEntity fiendfyre) {
                    fiendfyre.setCaster(player);
                    return;
                }
            }

            FiendfyreSerpentEntity fiendfyre = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), player.level());
            fiendfyre.setCaster(player);
            fiendfyre.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
            player.level().addFreshEntity(fiendfyre);
            playerData.putUUID(TAG_FIENDFYRE_ENTITY, fiendfyre.getUUID());
        }

        private static void stopFiendfyreChannel(ServerPlayer player, ItemStack wand, long now, boolean creative) {
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
            playerData.remove(TAG_FIENDFYRE_CHANNELING);
            playerData.remove(TAG_FIENDFYRE_LAST_DRAIN);
            playerData.remove(TAG_FIENDFYRE_LAST_SEEN);
            playerData.remove(TAG_FIENDFYRE_ADVANCE);
            if (playerData.hasUUID(TAG_FIENDFYRE_ENTITY)) {
                net.minecraft.world.entity.Entity existing = player.serverLevel().getEntity(playerData.getUUID(TAG_FIENDFYRE_ENTITY));
                if (existing instanceof FiendfyreSerpentEntity fiendfyre) {
                    fiendfyre.discard();
                }
            }
            playerData.remove(TAG_FIENDFYRE_ENTITY);

            if (!creative && MagicWandItem.isAnyWand(wand)) {
                SpellType spell = SpellType.FIENDFYRE;
                SpellCosts.startCooldown(player, wand, spell, now);
            }
        }

        private static void stopProtegoMaximaChannel(ServerPlayer player, ItemStack wand, long now, boolean creative) {
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
            playerData.remove(TAG_PROTEGO_MAXIMA_CHANNELING);
            playerData.remove(TAG_PROTEGO_MAXIMA_LAST_DRAIN);
            playerData.remove(TAG_PROTEGO_MAXIMA_LAST_SEEN);

            if (player.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                player.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            }

            if (!creative && MagicWandItem.isAnyWand(wand)) {
                SpellType spell = SpellType.PROTEGO_MAXIMA;
                SpellCosts.startCooldown(player, wand, spell, now);
            }
        }
    }

    public record ImperioControlPacket(boolean startChannel, boolean stopChannel, float forward, float strafe, boolean jump, boolean attack, float yaw, float pitch) {
        public static void handle(ImperioControlPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a thrall can't start or steer a channel of its own
            }
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand)) {
                return;
            }

            SpellType spell = MagicWandItem.getSelectedSpell(wand);
            if (spell != SpellType.IMPERIO) {
                if (p.stopChannel) {
                    com.hogwartsmagic.event.ModEvents.stopImperioChannel(player, wand);
                }
                return;
            }

            if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.spell_not_learned"), true);
                return;
            }

            long now = player.level().getGameTime();
            boolean creative = player.isCreative();
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();

            if (p.stopChannel) {
                com.hogwartsmagic.event.ModEvents.stopImperioChannel(player, wand);
                return;
            }

            // The channel was already dropped when the stun or silence landed (CombatEffects.onEffectAdded).
            if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                if (p.startChannel) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                }
                return;
            }

            com.hogwartsmagic.event.ModEvents.updateImperioInput(player, p.forward, p.strafe, p.jump, p.attack, p.yaw, p.pitch);

            if (!p.startChannel && playerData.getBoolean("hogwartsmagic:imperio_channeling")) {
                return;
            }

            if (!p.startChannel) {
                return;
            }

            LivingEntity target = com.hogwartsmagic.event.ModEvents.consumeRecentImperioHitTarget(player);
            if (target == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Hit a player or mob with Imperio, then keep holding").withStyle(net.minecraft.ChatFormatting.GREEN), true);
                return;
            }

            net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
            float maxMana = PlayerMagicData.getMaxMana(player);
            float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
            float upkeepCost = com.hogwartsmagic.event.ModEvents.getImperioStartCost(player, wand);

            if (!creative && currentMana < upkeepCost) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }

            playerData.putBoolean("hogwartsmagic:imperio_channeling", true);
            playerData.putUUID("hogwartsmagic:imperio_target", target.getUUID());
            playerData.putLong("hogwartsmagic:imperio_last_seen", now);
            playerData.putLong("hogwartsmagic:imperio_last_drain", now);
            playerData.putLong("hogwartsmagic:imperio_last_attack", now - 10L);

            if (target instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(null);
            }
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 80, 0));
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 80, 0));

            com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.95f);
        }
    }

    public record FlooDestinationData(BlockPos pos, String name) {
    }

    public record OpenFlooScreenPacket(BlockPos sourcePos, List<FlooDestinationData> destinations) {
        public static void handle(OpenFlooScreenPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openFlooScreen", p));
        }
    }

    public record ChooseFlooDestinationPacket(BlockPos sourcePos, BlockPos destinationPos) {
        public static void handle(ChooseFlooDestinationPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a controlled player acts through no mod packet
            }
            if (com.hogwartsmagic.spell.combat.CombatEffects.isStunned(player)) {
                com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                return;
            }
            if (!(player.level().getBlockEntity(p.sourcePos) instanceof FlooFireplaceBlockEntity source)
                    || !(player.level().getBlockEntity(p.destinationPos) instanceof FlooFireplaceBlockEntity destination)) {
                return;
            }
            if (!source.getLinkedPositions().contains(p.destinationPos)) {
                return;
            }
            if (!player.getInventory().contains(new ItemStack(ExampleMod.FLOO_POWDER.get()))) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.no_powder"), true);
                return;
            }
            CompoundTag modData = PlayerMagicData.getModData(player);
            if (modData.getBoolean("FlooTravelPending")) {
                return;
            }
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        player.getX(), player.getY() + 0.6, player.getZ(), 45, 0.45, 0.65, 0.45, 0.02);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        player.getX(), player.getY() + 0.4, player.getZ(), 20, 0.25, 0.4, 0.25, 0.01);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_ON,
                        player.getX(), player.getY() + 0.8, player.getZ(), 20, 0.3, 0.55, 0.3, 0.02);
            }
            source.ignite(player.serverLevel());
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 22, 0, false, false, false));
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 50, 0, false, false, false));
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20, 3, false, false, false));
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.hogwartsmagic.ModSounds.FLOO_DEPART.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ExampleMod.FLOO_POWDER.get()), 1, player.inventoryMenu.getCraftSlots());
            modData.putBoolean("FlooTravelPending", true);
            modData.putLong("FlooSourcePos", p.sourcePos.asLong());
            modData.putLong("FlooDestinationPos", p.destinationPos.asLong());
            modData.putInt("FlooTravelTicks", 18);
            PlayerMagicData.saveModData(player, modData);
            destination.ignite(player.serverLevel());
        }
    }

    public record OpenFlooNamingScreenPacket(BlockPos fireplacePos, String currentName) {
        public static void handle(OpenFlooNamingScreenPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openFlooNamingScreen", p));
        }
    }

    public record OpenHogwartsInviteScreenPacket(String recipient) {
        public static void handle(OpenHogwartsInviteScreenPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openHogwartsInviteScreen", p));
        }
    }

    public record OpenHogwartsGuideScreenPacket() {
        public static void handle(OpenHogwartsGuideScreenPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openHogwartsGuideScreen", p));
        }
    }

    public record SetFlooNamePacket(BlockPos fireplacePos, String name) {
        public static void handle(SetFlooNamePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null && com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a controlled player acts through no mod packet
            }
            if (player == null || p.name == null || p.name.isBlank()) {
                return;
            }
            if (player.level().getBlockEntity(p.fireplacePos) instanceof FlooFireplaceBlockEntity fireplace) {
                fireplace.setFireplaceName(p.name.trim(), player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.named", fireplace.getFireplaceName()), true);
            }
        }
    }

    public record CaveInimicumRegionSyncPacket(BlockPos minPos, int edgeLength, int[] stateIds) {
        public static void handle(CaveInimicumRegionSyncPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncCaveInimicumRegion", p));
        }
    }

    public record CaveInimicumBlockDeltaPacket(BlockPos pos, int stateId) {
        public static void handle(CaveInimicumBlockDeltaPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncCaveInimicumBlockDelta", p));
        }
    }

    public record CaveInimicumActiveWardData(BlockPos center, int edgeLength) {
    }

    /**
     * Which undercroft the client is standing in, so the fog can be keyed on the region rather than
     * on a biome. A radius of zero means "you have left one".
     *
     * <p>The Y values are varints and the corridor bottoms out below zero, so those two cost the
     * full five bytes each. That is fine and deliberately not optimised: this is one packet when
     * you arrive and one when you leave, not a stream.
     */
    public record UndercroftFogPacket(BlockPos centre, int radius, int topY, int bottomY) {
        public static void handle(UndercroftFogPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge
                    .handlePacket("undercroftFog", p));
        }
    }

    public record CaveInimicumActiveWardsPacket(List<CaveInimicumActiveWardData> wards) {
        public static void handle(CaveInimicumActiveWardsPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncActiveWards", p));
        }
    }

    public record StartHorcruxDeathSequencePacket(int soulEntityId, Vec3 startPos, BlockPos targetPos) {
        public static void handle(StartHorcruxDeathSequencePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("startHorcruxDeathSequence", p));
        }
    }

    public record FinishHorcruxDeathSequencePacket() {
        public static void handle(FinishHorcruxDeathSequencePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("finishHorcruxDeathSequence", p));
        }
    }

    /** Server → client: {@code casterId} raised or dropped the Patronus shield. */
    public record PatronusShieldStatePacket(int casterId, boolean active) {
        public static void handle(PatronusShieldStatePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("patronusShieldState", p));
        }
    }

    public record CloakTransitionPacket(UUID playerId, boolean hiding) {
        public static void handle(CloakTransitionPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("cloakTransition", p));
        }
    }

    /** Server → client: open the Apparition destination minimap, centered on {@code origin}. */
    public record OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks, List<FlooDestinationData> fireplaces) {
        public static void handle(OpenApparitionMapPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openApparitionMap", p));
        }
    }

    /** Server → client: force-close the Apparition map (channel ended on the server). */
    public record CloseApparitionMapPacket() {
        public static void handle(CloseApparitionMapPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("closeApparitionMap", p));
        }
    }

    /** Client → server: the player clicked a destination on the map. Coordinates are re-validated server-side. */
    public record ChooseApparitionDestinationPacket(int targetX, int targetZ) {
        public static void handle(ChooseApparitionDestinationPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                com.hogwartsmagic.event.ModEvents.completeApparition(player, p.targetX, p.targetZ);
            }
        }
    }

    /** Client to server: a Master picked a known fireplace on the map. Re-validated against the server's own list. */
    public record ChooseApparitionFireplacePacket(BlockPos fireplacePos) {
        public static void handle(ChooseApparitionFireplacePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                com.hogwartsmagic.event.ModEvents.completeApparitionToFireplace(player, p.fireplacePos);
            }
        }
    }

    /** Client → server: the player closed the map without choosing — cancel the channel now (full cooldown). */
    public record CancelApparitionPacket() {
        public static void handle(CancelApparitionPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
            }
        }
    }

    /** Server to client: Revelio at Master shows {@code playerId} undisguised for {@code ticks}. */
    public record RevealDisguisePacket(UUID playerId, int ticks) {
        public static void handle(RevealDisguisePacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                // Server-to-client only: ignore anything a client sends.
                ctx.setPacketHandled(true);
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("revealDisguise", p));
        }
    }

    /** Server to client: open the Tome of Mastery picker with the spells the server says are eligible. */
    public record OpenTomePickerPacket(boolean mainHand, int tierOrdinal, List<String> spellIds) {
        public static void handle(OpenTomePickerPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openTomePicker", p));
        }
    }

    /** Client to server: the player picked a spell in the tome picker. Fully re-validated in TomeOfMasteryItem.tryRaise. */
    public record UseTomePacket(boolean mainHand, String spellId) {
        public static void handle(UseTomePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                    return;
                }
                if (com.hogwartsmagic.spell.combat.CombatEffects.isStunned(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    return;
                }
                com.hogwartsmagic.item.TomeOfMasteryItem.tryRaise(player, p.mainHand, p.spellId);
            }
        }
    }
}
