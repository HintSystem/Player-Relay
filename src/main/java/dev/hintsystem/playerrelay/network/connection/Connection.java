package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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

    public static String addressFingerprint(InetAddress address) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                address.toString().getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash, 0, 3);
        } catch (Exception e) {
            return "unknown";
        }
    }

    public void onVersionHandshake(RelayVersionPayload versionPayload) {
        this.versionPayload = versionPayload;
    }

    public abstract void sendMessage(PayloadMessage message);

    public abstract void disconnect();
}
