package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.connection.PeerConnection;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.network.connection.PeerConnectionCollector;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.entity.player.PlayerEntity;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class P2PMessageHandler extends PayloadMessageHandler<PeerConnection> implements IP2PMessageHandler {
    public final NetworkLogger logger;

    public final PeerConnectionCollector peerConnections;

    @Nullable
    private final CommonCore.LocalInfoProvider clientInfoProvider;
    private final PayloadMessageHandler<Void> clientMessageHandler;

    public P2PMessageHandler(
        PeerConnectionCollector peerConnections,
        @Nullable CommonCore.LocalInfoProvider clientInfoProvider,
        @Nullable PayloadMessageHandler<Void> clientMessageHandler,
        NetworkLogger logger
    ) {
        this.logger = logger.withLocation(LogLocation.P2P_MESSAGE_HANDLER);
        this.peerConnections = peerConnections;
        this.clientInfoProvider = clientInfoProvider;
        this.clientMessageHandler = clientMessageHandler;

        init();
    }

    @Override
    protected void init() {
        register(PayloadRegistry.RELAY_VERSION, (version, sender) -> sender.onVersionHandshake(version));

        register(PayloadRegistry.UDP_HANDSHAKE, (udpHandshake, sender) -> {
            sender.setPeerUdpId(udpHandshake.getUdpId(), udpHandshake.getUdpPort());

            logger.info().message("UDP handshake received, id: {}, port: {}", udpHandshake.getUdpId(), udpHandshake.getUdpPort()).build();
        });

        register(PayloadRegistry.UDP_PING, (udpPing, sender) -> sender.onUdpPingReceived(udpPing));


        register(PayloadRegistry.PLAYER_INFO, (playerInfo, sender) -> {
            UUID clientPlayerId = clientInfoProvider != null ? clientInfoProvider.getLocalPlayerId() : null;
            peerConnections.updatePlayer(sender, playerInfo, clientPlayerId);

            passPayload(playerInfo);
        });

        register(PayloadRegistry.PLAYER_INVENTORY, this::onPlayerInventory);
        register(PayloadRegistry.PLAYER_DISCONNECT, this::onPlayerDisconnect);
    }

    public void onPlayerInventory(PlayerInventoryPayload inventory, PeerConnection sender) {
        if (inventory.isRequest()) {
            if (clientInfoProvider == null) return;
            PlayerEntity player = clientInfoProvider.getLocalPlayer();

            if (player != null && player.getUuid().equals(inventory.playerId)) {
                sender.sendMessage(PlayerInventoryPayload.respond(player, inventory.isEnderChest()).message());
            }
        } else {
            passPayload(inventory);
        }
    }

    public void onPlayerDisconnect(PlayerDisconnectPayload disconnect, PeerConnection sender) {
        passPayload(disconnect);
        peerConnections.removeAnnouncedPlayer(sender, disconnect.playerId()); // Remove after passing, because other handler might rely on tracked player info
    }

    @Override
    public void onPeerAccepted(PeerConnection peer) {
        if (clientInfoProvider != null) {
            // Send info about host player to client peer
            PlayerInfoPayload clientInfo = clientInfoProvider.getClientInfo();
            if (clientInfo != null) {
                clientInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, false);
                peer.sendMessage(clientInfo.message());
            }
        }

        // Send info about all known players to client peer
        for (PlayerInfoPayload playerInfo : peerConnections.getTrackedPlayers().values()) {
            playerInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, false);
            peer.sendMessage(playerInfo.message());
        }
    }

    @Override
    public void onConnectedToPeer(PeerConnection peer) {
        if (clientInfoProvider == null) return;
        
        // Send info about client player to host peer
        PlayerInfoPayload clientInfo = clientInfoProvider.getClientInfo();
        if (clientInfo != null) {
            clientInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, true);
            peer.sendMessage(clientInfo.message());
        }
    }

    @Override
    public void onPeerDisconnected(PeerConnection peer) {
        for (UUID playerId : peer.announcedPlayers) {
            if (!peerConnections.getTrackedPlayers().containsKey(playerId)) continue;

            // Send disconnect message to other peers if client is host and also process onPlayerDisconnect on client
            peer.getP2PManager().handleMessage(peer, new PlayerDisconnectPayload(playerId).message());
        }
    }

    @Override
    public void onClose() {}

    private void passPayload(Payload payload) {
        if (clientMessageHandler != null) clientMessageHandler.handlePayload(payload, null);
    }

    @Override
    protected void onMessagePass(PayloadMessage message, PeerConnection context) {
        if (clientMessageHandler != null) clientMessageHandler.handleMessage(message, null);
    }
}
