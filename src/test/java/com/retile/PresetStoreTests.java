package com.retile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.retile.client.config.PresetStore;
import com.retile.client.config.PresetStore.Entry;
import com.retile.client.config.PresetStore.Preset;
import com.retile.client.config.PresetStore.SaveResult;

/** Plain Java and Gson tests; no game classes or filesystem access. */
public final class PresetStoreTests {
    private static int passed;
    private static final Entry STONE = new Entry("minecraft:stone", 1);

    public static void main(String[] args) {
        validation();
        limitsAndOverwrite();
        immutableSnapshots();
        roundtrip();
        damagedJson();
        recoveryLimits();
        System.out.println(passed + " preset tests passed");
    }

    private static void validation() {
        PresetStore store = new PresetStore();
        check(store.list().isEmpty(), "New store is empty");
        for (String name : new String[]{null, "", " \t\n", "\u2003"}) {
            result(store, name, List.of(STONE), SaveResult.EMPTY_NAME);
        }
        result(store, "x".repeat(33), List.of(STONE), SaveResult.NAME_TOO_LONG);
        result(store, "empty", null, SaveResult.EMPTY_PALETTE);
        result(store, "empty", List.of(), SaveResult.EMPTY_PALETTE);
        for (Entry invalid : new Entry[]{null, new Entry(null, 1), new Entry("", 1),
                new Entry(" \t", 1), new Entry("minecraft:stone", 0),
                new Entry("minecraft:stone", 101), new Entry("minecraft:stone", Integer.MIN_VALUE)}) {
            result(store, "broken", Arrays.asList(STONE, invalid), SaveResult.INVALID_ENTRY);
        }
        check(store.list().isEmpty(), "Rejected saves do not alter the store");
        result(store, " \u2003Palette\u2003 ", List.of(STONE, new Entry("custom:block", 100)), SaveResult.SAVED);
        check(store.list().getFirst().name().equals("Palette"), "Names are stripped");
        result(store, "x".repeat(32), List.of(STONE), SaveResult.SAVED);
        result(store, "Palette", List.of(new Entry("invalid", -1)), SaveResult.INVALID_ENTRY);
        check(store.list().getFirst().blocks().getFirst().equals(STONE), "Invalid overwrite preserves old data");
        check(!store.remove(null) && !store.remove("missing"), "Removing absent names returns false");
        check(store.remove(" Palette ") && !store.remove("Palette"), "Removal strips the name");
        pass("validation, name normalization, and atomic rejection");
    }

    private static void limitsAndOverwrite() {
        PresetStore store = new PresetStore();
        List<Entry> blocks = new ArrayList<>();
        for (int i = 0; i < PresetStore.MAX_BLOCKS; i++) blocks.add(new Entry("custom:block_" + i, i % 100 + 1));
        result(store, "full palette", blocks, SaveResult.SAVED);
        blocks.add(STONE);
        result(store, "too many", blocks, SaveResult.PALETTE_TOO_LARGE);
        check(store.list().getFirst().blocks().size() == 64, "Palette maximum is inclusive");
        for (int i = 1; i < PresetStore.MAX_PRESETS; i++) {
            result(store, "palette " + i, List.of(STONE), SaveResult.SAVED);
        }
        result(store, "new palette", List.of(STONE), SaveResult.LIMIT_REACHED);
        List<Entry> replacement = List.of(new Entry("minecraft:dirt", 100));
        result(store, " full palette ", replacement, SaveResult.SAVED);
        check(store.list().size() == 32 && store.list().getFirst().blocks().equals(replacement),
                "Existing name overwrites at the full preset limit, in its original position");
        check(store.remove("palette 1"), "Existing preset is removed");
        result(store, "new palette", List.of(STONE), SaveResult.SAVED);
        check(store.list().size() == 32, "Removal frees a slot");
        pass("64 entries, 32 presets, overwrite at capacity, and slot reuse");
    }

