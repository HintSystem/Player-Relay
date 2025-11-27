package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;

public interface P2PMessageHandler extends MessageHandler<PeerConnection> {
    TrackedPlayerList.Sublist getPlayerList();

    void onPeerAccepted(PeerConnection peer);
    void onConnectedToPeer(PeerConnection peer);
    void onClose();
}
