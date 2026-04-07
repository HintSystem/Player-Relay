package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public class ConnectionCollectorGroup implements ConnectionCollectorReader<Connection>, TrackedPlayerMap.Listener {
    private final List<ConnectionCollector<? extends Connection>> collectors;
    private final Map<UUID, PlayerInfoPayload> mergedTrackedPlayers = new HashMap<>();

    ConnectionCollectorGroup(List<ConnectionCollector<? extends Connection>> collectors) {
        this.collectors = collectors;

        for (var c : collectors) {
            c.trackedPlayers.addListener(this);
        }

        rebuildTrackedPlayers();
    }

    @SafeVarargs
    public static ConnectionCollectorGroup with(ConnectionCollector<? extends Connection>... collectors) {
        if (collectors.length == 0)
            throw new IllegalArgumentException("Group must have at least one collector");

        return new ConnectionCollectorGroup(List.of(collectors));
    }

    public Iterable<Connection> getAll() {
        return () -> new Iterator<>() {
            private final Iterator<ConnectionCollector<? extends Connection>> collectorIt = collectors.iterator();
            private Iterator<? extends Connection> current = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && collectorIt.hasNext()) {
                    current = collectorIt.next().getAll().iterator();
                }
                return current.hasNext();
            }

            @Override
            public Connection next() {
                if (!hasNext()) throw new NoSuchElementException();
                return current.next();
            }
        };
    }

    public Map<UUID, PlayerInfoPayload> getTrackedPlayers() {
        return Collections.unmodifiableMap(mergedTrackedPlayers);
    }

    @Nullable
    public PlayerInfoPayload getPlayer(UUID id) {
        return mergedTrackedPlayers.get(id);
    }

    private void rebuildTrackedPlayers() { rebuildTrackedPlayers(mergedTrackedPlayers); }

    private void rebuildTrackedPlayers(Map<UUID, PlayerInfoPayload> trackedPlayers) {
        trackedPlayers.clear();
        for (var c : collectors) {
            trackedPlayers.putAll(c.getTrackedPlayers());
        }
    }

    private void assertPlayers() {
        Map<UUID, PlayerInfoPayload> tempPlayers = new HashMap<>();
        rebuildTrackedPlayers(tempPlayers);

        assert mergedTrackedPlayers.equals(tempPlayers);
    }

    @Override
    public void onPut(UUID id, PlayerInfoPayload playerInfo) {
        // Resolve priority (use last collector as source)
        for (int i = collectors.size() - 1; i >= 0; i--) {
            var map = collectors.get(i).getTrackedPlayers();
            if (map.containsKey(id)) {
                mergedTrackedPlayers.put(id, map.get(id));

                // assertPlayers();
                return;
            }
        }
    }

    @Override
    public void onRemove(UUID id) {
        // Re-resolve data from other collectors
        for (int i = collectors.size() - 1; i >= 0; i--) {
            var map = collectors.get(i).getTrackedPlayers();
            if (map.containsKey(id)) {
                mergedTrackedPlayers.put(id, map.get(id));

                // assertPlayers();
                return;
            }
        }

        // No collectors contain data for this player, remove them
        mergedTrackedPlayers.remove(id);

        // assertPlayers();
    }

    @Override
    public void onClear() { rebuildTrackedPlayers(); }
}
