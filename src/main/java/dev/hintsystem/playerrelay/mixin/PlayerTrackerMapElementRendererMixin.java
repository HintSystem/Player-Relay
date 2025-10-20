package dev.hintsystem.playerrelay.mixin;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.client.font.TextRenderer;

import xaero.map.element.MapElementGraphics;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.radar.tracker.PlayerTrackerMapElementRenderer;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerTrackerMapElementRenderer.class)
public class PlayerTrackerMapElementRendererMixin {
    @Redirect(
        method = "renderElement(Lxaero/map/radar/tracker/PlayerTrackerMapElement;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lxaero/map/element/MapElementGraphics;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/map/element/MapElementGraphics;drawString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"
        )
    )
    private void modifyColorValue(
        MapElementGraphics instance,
        TextRenderer font,
        String text,
        int x,
        int y,
        int color,
        @Local(argsOnly = true) PlayerTrackerMapElement<?> e
    ) {
        // Extract alpha from original color (highest 8 bits)
        int originalAlpha = (color >> 24) & 0xFF;

        int newRgbColor = 0xFFFFFF;
        boolean newShadow = false;
        PlayerInfoPayload playerInfo = PlayerRelay.getNetworkManager().connectedPlayers.get(e.getPlayerId());
        if (playerInfo != null) {
            newRgbColor = playerInfo.getNameColor() & 0xFFFFFF; // Strip any alpha from custom color
            newShadow = true;
        }

        // Combine original alpha with new RGB
        int finalColor = (originalAlpha << 24) | newRgbColor;

        instance.drawString(font, text, x, y, finalColor, newShadow);
    }
}
