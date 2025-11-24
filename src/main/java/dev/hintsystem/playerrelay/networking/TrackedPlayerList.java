package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class TrackedPlayerList {
    private final List<Sublist> sublists = new CopyOnWriteArrayList<>();

    private volatile Map<UUID, PlayerInfoPayload> cachedAllPlayers = Collections.emptyMap();
    private final AtomicLong cacheVersion = new AtomicLong(0);
    private volatile long lastKnownVersion = -1;

    /**
     * Sublists that are created later have higher priority
     * when using {@link #getAllTrackedPlayers()} and {@link #getTrackedPlayer(UUID)}
     */
    public Sublist createSublist() {
        Sublist sublist = new Sublist(this);
        sublists.add(sublist);
        invalidateCache();
        return sublist;
    }

    void invalidateCache() { cacheVersion.incrementAndGet(); }

    /**
     * Returns a tracked player's {@link PlayerInfoPayload} from any active sublist
     */
    @Nullable
    public PlayerInfoPayload getTrackedPlayer(UUID playerId) {
        return getAllTrackedPlayers().get(playerId);
    }

    /**
     * Returns an immutable map of all tracked players across all sublists.
     * If multiple sublists contain the same UUID, the newest one wins.
     * <p>
     * Does <b>not</b> include the client player
     */
    public Map<UUID, PlayerInfoPayload> getAllTrackedPlayers() {
        long currentVersion = cacheVersion.get();
        if (lastKnownVersion == currentVersion) return cachedAllPlayers;

        Map<UUID, PlayerInfoPayload> allPlayers = new ConcurrentHashMap<>();

        for (Sublist sublist : sublists) {
            allPlayers.putAll(sublist.getAll());
        }

        cachedAllPlayers = Collections.unmodifiableMap(allPlayers);
        lastKnownVersion = currentVersion;

        return cachedAllPlayers;
    }

    /**
     * Isolated player list for a single network manager.
     * When this object is no longer referenced, it will be garbage collected
     */
    public static class Sublist implements Map<UUID, PlayerInfoPayload> {
        private final Map<UUID, PlayerInfoPayload> players = new ConcurrentHashMap<>();
        public final TrackedPlayerList tracker;

        Sublist(TrackedPlayerList tracker) {
            this.tracker = tracker;
        }

        /** Returns an immutable view of all players */
        public Map<UUID, PlayerInfoPayload> getAll() { return Collections.unmodifiableMap(players); }

        @Override
        public @Nullable PlayerInfoPayload put(@NotNull UUID playerId, @NotNull PlayerInfoPayload payload) {
            PlayerInfoPayload previous = players.put(playerId, payload);
            tracker.invalidateCache();
            return previous;
        }

        @Override
        public void putAll(@NotNull Map<? extends UUID, ? extends PlayerInfoPayload> map) {
            if (!map.isEmpty()) {
                players.putAll(map);
                tracker.invalidateCache();
            }
        }

        @Override
        public @Nullable PlayerInfoPayload remove(Object key) {
            PlayerInfoPayload removed = players.remove(key);
            if (removed != null) tracker.invalidateCache();
            return removed;
        }

        @Override
        public void clear() {
            if (!players.isEmpty()) {
                players.clear();
                tracker.invalidateCache();
            }
        }

        @Nullable public PlayerInfoPayload get(UUID playerId) {
            return players.get(playerId);
        }
        @Override public @NotNull Set<UUID> keySet() { return players.keySet(); }
        @Override public @NotNull Collection<PlayerInfoPayload> values() { return players.values(); }
        @Override public @NotNull Set<Entry<UUID, PlayerInfoPayload>> entrySet() { return players.entrySet(); }
        @Override public int size() { return players.size(); }
        @Override public boolean isEmpty() { return players.isEmpty(); }
        @Override public boolean containsKey(Object o) { return players.containsKey(o); }
        @Override public boolean containsValue(Object o) { return players.containsValue(o); }
        @Override public PlayerInfoPayload get(Object o) { return players.get(o); }
    }
}
