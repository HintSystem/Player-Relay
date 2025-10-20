package dev.hintsystem.playerrelay.mixin.pingwheel;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.cast.pingwheel.DrawContextAccessor;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;

import nx.pingwheel.common.render.DrawContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(DrawContext.class)
public class DrawContextMixin implements DrawContextAccessor {
    @Unique
    private UUID playerrelay$authorId;

    @Unique
    public void playerrelay$setAuthorId(UUID authorId) {
        this.playerrelay$authorId = authorId;
    }

    @Redirect(
        method = "renderLabel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
        )
    )
    private void modifyTextColor(net.minecraft.client.gui.DrawContext instance, TextRenderer textRenderer,
                                 Text text, int x, int y, int color, boolean shadow) {
        int newColor = color;
        boolean newShadow = shadow;

        PlayerInfoPayload playerInfo = PlayerRelay.getConnectedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) {
            newColor = playerInfo.getNameColor();
            newShadow = true;
        }

        instance.drawText(textRenderer, text, x, y, newColor, newShadow);
    }

    @Redirect(
        method = "renderTexture",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"
        )
    )
    private void modifyTextureColor(net.minecraft.client.gui.DrawContext instance, RenderPipeline pipeline,
                                    Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        int color = Colors.WHITE;

        PlayerInfoPayload playerInfo = PlayerRelay.getConnectedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) color = playerInfo.getNameColor();

        instance.drawTexture(pipeline, sprite, x, y, u, v, width, height, textureWidth, textureHeight, color);
    }

    @Redirect(
        method = "renderDefaultPingIcon",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
        )
    )
    private void modifyDefaultIconColor(
        net.minecraft.client.gui.DrawContext instance,
        int x1, int y1, int x2, int y2, int color
    ) {
        int newColor = color;

        PlayerInfoPayload playerInfo = PlayerRelay.getConnectedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) newColor = playerInfo.getNameColor();

        instance.fill(x1, y1, x2, y2, newColor);
    }
}
