package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Connection {
    protected volatile boolean connected = true;
    protected volatile RelayVersionPayload versionPayload = null;

    public final Set<UUID> announcedPlayers = ConcurrentHashMap.newKeySet();

    public boolean isVersionValid() { return isVersionValid(versionPayload); }

    protected static boolean isVersionValid(RelayVersionPayload versionPayload) {
        return versionPayload != null
            && versionPayload.networkVersion == RelayVersionPayload.NETWORK_VERSION;
    }

    protected static boolean isVersionHandshake(PayloadMessage message) {
        return message.getPayload() instanceof RelayVersionPayload;
    }

    public void onVersionHandshake(RelayVersionPayload versionPayload) {
        this.versionPayload = versionPayload;
    }

    public abstract void sendMessage(PayloadMessage message);

    public abstract void disconnect();
}
