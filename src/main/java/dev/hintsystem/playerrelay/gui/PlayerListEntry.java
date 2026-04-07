package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

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

    private OtherClientPlayerEntity playerEntity;
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
        if (config.useResourcePackIcons) return Identifier.ofVanilla(path);

        return CommonCore.identifier(path);
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

        PlayerStatusEffectsData statusEffectsData = playerInfo.getComponent(PlayerStatusEffectsData.class);
        if (statusEffectsData != null) {
            playerEntity.setFrozenTicks(statusEffectsData.isFrozen() ? playerEntity.getMinFreezeDamageTicks() + 4 : 0);
            playerEntity.setOnFire(statusEffectsData.isOnFire());
        }

        PlayerEquipmentData equipmentData = playerInfo.getComponent(PlayerEquipmentData.class);
        if (equipmentData != null) equipmentData.applyToPlayer(player);
    }

    @Nullable
    public OtherClientPlayerEntity getRenderPlayerEntity() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if ((this.playerEntity == null || this.playerEntity.getEntityWorld() != world) && world != null) {
            this.playerEntity = new OtherClientPlayerEntity(world, playerInfo.toGameProfile());
        }

        return this.playerEntity;
    }

    public SkinTextures getPlayerSkinTextures() {
        return MinecraftClient.getInstance().getPlayerSkinCache()
            .get(ProfileComponent.ofStatic(playerInfo.toGameProfile()))
            .getTextures();
    }

    private Identifier getHeartTypeTexture(InGameHud.HeartType heartType, boolean half, boolean blinking) {
        PlayerWorldData world = playerInfo.getComponent(PlayerWorldData.class);
        return iconIdentifier(
            heartType.getTexture(
                world != null && world.isHardcore(),
                half,
                blinking
            ).getPath()
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
            } else if (effects.isFrozen()) {
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
            if (value == 0) return iconIdentifier("hud/food_empty_hunger");
            if (value == 1) return iconIdentifier("hud/food_half_hunger");
            return iconIdentifier("hud/food_full_hunger");
        }

        if (value == 0) return iconIdentifier("hud/food_empty");
        if (value == 1) return iconIdentifier("hud/food_half");
        return iconIdentifier("hud/food_full");
    }

    public void render(DrawContext context, int x, int y, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

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
                PlayerSkinDrawer.draw(context, getPlayerSkinTextures(), x, y, config.iconWidth);
            }

            drawPlayerOverlay(context, x, y, x2, y2);
            x += config.iconWidth + config.padding;
        }

        // Render player name
        int maxNameX = (playerStatusEffects != null) ? drawStatusEffects(context, playerStatusEffects, x + config.infoWidth, y)
            : x + config.infoWidth;
        context.enableScissor(x, y, maxNameX, y + 9);
        context.drawTextWithShadow(client.textRenderer, playerInfo.getName(), x, y, playerInfo.getNameColor());
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
        drawXpBar(context, playerStats.xp, config.infoWidth, x, y);

        if (config.showDimensionIcon) drawDimensionIcon(context, playerWorld.dimension, dimensionIconX, dimensionIconY);
    }

    enum PlayerOverlayState {
        NONE,
        AFK("AFK", Colors.WHITE, ColorHelper.withAlpha(0.6f, Colors.DARK_GRAY), 0),
        DEAD("DEAD", Colors.RED, ColorHelper.withAlpha(0.2f, Colors.RED), ColorHelper.withAlpha(0.2f, Colors.RED));

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

    private void drawPlayerUnderlay(DrawContext context, int x1, int y1, int x2, int y2) {
        PlayerOverlayState state = PlayerOverlayState.getState(playerInfo);
        if (state.underlayColor != 0) context.fill(x1, y1, x2, y2, state.underlayColor);
    }

    private void drawPlayerOverlay(DrawContext context, int x1, int y1, int x2, int y2) {
        PlayerOverlayState state = PlayerOverlayState.getState(playerInfo);

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

    public enum DimensionIcon {
        OVERWORLD(World.OVERWORLD, new ItemStack(Items.GRASS_BLOCK)),
        NETHER(World.NETHER, new ItemStack(Items.NETHERRACK)),
        END(World.END, new ItemStack(Items.END_PORTAL_FRAME));

        public final RegistryKey<World> dimension;
        public final ItemStack displayItem;

        private static final Map<RegistryKey<World>, DimensionIcon> BY_DIMENSION = new HashMap<>();

        static {
            for (DimensionIcon icon : values()) BY_DIMENSION.put(icon.dimension, icon);
        }

        DimensionIcon(RegistryKey<World> dimension, ItemStack displayItem) {
            this.dimension = dimension;
            this.displayItem = displayItem;
        }

        @Nullable
        public static DimensionIcon getIcon(RegistryKey<World> dimension) { return BY_DIMENSION.get(dimension); }
    }

    private void drawDimensionIcon(DrawContext context, RegistryKey<World> dimension, int centerX, int centerY) {
        DimensionIcon dimensionIcon = DimensionIcon.getIcon(dimension);
        if (dimensionIcon == null) return;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();

        float scale = 0.8f;
        int scaledSize = (int)(16 * scale);
        matrices.translate(centerX - (int)(scaledSize/4), centerY - (int)(scaledSize/2));
        matrices.scale(scale, scale);

        context.drawItem(dimensionIcon.displayItem, 0, 0);

        matrices.popMatrix();
    }

    private int drawStatusEffects(DrawContext context, PlayerStatusEffectsData statusEffects, int x, int y) {
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

    private void drawXpBar(DrawContext context, float xp, int barWidth, int x, int y) {
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