package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.network.PeerConnection;

public interface P2PMessageHandler extends MessageHandler<PeerConnection> {
    void onPeerAccepted(PeerConnection peer);
    void onConnectedToPeer(PeerConnection peer);
    void onPeerDisconnected(PeerConnection peer);
    void onClose();
}
