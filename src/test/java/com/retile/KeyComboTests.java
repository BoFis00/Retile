package com.retile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.retile.client.config.KeyCombo;

/** Plain Java and Gson tests; numeric GLFW codes do not load GLFW. */
public final class KeyComboTests {
    private static int passed;

    public static void main(String[] args) {
        supportedKeysAndConstructor();
        exactMatching();
        roundtrip();
        damagedJson();
        System.out.println(passed + " key combo tests passed");
    }

    private static void supportedKeysAndConstructor() {
        check(KeyCombo.SHIFT == 1 && KeyCombo.CTRL == 2 && KeyCombo.ALT == 4 && KeyCombo.MODIFIER_MASK == 7,
                "Modifier bits agree with GLFW input modifiers");
        for (int code : new int[]{32, 39, 44, 45, 46, 47, 48, 57, 59, 61, 65, 90, 91, 92, 93, 96,
                161, 162, 256, 257, 269, 280, 284, 290, 314, 320, 336, 348}) {
            for (int mask = 0; mask <= 7; mask++) {
                KeyCombo combo = new KeyCombo(code, mask);
                check(combo.isAssigned() && combo.keyCode() == code && combo.modifiers() == mask,
                        "Supported key and mask: " + code + "/" + mask);
            }
        }
        for (int code : new int[]{Integer.MIN_VALUE, -2, 0, 31, 33, 38, 40, 43, 58, 60, 62, 64, 94, 95,
                97, 160, 163, 255, 270, 279, 285, 289, 315, 319, 337, 338, 339, 349, Integer.MAX_VALUE}) {
            invalid(code, 0);
        }
        for (int modifierKey = 340; modifierKey <= 347; modifierKey++) invalid(modifierKey, KeyCombo.CTRL);
        for (int mask : new int[]{Integer.MIN_VALUE, -1, 8, 15, Integer.MAX_VALUE}) invalid(90, mask);
        for (int mask = 1; mask <= 7; mask++) invalid(-1, mask);
        check(!new KeyCombo(-1, 0).isAssigned() && new KeyCombo(-1, 0).equals(KeyCombo.UNASSIGNED),
                "Only -1 without modifiers is unassigned");
        pass("supported keys, excluded modifier keys, and constructor validation");
    }

    private static void exactMatching() {
        for (int mask = 0; mask <= 7; mask++) {
            KeyCombo combo = new KeyCombo(90, mask);
            for (int pressed = 0; pressed <= 15; pressed++) {
                check(combo.matches(true, pressed) == (pressed == mask), "Exact modifier matching " + mask + "/" + pressed);
                check(!combo.matches(false, pressed), "Released key cannot match");
                check(!KeyCombo.UNASSIGNED.matches(true, pressed), "Unassigned combo cannot match");
            }
            check(!combo.matches(true, -1) && !combo.matches(true, Integer.MAX_VALUE), "Unknown modifier bits are rejected");
        }
        check(new KeyCombo(90, KeyCombo.CTRL).matches(true, KeyCombo.CTRL), "Control plus Z matches");
        check(!new KeyCombo(90, KeyCombo.CTRL).matches(true, KeyCombo.CTRL | KeyCombo.SHIFT),
                "Control plus shift does not match control alone");
        check(!KeyCombo.UNASSIGNED.matches(false, 0), "Unassigned released key cannot match");
        pass("exact modifiers, extra bits, released keys, and unassigned bindings");
    }

