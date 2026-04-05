package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public abstract class ConnectionCollector<C extends Connection> implements ConnectionCollectorReader<C> {
    protected final TrackedPlayerMap trackedPlayers = new TrackedPlayerMap();

    public abstract Iterable<C> getAll();
    public Map<UUID, PlayerInfoPayload> getTrackedPlayers() { return trackedPlayers.entries(); }

    public abstract void add(C connection);
    public abstract void remove(C connection);

    @Nullable
    public PlayerInfoPayload getPlayer(UUID id) { return trackedPlayers.get(id); }

    public void updatePlayer(C connection, PlayerInfoPayload playerInfo) { updatePlayer(connection, playerInfo, null); }

    /** Adds player to tracked players and announced players or merges the existing player info */
    public void updatePlayer(C connection, PlayerInfoPayload playerInfo, UUID clientPlayerId) {
        if (playerInfo.playerId.equals(clientPlayerId)) return; // Do not include the client's info

        connection.announcedPlayers.add(playerInfo.playerId);

        PlayerInfoPayload existingPlayerInfo = trackedPlayers.putIfAbsent(playerInfo.playerId, playerInfo);
        if (existingPlayerInfo != null) {
            existingPlayerInfo.merge(playerInfo);
        }
    }

    public void addAnnouncedPlayer(C connection, PlayerInfoPayload playerInfo) {
        connection.announcedPlayers.add(playerInfo.playerId);
        trackedPlayers.put(playerInfo.playerId, playerInfo);
    }

    public void removeAnnouncedPlayer(C connection, UUID playerId) {
        connection.announcedPlayers.remove(playerId);
        trackedPlayers.remove(playerId);
    }

    public void close() { trackedPlayers.clear(); }
}
