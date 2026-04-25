package com.hogwartsmagic.sorting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaSortingService {
    private static final String OLLAMA_URL = "http://127.0.0.1:11434/api/chat";
    private static final String MODEL = "llama3.2:3b";
    private static final String QUESTION_SYSTEM_PROMPT = "You are the Hogwarts Sorting Hat. Ask exactly one short question to learn personality and values. "
            + "Reference earlier questions and answers when helpful, sound theatrical and perceptive like the films, and mention possible houses only as possibilities. "
            + "Do not decide a house yet. Keep it under 24 words.";
    private static final String DECISION_SYSTEM_PROMPT = "You are the Hogwarts Sorting Hat. Decide one house from GRYFFINDOR, RAVENCLAW, HUFFLEPUFF, SLYTHERIN based on the answers. "
            + "Use this rubric: courage/risk->GRYFFINDOR, curiosity/intellect->RAVENCLAW, loyalty/fairness->HUFFLEPUFF, ambition/cunning/resourcefulness->SLYTHERIN. "
            + "Answer with plain text and include the chosen house explicitly.";
    private static final String DECISION_RETRY_PROMPT = "Output exactly one token: GRYFFINDOR or RAVENCLAW or HUFFLEPUFF or SLYTHERIN. No other text.";
    private static final Pattern HOUSE_PATTERN = Pattern.compile("\\b(GRYFFINDOR|RAVENCLAW|HUFFLEPUFF|SLYTHERIN)\\b", Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CompletableFuture<String> generateQuestion(List<SortingTurn> history, List<HogwartsHouse> likelyHouses) {
        StringBuilder prompt = new StringBuilder();
        if (history.isEmpty()) {
            prompt.append("Start the interview and ask your first question.");
        } else {
            prompt.append("Interview so far:\n");
            for (int i = 0; i < history.size(); i++) {
                SortingTurn turn = history.get(i);
                prompt.append(i + 1).append(") Question: ").append(turn.question()).append('\n');
                prompt.append("   Answer: ").append(turn.answer()).append('\n');
            }
            if (!likelyHouses.isEmpty()) {
                prompt.append("Current likely houses: ");
                for (int i = 0; i < likelyHouses.size(); i++) {
                    if (i > 0) {
                        prompt.append(i == likelyHouses.size() - 1 ? " and " : ", ");
                    }
                    prompt.append(likelyHouses.get(i).displayName());
                }
                prompt.append(".\n");
            }
            prompt.append("Ask the next best single question that builds on this interview.");
        }
        return callOllama(QUESTION_SYSTEM_PROMPT, prompt.toString())
                .thenApply(this::sanitizeQuestion);
    }

    public CompletableFuture<SortingResult> decideHouse(List<SortingTurn> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Interview transcript:\n");
        for (int i = 0; i < history.size(); i++) {
            SortingTurn turn = history.get(i);
            prompt.append(i + 1).append(") Question: ").append(turn.question()).append('\n');
            prompt.append("   Answer: ").append(turn.answer()).append('\n');
        }
        prompt.append("\nChoose one best-fit house and explain briefly in one sentence.");

        List<HogwartsHouse> likelyHouses = inferLikelyHouses(history);

        return callOllama(DECISION_SYSTEM_PROMPT, prompt.toString())
                .thenCompose(content -> {
                    SortingResult firstPass = parseDecision(content, likelyHouses);
                    if (firstPass != null) {
                        return CompletableFuture.completedFuture(firstPass);
                    }

                    String retryPrompt = "Answers:\n" + prompt + "\n" + DECISION_RETRY_PROMPT;
                    return callOllama(DECISION_SYSTEM_PROMPT, retryPrompt)
                            .thenApply(response -> parseHouseOnlyDecision(response, likelyHouses));
                });
    }

    private CompletableFuture<String> callOllama(String systemPrompt, String userPrompt) {
        return callOllamaInternal(systemPrompt, userPrompt, false);
    }

    private CompletableFuture<String> callOllamaJson(String systemPrompt, String userPrompt) {
        return callOllamaInternal(systemPrompt, userPrompt, true);
    }

    private CompletableFuture<String> callOllamaInternal(String systemPrompt, String userPrompt, boolean jsonMode) {
        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("messages", messages);
        body.addProperty("stream", false);
        if (jsonMode) {
            body.addProperty("format", "json");
            JsonObject options = new JsonObject();
            options.addProperty("temperature", 0.1);
            body.add("options", options);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(
                                new IOException("Ollama returned status " + response.statusCode() + " body: " + response.body()));
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        JsonObject message = json.getAsJsonObject("message");
                        if (message == null || !message.has("content")) {
                            return CompletableFuture.failedFuture(new IOException("Missing message content from Ollama"));
                        }
                        return CompletableFuture.completedFuture(message.get("content").getAsString());
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    private SortingResult parseDecision(String content, List<HogwartsHouse> likelyHouses) {
        String candidate = content == null ? "" : content.trim();
        if (candidate.isEmpty()) {
            return null;
        }

        // Handle occasional JSON-ish outputs without requiring JSON mode.
        try {
            int firstBrace = candidate.indexOf('{');
            int lastBrace = candidate.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                String jsonCandidate = candidate.substring(firstBrace, lastBrace + 1);
                JsonObject json = JsonParser.parseString(jsonCandidate).getAsJsonObject();
                String rawHouse = "";
                if (json.has("house")) {
                    rawHouse = json.get("house").getAsString();
                } else if (json.has("House")) {
                    rawHouse = json.get("House").getAsString();
                }
                HogwartsHouse jsonHouse = parseHouseSafely(rawHouse.isEmpty() ? jsonCandidate : rawHouse);
                if (jsonHouse != null) {
                    String reason = json.has("reason") ? json.get("reason").getAsString() : "Your answers matched this house best.";
                    return new SortingResult(jsonHouse, reason, likelyHousesWithFallback(likelyHouses, jsonHouse));
                }
            }
        } catch (Exception ignored) {
            // Continue to plain-text parsing.
        }

        HogwartsHouse house = parseHouseSafely(candidate);
        if (house == null) {
            return null;
        }
        String reason = buildReasonFromPlainText(candidate);
        return new SortingResult(house, reason, likelyHousesWithFallback(likelyHouses, house));
    }

    private SortingResult parseHouseOnlyDecision(String content, List<HogwartsHouse> likelyHouses) {
        HogwartsHouse house = parseHouseSafely(content);
        if (house == null) {
            throw new IllegalStateException("Could not extract house from Ollama response: " + content);
        }
        return new SortingResult(house, "The Sorting Hat is certain.", likelyHousesWithFallback(likelyHouses, house));
    }

    private String sanitizeQuestion(String raw) {
        String question = raw.replace('\n', ' ').trim();
        if (question.startsWith("\"") && question.endsWith("\"") && question.length() > 1) {
            question = question.substring(1, question.length() - 1).trim();
        }
        if (question.isEmpty()) {
            return "Tell me: what quality in yourself are you most proud of?";
        }
        return question;
    }

    private HogwartsHouse parseHouseSafely(String text) {
        Matcher matcher = HOUSE_PATTERN.matcher(text == null ? "" : text);
        HogwartsHouse found = null;
        while (matcher.find()) {
            found = HogwartsHouse.fromModelText(matcher.group(1));
        }
        return found;
    }

    private String buildReasonFromPlainText(String candidate) {
        String compact = candidate.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) {
            return "Your answers matched this house best.";
        }
        int split = compact.toUpperCase().lastIndexOf(parseHouseToken(compact));
        if (split >= 0 && split + parseHouseToken(compact).length() < compact.length()) {
            String remainder = compact.substring(split + parseHouseToken(compact).length()).replaceFirst("^[\\s:;,.!-]+", "").trim();
            if (!remainder.isEmpty()) {
                return remainder;
            }
        }
        return compact.length() > 140 ? compact.substring(0, 140).trim() : compact;
    }

    private String parseHouseToken(String text) {
        Matcher matcher = HOUSE_PATTERN.matcher(text == null ? "" : text);
        String found = "";
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found;
    }

    private List<HogwartsHouse> likelyHousesWithFallback(List<HogwartsHouse> likelyHouses, HogwartsHouse decidedHouse) {
        List<HogwartsHouse> result = new ArrayList<>();
        result.add(decidedHouse);
        for (HogwartsHouse house : likelyHouses) {
            if (!result.contains(house)) {
                result.add(house);
            }
            if (result.size() >= 3) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private List<HogwartsHouse> inferLikelyHouses(List<SortingTurn> history) {
        if (history.isEmpty()) {
            return List.of(HogwartsHouse.GRYFFINDOR, HogwartsHouse.RAVENCLAW);
        }

        int[] scores = new int[HogwartsHouse.values().length];
        for (SortingTurn turn : history) {
            String text = (turn.question() + " " + turn.answer()).toLowerCase();
            score(text, scores, HogwartsHouse.GRYFFINDOR, "brave", "courage", "bold", "daring", "lead", "protect", "fight", "hero", "risk");
            score(text, scores, HogwartsHouse.RAVENCLAW, "smart", "wit", "wise", "wisdom", "learn", "study", "curious", "knowledge", "think", "book");
            score(text, scores, HogwartsHouse.HUFFLEPUFF, "loyal", "kind", "fair", "patient", "help", "trust", "friend", "hard work", "honest");
            score(text, scores, HogwartsHouse.SLYTHERIN, "ambition", "ambitious", "power", "greatness", "win", "resourceful", "cunning", "clever", "legacy");
        }

        List<HogwartsHouse> ordered = new ArrayList<>(List.of(HogwartsHouse.values()));
        ordered.sort(Comparator.comparingInt((HogwartsHouse house) -> scores[house.ordinal()]).reversed()
                .thenComparing(Enum::name));
        if (scores[ordered.get(0).ordinal()] == 0) {
            int hash = history.toString().hashCode();
            int firstIndex = Math.floorMod(hash, HogwartsHouse.values().length);
            int secondIndex = Math.floorMod(hash + 1, HogwartsHouse.values().length);
            HogwartsHouse first = HogwartsHouse.values()[firstIndex];
            HogwartsHouse second = HogwartsHouse.values()[secondIndex];
            return List.of(first, second);
        }
        return ordered.subList(0, Math.min(3, ordered.size()));
    }

    private void score(String text, int[] scores, HogwartsHouse house, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                scores[house.ordinal()] += 2;
            }
        }
    }

    public record SortingTurn(String question, String answer) {
    }
}
