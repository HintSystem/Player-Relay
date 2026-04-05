package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import java.util.*;

public class TrackedPlayerMap {
    private final HashMap<UUID, PlayerInfoPayload> trackedPlayers = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    public interface Listener {
        void onPut(UUID id, PlayerInfoPayload playerInfo);
        void onRemove(UUID id);
        void onClear();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public Map<UUID, PlayerInfoPayload> entries() { return Collections.unmodifiableMap(trackedPlayers); }
    public PlayerInfoPayload get(UUID id) { return trackedPlayers.get(id); }

    public PlayerInfoPayload put(UUID id, PlayerInfoPayload playerInfo) {
        PlayerInfoPayload old = trackedPlayers.put(id, playerInfo);

        for (var l : listeners) {
            l.onPut(id, playerInfo);
        }

        return old;
    }

    public PlayerInfoPayload putIfAbsent(UUID id, PlayerInfoPayload playerInfo) {
        PlayerInfoPayload old = trackedPlayers.putIfAbsent(id, playerInfo);

        if (old == null) {
            for (var l : listeners) {
                l.onPut(id, playerInfo);
            }
        }

        return old;
    }

    public PlayerInfoPayload remove(UUID id) {
        PlayerInfoPayload old = trackedPlayers.remove(id);

        if (old != null) {
            for (var l : listeners) {
                l.onRemove(id);
            }
        }

        return old;
    }

    public void clear() {
        if (trackedPlayers.isEmpty()) return;

        trackedPlayers.clear();
        for (var l : listeners) {
            l.onClear();
        }
    }
}
