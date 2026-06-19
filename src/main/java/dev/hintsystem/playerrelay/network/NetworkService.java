package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.config.CommonConfig;
import dev.hintsystem.playerrelay.network.connection.PeerConnection;

import java.net.*;
import java.util.concurrent.CompletableFuture;

public class NetworkService {
    public static InetSocketAddress parseAddress(String input, int defaultPort) throws URISyntaxException {
        String normalized = input;

        // Bare IPv6 without brackets
        if (input.indexOf(':') != input.lastIndexOf(':') && !input.startsWith("[")) {
            normalized = "[" + input + "]";
        }

        URI uri = new URI("tcp://" + normalized);

        String host = uri.getHost();
        int port = uri.getPort();

        if (host == null) {
            throw new IllegalArgumentException("Invalid address");
        }

        if (port == -1) port = defaultPort;
        return new InetSocketAddress(host, port);
    }

    public static CompletableFuture<PeerConnection> connect(String address) throws Exception {
        InetSocketAddress socketAddress;
        if (JoinCode.isJoinCode(address)) {
            try {
                socketAddress = JoinCode.decode(PlayerRelay.NETWORK_VERSION, address, "");
            } catch (Exception e) {
                throw new Exception("Failed to decode join code:\n" + e.getMessage(), e);
            }
        } else {
            socketAddress = parseAddress(address, P2PNetworkManager.DEFAULT_PORT);
        }

        return CommonCore.getP2PNetworkManager().connectToPeerAsync(socketAddress)
            .whenComplete((peer, throwable) -> {
                if (throwable != null) return;

                peer.requireVersionHandshake().whenComplete((versionPayload, err) -> {
                    if (err == null) ClientCore.onConnect(peer.getAddressFingerprint());
                });
            });
    }

    public static String getHostAddress() throws Exception {
        P2PNetworkManager manager = CommonCore.getP2PNetworkManager();
        if (!manager.isHost()) throw new Exception("Cannot get host address when not hosting");

        CommonConfig config = CommonCore.getConfig();
        return switch (config.connectionAddress) {
            case "external" -> manager.getExternalIp();
            case "local" -> manager.getLocalIp();
            default -> config.connectionAddress;
        };
    }

    public static String getConnectAddress() throws Exception {
        P2PNetworkManager manager = CommonCore.getP2PNetworkManager();
        if (!manager.isHost()) throw new Exception("Cannot get connect address when not hosting");

        InetAddress address = InetAddress.getByName(getHostAddress());
        if (CommonCore.getConfig().useJoinCodes) {
            return JoinCode.create(PlayerRelay.NETWORK_VERSION, address, manager.getPort(), "");
        }

        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }

        return host + ":" + manager.getPort();
    }
}
