package com.hogwartsmagic.sorting;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.PensieveSessions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sorting, server side. Putting the hat on (unsorted) plays the Sorting memory as a solo Pensieve memory; coming
 * home from it by its end or by leaving opens the fixed, voiced quiz; three answers (and a tie pick, if needed) sort
 * the player. A yank or an abort replays the memory on the next tick, and taking the hat off drops everything,
 * saving nothing. The quiz is server-authoritative: the client only sends (step, choice).
 *
 * <p>The quiz screen cannot be closed by the player and locks its buttons after a click, so every choice that is
 * refused re-sends the current question, and every dropped quiz is closed with a {@link SpellPackets.SortingClosePacket}.
 */
public class SortingHatManager {
    private static final String HOUSE_KEY = "SortingHouse";
    /** The first scene of the Sorting memory; later stages chain the full film from this id through {@code next}. */
    public static final String MEMORY_SCENE = "sorting";

    private static final String VOICE = "hogwartsmagic:sorting.voice.";
    private static final String GREETING_VOICE = VOICE + "greeting";
    private static final String TIE_VOICE = VOICE + "tie";
    private static final int REACTIONS = 3;

    private static final SortingHatManager INSTANCE = new SortingHatManager();

    private final Map<UUID, SortingSession> quizzes = new ConcurrentHashMap<>();
    /** Players this manager sent into the Sorting memory, waiting to come home. A preview never lands here. */
    private final Set<UUID> memoryPending = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    private boolean listening;

    public static SortingHatManager get() {
        return INSTANCE;
    }

    /** Hooks the manager to the Pensieve's home callback. Called once at common setup; repeat calls do nothing. */
    public synchronized void init() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(this::onHome);
    }

    /** Every 20 ticks per player, on the server thread. */
    public void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean open = quizzes.containsKey(id) || memoryPending.contains(id);
        if (!isWearingHat(player)) {
            if (open) drop(player);
            return;
        }
        if (memoryPending.contains(id) && !PensieveSessions.isViewer(player)) {
            // Marked for the memory but not in it, and no home callback came: treat it as an abort so they are never stuck.
            memoryPending.remove(id);
            open = quizzes.containsKey(id);
        }
        if (open || hasHouse(player) || PensieveSessions.isViewer(player)) {
            return;
        }
        if (PensieveSessions.startSolo(player, MEMORY_SCENE)) {
            memoryPending.add(id);
        } else {
            // No memory dimension or no scene: the quiz alone still sorts.
            startQuiz(player);
        }
    }

    private void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        if (!MEMORY_SCENE.equals(chainRoot) || !memoryPending.remove(player.getUUID())) {
            return;
        }
        if (reason != ExitReason.END && reason != ExitReason.LEFT) {
            // Yanked or aborted: the next tick plays the memory again from the start.
            return;
        }
        if (isWearingHat(player) && !hasHouse(player) && !quizzes.containsKey(player.getUUID())) {
            startQuiz(player);
        }
    }

    public void onChoice(ServerPlayer player, int step, int choice) {
        SortingSession quiz = quizzes.get(player.getUUID());
        if (quiz == null || !isWearingHat(player)) {
            return;
        }
        SortingSession.Outcome outcome = quiz.answer(step, choice);
        if (outcome == SortingSession.Outcome.NEXT_QUESTION) {
            voice(player, VOICE + "react" + (1 + random.nextInt(REACTIONS)));
            sendCurrent(player, quiz, true);
        } else if (outcome == SortingSession.Outcome.TIE) {
            sendCurrent(player, quiz, true);
        } else if (outcome == SortingSession.Outcome.SORTED) {
            quizzes.remove(player.getUUID());
            sorted(player, quiz.result());
        } else {
            // Refused (a stale or forged step, a slot out of range): unlock the screen on the same question.
            sendCurrent(player, quiz, false);
        }
    }

    /** The player logged out: the memory, if any, was ended by the Pensieve without telling the listener. */
    public void onLogout(ServerPlayer player) {
        quizzes.remove(player.getUUID());
        memoryPending.remove(player.getUUID());
    }

    /** The player died: the quiz closes and the memory, if any, starts from the beginning next time. */
    public void onDeath(ServerPlayer player) {
        if (quizzes.containsKey(player.getUUID()) || memoryPending.contains(player.getUUID())) {
            drop(player);
        }
    }

    /** Gametests: marks the player as sent into the memory without starting it. */
    public void markMemoryPendingForTests(UUID playerId) {
        memoryPending.add(playerId);
    }

    /** Gametests: whether the player is marked as waiting on the memory. */
    public boolean isMemoryPending(UUID playerId) {
        return memoryPending.contains(playerId);
    }

    /** Gametests: whether the player has a quiz open. */
    public boolean hasQuiz(UUID playerId) {
        return quizzes.containsKey(playerId);
    }

    /** Gametests: the open quiz's step (0..2 questions, 3 the tie), or -1 with none. */
    public int quizStep(UUID playerId) {
        SortingSession quiz = quizzes.get(playerId);
        return quiz == null ? -1 : quiz.step();
    }

    private void startQuiz(ServerPlayer player) {
        SortingSession quiz = new SortingSession(SortingQuestionBank.load(), random);
        quizzes.put(player.getUUID(), quiz);
        sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.greeting"));
        voice(player, GREETING_VOICE);
        sendCurrent(player, quiz, true);
    }

    /** Sends the quiz's current question or tie; {@code withVoice} false re-sends it silently. */
    private void sendCurrent(ServerPlayer player, SortingSession quiz, boolean withVoice) {
        int step = quiz.step();
        if (step < 3) {
            SortingQuestion q = quiz.question();
            List<String> answers = new ArrayList<>(4);
            for (HogwartsHouse house : quiz.order()) answers.add(q.answers().get(house));
            send(player, new SpellPackets.SortingQuestionPacket(step, q.text(), List.copyOf(answers),
                    withVoice ? q.voice() : ""));
        } else if (step == 3) {
            send(player, new SpellPackets.SortingTiePacket(quiz.tied(), withVoice ? TIE_VOICE : ""));
        }
    }

    private void sorted(ServerPlayer player, HogwartsHouse house) {
        saveHouse(player, house);
        send(player, new SpellPackets.HouseRevealPacket(house));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SORTED_SOUND.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        voice(player, VOICE + house.name().toLowerCase(java.util.Locale.ROOT));
        send(player, new SpellPackets.SortingClosePacket());
        sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.final", house.displayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** Drops the quiz and the memory mark, closing the screen; nothing is saved. */
    private void drop(ServerPlayer player) {
        quizzes.remove(player.getUUID());
        memoryPending.remove(player.getUUID());
        send(player, new SpellPackets.SortingClosePacket());
    }

    private void voice(ServerPlayer player, String id) {
        send(player, new SpellPackets.SortingVoicePacket(id));
    }

    private static void send(ServerPlayer player, Object packet) {
        SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    private boolean hasHouse(ServerPlayer player) {
        return !getModData(player).getString(HOUSE_KEY).isEmpty();
    }

    private void saveHouse(ServerPlayer player, HogwartsHouse house) {
        getModData(player).putString(HOUSE_KEY, house.name());
    }

    private net.minecraft.nbt.CompoundTag getModData(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
        return modData;
    }

    private boolean isWearingHat(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ExampleMod.SORTING_HAT.get());
    }

    private void sendHatMessage(ServerPlayer player, Component content) {
        player.sendSystemMessage(Component.literal("[Sorting Hat] ").withStyle(ChatFormatting.DARK_PURPLE).append(content));
    }
}
