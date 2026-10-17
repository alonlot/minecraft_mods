package com.hogwartsmagic.sorting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SortingQuestionBank {
    private static final String RESOURCE = "/data/hogwartsmagic/sorting/questions.json";
    private static List<SortingQuestion> cached;

    private SortingQuestionBank() {
    }

    public static synchronized List<SortingQuestion> load() {
        if (cached == null) {
            try (InputStream in = SortingQuestionBank.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing resource " + RESOURCE);
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    cached = parse(JsonParser.parseReader(reader).getAsJsonObject());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + RESOURCE, e);
            }
        }
        return cached;
    }

    public static List<SortingQuestion> parse(JsonObject root) throws IllegalArgumentException {
        if (root == null || !root.has("questions") || !root.get("questions").isJsonArray()) {
            throw new IllegalArgumentException("Missing questions array");
        }
        JsonArray array = root.getAsJsonArray("questions");
        List<SortingQuestion> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Question is not an object");
            }
            JsonObject q = element.getAsJsonObject();
            String id = string(q, "id");
            String text = string(q, "text");
            String voice = string(q, "voice");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate question id " + id);
            }
            if (!q.has("answers") || !q.get("answers").isJsonObject()) {
                throw new IllegalArgumentException("Question " + id + " has no answers");
            }
            JsonObject answersJson = q.getAsJsonObject("answers");
            Map<HogwartsHouse, String> answers = new EnumMap<>(HogwartsHouse.class);
            for (HogwartsHouse house : HogwartsHouse.values()) {
                answers.put(house, string(answersJson, house.name()));
            }
            if (answersJson.size() != answers.size()) {
                throw new IllegalArgumentException("Question " + id + " has unknown answer keys");
            }
            out.add(new SortingQuestion(id, text, voice, Map.copyOf(answers)));
        }
        return List.copyOf(out);
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive() || o.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing or empty \"" + key + "\"");
        }
        return o.get(key).getAsString();
    }
}
