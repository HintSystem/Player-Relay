package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.*;

import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import java.util.HashMap;
import java.util.Map;

public class PlayerListEntry {
    private final Identifier ARMOR_FULL_TEXTURE;
    private final Identifier ARMOR_HALF_TEXTURE;
    private final Identifier ARMOR_EMPTY_TEXTURE;
    private final Identifier XP_BACKGROUND;
    private final Identifier XP_PROGRESS;

    public static final float PLAYER_MODEL_ASPECT_RATIO = 1.58f;

    public PlayerInfoPayload playerInfo;
    public final Config config;

    private PaperDollRenderer.FakePlayer playerEntity;
    private final PaperDollRenderer paperDollRenderer;

    private float lastHealth = 0f;
    private long heartBlinkEndTimeMs = 0L;

    public enum PlayerIconType { NONE, PLAYER_MODEL, PLAYER_HEAD }

    public static class Config {
        int iconWidth = 24;
        int maxEffectInfoWidth = 40;
        int infoWidth = 86;
        int padding = 4;
        boolean showDimensionIcon = false;
        boolean useResourcePackIcons = false;
        PlayerIconType playerIconType = PlayerIconType.PLAYER_MODEL;
        AnchorPoint anchorPoint = AnchorPoint.TOP_RIGHT;

        public int getWidth() { return infoWidth + ((playerIconType != PlayerIconType.NONE) ? iconWidth + padding : 0); }

        public int getPlayerIconHeight() {
            return (playerIconType == PlayerIconType.PLAYER_MODEL) ? (int)Math.ceil(iconWidth * PLAYER_MODEL_ASPECT_RATIO)
                : iconWidth;
        }
        public int getHeight() { return Math.max(getPlayerIconHeight(), 28); }
    }

    public PlayerListEntry(PlayerInfoPayload playerInfo, @NotNull Config config) {
        this.playerInfo = playerInfo;
        this.config = config;
        this.paperDollRenderer = new PaperDollRenderer();

        this.ARMOR_FULL_TEXTURE = iconIdentifier("hud/armor_full");
        this.ARMOR_HALF_TEXTURE = iconIdentifier("hud/armor_half");
        this.ARMOR_EMPTY_TEXTURE = iconIdentifier("hud/armor_empty");
        this.XP_BACKGROUND = iconIdentifier("hud/experience_bar_background");
        this.XP_PROGRESS = iconIdentifier("hud/experience_bar_progress");
    }

    public Identifier iconIdentifier(String path) {
        if (config.useResourcePackIcons) return Identifier.withDefaultNamespace(path);

        return CommonCore.identifier(path);
    }

    public void tick() {
        if (config.playerIconType != PlayerIconType.PLAYER_MODEL) return;

        var player = getRenderPlayerEntity();
        if (player != null) {
            paperDollRenderer.tick(player);
            applyInfoToPlayer(player);
        }
    }

    private void applyInfoToPlayer(PaperDollRenderer.FakePlayer player) {
        PlayerPositionData positionData = playerInfo.getComponent(PlayerPositionData.class);
        if (positionData != null) {
            player.xo = player.getX();
            player.yo = player.getY();
            player.zo = player.getZ();
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.moveOrInterpolateTo(positionData.coords, positionData.yaw, positionData.pitch);

            player.applyPose(positionData.pose);
        }

        PlayerStatsData statsData = playerInfo.getComponent(PlayerStatsData.class);
        if (statsData != null) paperDollRenderer.applyHealth(player, statsData.health);

        PlayerStatusEffectsData statusEffectsData = playerInfo.getComponent(PlayerStatusEffectsData.class);
        if (statusEffectsData != null) {
            playerEntity.setTicksFrozen(statusEffectsData.isFrozen() ? playerEntity.getTicksRequiredToFreeze() + 4 : 0);
            playerEntity.setSharedFlagOnFire(statusEffectsData.isOnFire());
        }

        PlayerEquipmentData equipmentData = playerInfo.getComponent(PlayerEquipmentData.class);
        if (equipmentData != null) equipmentData.applyToPlayer(player);
    }

    @Nullable
    public PaperDollRenderer.FakePlayer getRenderPlayerEntity() {
        ClientLevel world = Minecraft.getInstance().level;

        if (this.playerEntity != null && this.playerEntity.level() != world) {
            this.playerEntity = null;
        }

        if (this.playerEntity == null && world != null) {
            this.playerEntity = new PaperDollRenderer.FakePlayer(world, playerInfo.toGameProfile());
        }

        return this.playerEntity;
    }

