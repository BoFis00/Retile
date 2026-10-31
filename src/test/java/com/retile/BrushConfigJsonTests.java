package com.retile;

import com.retile.client.config.BrushConfigJson;
import com.retile.client.config.BrushConfigJson.Data;
import com.retile.client.config.BrushConfigJson.WeightedEntry;
import com.retile.client.config.KeyCombo;
import com.retile.client.config.PresetStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Gson-only runner; temporary files stay under build/test-config. */
public final class BrushConfigJsonTests {
    private static int passed;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void defaults(Data data, String message) {
        check(data != null && data.filters != null && data.filters.isEmpty() && !data.includeAir
                && data.replacements != null && data.replacements.isEmpty() && data.chaos == 0
                && !data.limitHeight && data.minY == -64 && data.maxY == 320 && data.radius == 5
                && "SPHERE".equals(data.shape) && !data.fixedSeed && data.seed == 0
                && data.presets.list().isEmpty() && !data.undoKey.isAssigned() && !data.redoKey.isAssigned(), message);
    }

    private static void equal(Data expected, Data actual) {
        check(expected.filters.equals(actual.filters), "Filters");
        check(expected.includeAir == actual.includeAir, "Include air");
        check(expected.replacements.equals(actual.replacements), "Replacement ids and weights");
        check(expected.chaos == actual.chaos, "Chaos");
        check(expected.limitHeight == actual.limitHeight, "Height toggle");
        check(expected.minY == actual.minY && expected.maxY == actual.maxY, "Height bounds");
        check(expected.radius == actual.radius && expected.shape.equals(actual.shape), "Radius and shape");
        check(expected.fixedSeed == actual.fixedSeed && expected.seed == actual.seed, "Seed and toggle");
        check(expected.presets.list().equals(actual.presets.list()), "Presets");
        check(expected.undoKey.equals(actual.undoKey) && expected.redoKey.equals(actual.redoKey), "Hotkeys");
    }

