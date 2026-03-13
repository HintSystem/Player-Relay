package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.PayloadRegistry;
import dev.hintsystem.playerrelay.payload.PlayerDisconnectPayload;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

/** Handles messages received from the server on the client */
public class S2CMessageHandler extends ClientMessageHandler<Void> {
    public final TrackedPlayerList.Sublist playerList;

    public S2CMessageHandler(NetworkLogger logger, TrackedPlayerList.Sublist playerList) {
        super(logger);
        this.playerList = playerList;

        register(PayloadRegistry.RELAY_VERSION, (version, unused) -> {
            ClientCore.serverRelayVersion = version;
            if (version.networkVersion != RelayVersionPayload.NETWORK_VERSION) {
                logger.versionMismatch(version).build();
            }
        });
    }

    @Override
    public void onPlayerInfo(PlayerInfoPayload playerInfo) {
        addPlayerInfo(playerList, playerInfo, ClientCore.getClientUuid());
        super.onPlayerInfo(playerInfo);
    }

    @Override
    public void onPlayerDisconnect(PlayerDisconnectPayload disconnect) {
        super.onPlayerDisconnect(disconnect);
        playerList.remove(disconnect.playerId());
    }
}
