package com.retile.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** A keyboard key with an exact set of modifiers, without a GLFW dependency. */
public record KeyCombo(int keyCode, int modifiers) {
    public static final int SHIFT = 1;
    public static final int CTRL = 2;
    public static final int ALT = 4;
    public static final int MODIFIER_MASK = SHIFT | CTRL | ALT;
    public static final KeyCombo UNASSIGNED = new KeyCombo(-1, 0);

    public KeyCombo {
        if (modifiers < 0 || modifiers > MODIFIER_MASK) {
            throw new IllegalArgumentException("Unsupported key modifiers: " + modifiers);
        }
        if (keyCode == -1) {
            if (modifiers != 0) throw new IllegalArgumentException("An unassigned key cannot have modifiers");
        } else if (!supportedKey(keyCode)) {
            throw new IllegalArgumentException("Unsupported key code: " + keyCode);
        }
    }

    public boolean isAssigned() {
        return keyCode != -1;
    }

    public boolean matches(boolean keyDown, int pressedModifiers) {
        return isAssigned() && keyDown && pressedModifiers == modifiers;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("keyCode", keyCode);
        object.addProperty("modifiers", modifiers);
        return object;
    }

    public static KeyCombo fromJson(JsonElement json) {
        try {
            if (json == null || !json.isJsonObject()) return UNASSIGNED;
            JsonObject object = json.getAsJsonObject();
            Integer code = integer(object.get("keyCode"));
            Integer mask = integer(object.get("modifiers"));
            if (code == null || mask == null) return UNASSIGNED;
            return new KeyCombo(code, mask);
        } catch (RuntimeException malformed) {
            return UNASSIGNED;
        }
    }

    private static boolean supportedKey(int code) {
        return code == 32 || code == 39 || code >= 44 && code <= 57 || code == 59
                || code == 61 || code >= 65 && code <= 93 || code == 96
                || code == 161 || code == 162 || code >= 256 && code <= 269
                || code >= 280 && code <= 284 || code >= 290 && code <= 314
                || code >= 320 && code <= 336 || code == 348;
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