    public PlayerSkin getPlayerSkinTextures() {
        return Minecraft.getInstance().playerSkinRenderCache()
            .getOrDefault(ResolvableProfile.createResolved(playerInfo.toGameProfile()))
            .playerSkin();
    }

    private Identifier getHeartTypeTexture(Gui.HeartType heartType, boolean half, boolean blinking) {
        PlayerWorldData world = playerInfo.getComponent(PlayerWorldData.class);
        return iconIdentifier(
            heartType.getSprite(
                world != null && world.isHardcore(),
                half,
                blinking
            ).getPath()
        );
    }

    private Identifier getHeartTexture(boolean half, boolean blinking) {
        PlayerStatusEffectsData effects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        Gui.HeartType heartType = Gui.HeartType.NORMAL;
        if (effects != null) {
            if (effects.hasStatusEffect(MobEffects.POISON)) {
                heartType = Gui.HeartType.POISIONED;
            } else if (effects.hasStatusEffect(MobEffects.WITHER)) {
                heartType = Gui.HeartType.WITHERED;
            } else if (effects.isFrozen()) {
                heartType = Gui.HeartType.FROZEN;
            } else if (effects.hasStatusEffect(MobEffects.ABSORPTION)) {
                heartType = Gui.HeartType.ABSORBING;
            }
        }

        return getHeartTypeTexture(heartType, half, blinking);
    }

    private Identifier getFoodTexture(int value) {
        PlayerStatusEffectsData effects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        if (effects != null && effects.hasStatusEffect(MobEffects.HUNGER)) {
            if (value == 0) return iconIdentifier("hud/food_empty_hunger");
            if (value == 1) return iconIdentifier("hud/food_half_hunger");
            return iconIdentifier("hud/food_full_hunger");
        }

        if (value == 0) return iconIdentifier("hud/food_empty");
        if (value == 1) return iconIdentifier("hud/food_half");
        return iconIdentifier("hud/food_full");
    }