    private static void roundtrip() {
        for (int code : new int[]{32, 90, 256, 314, 336, 348}) {
            for (int mask = 0; mask <= 7; mask++) {
                KeyCombo combo = new KeyCombo(code, mask);
                KeyCombo recovered = KeyCombo.fromJson(JsonParser.parseString(combo.toJson().toString()));
                check(combo.equals(recovered) && combo.hashCode() == recovered.hashCode(), "JSON roundtrip and record equality");
            }
        }
        check(KeyCombo.fromJson(KeyCombo.UNASSIGNED.toJson()).equals(KeyCombo.UNASSIGNED), "Unassigned roundtrip");
        JsonObject json = new KeyCombo(90, KeyCombo.CTRL).toJson();
        check(json.size() == 2 && json.get("keyCode").getAsJsonPrimitive().isNumber()
                && json.get("modifiers").getAsJsonPrimitive().isNumber(), "JSON uses numeric named fields");
        KeyCombo combo = new KeyCombo(90, 2);
        json.addProperty("keyCode", 65);
        check(combo.keyCode() == 90 && combo.toJson().get("keyCode").getAsInt() == 90, "Mutating output does not mutate combo");
        check(KeyCombo.fromJson(JsonParser.parseString("{\"keyCode\":90,\"modifiers\":2,\"extra\":null}")).equals(combo),
                "Unknown JSON fields are ignored");
        pass("JSON roundtrip, numeric format, value equality, and output independence");
    }

    private static void damagedJson() {
        check(KeyCombo.fromJson(null).equals(KeyCombo.UNASSIGNED), "Missing field yields unassigned");
        for (String invalid : new String[]{"null", "[]", "true", "42", "\"binding\"", "{}",
                "{\"keyCode\":90}", "{\"modifiers\":2}",
                "{\"keyCode\":null,\"modifiers\":2}", "{\"keyCode\":\"90\",\"modifiers\":2}",
                "{\"keyCode\":true,\"modifiers\":2}", "{\"keyCode\":[],\"modifiers\":2}",
                "{\"keyCode\":{},\"modifiers\":2}", "{\"keyCode\":90,\"modifiers\":null}",
                "{\"keyCode\":90,\"modifiers\":\"2\"}", "{\"keyCode\":90,\"modifiers\":false}",
                "{\"keyCode\":90,\"modifiers\":[]}", "{\"keyCode\":90,\"modifiers\":{}}",
                "{\"keyCode\":90.5,\"modifiers\":2}", "{\"keyCode\":90.00000000000001,\"modifiers\":2}",
                "{\"keyCode\":4294967386,\"modifiers\":2}", "{\"keyCode\":-2147483649,\"modifiers\":2}",
                "{\"keyCode\":1e1000,\"modifiers\":2}", "{\"keyCode\":90,\"modifiers\":2.5}",
                "{\"keyCode\":90,\"modifiers\":2.00000000000001}", "{\"keyCode\":90,\"modifiers\":4294967298}",
                "{\"keyCode\":90,\"modifiers\":1e1000}", "{\"keyCode\":90,\"modifiers\":-1}",
                "{\"keyCode\":90,\"modifiers\":8}", "{\"keyCode\":340,\"modifiers\":0}",
                "{\"keyCode\":347,\"modifiers\":2}", "{\"keyCode\":349,\"modifiers\":0}",
                "{\"keyCode\":-1,\"modifiers\":2}"}) {
            check(KeyCombo.fromJson(JsonParser.parseString(invalid)).equals(KeyCombo.UNASSIGNED),
                    "Invalid JSON must fall back without throwing: " + invalid);
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (String field : new String[]{"keyCode", "modifiers"}) {
                JsonObject json = new KeyCombo(90, 2).toJson();
                json.add(field, new JsonPrimitive(invalid));
                check(KeyCombo.fromJson(json).equals(KeyCombo.UNASSIGNED), "Nonfinite field must fall back: " + field);
            }
        }
        pass("missing fields, incorrect types, fractions, overflow, unsupported values, and nonfinite numbers");
    }

    private static void invalid(int code, int modifiers) {
        try {
            new KeyCombo(code, modifiers);
            throw new AssertionError("Invalid combo accepted: " + code + "/" + modifiers);
        } catch (IllegalArgumentException expected) {
            // Expected invalid constructor arguments.
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
