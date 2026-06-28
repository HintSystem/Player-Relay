package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.UUID;

public class ServerConnectionCollector extends ConnectionCollector<ServerConnection> {
    // Should only be connected to one game server at a time, so store only 1 connection
    private final ServerConnection serverConnection;

    public ServerConnectionCollector(ServerConnection serverConnection) {
        this.serverConnection = serverConnection;
    }

    /** Always returns one server connection, use {@link Connection#isVersionValid()} to check if connection is usable */
    @NotNull
    public ServerConnection get() { return serverConnection; }

    @Override
    public Iterable<ServerConnection> getAll() { return Collections.singleton(serverConnection); }

    @Override
    public void add(ServerConnection connection) {}

    @Override
    public void remove(ServerConnection connection) {}

    public void updatePlayer(PlayerInfoPayload playerInfo, UUID clientPlayerId) {
        updatePlayer(serverConnection, playerInfo, clientPlayerId);
    }

    public void addAnnouncedPlayer(PlayerInfoPayload playerInfo) { addAnnouncedPlayer(serverConnection, playerInfo); }

    public void removeAnnouncedPlayer(UUID playerId) { removeAnnouncedPlayer(serverConnection, playerId); }

    @Override
    public void close() {
        serverConnection.disconnect();
        super.close();
    }
}
