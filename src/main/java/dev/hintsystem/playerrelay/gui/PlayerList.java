package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;

import java.util.Map;
import java.util.UUID;

public class PlayerList implements HudElement {
    private static final Identifier HEART_CONTAINER_TEXTURE = Identifier.ofVanilla("hud/heart/container");
    private static final Identifier HEART_TEXTURE = Identifier.ofVanilla("hud/heart/full");

    private static final Identifier ARMOR_FULL_TEXTURE = Identifier.ofVanilla("hud/armor_full");

    private static final Identifier FOOD_EMPTY_TEXTURE = Identifier.ofVanilla("hud/food_empty");
    private static final Identifier FOOD_FULL_TEXTURE = Identifier.ofVanilla("hud/food_full");

    private static final Identifier XP_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier XP_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        Map<UUID, PlayerInfoPayload> connectedPlayers = PlayerRelay.getNetworkManager().connectedPlayers;
        if (!PlayerRelay.config.showPlayerList) { return; }

        MinecraftClient client = MinecraftClient.getInstance();

        int backgroundPadding = 4;
        int headSize = 24;
        int infoWidth = 82;
        int infoGap = infoWidth / 3;

        int entryPadding = 5;
        int entryWidth = headSize + backgroundPadding + infoWidth;
        int entryHeight = 32 + backgroundPadding;

        int totalHeight = (entryHeight + entryPadding)
            * Math.min(connectedPlayers.size(), PlayerRelay.config.playerListMaxPlayers)
            - entryPadding;

        int[] origin = PlayerRelay.config.playerListAnchorPoint.resolve(context, entryWidth + backgroundPadding, totalHeight);

        int i = 0;
        int y = origin[1];
        for (PlayerInfoPayload player : connectedPlayers.values()) {
            i++;
            if (i > PlayerRelay.config.playerListMaxPlayers) break;

            int x = origin[0];

            context.fill(
                x, y,
                x + entryWidth + backgroundPadding, y + entryHeight,
                PlayerRelay.config.playerListbackgroundColor.getRGB()
            );
            int innerX = x + backgroundPadding;
            int innerY = y + backgroundPadding;

            SkinTextures skin = client.getSkinProvider().getSkinTextures(player.toGameProfile());
            PlayerSkinDrawer.draw(context, skin, innerX, innerY, headSize);
            innerX += headSize + backgroundPadding;

            context.drawTextWithShadow(client.textRenderer, player.name, innerX, innerY, Colors.WHITE);
            innerY += 10;

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER_TEXTURE, innerX, innerY, 9, 9);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HEART_TEXTURE, innerX, innerY, 9, 9);
            context.drawTextWithShadow(client.textRenderer, String.format("%2d", (int)Math.ceil(player.health)), innerX + 11, innerY+1, 0xFFFF6666);

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, ARMOR_FULL_TEXTURE, innerX + infoGap, innerY, 9, 9);
            context.drawTextWithShadow(client.textRenderer, String.format("%2d", player.armor), innerX + infoGap + 11, innerY+1, 0xFFafd8ed);

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY_TEXTURE, innerX + infoGap*2, innerY, 9, 9);
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_FULL_TEXTURE, innerX + infoGap*2, innerY, 9, 9);
            context.drawTextWithShadow(client.textRenderer, String.format("%2d", player.hunger), innerX + infoGap*2 + 11, innerY+1, 0xFFba8d4e);
            innerY += 12;

            renderXpBar(context, player.xp, infoWidth - 2, innerX, innerY);

            y += entryHeight + entryPadding;
        }
    }

    public static void renderXpBar(DrawContext context, float xp, int barWidth, int x, int y) {
        int capWidth = 1;
        int fillableWidth = barWidth - (capWidth * 2);
        int progress = (int)((xp % 1) * (float)barWidth);

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, 182, 5, 0, 0, x, y, capWidth, 5);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, 182, 5, capWidth, 0, x + capWidth, y, fillableWidth, 5);
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, 182, 5, 182 - capWidth, 0, x + capWidth + fillableWidth, y, capWidth, 5);

        if (progress > 0) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, 0, 0, x, y, Math.min(progress, capWidth), 5);

            if (progress > capWidth) {
                int middleProgress = Math.min(progress - capWidth, fillableWidth);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, capWidth, 0, x + capWidth, y, middleProgress, 5);
            }

            if (progress >= fillableWidth) {
                int rightCapProgress = Math.min(progress - fillableWidth, capWidth);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, 182 - capWidth, 0, x + capWidth + fillableWidth, y, rightCapProgress, 5);
            }
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        String level = String.valueOf((int)xp);
        int textXPos = x + barWidth/2 - textRenderer.getWidth(level)/2;

        drawTextOutline(context, textRenderer, level, textXPos, y-1, Colors.BLACK);
        context.drawTextWithShadow(textRenderer, level, textXPos, y-1, 0xFF5FBE18);
    }

    public static void drawTextOutline(DrawContext context, TextRenderer renderer,
                                        String text, int x, int y, int outlineColor) {
        context.drawText(renderer, text, x - 1, y, outlineColor, false);
        context.drawText(renderer, text, x + 1, y, outlineColor, false);
        context.drawText(renderer, text, x, y - 1, outlineColor, false);
        context.drawText(renderer, text, x, y + 1, outlineColor, false);
    }
}
