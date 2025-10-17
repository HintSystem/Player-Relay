package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PlayerListEntry {
    private static final Identifier ARMOR_FULL_TEXTURE = Identifier.ofVanilla("hud/armor_full");
    private static final Identifier ARMOR_HALF_TEXTURE = Identifier.ofVanilla("hud/armor_half");
    private static final Identifier ARMOR_EMPTY_TEXTURE = Identifier.ofVanilla("hud/armor_empty");
    private static final Identifier XP_BACKGROUND = Identifier.ofVanilla("hud/experience_bar_background");
    private static final Identifier XP_PROGRESS = Identifier.ofVanilla("hud/experience_bar_progress");

    public static final float PLAYER_MODEL_ASPECT_RATIO = 1.58f;

    public Config config = new Config.Builder().build();
    public PlayerInfoPayload playerInfo;
    private OtherClientPlayerEntity playerEntity;
    private final PaperDollRenderer paperDollRenderer;

    private float lastHealth = 0f;
    private long heartBlinkEndTimeMs = 0L;

    public enum PlayerIconType { NONE, PLAYER_HEAD, PLAYER_MODEL }

    public record Config(
        int iconWidth,
        int maxEffectInfoWidth,
        int infoWidth,
        int padding,
        PlayerIconType playerIconType,
        AnchorPoint anchorPoint
    ) {
        public int getWidth() { return infoWidth + ((playerIconType != PlayerIconType.NONE) ? iconWidth + padding : 0); }

        public int getIconHeight() {
            return (playerIconType == PlayerIconType.PLAYER_MODEL) ? (int)Math.ceil(iconWidth * PLAYER_MODEL_ASPECT_RATIO)
                : iconWidth;
        }
        public int getHeight() { return Math.max(getIconHeight(), 28); }

        public static class Builder {
            private int iconWidth = 24;
            private int maxEffectInfoWidth = 40;
            private int infoWidth = 86;
            private int padding = 4;
            private PlayerIconType playerIconType = PlayerIconType.PLAYER_MODEL;
            private AnchorPoint anchorPoint = AnchorPoint.TOP_RIGHT;

            public Builder iconWidth(int v) { iconWidth = v; return this; }
            public Builder maxEffectInfoWidth(int v) { maxEffectInfoWidth = v; return this; }
            public Builder infoWidth(int v) { infoWidth = v; return this; }
            public Builder padding(int v) { padding = v; return this; }
            public Builder playerIconType(PlayerIconType v) { playerIconType = v; return this; }
            public Builder anchorPoint(AnchorPoint v) { anchorPoint = v; return this; }

            public Config build() {
                return new Config(iconWidth, maxEffectInfoWidth, infoWidth, padding, playerIconType, anchorPoint);
            }
        }
    }
    public PlayerListEntry(PlayerInfoPayload playerInfo) {
        this.playerInfo = playerInfo;
        this.paperDollRenderer = new PaperDollRenderer();
    }

    public void tick() {
        if (config.playerIconType != PlayerIconType.PLAYER_MODEL) return;

        OtherClientPlayerEntity player = getRenderPlayerEntity();
        if (player != null) {
            paperDollRenderer.tick(player);
            applyInfoToPlayer(player);
        }
    }

    private void applyInfoToPlayer(PlayerEntity player) {
        PlayerPositionData positionData = playerInfo.getComponent(PlayerPositionData.class);
        if (positionData != null) {
            player.lastX = player.getX();
            player.lastY = player.getY();
            player.lastZ = player.getZ();
            player.lastYaw = player.getYaw();
            player.lastPitch = player.getPitch();
            player.updateTrackedPositionAndAngles(positionData.coords, positionData.yaw, positionData.pitch);

            paperDollRenderer.applyPoseToPlayer(player, positionData.pose);
        }

        PlayerStatsData statsData = playerInfo.getComponent(PlayerStatsData.class);
        if (statsData != null) paperDollRenderer.applyHealth(player, statsData.health);

        PlayerEquipmentData equipmentData = playerInfo.getComponent(PlayerEquipmentData.class);
        if (equipmentData != null) equipmentData.applyToPlayer(player);
    }

    @Nullable
    public OtherClientPlayerEntity getRenderPlayerEntity() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if ((this.playerEntity == null || this.playerEntity.clientWorld != world) && world != null) {
            this.playerEntity = new OtherClientPlayerEntity(world, playerInfo.toGameProfile());
        }

        return this.playerEntity;
    }

    public void render(DrawContext context, int x, int y, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        PlayerStatsData playerStats = playerInfo.getComponentOrEmpty(PlayerStatsData.class);
        PlayerStatusEffectsData playerStatusEffects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        // Render player icon
        if (config.playerIconType != PlayerIconType.NONE) {
            int x2 = x + config.iconWidth, y2 = y + config.getIconHeight();

            if (config.playerIconType == PlayerIconType.PLAYER_MODEL && getRenderPlayerEntity() != null) {
                paperDollRenderer.anchorPoint = config.anchorPoint;
                renderIconUnderlay(context, x, y, x2, y2, null);
                paperDollRenderer.renderPaperDoll(context, x, y, x2, y2, 22, playerEntity, tickCounter);
            } else {
                SkinTextures skin = client.getSkinProvider().getSkinTextures(playerInfo.toGameProfile());
                PlayerSkinDrawer.draw(context, skin, x, y, config.iconWidth);
            }

            renderIconOverlay(context, x, y, x2, y2, null);
            x += config.iconWidth + config.padding;
        }

        // Render player name
        int maxNameX = (playerStatusEffects != null) ? renderStatusEffects(context, playerStatusEffects, x + config.infoWidth, y)
            : x + config.infoWidth;
        context.enableScissor(x, y, maxNameX, y + 9);
        context.drawTextWithShadow(client.textRenderer, playerInfo.getName(), x, y, Colors.WHITE);
        context.disableScissor();

        y += 10;

        // Render health
        boolean shouldBlink = updateBlinkState(playerStats);
        boolean isHalfHeart = playerStats.health < 10;
        Identifier heartTexture = (playerStats.health > 0) ? getHeartTexture(isHalfHeart, shouldBlink) : null;

        drawStat(context, getHeartTypeTexture(InGameHud.HeartType.CONTAINER, isHalfHeart, shouldBlink), heartTexture,
            (int) Math.ceil(playerStats.health + playerStats.absorptionAmount),
            x, y, 0xFFFF6666, StatAnchor.LEFT);

        // Render armor
        Identifier armorTexture = (playerStats.armor >= 10) ? ARMOR_FULL_TEXTURE
            : (playerStats.armor > 0) ? ARMOR_HALF_TEXTURE
            : ARMOR_EMPTY_TEXTURE;

        drawStat(context, armorTexture, null,
            playerStats.armor,
            x, y, 0xFFafd8ed, StatAnchor.CENTER);

        // Render food
        int foodBlipValue = (playerStats.hunger >= 10) ? 2
            : (playerStats.hunger > 0) ? 1
            : 0;

        drawStat(context, getFoodTexture(0), getFoodTexture(foodBlipValue),
            playerStats.hunger,
            x, y, 0xFFba8d4e, StatAnchor.RIGHT);

        y += 12;

        // Render XP bar
        renderXpBar(context, playerStats.xp, config.infoWidth, x, y);
    }

    enum IconOverlayState {
        NONE,
        AFK("AFK", Colors.WHITE, ColorHelper.withAlpha(0.2f, Colors.GRAY), ColorHelper.withAlpha(0.2f, Colors.GRAY)),
        DEAD("DEAD", Colors.RED, ColorHelper.withAlpha(0.2f, Colors.RED), ColorHelper.withAlpha(0.2f, Colors.RED));

        public final String statusText;
        public final int textColor;
        public final int overlayColor;
        public final int underlayColor;

        IconOverlayState() { this(null, 0); }

        IconOverlayState(String statusText, int textColor) { this(statusText, textColor, 0, 0); }

        IconOverlayState(String statusText, int textColor, int overlayColor, int underlayColor) {
            this.statusText = statusText;
            this.textColor = textColor;
            this.overlayColor = overlayColor;
            this.underlayColor = underlayColor;
        }

        public static IconOverlayState getState(PlayerInfoPayload infoPayload) {
            PlayerStatsData statsData = infoPayload.getComponent(PlayerStatsData.class);
            if (statsData != null && statsData.health <= 0.0f) { return DEAD; }
            return NONE;
        }
    }

    private void renderIconUnderlay(DrawContext context, int x1, int y1, int x2, int y2, @Nullable IconOverlayState state) {
        if (state == null) state = IconOverlayState.getState(playerInfo);
        if (state.underlayColor != 0) context.fill(x1, y1, x2, y2, state.underlayColor);
    }

    private void renderIconOverlay(DrawContext context, int x1, int y1, int x2, int y2, @Nullable IconOverlayState state) {
        if (state == null) state = IconOverlayState.getState(playerInfo);

        MinecraftClient client = MinecraftClient.getInstance();
        int textHeight = client.textRenderer.fontHeight;

        if (state.overlayColor != 0) context.fill(x1, y1, x2, y2, state.overlayColor);
        if (state.statusText != null) {
            context.drawCenteredTextWithShadow(
                client.textRenderer, state.statusText,
                (x1 + x2) / 2, (y1 + y2) / 2 - textHeight / 2,
                state.textColor
            );
        }
    }

    private int renderStatusEffects(DrawContext context, PlayerStatusEffectsData statusEffects, int x, int y) {
        int startX = x;
        int endX = startX;
        int effectIconSize = 9;

        for (PlayerStatusEffectsData.StatusEffectEntry effect : statusEffects.getActiveStatusEffects()) {
            x -= effectIconSize;
            if (startX - x > config.maxEffectInfoWidth) break;

            float opacity = 1f;
            float remainingSeconds = statusEffects.getEffectRemainingMs(effect) / 1000f;
            if (remainingSeconds < 10f) {
                float n = 10f - remainingSeconds;

                opacity = MathHelper.clamp(remainingSeconds / 10f * 0.5f, 0.0f, 0.5f)
                    + (float)Math.cos(remainingSeconds * (Math.PI * 4))
                    * MathHelper.clamp(n / 10f * 0.25f, 0.0f, 0.25f);
                opacity = MathHelper.clamp(opacity, 0.0f, 1.0f);
            }

            Identifier effectTexture = InGameHud.getEffectTexture(effect.statusEffect());
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectTexture, x, y, effectIconSize, effectIconSize, ColorHelper.getWhite(opacity));
            endX = x;
        }
        return endX;
    }

    enum StatAnchor { LEFT, CENTER, RIGHT }

    private void drawStat(DrawContext context,
                          Identifier iconBase,
                          Identifier iconOverlay,
                          int value,
                          int x, int y,
                          int color,
                          StatAnchor anchor) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = (value > 99) ? "99+" : String.format("%2d", value);

        int textWidth = client.textRenderer.getWidth(text);
        int elementWidth = 9 + 2 + textWidth;

        int drawX;
        switch (anchor) {
            case CENTER -> drawX = x + config.infoWidth / 2 - elementWidth / 2;
            case RIGHT -> drawX = x + config.infoWidth - elementWidth;
            default -> drawX = x;
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, iconBase, drawX, y, 9, 9);
        if (iconOverlay != null) { context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, iconOverlay, drawX, y, 9, 9); }

        context.drawTextWithShadow(client.textRenderer, text, drawX + 9 + 2, y + 1, color);
    }


    private boolean updateBlinkState(PlayerStatsData stats) {
        float currentHealth = stats.health;
        long now = Util.getMeasuringTimeMs();

        // Check if we crossed an integer boundary (new heart gained/lost)
        int lastHeartLevel = (int) Math.ceil(lastHealth);
        int currentHeartLevel = (int) Math.ceil(currentHealth);

        if (currentHeartLevel < lastHeartLevel) {
            heartBlinkEndTimeMs = now + 20L * ClientCore.msPerTick;
        } else if (currentHeartLevel > lastHeartLevel) {
            heartBlinkEndTimeMs = now + 10L * ClientCore.msPerTick;
        }

        lastHealth = currentHealth;

        if (now < heartBlinkEndTimeMs) {
            long remaining = heartBlinkEndTimeMs - now;
            return (remaining / (3L * ClientCore.msPerTick)) % 2 == 1;
        }
        return false;
    }

    private Identifier getHeartTypeTexture(InGameHud.HeartType heartType, boolean half, boolean blinking) {
        PlayerWorldData world = playerInfo.getComponent(PlayerWorldData.class);
        return heartType.getTexture(
            world != null && world.hardcore,
            half,
            blinking
        );
    }

    private Identifier getHeartTexture(boolean half, boolean blinking) {
        PlayerStatusEffectsData effects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        InGameHud.HeartType heartType = InGameHud.HeartType.NORMAL;
        if (effects != null) {
            if (effects.hasStatusEffect(StatusEffects.POISON)) {
                heartType = InGameHud.HeartType.POISONED;
            } else if (effects.hasStatusEffect(StatusEffects.WITHER)) {
                heartType = InGameHud.HeartType.WITHERED;
            } else if (effects.isFrozen) {
                heartType = InGameHud.HeartType.FROZEN;
            } else if (effects.hasStatusEffect(StatusEffects.ABSORPTION)) {
                heartType = InGameHud.HeartType.ABSORBING;
            }
        }

        return getHeartTypeTexture(heartType, half, blinking);
    }

    private Identifier getFoodTexture(int value) {
        PlayerStatusEffectsData effects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        if (effects != null && effects.hasStatusEffect(StatusEffects.HUNGER)) {
            if (value == 0) return Identifier.ofVanilla("hud/food_empty_hunger");
            if (value == 1) return Identifier.ofVanilla("hud/food_half_hunger");
            return Identifier.ofVanilla("hud/food_full_hunger");
        }

        if (value == 0) return Identifier.ofVanilla("hud/food_empty");
        if (value == 1) return Identifier.ofVanilla("hud/food_half");
        return Identifier.ofVanilla("hud/food_full");
    }

    private void renderXpBar(DrawContext context, float xp, int barWidth, int x, int y) {
        int capWidth = 5;
        int fillableWidth = barWidth - (capWidth * 2);
        int progress = (int)((xp % 1) * (float)barWidth);

        int textureWidth = 182;
        int textureMiddleWidth = textureWidth - (capWidth * 2);
        int textureMiddleCenter = capWidth + textureMiddleWidth / 2;

        // Left cap
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, 0, 0, x, y, capWidth, 5);

        // Middle slice - centered from texture
        int middleSrcX = textureMiddleCenter - fillableWidth / 2;
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, middleSrcX, 0, x + capWidth, y, fillableWidth, 5);

        // Right cap
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, textureWidth - capWidth, 0, x + capWidth + fillableWidth, y, capWidth, 5);

        if (progress > 0) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, 0, 0, x, y, Math.min(progress, capWidth), 5);

            if (progress > capWidth) {
                int middleProgress = Math.min(progress - capWidth, fillableWidth);
                int progressSrcX = textureMiddleCenter - fillableWidth / 2;
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, progressSrcX, 0, x + capWidth, y, middleProgress, 5);
            }

            if (progress >= capWidth + fillableWidth) {
                int rightCapProgress = Math.min(progress - capWidth - fillableWidth, capWidth);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, textureWidth - capWidth, 0, x + capWidth + fillableWidth, y, rightCapProgress, 5);
            }
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        String level = String.valueOf((int)xp);
        int textXPos = x + barWidth / 2 - textRenderer.getWidth(level) / 2;

        drawTextOutline(context, textRenderer, level, textXPos, y - 1, Colors.BLACK);
        context.drawTextWithShadow(textRenderer, level, textXPos, y - 1, 0xFF5FBE18);
    }

    private void drawTextOutline(DrawContext context, TextRenderer renderer,
                                 String text, int x, int y, int outlineColor) {
        context.drawText(renderer, text, x - 1, y, outlineColor, false);
        context.drawText(renderer, text, x + 1, y, outlineColor, false);
        context.drawText(renderer, text, x, y - 1, outlineColor, false);
        context.drawText(renderer, text, x, y + 1, outlineColor, false);
    }
}