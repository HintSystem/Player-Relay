package dev.hintsystem.playerrelay.mixin.pingwheel;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.cast.pingwheel.DrawContextAccessor;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import nx.pingwheel.common.render.DrawContext;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
        )
    )
    private void playerrelay$modifyTextColor(
        GuiGraphics graphics, Font font,
        Component text, int x, int y, int color, boolean drawShadow
    ) {
        int nameColor = color;
        boolean useShadow = drawShadow;

        PlayerInfoPayload playerInfo = ClientCore.getTrackedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) {
            nameColor = playerInfo.getNameColor();
            useShadow = true;
        }

        graphics.drawString(font, text, x, y, nameColor, useShadow);
    }

    @ModifyVariable(
        method = "renderPing",
        at = @At("HEAD"),
        argsOnly = true, ordinal = 0
    )
    private int playerrelay$modifyPingColor(int color) {
        PlayerInfoPayload playerInfo = ClientCore.getTrackedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) return playerInfo.getNameColor();

        return color;
    }

    @ModifyVariable(
        method = "renderArrowIcon",
        at = @At("HEAD"),
        argsOnly = true, ordinal = 0,
        remap = false
    )
    private int playerrelay$modifyArrowColor(int color) {
        PlayerInfoPayload playerInfo = ClientCore.getTrackedPlayer(this.playerrelay$authorId);
        if (playerInfo != null) return playerInfo.getNameColor();

        return color;
    }
}
