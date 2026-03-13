package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.PeerConnection;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.*;

import net.minecraft.entity.player.PlayerEntity;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

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
    protected void init() {
        register(PayloadRegistry.RELAY_VERSION, (version, sender) -> sender.onVersionHandshake(version));

        register(PayloadRegistry.UDP_HANDSHAKE, (udpHandshake, sender) -> {
            sender.setPeerUdpId(udpHandshake.getUdpId(), udpHandshake.getUdpPort());

            logger.info().message("UDP handshake received, id: {}, port: {}", udpHandshake.getUdpId(), udpHandshake.getUdpPort()).build();
        });

        register(PayloadRegistry.UDP_PING, (udpPing, sender) -> sender.onUdpPingReceived(udpPing));


        register(PayloadRegistry.PLAYER_INFO, (playerInfo, sender) -> {
            sender.announcedPlayers.add(playerInfo.playerId);
            addPlayerInfo(playerList, playerInfo, clientInfoProvider != null ? clientInfoProvider.getLocalPlayerId() : null);
            passPayload(playerInfo);
        });

        register(PayloadRegistry.PLAYER_INVENTORY, (inventory, sender) -> {
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

        PartyPayload.ActionListener partyHandler = new PartyHandler();
        register(PayloadRegistry.PARTY, (party, sender) -> party.handle(partyHandler));

        register(PayloadRegistry.PLAYER_DISCONNECT, (disconnect, sender) -> {
            sender.announcedPlayers.remove(disconnect.playerId());
            passPayload(disconnect);
            playerList.remove(disconnect.playerId());
        });
    }

    private static class PartyHandler extends PartyPayload.ActionListener {
        @Override
        public void onCreate(PartyPayload party, PartyPayload.CreateAction createAction) {

        }
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
    public void onPeerDisconnected(PeerConnection peer) {
        for (UUID playerId : peer.announcedPlayers) {
            if (!playerList.containsKey(playerId)) continue;

            peer.getP2PManager().handleMessage(peer, new PlayerDisconnectPayload(playerId).message());
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
