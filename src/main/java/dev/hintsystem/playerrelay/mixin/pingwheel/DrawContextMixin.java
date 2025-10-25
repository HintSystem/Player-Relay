package dev.hintsystem.playerrelay.mixin.pingwheel;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.cast.pingwheel.DrawContextAccessor;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import nx.pingwheel.common.render.DrawContext;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(value = DrawContext.class, remap = false)
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

    @ModifyVariable(
        method = "renderPing",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int modifyPingColor(int color) {
        PlayerInfoPayload playerInfo = PlayerRelay.getConnectedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) return playerInfo.getNameColor();

        return color;
    }

    @ModifyVariable(
        method = "renderArrowIcon",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int modifyArrowColor(int color) {
        PlayerInfoPayload playerInfo = PlayerRelay.getConnectedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) return playerInfo.getNameColor();

        return color;
    }
}
