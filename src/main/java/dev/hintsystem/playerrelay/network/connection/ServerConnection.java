package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.PlayerRelayClient;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.network.logging.NetworkLogger;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

public class ServerConnection extends Connection {
    public ServerConnection(NetworkLogger logger) {
        super(logger);
    }

    public void onConnect() {
        connected = true;
        versionPayload = null;
        sendMessage(new RelayVersionPayload().packet());
    }

    @Override
    public void sendMessage(PayloadMessage message) {
        PlayerRelayClient.sendToServer(new PayloadMessage.Packet(message));
    }

    @Override
    public void disconnect() {
        connected = false;
        versionPayload = null;
    }
}
