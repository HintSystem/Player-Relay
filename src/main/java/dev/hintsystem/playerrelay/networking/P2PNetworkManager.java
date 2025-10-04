package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.logging.LogEventTypes;
import dev.hintsystem.playerrelay.logging.PlayerRelayLogger;
import dev.hintsystem.playerrelay.networking.message.*;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.logging.LogLocation;

import net.minecraft.network.PacketByteBuf;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public class P2PNetworkManager {
    public final PlayerRelayLogger logger = new PlayerRelayLogger(LogLocation.NETWORK_MANAGER);

    public static final int MAX_UDP_PACKET_SIZE = 65535;
    public static final int UDP_RECEIVE_TIMEOUT = 1000;
    public static final int MAX_MESSAGE_ID_HISTORY = 1024;

    public static final int DEFAULT_PORT = 25566;

    private UPnPManager upnpManager;
    private DatagramSocket udpSocket;
    private ServerSocket serverSocket;
    private int serverPort;
    private final P2PMessageHandler messageHandler;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean isHost = false;

    private final Set<PeerConnection> connectedPeers = ConcurrentHashMap.newKeySet();
    private final Map<Short, PeerConnection> connectedPeersByUdpId = new ConcurrentHashMap<>();
    private short nextUdpId = 1;
    public final Map<UUID, PlayerInfoPayload> connectedPlayers = new ConcurrentHashMap<>();

    private final Set<UUID> recentMessageIds = Collections.newSetFromMap(
        new LinkedHashMap<>(MAX_MESSAGE_ID_HISTORY + 1, 1.0f, false) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) { return size() > MAX_MESSAGE_ID_HISTORY; }
        }
    );

    public P2PNetworkManager() {
        executor = Executors.newCachedThreadPool();
        messageHandler = new P2PMessageHandler(this);
    }

    public void startServer() throws Exception {
        if (running.get()) { throw new Exception("Server already running"); }

        if (upnpManager == null) {
            try {
                upnpManager = new UPnPManager(logger);
            } catch (Exception e) {
                logger.warn()
                    .type(LogEventTypes.UPNP_FAIL)
                    .title("UPnP not available")
                    .exception(e).build();
            }
        }

        serverSocket = findAvailablePort(PlayerRelay.config.defaultHostingPort);
        serverPort = serverSocket.getLocalPort();

        try {
            udpSocket = new DatagramSocket(serverPort);
            udpSocket.setSoTimeout(UDP_RECEIVE_TIMEOUT);
        } catch (SocketException e) {
            logger.error()
                .title("Failed to create UDP socket for server")
                .exception(e).build();
        }

        // Open port via UPnP if available
        if (upnpManager != null) {
            boolean tcpOpened = upnpManager.openPort(serverPort, "TCP");
            boolean udpOpened = upnpManager.openPort(serverPort, "UDP");
            if (tcpOpened && udpOpened) {
                logger.info().message("Ports TCP/UDP '{}' forwarded successfully.", serverPort).build();
            } else {
                logger.warn()
                    .title("Port forwarding incomplete")
                    .message("TCP - {}, UDP - {}", tcpOpened ? "open" : "closed", udpOpened ? "open" : "closed").build();
            }
        }

        running.set(true);
        isHost = true;

        executor.submit(this::acceptTcpConnections);
        executor.submit(this::handleUdpMessages);

        logger.info().message("Player Relay server started on port {}", serverPort).build();
    }

    public void stopServer() {
        running.set(false);
        isHost = false;

        // Close all peer connections
        for (PeerConnection peer : connectedPeers) { peer.disconnect(); }
        connectedPeers.clear();
        connectedPeersByUdpId.clear();
        connectedPlayers.clear();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error().message("Error closing TCP server socket: {}", e.getMessage()).build();
            }
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
            udpSocket = null;
        }

        if (upnpManager != null && serverPort > 0) {
            upnpManager.closePort(serverPort, "TCP");
            upnpManager.closePort(serverPort, "UDP");
        }

        logger.info().message("Player Relay server stopped").build();
    }

    /** Connect to a peer using address that can be IP, domain, or IP:port format */
    public PeerConnection connectToPeer(String address) throws Exception {
        running.set(true);

        String host;
        int port = DEFAULT_PORT;

        if (address.contains(":")) {
            String[] parts = address.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Port must be between 1 and 65535");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid port number: " + parts[1]);
            }
        } else {
            host = address;
        }

        InetAddress resolvedAddress;
        InetSocketAddress socketAddress;
        try {
            resolvedAddress = InetAddress.getByName(host);
            socketAddress = new InetSocketAddress(resolvedAddress, port);
            logger.info().message("Resolved {} to {}", host, resolvedAddress.getHostAddress()).build();
        } catch (UnknownHostException e) {
            throw new Exception("Could not resolve host: " + host, e);
        }

        if (serverSocket != null) {
            int localPort = serverSocket.getLocalPort();

            if (localPort == port) {
                for (InetAddress localAddr : InetAddress.getAllByName(InetAddress.getLocalHost().getHostName())) {
                    if (localAddr.equals(resolvedAddress) || resolvedAddress.isLoopbackAddress()) {
                        throw new IllegalStateException("Cannot connect to self (" + socketAddress + ")");
                    }
                }
            }
        }
        for (PeerConnection p : connectedPeers) {
            if (p.getRemoteAddress().equals(socketAddress)) {
                throw new IllegalStateException("Already connected to peer (" + p.getRemoteAddress() + ")");
            }
        }

        if (udpSocket == null) {
            try {
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(UDP_RECEIVE_TIMEOUT);
                executor.submit(this::handleUdpMessages);
            } catch (SocketException e) {
                logger.warn()
                    .title("Failed to create UDP socket for client")
                    .exception(e).build();
            }
        }

        Socket socket = new Socket();
        PeerConnection peer = null;
        try {
            socket.connect(socketAddress, PlayerRelay.config.peerConnectionTimeout);
            peer = new PeerConnection(socket, this);
            connectedPeers.add(peer);

            onConnectedToPeer(peer);
            executor.submit(peer);

            logger.info().message("Connected to peer: {}:{} ({})", host, port, resolvedAddress.getHostAddress()).build();
            return peer;
        } catch (SocketTimeoutException e) {
            cancelPeerConnection(peer, socket);
            throw new Exception("Connection timeout to " + host + ":" + port, e);
        } catch (IOException e) {
            cancelPeerConnection(peer, socket);
            throw new Exception("Failed to connect to " + host + ":" + port, e);
        }
    }

    public CompletableFuture<Void> startServerAsync() {
        return CompletableFuture.runAsync(() -> {
            try { startServer(); } catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    public CompletableFuture<PeerConnection> connectToPeerAsync(String address) {
        return CompletableFuture.supplyAsync(() -> {
            try { return connectToPeer(address); } catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    public void broadcastMessage(P2PMessage message) {
        broadcastToAllPeers(message, null);
    }

    private void broadcastToAllPeers(P2PMessage message, PeerConnection sender) {
        for (PeerConnection peer : connectedPeers) {
            if (peer != sender) { peer.sendMessage(message); }
        }
    }

    private boolean shouldForwardMessage(P2PMessage message) {
        synchronized (recentMessageIds) {
            return isHost() && message.getType().shouldForward()
                && recentMessageIds.add(message.getId()); // Do not forward if message id has been seen before, to stop packets from continuously looping through the network
        }
    }

    public void handleMessage(PeerConnection sender, P2PMessage message) {
        messageHandler.handleMessage(message, sender);
        if (shouldForwardMessage(message)) broadcastToAllPeers(message, sender);
    }

    private void handleUdpMessages() {
        byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        logger.info().message("UDP socket listening on port {}", udpSocket.getLocalPort()).build();
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                udpSocket.receive(packet);

                if (packet.getLength() < 2) {
                    logger.warn().message("Received UDP packet too small to contain ID").build();
                    continue;
                }

                byte[] data = packet.getData();
                short udpId = (short) ((data[0] & 0xFF) << 8 | (data[1] & 0xFF));

                PeerConnection senderPeer = connectedPeersByUdpId.get(udpId);
                if (senderPeer == null) {
                    logger.warn().message("Received UDP packet from unknown peer ID: {}", udpId).build();
                    continue;
                }

                byte[] messageData = new byte[packet.getLength() - 2];
                System.arraycopy(data, 2, messageData, 0, messageData.length);

                P2PMessage message = P2PMessage.fromBytes(messageData, NetworkProtocol.UDP);
                handleMessage(senderPeer, message);

                logger.debug().message("Received UDP message from {}:{}, type: {}", packet.getAddress(), packet.getPort(), message.getType().name()).build();
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue loop
            } catch (IOException e) {
                if (running.get()) {
                    logger.error().message("Error receiving UDP packet: {}", e.getMessage()).build();
                }
            }
        }
    }

    private synchronized short assignUdpId(PeerConnection peer) {
        if (nextUdpId == 0) nextUdpId = 1;

        while (connectedPeersByUdpId.containsKey(nextUdpId)) {
            nextUdpId++;
            if (nextUdpId == 0) nextUdpId = 1;
        }

        short udpId = nextUdpId;
        nextUdpId++;

        peer.assignUdpId(udpId);
        connectedPeersByUdpId.put(udpId, peer);

        logger.info().message("Assigned UDP ID {} to peer {}", udpId, peer.getRemoteAddress()).build();
        return udpId;
    }

    private void acceptTcpConnections() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                PeerConnection peer = new PeerConnection(clientSocket, this);
                connectedPeers.add(peer);
                executor.submit(peer);

                onPeerAccepted(peer);
            } catch (IOException e) {
                if (running.get()) {
                    logger.error().message("Error accepting connection: {}", e.getMessage()).build();
                }
            }
        }
    }

    private void cancelPeerConnection(PeerConnection peer, Socket socket) {
        if (peer != null) { peer.disconnect(); }
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    public void shutdown() {
        stopServer();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private ServerSocket findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + 100; port++) {
            try {
                return new ServerSocket(port);
            } catch (IOException e) {
                // Port not available, try next
            }
        }
        throw new RuntimeException("No available ports found");
    }

    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Is host: ").append(isHost).append("\n");
        if (running.get()) {
            status.append("Port: ").append(serverPort).append("\n");
            if (upnpManager != null) {
                status.append("Local IP: ").append(upnpManager.getLocalIp()).append("\n");
                status.append("External IP: ").append(getExternalIp()).append("\n");
            }
        }
        status.append("Connected peers: ").append(getPeerCount());
        return status.toString();
    }

    @Nullable
    public String getConnectionAddress() {
        String portString = (getPort() > 0) ? ":" + getPort() : "";

        return switch (PlayerRelay.config.connectionAddress) {
            case "external" -> {
                String externalIp = getExternalIp();
                yield (externalIp != null) ? externalIp + portString : null;
            }
            case "local" -> {
                String localIp = getLocalIp();
                yield (localIp != null) ? localIp + portString : null;
            }
            default -> PlayerRelay.config.connectionAddress;
        };
    }

    public P2PMessageHandler getMessageHandler() { return messageHandler; }
    public DatagramSocket getUdpSocket() { return udpSocket; }

    @Nullable
    public String getLocalIp() { return (upnpManager != null) ? upnpManager.getLocalIp() : null; }

    @Nullable
    public String getExternalIp() { return (upnpManager != null) ? upnpManager.getExternalIp() : null; }

    public int getPeerCount() { return connectedPeers.size(); }
    public int getPort() { return serverPort; }
    public boolean isHost() { return isHost; }

    public void onPeerAccepted(PeerConnection peer) {
        peer.sendMessage(new RelayVersionPayload().message());
        assignUdpId(peer);

        // Send info about host player
        PlayerInfoPayload clientInfo = ClientCore.updateClientInfo();
        if (clientInfo != null) { peer.sendMessage(clientInfo.setNewConnectionFlag(false).message()); }

        // Send info about all connected players
        for (PlayerInfoPayload player : connectedPlayers.values()) {
            peer.sendMessage(player.setNewConnectionFlag(false).message());
        }
    }

    public void onConnectedToPeer(PeerConnection peer) {
        // Wait for version payload before doing anything
        peer.requireVersionHandshake().whenComplete((ok, err) -> {
            if (err != null) return;
            assignUdpId(peer);

            // Send info about client player to host
            PlayerInfoPayload clientInfo = ClientCore.updateClientInfo();
            if (clientInfo != null) { peer.sendMessage(clientInfo.setNewConnectionFlag(true).message()); }
        });
    }

    public void onPeerDisconnected(PeerConnection peer) {
        connectedPeers.remove(peer);

        if (peer.assignedUdpId != null) {
            nextUdpId = peer.assignedUdpId;
            connectedPeersByUdpId.remove(peer.assignedUdpId);
        }

        for (UUID playerId : peer.announcedPlayers) {
            if (!connectedPlayers.containsKey(playerId)) continue;

            PacketByteBuf uuidBuf = new PacketByteBuf(Unpooled.buffer());
            uuidBuf.writeUuid(playerId);

            handleMessage(peer, new P2PMessage(P2PMessageType.PLAYER_DISCONNECT, uuidBuf));
        }
        logger.info().message("Peer disconnected. Active connections: {}", connectedPeers.size()).build();
    }
}