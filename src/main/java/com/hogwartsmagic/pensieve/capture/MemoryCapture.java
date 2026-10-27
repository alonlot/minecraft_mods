package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.pensieve.EmptyMemoryPhialItem;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.RecordedMemoryItem;
import com.hogwartsmagic.pensieve.net.PensieveCaptureFxPacket;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The server half of drawing a memory into a phial: the checks, the phial that is spent, the effect everyone nearby
 * sees, and the Memory handed over when the recording is assembled and saved. Everything runs on the server thread;
 * only {@link RecordingAssembly}'s own background work and the file write leave it.
 */
public final class MemoryCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COOLDOWN_KEY = "PensieveCaptureAt";
    private static final int COOLDOWN_TICKS = 20;
    private static final String HINT = "message.hogwartsmagic.pensieve.capture.";

    private MemoryCapture() {
    }

    private static boolean isPhial(ItemStack stack) {
        return stack.getItem() instanceof EmptyMemoryPhialItem;
    }

    private static void hint(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(HINT + key), true);
    }

    /** The message key for a failed assembly: the two the player can act on, else a general failure. */
    private static String failureHint(Throwable t) {
        String key = RecordingAssembly.failureKey(t);
        if (RecordingAssembly.TOO_SOON.equals(key)) {
            return "too_soon";
        }
        if (RecordingAssembly.TOO_BIG.equals(key)) {
            return "too_big";
        }
        return "failed";
    }

    /** The server handler of the capture packet. */
    public static void capture(ServerPlayer player) {
        // Silent no-ops: a dead, standing or watching player, a repeat press, and a wand used without any phial.
        if (!player.isAlive() || !player.isShiftKeyDown() || PensieveSessions.isViewer(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        CompoundTag mod = PlayerMagicData.getModData(player);
        if (mod.contains(COOLDOWN_KEY) && now - mod.getLong(COOLDOWN_KEY) < COOLDOWN_TICKS && now >= mod.getLong(COOLDOWN_KEY)) {
            return;
        }
        boolean mainPhial = isPhial(player.getMainHandItem());
        boolean offPhial = isPhial(player.getOffhandItem());
        if (!mainPhial && !offPhial) {
            return;
        }
        mod.putLong(COOLDOWN_KEY, now);
        PlayerMagicData.saveModData(player, mod);

        // Hinted refusals.
        if (!Config.pensieveCaptureEnabled) {
            hint(player, "disabled");
            return;
        }
        if (PensieveDimension.isMemory(level)) {
            hint(player, "inside");
            return;
        }
        InteractionHand phialHand;
        if (offPhial && MagicWandItem.isAnyWand(player.getMainHandItem())) {
            phialHand = InteractionHand.OFF_HAND;
        } else if (mainPhial && MagicWandItem.isAnyWand(player.getOffhandItem())) {
            phialHand = InteractionHand.MAIN_HAND;
        } else {
            hint(player, "need_wand");
            return;
        }

        MinecraftServer server = level.getServer();
        UUID id = UUID.randomUUID();
        int day = (int) (server.overworld().getDayTime() / 24000L) + 1;
        String name = player.getGameProfile().getName();
        // The window is checked here, before the phial is spent: assembly validates and copies on this thread, so a
        // memory that is too short fails at once, as an already-failed future.
        CompletableFuture<Recording> assembled;
        try {
            assembled = RecordingAssembly.assemble(player, id, name + "'s Memory — Day " + day);
        } catch (RuntimeException e) {
            LOGGER.warn("Preparing a memory for {} failed", name, e);
            hint(player, "failed");
            return;
        }
        if (assembled.isCompletedExceptionally()) {
            assembled.exceptionally(t -> {
                hint(player, failureHint(t));
                return null;
            });
            return;
        }

        // Capture: spend the phial now, so a double press cannot draw two memories out of one.
        player.getItemInHand(phialHand).shrink(1);
        PacketDistributor.PacketTarget target = PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player);
        SpellPackets.CHANNEL.send(new PensieveCaptureFxPacket(player.getId(), phialHand == InteractionHand.OFF_HAND), target);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PENSIEVE_CAPTURE.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);

        UUID ownerId = player.getUUID();
        net.minecraft.world.phys.Vec3 at = player.position();
        assembled.thenCompose(rec -> RecordingStore.save(server, rec).thenApply(v -> rec))
                .whenComplete((rec, error) -> server.execute(() -> {
                    if (error != null || rec == null) {
                        if (RecordingAssembly.failureKey(error) == null) {
                            LOGGER.warn("Capturing a memory for {} failed", name, error);
                        }
                        ServerPlayer back = give(server, level, ownerId, at, phialHand,
                                new ItemStack(ExampleMod.EMPTY_MEMORY_PHIAL.get()));
                        if (back != null) {
                            hint(back, failureHint(error));
                        }
                        return;
                    }
                    give(server, level, ownerId, at, phialHand, RecordedMemoryItem.create(ExampleMod.MEMORY.get(), rec.id(), rec.owner(),
                            rec.ownerName(), day, Math.max(1, Math.round(rec.length() / 20f))));
                }));
    }

    /**
     * Into the hand that held the phial if it is empty, else the inventory, else at the player's feet. The player is
     * looked up again by uuid, since they may have logged out or died while the recording was saved; a player who is
     * gone or dead gets the item dropped where the capture happened. Returns the player it went to, or null.
     */
    private static ServerPlayer give(MinecraftServer server, ServerLevel level, UUID ownerId,
                                     net.minecraft.world.phys.Vec3 at, InteractionHand hand, ItemStack stack) {
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player == null || !player.isAlive()) {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack));
            return null;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            player.setItemInHand(hand, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return player;
    }
}
