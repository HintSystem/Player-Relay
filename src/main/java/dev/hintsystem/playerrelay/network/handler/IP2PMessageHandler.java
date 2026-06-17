package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.network.connection.PeerConnection;

public interface IP2PMessageHandler extends MessageHandler<PeerConnection> {
    void onPeerAccepted(PeerConnection peer);
    void onConnectedToPeer(PeerConnection peer);
    void onPeerDisconnected(PeerConnection peer);
    void onClose();
}
