package dev.hintsystem.playerrelay.network;

import dev.hintsystem.playerrelay.network.logging.LogEventTypes;
import dev.hintsystem.playerrelay.network.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.connection.Connection;
import dev.hintsystem.playerrelay.network.connection.PeerConnection;
import dev.hintsystem.playerrelay.network.connection.PeerConnectionCollector;
import dev.hintsystem.playerrelay.network.handler.IP2PMessageHandler;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;
import dev.hintsystem.playerrelay.network.logging.LogEventLocation;

import org.jetbrains.annotations.Nullable;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class P2PNetworkManager {
    public static final int DEFAULT_PORT = 25566;
    public static final int MAX_UDP_PACKET_SIZE = 65535;
    public static final int UDP_RECEIVE_TIMEOUT = 1000;
    public static final int MAX_MESSAGE_ID_HISTORY = 1024;

    private final PeerConnectionCollector connections;
    private final IP2PMessageHandler messageHandler;

    public final NetworkConfig config;
    public final NetworkLogger logger;

    private UPnPManager upnpManager;
    private DatagramSocket udpSocket;
    private ServerSocket serverSocket;
    private int serverPort;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean isHost = false;

    private final Set<UUID> recentMessageIds = Collections.newSetFromMap(
        new LinkedHashMap<>(MAX_MESSAGE_ID_HISTORY + 1, 1.0f, false) {
            @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) { return size() > MAX_MESSAGE_ID_HISTORY; }
        }
    );

    public P2PNetworkManager(
        PeerConnectionCollector connectionCollector,
        IP2PMessageHandler p2pMessageHandler,
        NetworkConfig config,
        NetworkLogger logger
    ) {
        this.connections = connectionCollector;
        this.messageHandler = p2pMessageHandler;
        this.config = config;
        this.logger = logger.withLocation(LogEventLocation.NETWORK_MANAGER);

        this.executor = Executors.newCachedThreadPool();

        if (config.autoHost) startServerAsync();
    }

    public void startServer() throws Exception {
        if (running.get()) throw new Exception("Server already running");

        if (upnpManager == null && config.UPnPEnabled) {
            try {
                upnpManager = new UPnPManager(logger);
            } catch (Exception e) {
                logger.builder()
                    .type(LogEventTypes.UPNP_FAIL)
                    .title("UPnP not available")
                    .cause(e).warn();
            }
        }

        serverSocket = findAvailablePort(config.defaultHostingPort);
        serverPort = serverSocket.getLocalPort();

        try {
            udpSocket = new DatagramSocket(serverPort);
            udpSocket.setSoTimeout(UDP_RECEIVE_TIMEOUT);
        } catch (SocketException e) {
            logger.builder()
                .title("Failed to create UDP socket for server")
                .cause(e).error();
        }

        // Open port via UPnP if available
        if (upnpManager != null && config.UPnPEnabled) {
            boolean tcpOpened = upnpManager.openPort(serverPort, "TCP");
            boolean udpOpened = upnpManager.openPort(serverPort, "UDP");
            if (tcpOpened && udpOpened) {
                logger.builder()
                    .message("Ports TCP/UDP '{}' forwarded successfully.", serverPort)
                    .info();
            } else {
                logger.builder()
                    .title("Port forwarding incomplete")
                    .message("TCP - {}, UDP - {}", tcpOpened ? "open" : "closed", udpOpened ? "open" : "closed")
                    .warn();
            }
        }

        running.set(true);
        isHost = true;

        executor.submit(this::acceptTcpConnections);
        executor.submit(this::handleUdpMessages);

        logger.builder()
            .message("Player Relay server started on port {}", serverPort)
            .info();
    }

    public void stopServer() {
        running.set(false);
        isHost = false;

        // Close all peer connections
        connections.close();
        messageHandler.onClose();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.builder()
                    .message("Error closing TCP server socket: {}", e.getMessage())
                    .error();
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

        logger.builder()
            .message("Player Relay server stopped")
            .info();
    }

    public PeerConnection connectToPeer(InetSocketAddress socketAddress) throws Exception {
        running.set(true);

        InetAddress address = socketAddress.getAddress();
        if (serverSocket != null) {
            int localPort = serverSocket.getLocalPort();

            if (localPort == socketAddress.getPort()) {
                for (InetAddress localAddr : InetAddress.getAllByName(InetAddress.getLocalHost().getHostName())) {
                    if (localAddr.equals(address) || address.isLoopbackAddress()) {
                        throw new IllegalStateException("Cannot connect to self (" + socketAddress + ")");
                    }
                }
            }
        }

        for (PeerConnection p : connections.getAll()) {
            if (p.getRemoteAddress().equals(socketAddress)) {
                throw new IllegalStateException("Already connected to " + p.getAddressFingerprint());
            }
        }

        if (udpSocket == null) {
            try {
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(UDP_RECEIVE_TIMEOUT);
                executor.submit(this::handleUdpMessages);
            } catch (SocketException e) {
                logger.builder()
                    .title("Failed to create UDP socket for client")
                    .cause(e).warn();
            }
        }

        Socket socket = new Socket();
        PeerConnection peer = null;
        try {
            socket.connect(socketAddress, config.peerConnectionTimeout);
            peer = new PeerConnection(socket, this);
            connections.add(peer);

            onConnectedToPeer(peer);
            executor.submit(peer);

            logger.builder()
                .message("Connected to peer: " + peer.getAddressFingerprint())
                .info();
            return peer;
        } catch (SocketTimeoutException e) {
            cancelPeerConnection(peer, socket);
            throw new Exception("Connection timeout to [" + Connection.addressFingerprint(socketAddress.getAddress()) + "]", e);
        } catch (IOException e) {
            cancelPeerConnection(peer, socket);
            throw new Exception("Failed to connect to [" + Connection.addressFingerprint(socketAddress.getAddress()) + "]", e);
        }
    }

    public CompletableFuture<Void> startServerAsync() {
        return CompletableFuture.runAsync(() -> {
            try { startServer(); } catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    public CompletableFuture<PeerConnection> connectToPeerAsync(InetSocketAddress socketAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try { return connectToPeer(socketAddress); } catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    public void broadcastMessage(PayloadMessage message) {
        broadcastToAllPeers(message, null);
    }

    private void broadcastToAllPeers(PayloadMessage message, @Nullable PeerConnection sender) {
        for (PeerConnection peer : connections.getAll()) {
            if (peer != sender) peer.sendMessage(message);
        }
    }

    private boolean shouldForwardMessage(PayloadMessage message) {
        return isHost() && message.getPayloadType().shouldForward();
    }

    /** Handles the message on the client and forwards it to other peers */
    public void handleMessage(PeerConnection sender, PayloadMessage message) {
        synchronized (recentMessageIds) {
            if (message.hasMessageId() &&
                !recentMessageIds.add(message.getMessageId())) return; // Do not process if message id has been seen before, to stop packets from continuously looping through the network

            messageHandler.handleMessage(message, sender);
            if (shouldForwardMessage(message)) broadcastToAllPeers(message, sender);
        }
    }

    private void handleUdpMessages() {
        byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        logger.builder()
            .message("UDP socket listening on port {}", udpSocket.getLocalPort())
            .info();
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                udpSocket.receive(packet);

                if (packet.getLength() < 2) {
                    logger.builder()
                        .message("Received UDP packet too small to contain ID")
                        .warn();
                    continue;
                }

                byte[] data = packet.getData();
                short udpId = (short) ((data[0] & 0xFF) << 8 | (data[1] & 0xFF));

                PeerConnection senderPeer = connections.getByUdpId(udpId);
                if (senderPeer == null) {
                    logger.builder()
                        .message("Received UDP packet from unknown peer ID: {}", udpId)
                        .warn();
                    continue;
                }

                byte[] messageData = new byte[packet.getLength() - 2];
                System.arraycopy(data, 2, messageData, 0, messageData.length);

                PayloadMessage message = PayloadMessage.fromBytes(messageData, NetworkProtocol.UDP);
                handleMessage(senderPeer, message);

                logger.builder()
                    .message("Received UDP message from {}:{}, type: {}", packet.getAddress(), packet.getPort(), message.getPayloadType())
                    .debug();
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue loop
            } catch (IOException e) {
                if (running.get())
                    logger.builder()
                        .message("Error receiving UDP packet: {}", e.getMessage())
                        .error();
            }
        }
    }

    private void assignUdpId(PeerConnection peer) {
        short udpId = connections.assignUdpId(peer);

        logger.builder()
            .message("Assigned UDP ID {} to peer {}", udpId, peer.getAddressFingerprint())
            .info();
    }

    private void acceptTcpConnections() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                PeerConnection peer = new PeerConnection(clientSocket, this);
                connections.add(peer);
                executor.submit(peer);

                onPeerAccepted(peer);
            } catch (IOException e) {
                if (running.get()) {
                    logger.builder()
                        .message("Error accepting connection: {}", e.getMessage())
                        .error();
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
                status.append("Local IP: ").append(getLocalIp()).append("\n");
                status.append("External IP: ").append(getExternalIp()).append("\n");
            }
        }
        status.append("Connected peers: ").append(getPeerCount());
        return status.toString();
    }

    public DatagramSocket getUdpSocket() { return udpSocket; }

    @Nullable
    public String getExternalIp() { return (upnpManager != null) ? upnpManager.getExternalIp() : null; }

    @Nullable
    public String getLocalIp() {
        if (upnpManager != null) return upnpManager.getLocalIp();

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) { return null; }
    }

    public int getPeerCount() { return connections.count(); }
    public int getPort() { return serverPort; }
    public boolean isHost() { return isHost; }

    public void onPeerAccepted(PeerConnection peer) {
        peer.sendMessage(new RelayVersionPayload().message());
        assignUdpId(peer);

        messageHandler.onPeerAccepted(peer);
    }

    public void onConnectedToPeer(PeerConnection peer) {
        // Wait for version payload before doing anything
        peer.requireVersionHandshake().whenComplete((ok, err) -> {
            if (err != null) return;
            assignUdpId(peer);

            messageHandler.onConnectedToPeer(peer);
        });
    }

    public void onPeerDisconnected(PeerConnection peer) {
        messageHandler.onPeerDisconnected(peer);

        // Remove connection and related announced players after event, because it might rely on player info
        connections.remove(peer);

        logger.builder()
            .message("Peer disconnected. Active connections: {}", connections.count())
            .info();
    }
}