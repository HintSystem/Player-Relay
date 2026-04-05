package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import java.util.Map;
import java.util.UUID;

public interface ConnectionCollectorReader<C extends Connection> {
    Iterable<C> getAll();
    Map<UUID, PlayerInfoPayload> getTrackedPlayers();

    PlayerInfoPayload getPlayer(UUID id);
}
