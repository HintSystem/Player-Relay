package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelayClient;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import org.joml.Vector2i;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerList implements HudElement {
    private final Map<UUID, PlayerListEntry> entries = new LinkedHashMap<>();

    public static final int ENTRY_GAP = 5;
    public static final int BACKGROUND_PADDING = 3;

    public final PlayerListEntry.Config entryConfig = new PlayerListEntry.Config();

    public void onClientTickEnd(MinecraftClient client) {
        if (!PlayerRelayClient.config.showPlayerList) {
            entries.clear();
            return;
        }

        Map<UUID, PlayerInfoPayload> connectedPlayers = ClientCore.getListedPlayers();
        updateEntries(connectedPlayers);

        for (PlayerListEntry entry : entries.values()) entry.tick();
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        AnchorPoint anchor = PlayerRelayClient.config.playerListAnchorPoint;
        Vector2i offset = PlayerRelayClient.config.playerListOffset;

        entryConfig.infoWidth = PlayerRelayClient.config.playerListInfoWidth;
        entryConfig.padding = BACKGROUND_PADDING;
        entryConfig.showDimensionIcon = PlayerRelayClient.config.showPlayerListDimensionIcon;
        entryConfig.useResourcePackIcons = PlayerRelayClient.config.useResourcePackIcons;
        entryConfig.playerIconType = PlayerRelayClient.config.playerListIconType;
        entryConfig.anchorPoint = anchor;

        int entryWidth = entryConfig.getWidth() + BACKGROUND_PADDING*2;
        int entryHeight = entryConfig.getHeight() + BACKGROUND_PADDING*2;

        int totalHeight = (entryHeight + ENTRY_GAP)
            * Math.min(entries.size(), PlayerRelayClient.config.playerListMaxPlayers)
            - ENTRY_GAP;

        int xOffset = (anchor.x == 1) ? -offset.x : offset.x;
        int yOffset = (anchor.y == 1) ? -offset.y : offset.y;

        int[] origin = anchor.resolve(context, entryWidth, totalHeight);

        int i = 0;
        int y = origin[1] + yOffset;
        for (PlayerListEntry entry : entries.values()) {
            if (i++ >= PlayerRelayClient.config.playerListMaxPlayers) break;

            int x = origin[0] + xOffset;

            context.fill(
                x, y,
                x + entryWidth, y + entryHeight,
                PlayerRelayClient.config.playerListbackgroundColor.getRGB()
            );

            entry.render(context, x + BACKGROUND_PADDING, y + BACKGROUND_PADDING, tickCounter);

            y += entryHeight + ENTRY_GAP;
        }
    }

    private void updateEntries(Map<UUID, PlayerInfoPayload> connectedPlayers) {
        entries.keySet().retainAll(connectedPlayers.keySet());

        for (Map.Entry<UUID, PlayerInfoPayload> player : connectedPlayers.entrySet()) {
            UUID playerId = player.getKey();
            PlayerInfoPayload playerInfo = player.getValue();

            PlayerListEntry entry = entries.get(playerId);
            if (entry == null) {
                entry = new PlayerListEntry(playerInfo, entryConfig);
                entries.put(playerId, entry);
            } else {
                entry.playerInfo = playerInfo;
            }
        }
    }
}
