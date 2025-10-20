package dev.hintsystem.playerrelay.mixin.pingwheel;

import dev.hintsystem.playerrelay.cast.pingwheel.DrawContextAccessor;

import nx.pingwheel.common.core.PingView;
import nx.pingwheel.common.render.DirectionIndicatorRenderer;
import nx.pingwheel.common.render.DrawContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DirectionIndicatorRenderer.class, remap = false)
public class DirectionIndicatorRendererMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private static void captureAuthorId(DrawContext ctx, PingView ping, CallbackInfo ci) {
        // Store the authorId in DrawContext before renderPing is called
        ((DrawContextAccessor) ctx).playerrelay$setAuthorId(ping.authorId);
    }
}
