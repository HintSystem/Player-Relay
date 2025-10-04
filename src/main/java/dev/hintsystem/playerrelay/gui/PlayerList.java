package dev.hintsystem.playerrelay.gui;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import org.joml.Vector2i;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerList implements HudElement {
    private final Map<UUID, PlayerListEntry> entries = new LinkedHashMap<>();

    public static final int entryPadding = 5;

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        Map<UUID, PlayerInfoPayload> connectedPlayers = PlayerRelay.getNetworkManager().connectedPlayers;
        if (!PlayerRelay.config.showPlayerList || connectedPlayers.isEmpty()) {
            entries.clear();
            return;
        }

        updateEntries(connectedPlayers);

        int entryWidth = PlayerListEntry.getWidth() + PlayerListEntry.padding;
        int entryHeight = PlayerListEntry.getHeight() + PlayerListEntry.padding;

        int totalHeight = (entryHeight + entryPadding)
            * Math.min(entries.size(), PlayerRelay.config.playerListMaxPlayers)
            - entryPadding;

        AnchorPoint anchor = PlayerRelay.config.playerListAnchorPoint;
        Vector2i offset = PlayerRelay.config.playerListOffset;
        int xOffset = (anchor.x == 1) ? -offset.x : offset.x;
        int yOffset = (anchor.y == 1) ? -offset.y : offset.y;

        int[] origin = anchor.resolve(context, entryWidth, totalHeight);

        int i = 0;
        int y = origin[1] + yOffset;
        for (PlayerListEntry entry : entries.values()) {
            if (i++ >= PlayerRelay.config.playerListMaxPlayers) break;

            int x = origin[0] + xOffset;

            context.fill(
                x, y,
                x + entryWidth + PlayerListEntry.padding, y + entryHeight,
                PlayerRelay.config.playerListbackgroundColor.getRGB()
            );

            entry.render(context, x + PlayerListEntry.padding, y + PlayerListEntry.padding, tickCounter);

            y += entryHeight + entryPadding;
        }
    }

    private void updateEntries(Map<UUID, PlayerInfoPayload> connectedPlayers) {
        entries.keySet().retainAll(connectedPlayers.keySet());

        for (Map.Entry<UUID, PlayerInfoPayload> player : connectedPlayers.entrySet()) {
            UUID playerId = player.getKey();
            PlayerInfoPayload playerInfo = player.getValue();

            PlayerListEntry entry = entries.get(playerId);
            if (entry == null) {
                entry = new PlayerListEntry(playerInfo);
                entries.put(playerId, entry);
            } else {
                entry.setPlayerInfo(playerInfo);
            }
        }
    }
}