    private static void immutableSnapshots() {
        PresetStore store = new PresetStore();
        List<Entry> source = new ArrayList<>(List.of(STONE));
        result(store, "first", source, SaveResult.SAVED);
        List<Preset> snapshot = store.list();
        source.set(0, new Entry("minecraft:dirt", 5));
        source.add(new Entry("minecraft:glass", 10));
        check(snapshot.getFirst().blocks().equals(List.of(STONE)), "Saved palettes copy the source list");
        immutable(() -> snapshot.add(new Preset("other", List.of(STONE))), "Preset list is immutable");
        immutable(() -> snapshot.getFirst().blocks().add(STONE), "Palette list is immutable");
        result(store, "first", source, SaveResult.SAVED);
        result(store, "second", List.of(STONE), SaveResult.SAVED);
        check(snapshot.size() == 1 && snapshot.getFirst().blocks().equals(List.of(STONE)),
                "Old snapshots survive additions and overwrites");
        Preset direct = new Preset("direct", source);
        source.clear();
        check(direct.blocks().size() == 2, "Public Preset constructor also copies its list");
        immutable(() -> direct.blocks().clear(), "Direct Preset palettes are immutable");
        pass("immutable snapshots and defensive copies");
    }

    private static void roundtrip() {
        PresetStore store = new PresetStore();
        List<Entry> repeated = List.of(STONE, new Entry("minecraft:stone", 100), new Entry("custom:\u0431\u043b\u043e\u043a", 30));
        result(store, "\u041c\u043e\u0439 \u043f\u0440\u0435\u0441\u0435\u0442", repeated, SaveResult.SAVED);
        result(store, "another", List.of(new Entry("minecraft:glass", 10)), SaveResult.SAVED);
        JsonArray json = store.toJson();
        check(PresetStore.fromJson(JsonParser.parseString(json.toString())).list().equals(store.list()),
                "Names, entry order, duplicate ids, and weights roundtrip");
        JsonObject first = json.get(0).getAsJsonObject();
        check(first.size() == 2 && first.has("name") && first.has("blocks") && !first.has("filters"),
                "Preset JSON contains only the name and palette");
        JsonObject entry = first.getAsJsonArray("blocks").get(0).getAsJsonObject();
        check(entry.size() == 2 && entry.get("id").getAsJsonPrimitive().isString()
                && entry.get("weight").getAsJsonPrimitive().isNumber(), "Typed id and weight fields");
        json.remove(0);
        check(store.list().size() == 2 && store.toJson().size() == 2, "JSON output is independent of the store");
        pass("roundtrip, duplicate preservation, and independent JSON output");
    }

    private static void damagedJson() {
        check(PresetStore.fromJson(null).list().isEmpty(), "Missing presets field yields an empty list");
        for (String invalid : new String[]{"null", "{}", "true", "42", "\"array\""}) {
            check(load(invalid).list().isEmpty(), "Wrong presets type: " + invalid);
        }
        for (String invalid : new String[]{"null", "1", "[]", "{}", "{\"name\":\"missing blocks\"}",
                "{\"name\":null,\"blocks\":[]}", "{\"name\":1,\"blocks\":[]}",
                "{\"name\":\" \u2003 \",\"blocks\":[]}",
                "{\"name\":\"" + "n".repeat(33) + "\",\"blocks\":[]}",
                "{\"name\":\"bad\",\"blocks\":null}", "{\"name\":\"bad\",\"blocks\":{}}",
                "{\"name\":\"bad\",\"blocks\":[]}"}) {
            PresetStore recovered = load("[" + invalid + "," + palette("good", List.of(STONE)) + "]");
            check(recovered.list().size() == 1 && recovered.list().getFirst().name().equals("good"),
                    "Bad preset cannot hide a later valid palette: " + invalid);
        }
        for (String invalid : new String[]{"null", "true", "1", "[]", "{}", "{\"id\":\"stone\"}",
                "{\"weight\":1}", "{\"id\":null,\"weight\":1}", "{\"id\":7,\"weight\":1}",
                "{\"id\":false,\"weight\":1}", "{\"id\":\"\",\"weight\":1}",
                "{\"id\":\" \",\"weight\":1}", "{\"id\":\"stone\",\"weight\":null}",
                "{\"id\":\"stone\",\"weight\":\"1\"}", "{\"id\":\"stone\",\"weight\":true}",
                "{\"id\":\"stone\",\"weight\":[]}", "{\"id\":\"stone\",\"weight\":0}",
                "{\"id\":\"stone\",\"weight\":101}", "{\"id\":\"stone\",\"weight\":1.5}",
                "{\"id\":\"stone\",\"weight\":1.0000000000000001}",
                "{\"id\":\"stone\",\"weight\":4294967297}",
                "{\"id\":\"stone\",\"weight\":-2147483649}",
                "{\"id\":\"stone\",\"weight\":1e1000}"}) {
            PresetStore recovered = load("[{\"name\":\"good\",\"blocks\":[" + invalid
                    + ",{\"id\":\"minecraft:stone\",\"weight\":1}]}]");
            check(recovered.list().size() == 1 && recovered.list().getFirst().blocks().equals(List.of(STONE)),
                    "Bad entry skipped without coercion or loss of valid entries: " + invalid);
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            JsonObject object = palette("bad numbers", List.of(STONE));
            object.getAsJsonArray("blocks").get(0).getAsJsonObject().add("weight", new JsonPrimitive(invalid));
            JsonArray array = new JsonArray();
            array.add(object);
            array.add(palette("good", List.of(STONE)));
            check(PresetStore.fromJson(array).list().getFirst().name().equals("good"), "Nonfinite weights rejected");
        }
        PresetStore unknown = load("[{\"name\":\"unknown fields\",\"filters\":42,\"blocks\":["
                + "{\"id\":\"stone\",\"weight\":1,\"other\":null}]}]");
        check(unknown.list().size() == 1, "Unknown fields are ignored");
        result(unknown, "usable", List.of(STONE), SaveResult.SAVED);
        check(unknown.remove("unknown fields"), "Recovered store remains usable");
        pass("missing fields, damaged elements, strict types, fractions, overflow, and recovery");
    }

