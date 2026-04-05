package dev.hintsystem.playerrelay.network.connection;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Connection {
    protected volatile boolean connected = true;

    public final Set<UUID> announcedPlayers = ConcurrentHashMap.newKeySet();

    public abstract void disconnect();
}
