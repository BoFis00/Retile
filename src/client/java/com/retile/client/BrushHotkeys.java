package com.retile.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.retile.client.config.KeyCombo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Read-only keyboard polling; does not consume or change Minecraft's input bindings. */
public final class BrushHotkeys {
    private static boolean undoDown, redoDown;
    private BrushHotkeys() {}

    public static void tick(Minecraft client) {
        Window window = client.getWindow();
        int modifiers = modifiers(window);
        boolean undo = keyDown(window, BrushConfig.undoKey);
        boolean redo = keyDown(window, BrushConfig.redoKey);
        // Track while a screen is open too, so closing it with a held key cannot fire an action.
        if (client.screen == null && client.getOverlay() == null && client.isWindowActive()
                && client.level != null && client.player != null && client.player.isCreative()) {
            if (!undoDown && BrushConfig.undoKey.matches(undo, modifiers)) RetileClient.sendHistory(false);
            if (!redoDown && BrushConfig.redoKey.matches(redo, modifiers)) RetileClient.sendHistory(true);
        }
        undoDown = undo;
        redoDown = redo;
    }

    private static boolean keyDown(Window window, KeyCombo combo) {
        return combo.isAssigned() && InputConstants.isKeyDown(window, combo.keyCode());
    }
    public static int modifiers(Window window) {
        int result = 0;
        if (down(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) result |= KeyCombo.CTRL;
        if (down(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) result |= KeyCombo.SHIFT;
        if (down(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) result |= KeyCombo.ALT;
        return result;
    }
    private static boolean down(Window window, int left, int right) {
        return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
    }
    public static Component label(KeyCombo combo) {
        if (!combo.isAssigned()) return Component.translatable("retile.gui.key_unassigned");
        Component label = Component.empty();
        if ((combo.modifiers() & KeyCombo.CTRL) != 0) label = label.copy().append(Component.translatable("retile.gui.key_ctrl")).append("+");
        if ((combo.modifiers() & KeyCombo.SHIFT) != 0) label = label.copy().append(Component.translatable("retile.gui.key_shift")).append("+");
        if ((combo.modifiers() & KeyCombo.ALT) != 0) label = label.copy().append(Component.translatable("retile.gui.key_alt")).append("+");
        return label.copy().append(InputConstants.Type.KEYSYM.getOrCreate(combo.keyCode()).getDisplayName());
    }
}
