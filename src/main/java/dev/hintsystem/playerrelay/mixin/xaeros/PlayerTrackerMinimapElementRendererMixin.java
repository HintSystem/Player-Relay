package dev.hintsystem.playerrelay.mixin.xaeros;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.joml.Matrix4f;

@Mixin(PlayerTrackerMinimapElementRenderer.class)
public class PlayerTrackerMinimapElementRendererMixin {
    @Redirect(
        method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lxaero/hud/minimap/element/render/MinimapElementGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        )
    )
    private void modifyColorValue(
        Font textRenderer,
        String text,
        float x,
        float y,
        int color,
        boolean drawShadow,
        Matrix4f pose,
        MultiBufferSource bufferSource,
        Font.DisplayMode mode,
        int backgroundColor,
        int packedLightCoords,
        @Local(argsOnly = true) PlayerTrackerMinimapElement<?> e
    ) {
        int newColor = color;
        boolean newShadow = drawShadow;

        PlayerInfoPayload playerInfo = CommonCore.connections.getPlayer(e.getPlayerId());
        if (playerInfo != null) {
            newColor = playerInfo.getNameColor();
            newShadow = true;
        }

        textRenderer.drawInBatch(
            text, x, y, newColor, newShadow, pose,
            bufferSource, mode, backgroundColor, packedLightCoords
        );
    }
}
