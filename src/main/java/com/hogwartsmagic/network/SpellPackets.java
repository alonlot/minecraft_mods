package com.hogwartsmagic.network;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
            .networkProtocolVersion(1)
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
        CHANNEL.messageBuilder(CloakTransitionPacket.class)
                .encoder((p, buf) -> {
                    buf.writeUUID(p.playerId);
                    buf.writeBoolean(p.hiding);
                })
                .decoder(buf -> new CloakTransitionPacket(buf.readUUID(), buf.readBoolean()))
                .consumerMainThread(CloakTransitionPacket::handle)
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
                if (player != null && player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
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
                if (player != null && player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
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

    public record SelectSpellPacket(String spellId) {
        public static void handle(SelectSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
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
        private static final String TAG_WINGARDIUM_CHANNELING = "hogwartsmagic:wingardium_channeling";
        private static final String TAG_WINGARDIUM_TARGET = "hogwartsmagic:wingardium_target";
        private static final String TAG_WINGARDIUM_LAST_SEEN = "hogwartsmagic:wingardium_last_seen";
        private static final String TAG_WINGARDIUM_LAST_DRAIN = "hogwartsmagic:wingardium_last_drain";

        public static void handle(CastSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
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

            long now = player.level().getGameTime();
            net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();

            if (p.releaseChannel) {
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
                    stopProtegoMaximaChannel(player, wand, now, creative);
                }
                if (playerData.getBoolean(TAG_FIENDFYRE_CHANNELING)) {
                    stopFiendfyreChannel(player, wand, now, creative);
                }
                if (playerData.getBoolean(TAG_WINGARDIUM_CHANNELING)) {
                    com.hogwartsmagic.event.ModEvents.stopWingardiumChannel(player, wand);
                }
                playerData.remove(TAG_FIENDFYRE_ADVANCE);
                return;
            }

            boolean shouldChannelProtegoMaxima = spell == SpellType.PROTEGO_MAXIMA && player.getXRot() <= -55.0F;
            if (p.channelHeartbeat && shouldChannelProtegoMaxima) {
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
                    playerData.putLong(TAG_PROTEGO_MAXIMA_LAST_SEEN, now);
                    return;
                }
                float upkeepCost = getProtegoMaximaUpkeepCost(wand);
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
                float startupCost = getFiendfyreStartupCost(wand);

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

                float startupCost = com.hogwartsmagic.event.ModEvents.getWingardiumStartCost(wand);
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

            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

            // Mana Check
            net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
            float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
            int cost = Math.max(1, Math.round(spell.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)));

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

            long cooldownTicks = Math.max(1L, Math.round(
                    spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
            ));

            if (p.selfCastProtego) {
                spell.applyHitEffect(player, player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
                
                // Set per-spell cooldown on the wand
                if (!creative) {
                    MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
                }
                return;
            }

            if (spell == SpellType.LUMOS) {
                // Toggle Lumos
                if (player.hasEffect(ExampleMod.LUMOS_EFFECT.getHolder().get())) {
                    player.removeEffect(ExampleMod.LUMOS_EFFECT.getHolder().get());
                } else {
                    player.addEffect(new MobEffectInstance(ExampleMod.LUMOS_EFFECT.getHolder().get(), 12000, 0, false, false, true));
                }
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);

                // Set per-spell cooldown on the wand
                if (!creative) {
                    MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
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
                    MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
                }
                return;
            }

            if (spell == SpellType.EXPECTO_PATRONUM) {
                com.hogwartsmagic.entity.PatronusEntity patronus = new com.hogwartsmagic.entity.PatronusEntity(com.hogwartsmagic.entity.ModEntities.PATRONUS.get(), player.level());
                patronus.setPos(player.getX(), player.getY(), player.getZ());
                player.level().addFreshEntity(patronus);
                
                // Play cast sound; Patronus handles ongoing light pulses and dementor repulsion.
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);

                if (!creative) {
                    MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
                }
                return;
            }

            SpellBeamEntity beam = new SpellBeamEntity(player.level(), player, spell);
            float beamSpeed = 1.5f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
            if (spell == SpellType.AQUA_ERUCTO) {
                beamSpeed = 5.75f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
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
                MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
            }
        }

        private static float getProtegoMaximaUpkeepCost(ItemStack wand) {
            return Math.max(0.75f, (SpellType.PROTEGO_MAXIMA.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)) / 24.0f);
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

        private static float getFiendfyreStartupCost(ItemStack wand) {
            return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand) * 0.16f);
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
                long cooldownTicks = Math.max(1L, Math.round(
                        spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
                ));
                MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
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
                long cooldownTicks = Math.max(1L, Math.round(
                        spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)
                ));
                MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks);
            }
        }
    }

    public record ImperioControlPacket(boolean startChannel, boolean stopChannel, float forward, float strafe, boolean jump, boolean attack, float yaw, float pitch) {
        public static void handle(ImperioControlPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
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
            float upkeepCost = com.hogwartsmagic.event.ModEvents.getImperioStartCost(wand);

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
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.7f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.4f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f);
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

    public record CloakTransitionPacket(UUID playerId, boolean hiding) {
        public static void handle(CloakTransitionPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("cloakTransition", p));
        }
    }
}
