package com.retile.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A bounded collection of named palettes, independent of the game registry. */
public final class PresetStore {
    public static final int MAX_PRESETS = 32;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_BLOCKS = 64;

    public record Entry(String id, int weight) {}

    public record Preset(String name, List<Entry> blocks) {
        public Preset {
            Objects.requireNonNull(name, "name");
            blocks = List.copyOf(blocks);
        }
    }

    public enum SaveResult {
        SAVED, EMPTY_NAME, NAME_TOO_LONG, EMPTY_PALETTE, PALETTE_TOO_LARGE,
        LIMIT_REACHED, INVALID_ENTRY
    }

    private final Map<String, Preset> presets = new LinkedHashMap<>();

    public PresetStore() {}

    /** Both the returned list and every palette in it are immutable snapshots. */
    public List<Preset> list() {
        return List.copyOf(presets.values());
    }

    public SaveResult save(String name, List<Entry> blocks) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty()) return SaveResult.EMPTY_NAME;
        if (normalized.length() > MAX_NAME_LENGTH) return SaveResult.NAME_TOO_LONG;
        if (blocks == null || blocks.isEmpty()) return SaveResult.EMPTY_PALETTE;
        if (blocks.size() > MAX_BLOCKS) return SaveResult.PALETTE_TOO_LARGE;
        for (Entry entry : blocks) {
            if (!validEntry(entry)) return SaveResult.INVALID_ENTRY;
        }
        if (!presets.containsKey(normalized) && presets.size() >= MAX_PRESETS) {
            return SaveResult.LIMIT_REACHED;
        }
        // Repeated ids remain separate weighted entries, preserving palette order and weights.
        presets.put(normalized, new Preset(normalized, blocks));
        return SaveResult.SAVED;
    }

    public boolean remove(String name) {
        return name != null && presets.remove(name.strip()) != null;
    }

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Preset preset : presets.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", preset.name());
            JsonArray blocks = new JsonArray();
            for (Entry entry : preset.blocks()) {
                JsonObject block = new JsonObject();
                block.addProperty("id", entry.id());
                block.addProperty("weight", entry.weight());
                blocks.add(block);
            }
            object.add("blocks", blocks);
            array.add(object);
        }
        return array;
    }

    /** Recover valid palettes and entries; malformed fields never coerce into valid values. */
    public static PresetStore fromJson(JsonElement json) {
        PresetStore store = new PresetStore();
        if (json == null || !json.isJsonArray()) return store;
        for (JsonElement element : json.getAsJsonArray()) {
            try {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String name = string(object.get("name"));
                if (name == null || name.strip().isEmpty()
                        || name.strip().length() > MAX_NAME_LENGTH) continue;
                JsonElement blocksJson = object.get("blocks");
                if (blocksJson == null || !blocksJson.isJsonArray()) continue;
                List<Entry> blocks = new ArrayList<>();
                for (JsonElement blockJson : blocksJson.getAsJsonArray()) {
                    Entry entry = readEntry(blockJson);
                    if (entry == null) continue;
                    blocks.add(entry);
                    if (blocks.size() == MAX_BLOCKS) break;
                }
                store.save(name, blocks);
            } catch (RuntimeException malformed) {
                // A damaged preset must not discard other palettes in the same document.
            }
        }
        return store;
    }

    private static Entry readEntry(JsonElement json) {
        try {
            if (json == null || !json.isJsonObject()) return null;
            JsonObject object = json.getAsJsonObject();
            String id = string(object.get("id"));
            Integer weight = integer(object.get("weight"));
            if (weight == null) return null;
            Entry entry = new Entry(id, weight);
            return validEntry(entry) ? entry : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static boolean validEntry(Entry entry) {
        return entry != null && entry.id() != null && !entry.id().isBlank()
                && entry.weight() >= 1 && entry.weight() <= 100;
    }

    private static String string(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        JsonPrimitive value = element.getAsJsonPrimitive();
        return value.isString() ? value.getAsString() : null;
    }

    private static Integer integer(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return null;
        JsonPrimitive value = element.getAsJsonPrimitive();
        if (!value.isNumber()) return null;
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (NumberFormatException | ArithmeticException malformed) {
            return null;
        }
    }
}
