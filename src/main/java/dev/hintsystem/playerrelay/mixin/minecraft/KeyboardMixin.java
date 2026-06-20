package dev.hintsystem.playerrelay.mixin.minecraft;

import dev.hintsystem.playerrelay.ClientCore;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
        // Only count press or repeat (not release)
        if (action != 0) ClientCore.updateInputActivity();
    }
}
