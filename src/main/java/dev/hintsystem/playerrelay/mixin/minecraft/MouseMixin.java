package dev.hintsystem.playerrelay.mixin.minecraft;

import dev.hintsystem.playerrelay.ClientCore;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action != 0) ClientCore.updateInputActivity();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        ClientCore.updateInputActivity();
    }
}