package com.hogwartsmagic.sorting;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SortingHatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HOUSE_KEY = "SortingHouse";
    private static final int REQUIRED_ANSWERS = 3;

    private static final SortingHatManager INSTANCE = new SortingHatManager();

    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sessionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> waitingOnModel = ConcurrentHashMap.newKeySet();
    private final OllamaSortingService ollamaService = new OllamaSortingService();

    public static SortingHatManager get() {
        return INSTANCE;
    }

    public void maybeStartSession(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!isWearingHat(player)) {
            clearSession(playerId);
            return;
        }
        if (hasHouse(player) || sessions.containsKey(playerId)) {
            return;
        }

        UUID sessionToken = UUID.randomUUID();
        sessions.put(playerId, new SessionState());
        sessionTokens.put(playerId, sessionToken);

        sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.greeting"));
        requestNextQuestion(player, sessionToken);
    }

    public boolean handleChat(ServerPlayer player, String rawText) {
        UUID playerId = player.getUUID();
        SessionState session = sessions.get(playerId);
        if (session == null) {
            return false;
        }

        if (!isWearingHat(player)) {
            clearSession(playerId);
            return false;
        }

        if (waitingOnModel.contains(playerId)) {
            sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.wait"));
            return true;
        }

        String answer = rawText == null ? "" : rawText.trim();
        if (answer.isEmpty()) {
            sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.empty"));
            return true;
        }

        UUID sessionToken = sessionTokens.get(playerId);
        if (sessionToken == null) {
            clearSession(playerId);
            return false;
        }

        String askedQuestion = session.pendingQuestion();
        if (askedQuestion == null || askedQuestion.isBlank()) {
            askedQuestion = fallbackQuestion(session.turns().size());
        }
        session.turns().add(new OllamaSortingService.SortingTurn(askedQuestion, answer));
        session.setPendingQuestion(null);

        if (session.turns().size() >= REQUIRED_ANSWERS) {
            decideHouse(player, sessionToken);
        } else {
            requestNextQuestion(player, sessionToken);
        }
        return true;
    }

    private void requestNextQuestion(ServerPlayer player, UUID sessionToken) {
        UUID playerId = player.getUUID();
        SessionState session = sessions.get(playerId);
        if (session == null || !sessionToken.equals(sessionTokens.get(playerId))) {
            return;
        }

        waitingOnModel.add(playerId);
        List<OllamaSortingService.SortingTurn> history = List.copyOf(session.turns());
        List<HogwartsHouse> likelyHouses = inferLikelyHouses(history);
        ollamaService.generateQuestion(history, likelyHouses)
                .whenComplete((question, error) -> player.server.execute(() -> {
                    SessionState liveSession = sessions.get(playerId);
                    if (liveSession == null || !sessionToken.equals(sessionTokens.get(playerId))) {
                        return;
                    }
                    waitingOnModel.remove(playerId);
                    if (error != null) {
                        LOGGER.warn("Sorting Hat question generation failed for {}: {}", player.getScoreboardName(), error.toString());
                        String fallbackQuestion = fallbackQuestion(liveSession.turns().size());
                        liveSession.setPendingQuestion(fallbackQuestion);
                        sendReflectionIfNeeded(player, liveSession.turns(), likelyHouses);
                        sendHatMessage(player, Component.literal(fallbackQuestion));
                        return;
                    }
                    liveSession.setPendingQuestion(question);
                    sendReflectionIfNeeded(player, liveSession.turns(), likelyHouses);
                    sendHatMessage(player, Component.literal(question));
                }));
    }

    private void decideHouse(ServerPlayer player, UUID sessionToken) {
        UUID playerId = player.getUUID();
        SessionState session = sessions.get(playerId);
        if (session == null || !sessionToken.equals(sessionTokens.get(playerId))) {
            return;
        }

        waitingOnModel.add(playerId);
        sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.thinking"));
        List<OllamaSortingService.SortingTurn> history = List.copyOf(session.turns());
        ollamaService.decideHouse(history)
                .whenComplete((result, error) -> player.server.execute(() -> {
                    SessionState liveSession = sessions.get(playerId);
                    if (liveSession == null || !sessionToken.equals(sessionTokens.get(playerId))) {
                        return;
                    }
                    clearSession(playerId);

                    SortingResult finalResult = result;
                    if (error != null || result == null) {
                        if (error != null) {
                            LOGGER.warn("Sorting Hat house decision failed for {}: {}", player.getScoreboardName(), error.toString());
                        } else {
                            LOGGER.warn("Sorting Hat house decision returned null for {}", player.getScoreboardName());
                        }
                        List<HogwartsHouse> likelyHouses = inferLikelyHouses(liveSession.turns());
                        HogwartsHouse fallback = fallbackHouseFromTurns(liveSession.turns());
                        finalResult = new SortingResult(fallback, "Hmm... your traits still point me here.", prependHouse(fallback, likelyHouses));
                    }

                    saveHouse(player, finalResult.house());
                    SpellPackets.CHANNEL.send(new SpellPackets.HouseRevealPacket(finalResult.house()),
                            PacketDistributor.PLAYER.with(player));
                    player.level().playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            ModSounds.SORTED_SOUND.get(),
                            SoundSource.PLAYERS,
                            1.0f,
                            1.0f
                    );
                    sendHatMessage(player, Component.translatable("chat.hogwartsmagic.sorting_hat.final", finalResult.house().displayName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    if (!finalResult.likelyHouses().isEmpty()) {
                        sendHatMessage(player, Component.literal(buildFinalLikelyHouseLine(finalResult)).withStyle(ChatFormatting.GRAY));
                    }
                    sendHatMessage(player, Component.literal(finalResult.reason()).withStyle(ChatFormatting.YELLOW));
                }));
    }

    private void clearSession(UUID playerId) {
        sessions.remove(playerId);
        sessionTokens.remove(playerId);
        waitingOnModel.remove(playerId);
    }

    private boolean hasHouse(ServerPlayer player) {
        return !getHouseValue(player).isEmpty();
    }

    private String getHouseValue(ServerPlayer player) {
        return getModData(player).getString(HOUSE_KEY);
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

    private String fallbackQuestion(int count) {
        return switch (count) {
            case 0 -> "Tell me, what trait matters most to you: courage, wisdom, loyalty, or ambition?";
            case 1 -> "You answered quickly before. When things turn difficult, do you lead, learn, help, or scheme?";
            default -> "Earlier you spoke of your values. What legacy would you want whispered about you at Hogwarts?";
        };
    }

    private HogwartsHouse fallbackHouseFromTurns(List<OllamaSortingService.SortingTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return HogwartsHouse.RAVENCLAW;
        }
        // Without the model (no Ollama server), sort by the answers' keywords rather than a hash, so
        // "brave and bold" is not sent to Slytherin. inferLikelyHouses falls back to the hash itself
        // when no keyword matched.
        return inferLikelyHouses(turns).get(0);
    }

    private List<HogwartsHouse> inferLikelyHouses(List<OllamaSortingService.SortingTurn> turns) {
        if (turns.isEmpty()) {
            return List.of(HogwartsHouse.GRYFFINDOR, HogwartsHouse.RAVENCLAW);
        }

        int[] scores = new int[HogwartsHouse.values().length];
        for (OllamaSortingService.SortingTurn turn : turns) {
            String text = (turn.question() + " " + turn.answer()).toLowerCase();
            addHouseScore(scores, HogwartsHouse.GRYFFINDOR, text, "brave", "courage", "bold", "daring", "protect", "fight", "risk", "lead");
            addHouseScore(scores, HogwartsHouse.RAVENCLAW, text, "smart", "wise", "wisdom", "curious", "learn", "study", "knowledge", "understand", "think");
            addHouseScore(scores, HogwartsHouse.HUFFLEPUFF, text, "loyal", "kind", "fair", "help", "patient", "friend", "trust", "honest", "hard work");
            addHouseScore(scores, HogwartsHouse.SLYTHERIN, text, "ambition", "ambitious", "power", "win", "resourceful", "cunning", "legacy", "greatness", "scheme");
        }

        java.util.List<HogwartsHouse> ordered = new java.util.ArrayList<>(List.of(HogwartsHouse.values()));
        ordered.sort(java.util.Comparator.comparingInt((HogwartsHouse house) -> scores[house.ordinal()]).reversed()
                .thenComparing(Enum::name));

        if (scores[ordered.get(0).ordinal()] == 0) {
            int hash = turns.toString().hashCode();
            int firstIndex = Math.floorMod(hash, HogwartsHouse.values().length);
            int secondIndex = Math.floorMod(hash + 1, HogwartsHouse.values().length);
            HogwartsHouse first = HogwartsHouse.values()[firstIndex];
            HogwartsHouse second = HogwartsHouse.values()[secondIndex];
            return List.of(first, second);
        }
        return List.copyOf(ordered.subList(0, Math.min(3, ordered.size())));
    }

    private void addHouseScore(int[] scores, HogwartsHouse house, String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                scores[house.ordinal()] += 2;
            }
        }
    }

    private void sendReflectionIfNeeded(ServerPlayer player, List<OllamaSortingService.SortingTurn> turns, List<HogwartsHouse> likelyHouses) {
        if (turns.isEmpty()) {
            return;
        }
        OllamaSortingService.SortingTurn lastTurn = turns.get(turns.size() - 1);
        sendHatMessage(player, Component.literal(buildReflectionLine(lastTurn, likelyHouses)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private String buildReflectionLine(OllamaSortingService.SortingTurn lastTurn, List<HogwartsHouse> likelyHouses) {
        String question = trimForHat(lastTurn.question(), 42);
        String answer = trimForHat(lastTurn.answer(), 42);
        String likely = formatLikelyHouses(likelyHouses);
        return "When I asked \"" + question + "\", you answered \"" + answer + "\". " + likely;
    }

    private String buildFinalLikelyHouseLine(SortingResult result) {
        if (result.likelyHouses().size() <= 1) {
            return "I considered " + result.house().displayName() + " from the moment I looked inside your mind.";
        }
        HogwartsHouse runnerUp = result.likelyHouses().get(1);
        return "You could have suited " + runnerUp.displayName() + " as well, but " + result.house().displayName() + " fits you best.";
    }

    private String formatLikelyHouses(List<HogwartsHouse> likelyHouses) {
        List<HogwartsHouse> live = likelyHouses == null ? List.of() : likelyHouses;
        if (live.isEmpty()) {
            return "I still need one more glimpse into your mind.";
        }
        if (live.size() == 1) {
            return "I'm leaning strongly toward " + live.get(0).displayName() + ", but I want one more answer.";
        }
        if (live.size() == 2) {
            return "I can see " + live.get(0).displayName() + " and " + live.get(1).displayName() + " circling you already.";
        }
        return "I can see " + live.get(0).displayName() + ", " + live.get(1).displayName() + ", and perhaps even " + live.get(2).displayName() + ".";
    }

    private String trimForHat(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private List<HogwartsHouse> prependHouse(HogwartsHouse first, List<HogwartsHouse> houses) {
        java.util.List<HogwartsHouse> ordered = new java.util.ArrayList<>();
        ordered.add(first);
        for (HogwartsHouse house : houses) {
            if (!ordered.contains(house)) {
                ordered.add(house);
            }
        }
        return List.copyOf(ordered);
    }

    private static final class SessionState {
        private final java.util.List<OllamaSortingService.SortingTurn> turns = new java.util.ArrayList<>();
        private String pendingQuestion;

        public java.util.List<OllamaSortingService.SortingTurn> turns() {
            return turns;
        }

        public String pendingQuestion() {
            return pendingQuestion;
        }

        public void setPendingQuestion(String pendingQuestion) {
            this.pendingQuestion = pendingQuestion;
        }
    }
}
