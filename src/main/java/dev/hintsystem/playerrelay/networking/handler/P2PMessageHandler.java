package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import org.jetbrains.annotations.Nullable;

public class P2PMessageHandler extends PayloadMessageHandler<PeerConnection> {
    public final NetworkLogger logger;

    @Nullable
    private final PayloadMessageHandler<Void> clientMessageHandler;
    public final TrackedPlayerList.Sublist playerList;

    public P2PMessageHandler(NetworkLogger logger, TrackedPlayerList.Sublist playerList, @Nullable PayloadMessageHandler<Void> clientMessageHandler) {
        this.logger = logger.withLocation(LogLocation.P2P_MESSAGE_HANDLER);
        this.playerList = playerList;
        this.clientMessageHandler = clientMessageHandler;

        init();
    }

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
            addPlayerInfo(playerList, playerInfo);
            passPayload(playerInfo);
        });

        register(PlayerInventoryPayload.class, (inventory, sender) -> {
            if (inventory.isRequest()) {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;

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

    public void onPeerAccepted(PeerConnection peer) {
        // Send info about host player to client peer
        PlayerInfoPayload clientInfo = ClientCore.getUpdatedClientInfo();
        if (clientInfo != null) {
            clientInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, false);
            peer.sendMessage(clientInfo.message());
        }

        // Send info about all known players to client peer
        for (PlayerInfoPayload playerInfo : playerList.tracker.getAllTrackedPlayers().values()) {
            playerInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, false);
            peer.sendMessage(playerInfo.message());
        }
    }

    public void onConnectedToPeer(PeerConnection peer) {
        // Send info about client player to host peer
        PlayerInfoPayload clientInfo = ClientCore.getUpdatedClientInfo();
        if (clientInfo != null) {
            clientInfo.setFlag(PlayerInfoPayload.FLAGS.NEW_CONNECTION, true);
            peer.sendMessage(clientInfo.message());
        }
    }

    private void passPayload(IPayload payload) {
        if (clientMessageHandler != null) clientMessageHandler.handlePayload(payload, null);
    }

    @Override
    protected void onMessagePass(PayloadMessage message, PeerConnection context) {
        if (clientMessageHandler != null) clientMessageHandler.handleMessage(message, null);
    }
}
