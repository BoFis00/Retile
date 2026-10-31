package com.retile.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Gson transport DTO: identifiers and enum names are plain strings. */
public final class BrushConfigJson {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record WeightedEntry(String id, int weight) {}
    public static final class Data {
        public List<String> filters = new ArrayList<>();
        public boolean includeAir = false;
        public List<WeightedEntry> replacements = new ArrayList<>();
        public float chaos = 0;
        public boolean limitHeight = false;
        public int minY = -64, maxY = 320, radius = 5;
        public String shape = "SPHERE";
        public boolean fixedSeed = false;
        public long seed = 0;
        public PresetStore presets = new PresetStore();
        public KeyCombo undoKey = KeyCombo.UNASSIGNED, redoKey = KeyCombo.UNASSIGNED;
    }
    private BrushConfigJson() {}

    public static Data load(Path path) {
        if (path == null) return new Data();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            JsonElement json = readJson(jsonReader);
            if (jsonReader.peek() != JsonToken.END_DOCUMENT || !validJson(json)) return new Data();
            JsonObject object = json.getAsJsonObject();
            JsonElement presets = object.remove("presets"), undo = object.remove("undoKey"), redo = object.remove("redoKey");
            Data data = GSON.fromJson(object, Data.class);
            data.presets = PresetStore.fromJson(presets);
            data.undoKey = KeyCombo.fromJson(undo);
            data.redoKey = KeyCombo.fromJson(redo);
            if (data.undoKey.isAssigned() && data.undoKey.equals(data.redoKey)) data.redoKey = KeyCombo.UNASSIGNED;
            return validData(data) ? data : new Data();
        } catch (IOException | RuntimeException error) {
            return new Data();
        }
    }

    /** Preserve number text and reject duplicate fields before Gson can overwrite them. */
    private static JsonElement readJson(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate config field: " + name);
                    object.add(name, readJson(reader));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> GSON.getAdapter(JsonElement.class).read(reader);
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException("Unexpected config token: " + reader.peek());
        };
    }

    public static void save(Path path, Data data) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(data, "data");
        if (!validData(data)) throw new IllegalArgumentException("Invalid brush configuration");
        Path destination = path.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "brush-", ".json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                JsonObject object = GSON.toJsonTree(data).getAsJsonObject();
                object.add("presets", data.presets.toJson());
                object.add("undoKey", data.undoKey.toJson());
                object.add("redoKey", data.redoKey.toJson());
                GSON.toJson(object, writer);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean validData(Data data) {
        if (data == null || data.presets == null || data.undoKey == null || data.redoKey == null
                || data.undoKey.isAssigned() && data.undoKey.equals(data.redoKey)
                || data.filters == null || data.filters.size() > 64
                || data.replacements == null || data.replacements.size() > 64
                || !Float.isFinite(data.chaos) || data.chaos < -1 || data.chaos > 1
                || data.radius < 1 || data.radius > 32
                || !("SPHERE".equals(data.shape) || "CYLINDER".equals(data.shape))) return false;
        for (String id : data.filters) if (id == null || id.isBlank()) return false;
        for (WeightedEntry entry : data.replacements) {
            if (entry == null || entry.id() == null || entry.id().isBlank()
                    || entry.weight() < 1 || entry.weight() > 100) return false;
        }
        return true;
    }

    /** Validate the JSON types before Gson can coerce strings, booleans or fractional integers. */
    private static boolean validJson(JsonElement json) {
        if (json == null || !json.isJsonObject()) return false;
        JsonObject object = json.getAsJsonObject();
        for (String name : new String[]{"includeAir", "limitHeight", "fixedSeed"}) {
            if (object.has(name) && !primitive(object.get(name), "boolean")) return false;
        }
        for (String name : new String[]{"minY", "maxY", "radius"}) {
            if (object.has(name) && !integer(object.get(name), false)) return false;
        }
        if (object.has("seed") && !integer(object.get("seed"), true)) return false;
        if (object.has("chaos") && !chaos(object.get("chaos"))) return false;
        if (object.has("shape") && !primitive(object.get("shape"), "string")) return false;
        if (object.has("filters")) {
            JsonElement filters = object.get("filters");
            if (!filters.isJsonArray() || filters.getAsJsonArray().size() > 64) return false;
            for (JsonElement entry : filters.getAsJsonArray()) if (!primitive(entry, "string")) return false;
        }
        if (object.has("replacements")) {
            JsonElement replacements = object.get("replacements");
            if (!replacements.isJsonArray() || replacements.getAsJsonArray().size() > 64) return false;
            for (JsonElement entry : replacements.getAsJsonArray()) {
                if (!entry.isJsonObject()) return false;
                JsonObject weighted = entry.getAsJsonObject();
                if (!primitive(weighted.get("id"), "string") || !integer(weighted.get("weight"), false)) return false;
            }
        }
        return true;
    }

    private static boolean primitive(JsonElement element, String type) {
        if (element == null || !element.isJsonPrimitive()) return false;
        JsonPrimitive value = element.getAsJsonPrimitive();
        return switch (type) {
            case "boolean" -> value.isBoolean();
            case "number" -> value.isNumber();
            case "string" -> value.isString();
            default -> false;
        };
    }

    private static boolean integer(JsonElement element, boolean wide) {
        if (!primitive(element, "number")) return false;
        try {
            BigDecimal number = element.getAsBigDecimal();
            if (wide) number.longValueExact(); else number.intValueExact();
            return true;
        } catch (NumberFormatException | ArithmeticException error) {
            return false;
        }
    }

    /** Check the original number before float rounding can hide an out-of-range value. */
    private static boolean chaos(JsonElement element) {
        if (!primitive(element, "number")) return false;
        try {
            BigDecimal number = element.getAsBigDecimal();
            return number.compareTo(BigDecimal.ONE.negate()) >= 0
                    && number.compareTo(BigDecimal.ONE) <= 0;
        } catch (NumberFormatException | ArithmeticException error) {
            return false;
        }
    }
}
