package dev.hintsystem.playerrelay.network.connection;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PeerConnectionCollector extends ConnectionCollector<PeerConnection> {
    private final Set<PeerConnection> connectedPeers = ConcurrentHashMap.newKeySet();
    private final Map<Short, PeerConnection> connectedPeersByUdpId = new ConcurrentHashMap<>();
    private short nextUdpId = 1;

    public int count() { return connectedPeers.size(); }

    @Override
    public Iterable<PeerConnection> getAll() { return connectedPeers; }

    @Nullable
    public PeerConnection getByUdpId(Short udpId) { return connectedPeersByUdpId.get(udpId); }

    @Override
    public void add(PeerConnection peer) { connectedPeers.add(peer); }

    @Override
    public void remove(PeerConnection peer) {
        connectedPeers.remove(peer);

        if (peer.assignedUdpId != null) {
            nextUdpId = peer.assignedUdpId;
            connectedPeersByUdpId.remove(peer.assignedUdpId);
        }

        // Remove tracked players that are not announced anymore
        removeAnnounced(peer);
    }

    /** Removes the peer's announced players and removes their data if they are no longer announced elsewhere */
    public void removeAnnounced(PeerConnection peer) {
        Set<UUID> stillAnnounced = new HashSet<>();
        for (PeerConnection other : connectedPeers) {
            stillAnnounced.addAll(other.announcedPlayers);
        }

        for (UUID uuid : peer.announcedPlayers) {
            if (!stillAnnounced.contains(uuid)) continue;

            trackedPlayers.remove(uuid);
        }
    }

    @Override
    public void removeAnnouncedPlayer(PeerConnection peer, UUID playerId) {
        if (!peer.announcedPlayers.remove(playerId)) return; // Connection has already removed this player

        for (PeerConnection other : connectedPeers) {
            if (other.announcedPlayers.contains(playerId))
                return; // Player is still announced by another connection
        }

        trackedPlayers.remove(playerId);
    }

    public synchronized short assignUdpId(PeerConnection peer) {
        while (connectedPeersByUdpId.containsKey(nextUdpId)) {
            nextUdpId++;
            if (nextUdpId == 0) nextUdpId = 1;
        }

        short udpId = nextUdpId++;
        peer.assignUdpId(udpId);
        connectedPeersByUdpId.put(udpId, peer);

        return udpId;
    }

    @Override
    public void close() {
        for (PeerConnection peer : connectedPeers) {
            peer.disconnect();
        }

        connectedPeers.clear();
        connectedPeersByUdpId.clear();
        trackedPlayers.clear();
        nextUdpId = 1;
    }
}
