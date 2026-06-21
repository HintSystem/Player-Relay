package dev.hintsystem.playerrelay.mixin.minecraft;

import dev.hintsystem.playerrelay.ClientCore;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void playerrelay$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (action != 0) ClientCore.updateInputActivity();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void playerrelay$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        ClientCore.updateInputActivity();
    }
}