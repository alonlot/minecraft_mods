package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ClientRuntimeBridge {
    private ClientRuntimeBridge() {
    }

    public static void handlePacket(String action, Object payload) {
        switch (action) {
            case "sortingQuestion" -> com.hogwartsmagic.client.sorting.SortingQuizScreen.openQuestion((SpellPackets.SortingQuestionPacket) payload);
            case "sortingTie" -> com.hogwartsmagic.client.sorting.SortingQuizScreen.openTie((SpellPackets.SortingTiePacket) payload);
            case "sortingClose" -> com.hogwartsmagic.client.sorting.SortingQuizScreen.closeFromServer();
            case "sortingVoice" -> com.hogwartsmagic.client.sorting.HatVoice.play(((SpellPackets.SortingVoicePacket) payload).voice());
            case "showHouseReveal" -> showHouseReveal((SpellPackets.HouseRevealPacket) payload);
            case "syncPolyjuiceDisguise" -> syncPolyjuiceDisguise((SpellPackets.SyncPolyjuiceDisguisePacket) payload);
            case "syncMana" -> syncMana((SpellPackets.SyncManaPacket) payload);
            case "syncRelics" -> syncRelics((SpellPackets.SyncRelicsPacket) payload);
            case "syncLearnedSpells" -> syncLearnedSpells((SpellPackets.SyncLearnedSpellsPacket) payload);
            case "syncSpellMastery" -> syncSpellMastery((SpellPackets.SyncSpellMasteryPacket) payload);
            case "syncDarkMagic" -> DarkMagicClientState.accept(((SpellPackets.SyncDarkMagicPacket) payload).darkness());
            case "openFlooScreen" -> openFlooScreen((SpellPackets.OpenFlooScreenPacket) payload);
            case "openFlooNamingScreen" -> openFlooNamingScreen((SpellPackets.OpenFlooNamingScreenPacket) payload);
            case "openHogwartsInviteScreen" -> openHogwartsInviteScreen((SpellPackets.OpenHogwartsInviteScreenPacket) payload);
            case "openHogwartsGuideScreen" -> openHogwartsGuideScreen();
            case "openAdvancedPotionsScreen" -> openAdvancedPotionsScreen();
            case "syncCaveInimicumRegion" -> syncCaveInimicumRegion((SpellPackets.CaveInimicumRegionSyncPacket) payload);
            case "syncCaveInimicumBlockDelta" -> syncCaveInimicumBlockDelta((SpellPackets.CaveInimicumBlockDeltaPacket) payload);
            case "syncActiveWards" -> syncActiveWards((SpellPackets.CaveInimicumActiveWardsPacket) payload);
            case "undercroftFog" -> undercroftFog((SpellPackets.UndercroftFogPacket) payload);
            case "startHorcruxDeathSequence" -> startHorcruxDeathSequence((SpellPackets.StartHorcruxDeathSequencePacket) payload);
            case "finishHorcruxDeathSequence" -> finishHorcruxDeathSequence();
            case "cloakTransition" -> cloakTransition((SpellPackets.CloakTransitionPacket) payload);
            case "patronusShieldState" -> patronusShieldState((SpellPackets.PatronusShieldStatePacket) payload);
            case "avadaCharge" -> AvadaChargeClientState.accept((SpellPackets.AvadaChargePacket) payload);
            case "beamImpact" -> SpellBeamClientState.acceptImpact((SpellPackets.BeamImpactPacket) payload);
            case "beamClash" -> SpellBeamClientState.acceptClash((SpellPackets.BeamClashPacket) payload);
            case "castMotion" -> CastMotions.accept((SpellPackets.CastMotionPacket) payload);
            case "hitReaction" -> HitReactions.accept((SpellPackets.HitReactionPacket) payload);
            case "prioriLock" -> PrioriClient.accept((SpellPackets.PrioriLockPacket) payload);
            case "travelFx" -> TravelFxClient.accept((SpellPackets.TravelFxPacket) payload);
            case "musicSting" -> MusicStingClient.accept((SpellPackets.MusicStingPacket) payload);
            case "crucioChannel" -> CrucioClient.accept((SpellPackets.CrucioChannelPacket) payload);
            case "aquaStream" -> AquaStreamClient.accept((SpellPackets.AquaStreamPacket) payload);
            case "mandrakePull" -> MandrakePullClient.accept((SpellPackets.MandrakePullPacket) payload);
            case "protegoShield" -> protegoShield((SpellPackets.ProtegoShieldPacket) payload);
            case "protegoMaximaImpact" -> protegoMaximaImpact((SpellPackets.ProtegoMaximaImpactPacket) payload);
            case "incarcerousRopes" -> IncarcerousRopesClientState.accept((SpellPackets.IncarcerousRopesPacket) payload);
            case "openApparitionMap" -> openApparitionMap((SpellPackets.OpenApparitionMapPacket) payload);
            case "closeApparitionMap" -> closeApparitionMap();
            case "openTomePicker" -> openTomePicker((SpellPackets.OpenTomePickerPacket) payload);
            case "revealDisguise" -> revealDisguise((SpellPackets.RevealDisguisePacket) payload);
            case "thrallLabel" -> ImperiusClient.acceptLabel((SpellPackets.ThrallLabelPacket) payload);
            case "possessionState" -> PossessionClient.acceptState((SpellPackets.PossessionStatePacket) payload);
            case "puppetInput" -> PossessionClient.acceptPuppetInput((SpellPackets.PuppetInputPacket) payload);
            case "puppetSnapshot" -> PossessionClient.acceptSnapshot((SpellPackets.PuppetSnapshotPacket) payload);
            case "lockPickState" -> LockPickClient.accept((SpellPackets.LockPickStatePacket) payload);
            case "openAssignment" -> net.minecraft.client.Minecraft.getInstance().setScreen(
                    new AssignmentScreen((com.hogwartsmagic.assignment.OpenAssignmentPacket) payload));
            case "pensieveEnter" -> com.hogwartsmagic.client.pensieve.PensieveClientState.onEnter(
                    ((com.hogwartsmagic.pensieve.net.PensieveEnterPacket) payload).basin());
            case "pensieveStart" -> {
                var p = (com.hogwartsmagic.pensieve.net.PensieveStartPacket) payload;
                com.hogwartsmagic.client.pensieve.PensieveClientState.onStart(p.sceneId(), p.sceneJson(), p.origin(),
                        p.startGameTime(), p.buildTicks(), p.preview());
            }
            case "pensieveExit" -> com.hogwartsmagic.client.pensieve.PensieveClientState.onExit(
                    ((com.hogwartsmagic.pensieve.net.PensieveExitPacket) payload).reason());
            case "pensieveThud" -> com.hogwartsmagic.client.pensieve.PensieveClientState.onThud(
                    ((com.hogwartsmagic.pensieve.net.PensieveThudPacket) payload).strength());
            case "ambienceSync" -> com.hogwartsmagic.client.ambience.WorldAmbience.accept(
                    (com.hogwartsmagic.ambience.AmbienceNet.AmbienceSyncPacket) payload);
            case "ambienceDelta" -> com.hogwartsmagic.client.ambience.WorldAmbience.accept(
                    (com.hogwartsmagic.ambience.AmbienceNet.AmbienceDeltaPacket) payload);
            case "ceilingReveal" -> com.hogwartsmagic.client.ambience.WorldAmbience.startReveal(
                    ((com.hogwartsmagic.ambience.AmbienceNet.CeilingRevealPacket) payload).id());
            case "pensieveFade" -> com.hogwartsmagic.client.pensieve.PensieveClientState.onFade(
                    ((com.hogwartsmagic.pensieve.net.PensieveFadePacket) payload).ticks());
            case "pensieveWake" -> com.hogwartsmagic.client.pensieve.PensieveClientState.onWake(
                    ((com.hogwartsmagic.pensieve.net.PensieveWakePacket) payload).ticks());
            case "pensieveCaptureFx" -> {
                var p = (com.hogwartsmagic.pensieve.net.PensieveCaptureFxPacket) payload;
                com.hogwartsmagic.client.pensieve.CaptureFx.start(p.entityId(), p.mainHandWand());
            }
            case "pensieveRecording" -> com.hogwartsmagic.client.pensieve.RecordingCache.accept(
                    (com.hogwartsmagic.pensieve.net.PensieveRecordingPacket) payload);
            case "pensieveGhosts" -> com.hogwartsmagic.client.pensieve.SilverGhosts.set(
                    ((com.hogwartsmagic.pensieve.net.PensieveGhostsPacket) payload).ghosts());
            default -> throw new IllegalArgumentException("Unknown client packet action: " + action);
        }
    }

    public static void onClientPlayerTick(net.minecraft.world.entity.player.Player player,
                                          boolean serpensortiaUseLatched,
                                          boolean fiendfyreUseLatched,
                                          boolean wingardiumUseLatched,
                                          boolean imperioUseLatched) {
        ModEventsClientHooks.onClientPlayerTick(
                player,
                serpensortiaUseLatched,
                fiendfyreUseLatched,
                wingardiumUseLatched,
                imperioUseLatched
        );
    }

    public static void openSpellMenu(net.minecraft.world.entity.player.Player player) {
        ModEventsClientHooks.openSpellMenu(player);
    }

    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        if (com.hogwartsmagic.event.ClientModEvents.startPatronusHold()) {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    public static void startLockHold(net.minecraft.world.entity.player.Player player) {
        com.hogwartsmagic.event.ClientModEvents.startLockHold(net.minecraft.client.Minecraft.getInstance().options.keyUse);
    }

    public static void playNearMissFeedback(SpellBeamEntity beam) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (beam.hogwartsmagic$hasNearMissWhooshPlayed() || minecraft.player == null) {
            return;
        }
        if (beam.getOwner() == minecraft.player) {
            return;
        }
        if (beam.getBoundingBox().intersects(minecraft.player.getBoundingBox().inflate(0.05D))) {
            return;
        }

        Vec3 current = beam.position();
        Vec3 previous = current.subtract(beam.getDeltaMovement());
        Vec3 segment = current.subtract(previous);
        Vec3 playerEye = minecraft.player.getEyePosition(1.0f);

        double closestDistSq;
        double segLenSq = segment.lengthSqr();
        if (segLenSq < 1.0E-6) {
            closestDistSq = current.distanceToSqr(playerEye);
        } else {
            double t = net.minecraft.util.Mth.clamp(playerEye.subtract(previous).dot(segment) / segLenSq, 0.0D, 1.0D);
            Vec3 closest = previous.add(segment.scale(t));
            closestDistSq = closest.distanceToSqr(playerEye);
        }

        if (closestDistSq <= (SpellBeamEntity.getNearMissRadius() * SpellBeamEntity.getNearMissRadius())) {
            beam.level().addParticle(ParticleTypes.POOF, current.x, current.y + 0.2, current.z, 0.0, 0.0, 0.0);
            beam.level().playLocalSound(current.x, current.y, current.z,
                    SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.28f, 1.7f, false);
            beam.hogwartsmagic$markNearMissWhooshPlayed();
        }
    }

    private static void showHouseReveal(SpellPackets.HouseRevealPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || packet.house() == null) {
            return;
        }

        // if-chain, not an enum switch: the synthetic switch-map class fails to load in client code here.
        com.hogwartsmagic.sorting.HogwartsHouse house = packet.house();
        ItemStack revealStack;
        if (house == com.hogwartsmagic.sorting.HogwartsHouse.GRYFFINDOR) {
            revealStack = new ItemStack(ExampleMod.GRYFFINDOR_HOUSE_REVEAL.get());
        } else if (house == com.hogwartsmagic.sorting.HogwartsHouse.RAVENCLAW) {
            revealStack = new ItemStack(ExampleMod.RAVENCLAW_HOUSE_REVEAL.get());
        } else if (house == com.hogwartsmagic.sorting.HogwartsHouse.HUFFLEPUFF) {
            revealStack = new ItemStack(ExampleMod.HUFFLEPUFF_HOUSE_REVEAL.get());
        } else {
            revealStack = new ItemStack(ExampleMod.SLYTHERIN_HOUSE_REVEAL.get());
        }

        minecraft.gameRenderer.displayItemActivation(revealStack);
        minecraft.player.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
    }

    private static void syncPolyjuiceDisguise(SpellPackets.SyncPolyjuiceDisguisePacket packet) {
        PolyjuiceClientState.update(
                packet.playerId(),
                packet.disguise() == null ? new CompoundTag() : packet.disguise().copy()
        );
    }

    private static void syncMana(SpellPackets.SyncManaPacket packet) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        modData.putFloat("Mana", packet.mana());
        modData.putFloat("MaxMana", packet.maxMana());
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    private static void syncRelics(SpellPackets.SyncRelicsPacket packet) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        net.minecraftforge.items.ItemStackHandler relics = PlayerMagicData.createRelicHandler();
        relics.setStackInSlot(0, packet.capacityRelic().copy());
        relics.setStackInSlot(1, packet.regenRelic().copy());
        PlayerMagicData.saveRelicHandler(player, relics);
    }

    private static void syncLearnedSpells(SpellPackets.SyncLearnedSpellsPacket packet) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        modData.putString("LearnedSpells", packet.learnedSpells());
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    private static void syncSpellMastery(SpellPackets.SyncSpellMasteryPacket packet) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        modData.put(com.hogwartsmagic.spell.mastery.SpellMasteryData.KEY, packet.mastery());
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }

    private static void openFlooScreen(SpellPackets.OpenFlooScreenPacket packet) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new FlooTravelScreen(packet.sourcePos(), packet.destinations()));
    }

    private static void openFlooNamingScreen(SpellPackets.OpenFlooNamingScreenPacket packet) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new FlooNamingScreen(packet.fireplacePos(), packet.currentName()));
    }

    private static void openHogwartsInviteScreen(SpellPackets.OpenHogwartsInviteScreenPacket packet) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new HogwartsInviteScreen(packet.recipient()));
    }

    private static void openAdvancedPotionsScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new HogwartsGuideScreen(true));
    }

    private static void openHogwartsGuideScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new HogwartsGuideScreen());
    }

    private static void openApparitionMap(SpellPackets.OpenApparitionMapPacket packet) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new ApparitionMapScreen(packet.origin(), packet.radius(), packet.durationTicks(), packet.fireplaces()));
    }

    private static void revealDisguise(SpellPackets.RevealDisguisePacket packet) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            PolyjuiceClientState.reveal(packet.playerId(), level.getGameTime() + Math.max(0, packet.ticks()));
        }
    }

    private static void openTomePicker(SpellPackets.OpenTomePickerPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        com.hogwartsmagic.spell.SpellLootRarity[] tiers = com.hogwartsmagic.spell.SpellLootRarity.values();
        if (packet.tierOrdinal() < 0 || packet.tierOrdinal() >= tiers.length) {
            return;
        }
        List<com.hogwartsmagic.spell.SpellType> spells = new ArrayList<>();
        for (String id : packet.spellIds()) {
            for (com.hogwartsmagic.spell.SpellType spell : com.hogwartsmagic.spell.SpellType.values()) {
                if (spell.getId().equals(id)) {
                    spells.add(spell);
                    break;
                }
            }
        }
        minecraft.setScreen(new TomePickerScreen(packet.mainHand(), tiers[packet.tierOrdinal()], spells));
    }

    private static void closeApparitionMap() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof ApparitionMapScreen apparitionMap) {
            apparitionMap.closeFromServer();
        }
    }

    private static void syncCaveInimicumRegion(SpellPackets.CaveInimicumRegionSyncPacket packet) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (packet.edgeLength() <= 0 || packet.edgeLength() > 64) {
            return;
        }
        int expectedStates = packet.edgeLength() * packet.edgeLength() * packet.edgeLength();
        if (packet.stateIds() == null || packet.stateIds().length != expectedStates) {
            return;
        }
        int index = 0;
        for (int y = 0; y < packet.edgeLength(); y++) {
            for (int z = 0; z < packet.edgeLength(); z++) {
                for (int x = 0; x < packet.edgeLength(); x++) {
                    level.setBlock(packet.minPos().offset(x, y, z), Block.stateById(packet.stateIds()[index++]), 3);
                }
            }
        }
    }

    private static void syncCaveInimicumBlockDelta(SpellPackets.CaveInimicumBlockDeltaPacket packet) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            level.setBlock(packet.pos(), Block.stateById(packet.stateId()), 3);
        }
    }

    private static void undercroftFog(SpellPackets.UndercroftFogPacket p) {
        UndercroftFogClientState.set(p.centre(), p.radius(), p.topY(), p.bottomY());
    }

    private static void syncActiveWards(SpellPackets.CaveInimicumActiveWardsPacket packet) {
        List<CaveInimicumWardBounds> bounds = new ArrayList<>(packet.wards().size());
        for (SpellPackets.CaveInimicumActiveWardData ward : packet.wards()) {
            bounds.add(new CaveInimicumWardBounds(ward.center(), ward.edgeLength()));
        }
        CaveInimicumClientState.setActiveWards(bounds);
    }

    private static void startHorcruxDeathSequence(SpellPackets.StartHorcruxDeathSequencePacket packet) {
        HorcruxClientState.start(packet.soulEntityId(), packet.startPos(), packet.targetPos());
    }

    private static void finishHorcruxDeathSequence() {
        HorcruxClientState.finish();
    }

    private static void patronusShieldState(SpellPackets.PatronusShieldStatePacket packet) {
        PatronusShieldClientState.set(packet.casterId(), packet.active());
    }

    private static void protegoShield(SpellPackets.ProtegoShieldPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level != null) {
            com.hogwartsmagic.spell.support.ProtegoShield.accept(packet.entityId(), packet.layers(), packet.remaining(),
                    packet.dome(), packet.ranked(), packet.kind(), minecraft.level.getGameTime());
        }
    }

    private static void protegoMaximaImpact(SpellPackets.ProtegoMaximaImpactPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level != null) {
            ProtegoMaximaRenderer.acceptImpact(packet.entityId(),
                    new net.minecraft.world.phys.Vec3(packet.x(), packet.y(), packet.z()), packet.health(),
                    minecraft.level.getGameTime());
        }
    }

    private static void cloakTransition(SpellPackets.CloakTransitionPacket packet) {
        InvisibilityCloakClientState.start(packet.playerId(), packet.hiding());
    }
}
