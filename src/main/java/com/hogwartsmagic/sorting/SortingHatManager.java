package com.hogwartsmagic.sorting;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sorting, server side. Putting the hat on (unsorted) plays the Sorting memory as a solo Pensieve memory. Its
 * chain reaches {@link #CHOICE_SCENE}, the mind with the hat on: entering it opens the fixed, voiced quiz there and
 * then, inside the memory. Three answers (and a tie pick, if needed) sort the player: the house is saved at once and
 * the memory cuts ({@link PensieveSessions#handOver}) to that house's {@link #revealScene}, which ends in a wake (the
 * eyes open on the real world); the house reveal (toast, sound, chat) is held back until the wake is over.
 *
 * <p>Leaving: coming home before the choice scene (by the memory's end or by leaving) opens the quiz in the real world,
 * as before; leaving during the choice scene takes the open quiz home with it; leaving during a reveal goes home with
 * the wake and the held-back reveal. A yank or an abort ends the memory without the quiz (one open inside it is closed),
 * and it replays only once the hat has been taken off and put back on (so a hurt player is not dived again at once).
 * Taking the hat off drops everything, ending a memory still playing, and saves nothing. A dead player or a spectator
 * is left alone. The quiz is server-authoritative: the client only sends (step, choice).
 *
 * <p>The quiz screen cannot be closed by the player and locks its buttons after a click, so every choice that is
 * refused re-sends the current question, and every dropped quiz is closed with a {@link SpellPackets.SortingClosePacket}.
 * A quiz with no choice for {@link #RESEND_TICKS} has its question re-sent silently, in case the screen was replaced;
 * one that comes home from the memory is re-sent at once and again shortly after (a dimension change can replace it).
 */
public class SortingHatManager {
    private static final String HOUSE_KEY = "SortingHouse";
    /** The first scene of the Sorting memory; the chain runs from it through {@code next} to {@link #CHOICE_SCENE}. */
    public static final String MEMORY_SCENE = "sorting";
    /** The hold scene inside the mind where the quiz is answered. */
    public static final String CHOICE_SCENE = "sorting_choice";
    /** The reveal scenes are this prefix and the house in lower case: {@code sorting_reveal_gryffindor} and so on. */
    public static final String REVEAL_PREFIX = "sorting_reveal_";

    private static final String VOICE = "hogwartsmagic:sorting.voice.";
    private static final String GREETING_VOICE = VOICE + "greeting";
    private static final String TIE_VOICE = VOICE + "tie";
    private static final int REACTIONS = 3;
    /** An open quiz with no choice for this long re-sends its current question (silently). */
    static final long RESEND_TICKS = 600;
    /** A quiz that came home from the memory is re-sent again this long after the home callback. */
    private static final long HOME_RESEND_TICKS = 40;

    private static final SortingHatManager INSTANCE = new SortingHatManager();

    private final Map<UUID, SortingSession> quizzes = new ConcurrentHashMap<>();
    /** Players this manager sent into the Sorting memory, waiting to come home. A preview never lands here. */
    private final Set<UUID> memoryPending = ConcurrentHashMap.newKeySet();
    /** Players yanked or aborted out of the memory: it replays only after the hat comes off and goes back on. */
    private final Set<UUID> needsRewear = ConcurrentHashMap.newKeySet();
    /** Server game time the open quiz last sent its question or tie, per player. */
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();
    /** Sorted inside the memory: the house reveal held back until after the wake. */
    private final Map<UUID, HeldReveal> held = new ConcurrentHashMap<>();
    /** Gametests: server game time of each player's last house reveal. */
    private final Map<UUID, Long> revealedAt = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private boolean listening;

    /** A house reveal waiting: {@code due} is the game time it fires, or -1 while the player is still in the memory. */
    private record HeldReveal(HogwartsHouse house, long due) {
    }

    public static SortingHatManager get() {
        return INSTANCE;
    }

    /** The reveal scene for {@code house}: {@code sorting_reveal_<house in lower case>}. */
    public static String revealScene(HogwartsHouse house) {
        return REVEAL_PREFIX + house.name().toLowerCase(Locale.ROOT);
    }

    /** Hooks the manager to the Pensieve's callbacks. Called once at common setup; repeat calls do nothing. */
    public synchronized void init() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(this::onHome);
        PensieveSessions.addSceneEnterListener(CHOICE_SCENE, this::onChoiceScene);
        PensieveSessions.addSceneTimeoutListener(CHOICE_SCENE, this::onChoiceTimeout);
    }

    /** Every 20 ticks per player, on the server thread. */
    public void tick(ServerPlayer player) {
        // The death screen ticks too: with keepInventory the hat is still on, and a dead player must not be dived.
        if (!player.isAlive() || player.isSpectator()) return;
        UUID id = player.getUUID();
        boolean open = quizzes.containsKey(id) || memoryPending.contains(id);
        if (!isWearingHat(player)) {
            needsRewear.remove(id);
            if (open) {
                // Mark first, so the home callback of the aborted memory finds nothing and opens no quiz.
                boolean inMemory = memoryPending.contains(id);
                drop(player);
                if (inMemory && PensieveSessions.isViewer(player)) PensieveSessions.leave(player, ExitReason.ABORT);
            }
            return;
        }
        if (needsRewear.contains(id)) return;
        if (memoryPending.contains(id) && !PensieveSessions.isViewer(player)) {
            // Marked for the memory but not in it, and no home callback came: treat it as an abort so they are never stuck.
            memoryPending.remove(id);
            open = quizzes.containsKey(id);
            HeldReveal r = held.get(id);
            if (r != null && r.due() < 0) held.put(id, new HeldReveal(r.house(), player.level().getGameTime()));
        }
        SortingSession quiz = quizzes.get(id);
        if (quiz != null && player.level().getGameTime() - lastSent.getOrDefault(id, 0L) >= RESEND_TICKS) {
            // No choice in a while: the screen may have been replaced on the client, so put the question back up.
            sendCurrent(player, quiz, false);
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

    /** Every tick per player, on the server thread: fires a held-back house reveal once its wake is over. */
    public void tickReveal(ServerPlayer player) {
        HeldReveal r = held.get(player.getUUID());
        if (r == null || r.due() < 0 || player.level().getGameTime() < r.due()) return;
        held.remove(player.getUUID());
        reveal(player, r.house());
    }

    /** A viewer the hat sent in has reached the choice scene (or it looped): the quiz opens here, inside the memory. */
    private void onChoiceScene(ServerPlayer player, Session session) {
        UUID id = player.getUUID();
        if (!memoryPending.contains(id) || !isWearingHat(player) || hasHouse(player)) return;
        SortingSession quiz = quizzes.get(id);
        if (quiz == null) {
            startQuiz(player);
        } else {
            // The hold scene looped round to itself: the quiz carries on where it was.
            sendCurrent(player, quiz, false);
        }
    }

    /** The choice scene ran its whole length unanswered: it plays again, with the quiz still open. */
    private void onChoiceTimeout(ServerPlayer player, Session session) {
        UUID id = player.getUUID();
        if (memoryPending.contains(id) && quizzes.containsKey(id)) PensieveSessions.handOver(player, CHOICE_SCENE);
    }

    private void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        UUID id = player.getUUID();
        if (!MEMORY_SCENE.equals(chainRoot) || !memoryPending.remove(id)) {
            return;
        }
        HeldReveal r = held.get(id);
        if (reason != ExitReason.END && reason != ExitReason.LEFT) {
            // Yanked or aborted: the memory plays again from the start, but only once the hat is re-worn. A quiz open
            // inside it goes with it; a house already chosen is revealed now, with no wake.
            needsRewear.add(id);
            if (quizzes.remove(id) != null) {
                lastSent.remove(id);
                send(player, new SpellPackets.SortingClosePacket());
            }
            if (r != null) {
                held.remove(id);
                reveal(player, r.house());
            }
            return;
        }
        long now = player.level().getGameTime();
        if (r != null) {
            // Home from a reveal (or from the choice, if the reveal would not load): the reveal waits for the wake.
            held.put(id, new HeldReveal(r.house(), now + PensieveSessions.WAKE_DONE_TICKS));
            return;
        }
        if (!isWearingHat(player) || hasHouse(player)) return;
        SortingSession quiz = quizzes.get(id);
        if (quiz == null) {
            // Home before the choice scene: the quiz opens in the real world.
            startQuiz(player);
        } else {
            // Home from the choice scene unanswered: the quiz carries on out here. Re-sent now and again shortly.
            sendCurrent(player, quiz, false);
            lastSent.put(id, now - RESEND_TICKS + HOME_RESEND_TICKS);
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
            lastSent.remove(player.getUUID());
            if (inChoiceScene(player)) {
                sortedInMind(player, quiz.result());
            } else {
                sorted(player, quiz.result());
            }
        } else {
            // Refused (a stale or forged step, a slot out of range): unlock the screen on the same question.
            sendCurrent(player, quiz, false);
        }
    }

    /** The player logged out: the memory, if any, was ended by the Pensieve without telling the listener. */
    public void onLogout(ServerPlayer player) {
        quizzes.remove(player.getUUID());
        memoryPending.remove(player.getUUID());
        needsRewear.remove(player.getUUID());
        lastSent.remove(player.getUUID());
        held.remove(player.getUUID());
        revealedAt.remove(player.getUUID());
    }

    /** The player died: the quiz closes and the memory, if any, starts from the beginning next time. */
    public void onDeath(ServerPlayer player) {
        UUID id = player.getUUID();
        if (quizzes.containsKey(id) || memoryPending.contains(id) || held.containsKey(id)) {
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

    /** Gametests: whether a yank or an abort is waiting for the hat to be taken off and put back on. */
    public boolean needsRewear(UUID playerId) {
        return needsRewear.contains(playerId);
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

    /** Gametests: the house whose reveal is held back for the player, or null. */
    public HogwartsHouse heldReveal(UUID playerId) {
        HeldReveal r = held.get(playerId);
        return r == null ? null : r.house();
    }

    /** Gametests: game time the held reveal fires, -1 while still in the memory, or -2 with none held. */
    public long heldRevealDue(UUID playerId) {
        HeldReveal r = held.get(playerId);
        return r == null ? -2 : r.due();
    }

    /** Gametests: server game time of the player's last house reveal, or -1. */
    public long revealedAt(UUID playerId) {
        return revealedAt.getOrDefault(playerId, -1L);
    }

    /** True while the hat's player is inside the choice scene and not on the way out. */
    private boolean inChoiceScene(ServerPlayer player) {
        if (!memoryPending.contains(player.getUUID())) return false;
        Session s = PensieveSessions.sessionOf(player).orElse(null);
        if (s == null || !CHOICE_SCENE.equals(s.sceneId())) return false;
        Session.Viewer v = s.viewers().get(player.getUUID());
        return v != null && v.exitReason() == null;
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
        lastSent.put(player.getUUID(), player.level().getGameTime());
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

    /** Sorted in the real world: saved and revealed at once. */
    private void sorted(ServerPlayer player, HogwartsHouse house) {
        saveHouse(player, house);
        voice(player, VOICE + house.name().toLowerCase(Locale.ROOT));
        send(player, new SpellPackets.SortingClosePacket());
        reveal(player, house);
    }

    /**
     * Sorted inside the memory: saved now, the hat's shout heard as the choice scene fades, and the memory cut to the
     * house's reveal scene. The reveal itself waits for the wake at its end (see {@link #onHome}); if the reveal scene
     * will not load, the memory is left instead and the reveal follows the trip home.
     */
    private void sortedInMind(ServerPlayer player, HogwartsHouse house) {
        saveHouse(player, house);
        voice(player, VOICE + house.name().toLowerCase(Locale.ROOT));
        send(player, new SpellPackets.SortingClosePacket());
        held.put(player.getUUID(), new HeldReveal(house, -1));
        if (!PensieveSessions.handOver(player, revealScene(house))) {
            PensieveSessions.leave(player, ExitReason.LEFT);
        }
    }

    /** The house reveal: the toast, the sorted sound and the hat's last word in chat. */
    private void reveal(ServerPlayer player, HogwartsHouse house) {
        revealedAt.put(player.getUUID(), player.level().getGameTime());
        send(player, new SpellPackets.HouseRevealPacket(house));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SORTED_SOUND.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f);
        sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.final", house.displayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** Drops the quiz and the memory mark, closing the screen; nothing is saved. A house already saved is revealed. */
    private void drop(ServerPlayer player) {
        quizzes.remove(player.getUUID());
        memoryPending.remove(player.getUUID());
        lastSent.remove(player.getUUID());
        send(player, new SpellPackets.SortingClosePacket());
        HeldReveal r = held.remove(player.getUUID());
        if (r != null) reveal(player, r.house());
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