    public static void main(String[] args) throws IOException {
        Path directory = Path.of("build", "test-config");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "brush-config-", ".json");
        try {
            Data settings = new Data();
            settings.filters = new ArrayList<>(List.of("minecraft:stone", "custom:блок"));
            settings.includeAir = true;
            settings.replacements = new ArrayList<>(List.of(new WeightedEntry("minecraft:quartz_block", 1),
                    new WeightedEntry("minecraft:white_wool", 100)));
            settings.chaos = -.625f;
            settings.limitHeight = true;
            settings.minY = -20; settings.maxY = 140; settings.radius = 32;
            settings.shape = "CYLINDER"; settings.fixedSeed = true; settings.seed = Long.MIN_VALUE;
            BrushConfigJson.save(file, settings);
            equal(settings, BrushConfigJson.load(file));
            String json = Files.readString(file, StandardCharsets.UTF_8);
            check(json.contains("\n") && json.contains("custom:блок"), "Pretty UTF-8 JSON");
            settings.chaos = 1; settings.radius = 1; settings.shape = "SPHERE"; settings.seed = Long.MAX_VALUE;
            BrushConfigJson.save(file, settings);
            equal(settings, BrushConfigJson.load(file));
            pass("all fields roundtrip and replacement save");

            Path missing = file.resolveSibling(file.getFileName() + ".missing");
            defaults(BrushConfigJson.load(missing), "Absent config");
            defaults(BrushConfigJson.load(null), "Null path");
            Data first = BrushConfigJson.load(missing), second = BrushConfigJson.load(missing);
            first.filters.add("changed"); first.replacements.add(new WeightedEntry("test", 2));
            defaults(second, "Defaults must be fresh objects");
            pass("absent file and independent defaults");

            for (String broken : new String[]{"", " ", "null", "{\"includeAir\":true,broken", "{\"includeAir\":true,\"radius\":",
                    "[]", "42", "true", "{\"includeAir\":true} {}",
                    "{\"includeAir\":true,radius:5}", "{\"includeAir\":true,'radius':5}",
                    "{\"includeAir\":true,\"radius\":5,}", "/* comment */ {\"includeAir\":true}",
                    "{\"limitHeight\":true,\"includeAir\":TRUE}", "{\"includeAir\":true,\"filters\":[\"custom:line\nbreak\"]}",
                    "{\"includeAir\":true,\"filters\":[\"custom:tab\tblock\"]}"}) {
                Files.writeString(file, broken, StandardCharsets.UTF_8);
                defaults(BrushConfigJson.load(file), "Broken JSON: " + broken);
            }
            pass("broken empty null and non-object JSON");

            for (String invalid : new String[]{
                    "\"filters\":null", "\"filters\":{}", "\"filters\":[null]", "\"filters\":[1]", "\"filters\":[\"\"]",
                    "\"replacements\":null", "\"replacements\":[null]", "\"replacements\":[{}]",
                    "\"replacements\":[{\"id\":null,\"weight\":1}]", "\"replacements\":[{\"id\":2,\"weight\":1}]",
                    "\"replacements\":[{\"id\":\"stone\",\"weight\":0}]", "\"replacements\":[{\"id\":\"stone\",\"weight\":101}]",
                    "\"replacements\":[{\"id\":\"stone\",\"weight\":1.5}]", "\"replacements\":[{\"id\":\"stone\",\"weight\":\"1\"}]",
                    "\"shape\":\"CUBE\"", "\"shape\":null", "\"shape\":5", "\"radius\":0", "\"radius\":33",
                    "\"radius\":1.5", "\"radius\":\"5\"", "\"radius\":4294967301", "\"chaos\":1.1",
                    "\"chaos\":-1.1", "\"chaos\":1.00000001", "\"chaos\":-1.00000001",
                    "\"chaos\":1e1000", "\"chaos\":\"NaN\"", "\"chaos\":null",
                    "\"includeAir\":\"true\"", "\"limitHeight\":1", "\"fixedSeed\":null", "\"minY\":0.5",
                    "\"maxY\":2147483648", "\"seed\":9223372036854775808", "\"seed\":1.5", "\"seed\":\"2\""}) {
                String marker = invalid.startsWith("\"includeAir\":") ? "\"limitHeight\":true," : "\"includeAir\":true,";
                Files.writeString(file, "{" + marker + invalid + "}", StandardCharsets.UTF_8);
                defaults(BrushConfigJson.load(file), "Invalid field: " + invalid);
            }
            Files.writeString(file, "{\"includeAir\":true,\"radius\":0}", StandardCharsets.UTF_8);
            defaults(BrushConfigJson.load(file), "Invalid config must reset every field");
            pass("invalid types ranges and overflow reset full config");

            for (String duplicate : new String[]{
                    "\"radius\":3,\"radius\":7", "\"radius\":null,\"radius\":7", "\"radius\":1.5,\"radius\":7",
                    "\"seed\":9223372036854775808,\"seed\":7",
                    "\"replacements\":[{\"id\":\"stone\",\"id\":\"dirt\",\"weight\":1}]",
                    "\"replacements\":[{\"id\":null,\"id\":\"stone\",\"weight\":1}]",
                    "\"replacements\":[{\"id\":\"stone\",\"weight\":1.5,\"weight\":1}]",
                    "\"replacements\":[{\"id\":\"stone\",\"weight\":4294967297,\"weight\":1}]"}) {
                Files.writeString(file, "{\"includeAir\":true," + duplicate + "}", StandardCharsets.UTF_8);
                defaults(BrushConfigJson.load(file), "Duplicate key must reset full config: " + duplicate);
            }
            pass("duplicate root and replacement keys reset full config");

            for (String bound : new String[]{"-1", "1"}) {
                Files.writeString(file, "{\"includeAir\":true,\"chaos\":" + bound + "}", StandardCharsets.UTF_8);
                Data boundary = BrushConfigJson.load(file);
                check(boundary.includeAir && boundary.chaos == Float.parseFloat(bound), "Chaos bound: " + bound);
            }
            pass("chaos accepts both exact bounds");

            Data full = new Data();
            for (int i = 0; i < 64; i++) {
                full.filters.add("custom:block_" + i);
                full.replacements.add(new WeightedEntry("custom:block_" + i, i + 1));
            }
            BrushConfigJson.save(file, full); equal(full, BrushConfigJson.load(file));
            json = Files.readString(file, StandardCharsets.UTF_8);
            Files.writeString(file, json.replace("\"filters\": [", "\"filters\": [\"custom:extra\","), StandardCharsets.UTF_8);
            defaults(BrushConfigJson.load(file), "Too many filters");
            Files.writeString(file, json.replace("\"replacements\": [", "\"replacements\": [{\"id\":\"custom:extra\",\"weight\":1},"), StandardCharsets.UTF_8);
            defaults(BrushConfigJson.load(file), "Too many replacements");
            pass("list limits include exact boundary");

            Files.writeString(file, "{\"includeAir\":true}", StandardCharsets.UTF_8);
            Data partial = BrushConfigJson.load(file);
            check(partial.includeAir && partial.radius == 5 && partial.minY == -64 && partial.maxY == 320
                    && partial.filters.isEmpty() && partial.replacements.isEmpty(), "Missing fields use defaults");
            pass("partial config preserves defaults");

            check(partial.presets.list().isEmpty() && !partial.undoKey.isAssigned() && !partial.redoKey.isAssigned(),
                    "Old config without extra fields");
            partial.presets.save("Stone", List.of(new PresetStore.Entry("minecraft:stone", 75)));
            partial.undoKey = new KeyCombo(90, KeyCombo.CTRL);
            partial.redoKey = new KeyCombo(90, KeyCombo.CTRL | KeyCombo.SHIFT);
            BrushConfigJson.save(file, partial); equal(partial, BrushConfigJson.load(file));
            json = Files.readString(file, StandardCharsets.UTF_8);
            check(json.contains("\"presets\": [") && json.contains("\"blocks\": [")
                    && json.contains("\"undoKey\": {") && !json.contains("\"presets\": {"), "Explicit JSON formats");
            for (String extra : new String[]{"\"presets\":null", "\"presets\":{}", "\"presets\":[null,{}]",
                    "\"undoKey\":{\"keyCode\":\"90\",\"modifiers\":2}",
                    "\"redoKey\":{\"keyCode\":90,\"modifiers\":8}"}) {
                Files.writeString(file, "{\"includeAir\":true," + extra + "}", StandardCharsets.UTF_8);
                Data recovered = BrushConfigJson.load(file);
                check(recovered.includeAir && recovered.presets.list().isEmpty() && !recovered.undoKey.isAssigned()
                        && !recovered.redoKey.isAssigned(), "Damaged extra field preserves original settings");
            }
            Files.writeString(file, "{\"undoKey\":{\"keyCode\":90,\"modifiers\":2},"
                    + "\"redoKey\":{\"keyCode\":90,\"modifiers\":2}}", StandardCharsets.UTF_8);
            Data conflict = BrushConfigJson.load(file);
            check(conflict.undoKey.isAssigned() && !conflict.redoKey.isAssigned(), "Recover duplicate bindings");
            pass("new fields roundtrip compatibility corruption and binding conflicts");

            Data invalid = new Data(); invalid.chaos = Float.NaN;
            String original = Files.readString(file, StandardCharsets.UTF_8);
            try { BrushConfigJson.save(file, invalid); throw new AssertionError("Invalid save accepted"); }
            catch (IllegalArgumentException expected) { /* expected */ }
            check(Files.readString(file, StandardCharsets.UTF_8).equals(original), "Invalid save must preserve previous file");
            pass("invalid save preserves file");
        } finally {
            Files.deleteIfExists(file);
        }
        System.out.println(passed + " tests passed");
    }

    private static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }
}
