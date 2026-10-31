package com.retile.client.mixin;
import com.retile.brush.BrushSelection;
import com.retile.client.RetileClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve vanilla's queued clicks until END_CLIENT_TICK; otherwise handleKeybinds consumes them first. */
@Mixin(Minecraft.class)
public abstract class BrushInputMixin {
    @Shadow @Final public Options options;
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
    private boolean brush$deferAttack(net.minecraft.client.KeyMapping key) {
        Minecraft client = (Minecraft) (Object) this;
        if (key == options.keyAttack && client.screen == null && BrushSelection.isBrush(client.player) && !client.player.isUsingItem()) return false;
        return key.consumeClick();
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void brush$stopHeldAttack(boolean held, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (BrushSelection.isBrush(client.player)) { if (client.gameMode != null) client.gameMode.stopDestroyBlock(); ci.cancel(); }
    }
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void brush$endOnScreen(Screen screen, CallbackInfo ci) {
        if (screen != null) RetileClient.endStroke();
    }
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void brush$endOnDisconnect(Screen screen, boolean transferring, boolean clearState, CallbackInfo ci) {
        RetileClient.endStroke();
    }
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void brush$endOnWorldChange(ClientLevel level, CallbackInfo ci) {
        if (((Minecraft) (Object) this).level != level) RetileClient.endStroke();
    }
}
