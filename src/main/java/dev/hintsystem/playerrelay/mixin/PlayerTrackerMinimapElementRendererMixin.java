package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.font.TextRenderer;

import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer;

import org.joml.Matrix4f;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerTrackerMinimapElementRenderer.class)
public class PlayerTrackerMinimapElementRendererMixin {
    @Redirect(
        method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lxaero/hud/minimap/element/render/MinimapElementGraphics;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V"
        )
    )
    private void modifyColorValue(
        TextRenderer textRenderer,
        String text,
        float x,
        float y,
        int color,
        boolean shadow,
        Matrix4f matrix,
        VertexConsumerProvider vertexConsumers,
        TextRenderer.TextLayerType layerType,
        int backgroundColor,
        int light,
        @Local(argsOnly = true) PlayerTrackerMinimapElement<?> e
    ) {
        int newColor = color;
        boolean newShadow = shadow;

        PlayerInfoPayload playerInfo = PlayerRelay.getNetworkManager().connectedPlayers.get(e.getPlayerId());
        if (playerInfo != null) {
            newColor = playerInfo.getNameColor();
            newShadow = true;
        }

        textRenderer.draw(
            text, x, y, newColor, newShadow, matrix,
            vertexConsumers, layerType, backgroundColor, light
        );
    }
}
