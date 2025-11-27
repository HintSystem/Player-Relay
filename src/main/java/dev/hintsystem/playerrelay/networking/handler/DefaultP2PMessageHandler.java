package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.entity.player.PlayerEntity;

import org.jetbrains.annotations.Nullable;

public class DefaultP2PMessageHandler extends PayloadMessageHandler<PeerConnection> implements P2PMessageHandler {
    public final NetworkLogger logger;

    public final TrackedPlayerList.Sublist playerList;
    @Nullable
    private final CommonCore.LocalInfoProvider clientInfoProvider;
    private final PayloadMessageHandler<Void> clientMessageHandler;

    public DefaultP2PMessageHandler(
        NetworkLogger logger,
        TrackedPlayerList.Sublist playerList,
        @Nullable CommonCore.LocalInfoProvider clientInfoProvider,
        @Nullable PayloadMessageHandler<Void> clientMessageHandler
    ) {
        this.logger = logger.withLocation(LogLocation.P2P_MESSAGE_HANDLER);
        this.playerList = playerList;
        this.clientInfoProvider = clientInfoProvider;
        this.clientMessageHandler = clientMessageHandler;

        init();
    }

    @Override
    public TrackedPlayerList.Sublist getPlayerList() { return playerList; }

    @Override
    protected void init() {
        register(RelayVersionPayload.class, (version, sender) -> sender.onVersionHandshake(version));

        register(UdpHandshakePayload.class, (udpHandshake, sender) -> {
            sender.setPeerUdpId(udpHandshake.getUdpId(), udpHandshake.getUdpPort());

            logger.info().message("UDP handshake received, id: {}, port: {}", udpHandshake.getUdpId(), udpHandshake.getUdpPort()).build();
        });

        register(UdpPingPayload.class, (udpPing, sender) -> sender.onUdpPingReceived(udpPing));


        register(PlayerInfoPayload.class, (playerInfo, sender) -> {
            sender.announcedPlayers.add(playerInfo.playerId);
            addPlayerInfo(playerList, playerInfo, clientInfoProvider != null ? clientInfoProvider.getLocalPlayerId() : null);
            passPayload(playerInfo);
        });

        register(PlayerInventoryPayload.class, (inventory, sender) -> {
            if (inventory.isRequest()) {
                if (clientInfoProvider == null) return;
                PlayerEntity player = clientInfoProvider.getLocalPlayer();

                if (player != null && player.getUuid().equals(inventory.playerId)) {
                    sender.sendMessage(PlayerInventoryPayload.respond(player, inventory.isEnderChest()).message());
                }
            } else {
                passPayload(inventory);
            }
        });

        register(PlayerDisconnectPayload.class, (disconnect, sender) -> {
            sender.announcedPlayers.remove(disconnect.playerId);
            passPayload(disconnect);
            playerList.remove(disconnect.playerId);
        });
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
        for (PlayerInfoPayload playerInfo : playerList.tracker.getAllTrackedPlayers().values()) {
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
    public void onClose() {
        playerList.clear();
    }

    private void passPayload(Payload payload) {
        if (clientMessageHandler != null) clientMessageHandler.handlePayload(payload, null);
    }

    @Override
    protected void onMessagePass(PayloadMessage message, PeerConnection context) {
        if (clientMessageHandler != null) clientMessageHandler.handleMessage(message, null);
    }
}