    public void render(GuiGraphics context, int x, int y, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();

        PlayerWorldData playerWorld = playerInfo.getComponentOrEmpty(PlayerWorldData.class);
        PlayerStatsData playerStats = playerInfo.getComponentOrEmpty(PlayerStatsData.class);
        PlayerStatusEffectsData playerStatusEffects = playerInfo.getComponent(PlayerStatusEffectsData.class);

        // Render player icon
        int dimensionIconX = x;
        int dimensionIconY = y + config.getPlayerIconHeight();
        if (config.playerIconType != PlayerIconType.NONE) {
            int x2 = x + config.iconWidth, y2 = y + config.getPlayerIconHeight();

            if (config.playerIconType == PlayerIconType.PLAYER_MODEL && getRenderPlayerEntity() != null) {
                paperDollRenderer.anchorPoint = config.anchorPoint;
                drawPlayerUnderlay(context, x, y, x2, y2);
                paperDollRenderer.renderPaperDoll(context, x, y, x2, y2, 22, playerEntity, tickCounter);
            } else {
                PlayerFaceRenderer.draw(context, getPlayerSkinTextures(), x, y, config.iconWidth);
            }

            drawPlayerOverlay(context, x, y, x2, y2);
            x += config.iconWidth + config.padding;
        }

        // Render player name
        int maxNameX = (playerStatusEffects != null) ? drawStatusEffects(context, playerStatusEffects, x + config.infoWidth, y)
            : x + config.infoWidth;
        context.enableScissor(x, y, maxNameX, y + 9);
        context.drawString(client.font, playerInfo.getName(), x, y, playerInfo.getNameColor());
        context.disableScissor();

        y += 10;

        // Render health
        boolean shouldBlink = updateBlinkState(playerStats);
        boolean isHalfHeart = playerStats.health < 10;
        Identifier heartTexture = (playerStats.health > 0) ? getHeartTexture(isHalfHeart, shouldBlink) : null;

        drawStat(context, getHeartTypeTexture(Gui.HeartType.CONTAINER, isHalfHeart, shouldBlink), heartTexture,
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
        drawXpBar(context, playerStats.xp, config.infoWidth, x, y);

        if (config.showDimensionIcon) drawDimensionIcon(context, playerWorld.dimension, dimensionIconX, dimensionIconY);
    }

    enum PlayerOverlayState {
        NONE,
        AFK("AFK", CommonColors.WHITE, ARGB.color(0.6f, CommonColors.DARK_GRAY), 0),
        DEAD("DEAD", CommonColors.RED, ARGB.color(0.2f, CommonColors.RED), ARGB.color(0.2f, CommonColors.RED));

        public final String statusText;
        public final int textColor;
        public final int overlayColor;
        public final int underlayColor;

        PlayerOverlayState() { this(null, 0); }

        PlayerOverlayState(String statusText, int textColor) { this(statusText, textColor, 0, 0); }

        PlayerOverlayState(String statusText, int textColor, int overlayColor, int underlayColor) {
            this.statusText = statusText;
            this.textColor = textColor;
            this.overlayColor = overlayColor;
            this.underlayColor = underlayColor;
        }

        public static PlayerOverlayState getState(PlayerInfoPayload infoPayload) {
            if (infoPayload.isAfk()) return AFK;

            PlayerStatsData statsData = infoPayload.getComponent(PlayerStatsData.class);
            if (statsData != null && statsData.health <= 0.0f) return DEAD;
            return NONE;
        }
    }

    private void drawPlayerUnderlay(GuiGraphics context, int x1, int y1, int x2, int y2) {
        PlayerOverlayState state = PlayerOverlayState.getState(playerInfo);
        if (state.underlayColor != 0) context.fill(x1, y1, x2, y2, state.underlayColor);
    }

    private void drawPlayerOverlay(GuiGraphics context, int x1, int y1, int x2, int y2) {
        PlayerOverlayState state = PlayerOverlayState.getState(playerInfo);

        Minecraft client = Minecraft.getInstance();
        int textHeight = client.font.lineHeight;

        if (state.overlayColor != 0) context.fill(x1, y1, x2, y2, state.overlayColor);
        if (state.statusText != null) {
            context.drawCenteredString(
                client.font, state.statusText,
                (x1 + x2) / 2, (y1 + y2) / 2 - textHeight / 2,
                state.textColor
            );
        }
    }

    public enum DimensionIcon {
        OVERWORLD(Level.OVERWORLD, new ItemStack(Items.GRASS_BLOCK)),
        NETHER(Level.NETHER, new ItemStack(Items.NETHERRACK)),
        END(Level.END, new ItemStack(Items.END_PORTAL_FRAME));

        public final ResourceKey<Level> dimension;
        public final ItemStack displayItem;

        private static final Map<ResourceKey<Level>, DimensionIcon> BY_DIMENSION = new HashMap<>();

        static {
            for (DimensionIcon icon : values()) BY_DIMENSION.put(icon.dimension, icon);
        }

        DimensionIcon(ResourceKey<Level> dimension, ItemStack displayItem) {
            this.dimension = dimension;
            this.displayItem = displayItem;
        }

        @Nullable
        public static DimensionIcon getIcon(ResourceKey<Level> dimension) { return BY_DIMENSION.get(dimension); }
    }

    private void drawDimensionIcon(GuiGraphics context, ResourceKey<Level> dimension, int centerX, int centerY) {
        DimensionIcon dimensionIcon = DimensionIcon.getIcon(dimension);
        if (dimensionIcon == null) return;

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();

        float scale = 0.8f;
        int scaledSize = (int)(16 * scale);
        matrices.translate(centerX - (int)(scaledSize/4), centerY - (int)(scaledSize/2));
        matrices.scale(scale, scale);

        context.renderItem(dimensionIcon.displayItem, 0, 0);

        matrices.popMatrix();
    }

    private int drawStatusEffects(GuiGraphics context, PlayerStatusEffectsData statusEffects, int x, int y) {
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

                opacity = Mth.clamp(remainingSeconds / 10f * 0.5f, 0.0f, 0.5f)
                    + (float)Math.cos(remainingSeconds * (Math.PI * 4))
                    * Mth.clamp(n / 10f * 0.25f, 0.0f, 0.25f);
                opacity = Mth.clamp(opacity, 0.0f, 1.0f);
            }

            Identifier effectTexture = Gui.getMobEffectSprite(effect.statusEffect());
            context.blitSprite(RenderPipelines.GUI_TEXTURED, effectTexture, x, y, effectIconSize, effectIconSize, ARGB.white(opacity));
            endX = x;
        }
        return endX;
    }

    enum StatAnchor { LEFT, CENTER, RIGHT }

    private void drawStat(GuiGraphics context,
                          Identifier iconBase,
                          Identifier iconOverlay,
                          int value,
                          int x, int y,
                          int color,
                          StatAnchor anchor) {
        Minecraft client = Minecraft.getInstance();
        String text = (value > 99) ? "99+" : String.format("%2d", value);

        int textWidth = client.font.width(text);
        int elementWidth = 9 + 2 + textWidth;

        int drawX;
        switch (anchor) {
            case CENTER -> drawX = x + config.infoWidth / 2 - elementWidth / 2;
            case RIGHT -> drawX = x + config.infoWidth - elementWidth;
            default -> drawX = x;
        }

        context.blitSprite(RenderPipelines.GUI_TEXTURED, iconBase, drawX, y, 9, 9);
        if (iconOverlay != null) { context.blitSprite(RenderPipelines.GUI_TEXTURED, iconOverlay, drawX, y, 9, 9); }

        context.drawString(client.font, text, drawX + 9 + 2, y + 1, color);
    }

    private boolean updateBlinkState(PlayerStatsData stats) {
        float currentHealth = stats.health;
        long now = Util.getMillis();

        // Check if we crossed an integer boundary (new heart gained/lost)
        int lastHeartLevel = (int) Math.ceil(lastHealth);
        int currentHeartLevel = (int) Math.ceil(currentHealth);

        if (currentHeartLevel < lastHeartLevel) {
            heartBlinkEndTimeMs = now + 20L * CommonCore.msPerTick;
        } else if (currentHeartLevel > lastHeartLevel) {
            heartBlinkEndTimeMs = now + 10L * CommonCore.msPerTick;
        }

        lastHealth = currentHealth;

        if (now < heartBlinkEndTimeMs) {
            long remaining = heartBlinkEndTimeMs - now;
            return (remaining / (3L * CommonCore.msPerTick)) % 2 == 1;
        }
        return false;
    }

    private void drawXpBar(GuiGraphics context, float xp, int barWidth, int x, int y) {
        int capWidth = 5;
        int fillableWidth = barWidth - (capWidth * 2);
        int progress = (int)((xp % 1) * (float)barWidth);

        int textureWidth = 182;
        int textureMiddleWidth = textureWidth - (capWidth * 2);
        int textureMiddleCenter = capWidth + textureMiddleWidth / 2;

        // Left cap
        context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, 0, 0, x, y, capWidth, 5);

        // Middle slice - centered from texture
        int middleSrcX = textureMiddleCenter - fillableWidth / 2;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, middleSrcX, 0, x + capWidth, y, fillableWidth, 5);

        // Right cap
        context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, textureWidth, 5, textureWidth - capWidth, 0, x + capWidth + fillableWidth, y, capWidth, 5);

        if (progress > 0) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, 0, 0, x, y, Math.min(progress, capWidth), 5);

            if (progress > capWidth) {
                int middleProgress = Math.min(progress - capWidth, fillableWidth);
                int progressSrcX = textureMiddleCenter - fillableWidth / 2;
                context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, progressSrcX, 0, x + capWidth, y, middleProgress, 5);
            }

            if (progress >= capWidth + fillableWidth) {
                int rightCapProgress = Math.min(progress - capWidth - fillableWidth, capWidth);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, textureWidth, 5, textureWidth - capWidth, 0, x + capWidth + fillableWidth, y, rightCapProgress, 5);
            }
        }

        Font textRenderer = Minecraft.getInstance().font;

        String level = String.valueOf((int)xp);
        int textXPos = x + barWidth / 2 - textRenderer.width(level) / 2;

        drawTextOutline(context, textRenderer, level, textXPos, y - 1, CommonColors.BLACK);
        context.drawString(textRenderer, level, textXPos, y - 1, 0xFF5FBE18);
    }

    private void drawTextOutline(GuiGraphics context, Font renderer,
                                 String text, int x, int y, int outlineColor) {
        context.drawString(renderer, text, x - 1, y, outlineColor, false);
        context.drawString(renderer, text, x + 1, y, outlineColor, false);
        context.drawString(renderer, text, x, y - 1, outlineColor, false);
        context.drawString(renderer, text, x, y + 1, outlineColor, false);
    }
}