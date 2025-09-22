package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.payload.player.PlayerInfoPayload;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public interface PlayerInfoHandler {
    void onPlayerInfo(PlayerInfoPayload payload, PeerConnection sender);
    void onPlayerDisconnect(UUID playerId, @Nullable PlayerInfoPayload lastInfo);
}