    private static void recoveryLimits() {
        JsonArray array = new JsonArray();
        List<Entry> large = new ArrayList<>();
        for (int i = 0; i < 70; i++) large.add(new Entry("custom:block_" + i, 1));
        JsonObject object = palette("large", large);
        JsonArray blocks = new JsonArray();
        for (JsonElement block : object.getAsJsonArray("blocks")) {
            blocks.add(JsonNull.INSTANCE);
            blocks.add(block);
        }
        object.add("blocks", blocks);
        array.add(object);
        for (int i = 1; i < 40; i++) array.add(palette("palette " + i, List.of(STONE)));
        PresetStore store = PresetStore.fromJson(array);
        check(store.list().size() == 32, "Recovery keeps at most 32 unique palettes");
        check(store.list().getFirst().blocks().size() == 64
                && store.list().getFirst().blocks().getLast().id().equals("custom:block_63"),
                "Recovery keeps the first 64 valid entries, ignoring malformed entries");
        JsonObject invalidOverwrite = palette("large", List.of(STONE));
        invalidOverwrite.add("blocks", new JsonArray());
        array.add(invalidOverwrite);
        check(PresetStore.fromJson(array).list().getFirst().blocks().size() == 64,
                "Malformed duplicate does not replace a valid palette");
        array.add(palette(" large ", List.of(new Entry("minecraft:dirt", 100))));
        store = PresetStore.fromJson(array);
        check(store.list().size() == 32 && store.list().getFirst().blocks().equals(List.of(new Entry("minecraft:dirt", 100))),
                "Later valid duplicate replaces an existing name even when recovery is at capacity");
        pass("recovery limits count valid entries and unique names; valid duplicates overwrite");
    }

    private static PresetStore load(String json) {
        return PresetStore.fromJson(JsonParser.parseString(json));
    }

    private static JsonObject palette(String name, List<Entry> blocks) {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        JsonArray array = new JsonArray();
        for (Entry entry : blocks) {
            JsonObject block = new JsonObject();
            block.addProperty("id", entry.id());
            block.addProperty("weight", entry.weight());
            array.add(block);
        }
        object.add("blocks", array);
        return object;
    }

    private static void result(PresetStore store, String name, List<Entry> blocks, SaveResult expected) {
        check(store.save(name, blocks) == expected, "Expected " + expected + " for name " + name);
    }

    private static void immutable(Runnable mutation, String message) {
        try {
            mutation.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
            // Expected immutable snapshot.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }
}
