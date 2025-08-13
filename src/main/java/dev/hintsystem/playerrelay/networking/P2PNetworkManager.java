package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.ClientCore;
import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;

import net.minecraft.network.PacketByteBuf;

import io.netty.buffer.Unpooled;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class P2PNetworkManager {
    public static final int MAX_UDP_PACKET_SIZE = 65535;
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
    public final Map<UUID, PlayerInfoPayload> connectedPlayers = new HashMap<>();

    public P2PNetworkManager() {
        executor = Executors.newCachedThreadPool();
        messageHandler = new P2PMessageHandler(this);
    }

    public void startServer() throws Exception {
        if (running.get()) { throw new Exception("Server already running"); }

        // Initialize UPnP
        if (upnpManager == null) {
            try {
                upnpManager = new UPnPManager();
            } catch (Exception e) {
                PlayerRelay.LOGGER.warn("UPnP not available, proceeding without port forwarding: {}", e.getMessage());
            }
        }

        serverSocket = findAvailablePort(PlayerRelay.config.defaultHostingPort);
        serverPort = serverSocket.getLocalPort();

        try {
            udpSocket = new DatagramSocket(serverPort);
            udpSocket.setSoTimeout(1000);
        } catch (SocketException e) {
            PlayerRelay.LOGGER.error("Failed to create UDP socket on port {}, {}", serverPort, e);
        }

        // Open port via UPnP if available
        if (upnpManager != null) {
            boolean tcpOpened = upnpManager.openPort(serverPort, "TCP");
            boolean udpOpened = upnpManager.openPort(serverPort, "UDP");
            if (tcpOpened && udpOpened) {
                PlayerRelay.LOGGER.info("Ports TCP/UDP '{}' forwarded successfully.", serverPort);
            } else {
                PlayerRelay.LOGGER.warn("Port forwarding incomplete - TCP: {}, UDP: {}", tcpOpened, udpOpened);
            }
        }

        running.set(true);
        isHost = true;

        executor.submit(this::acceptTcpConnections);
        executor.submit(this::handleUdpMessages);

        PlayerRelay.LOGGER.info("Player Relay server started on port {}", serverPort);
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
                PlayerRelay.LOGGER.error("Error closing TCP server socket: {}", e.getMessage());
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

        PlayerRelay.LOGGER.info("Player Relay server stopped");
    }

    /** Connect to a peer using address that can be IP, domain, or IP:port format */
    public void connectToPeer(String address) throws Exception {
        if (isHost()) { return; }
        running.set(true);

        String host;
        int port = DEFAULT_PORT;

        // Parse address - can be "domain.com", "192.168.1.1", "domain.com:25566", or "192.168.1.1:25566"
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
        try {
            resolvedAddress = InetAddress.getByName(host);
            PlayerRelay.LOGGER.info("Resolved {} to {}", host, resolvedAddress.getHostAddress());
        } catch (UnknownHostException e) {
            throw new Exception("Could not resolve host: " + host, e);
        }

        if (udpSocket == null) {
            try {
                udpSocket = new DatagramSocket();
                udpSocket.setSoTimeout(1000);
                executor.submit(this::handleUdpMessages);
            } catch (SocketException e) {
                PlayerRelay.LOGGER.warn("Failed to create UDP socket for client: {}", e.getMessage());
            }
        }

        // Create connection
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(resolvedAddress, port), 5000);
        } catch (SocketTimeoutException e) {
            throw new Exception("Connection timeout to " + host + ":" + port, e);
        } catch (IOException e) {
            throw new Exception("Failed to connect to " + host + ":" + port, e);
        }

        PeerConnection peer = new PeerConnection(socket, this);
        connectedPeers.add(peer);
        executor.submit(peer);

        onConnectedToPeer(peer);
        PlayerRelay.LOGGER.info("Connected to peer: {}:{} ({})", host, port, resolvedAddress.getHostAddress());
    }

    public CompletableFuture<Void> startServerAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                startServer();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> connectToPeerAsync(String address) {
        return CompletableFuture.runAsync(() -> {
            try {
                connectToPeer(address);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
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

    public void handleMessage(PeerConnection sender, P2PMessage message) {
        messageHandler.handleMessage(message, sender);
        if (isHost() && message.getType().shouldForward()) {
            broadcastToAllPeers(message, sender);
        }
    }

    private void handleUdpMessages() {
        byte[] buffer = new byte[MAX_UDP_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        PlayerRelay.LOGGER.info("UDP socket listening on port {}", udpSocket.getLocalPort());
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                udpSocket.receive(packet);

                if (packet.getLength() < 2) {
                    PlayerRelay.LOGGER.warn("Received UDP packet too small to contain ID");
                    continue;
                }

                byte[] data = packet.getData();
                short udpId = (short) ((data[0] & 0xFF) << 8 | (data[1] & 0xFF));

                PeerConnection senderPeer = connectedPeersByUdpId.get(udpId);
                if (senderPeer == null) {
                    PlayerRelay.LOGGER.warn("Received UDP packet from unknown peer ID: {}", udpId);
                    continue;
                }

                byte[] messageData = new byte[packet.getLength() - 2];
                System.arraycopy(data, 2, messageData, 0, messageData.length);

                P2PMessage message = P2PMessage.fromBytes(messageData, NetworkProtocol.UDP);
                PlayerRelay.LOGGER.info("Received UDP message from {}:{}, type: {}", packet.getAddress(), packet.getPort(), message.getType().name());
                handleMessage(senderPeer, message);
            } catch (SocketTimeoutException e) {
                // Normal timeout, continue loop
            } catch (IOException e) {
                if (running.get()) {
                    PlayerRelay.LOGGER.error("Error receiving UDP packet: {}", e.getMessage());
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

        PlayerRelay.LOGGER.info("Assigned UDP ID {} to peer {}", udpId, peer.getRemoteAddress());
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
                    PlayerRelay.LOGGER.error("Error accepting connection: {}", e.getMessage());
                }
            }
        }
    }

    private void removePeerConnection(PeerConnection peer) {
        connectedPeers.remove(peer);

        if (peer.assignedUdpId != null) {
            nextUdpId = peer.assignedUdpId;
            connectedPeersByUdpId.remove(peer.assignedUdpId);
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

    public String getConnectionAddress() {
        String portString = (getPort() > 0) ? ":" + getPort() : "";

        return switch (PlayerRelay.config.connectionAddress) {
            case "external" -> getExternalIp() + portString;
            case "local" -> getLocalIp() + portString;
            default -> PlayerRelay.config.connectionAddress;
        };
    }

    public P2PMessageHandler getMessageHandler() { return messageHandler; }
    public DatagramSocket getUdpSocket() { return udpSocket; }

    public int getPeerCount() { return connectedPeers.size(); }
    public int getPort() { return serverPort; }
    public String getLocalIp() { return upnpManager.getLocalIp(); }
    public String getExternalIp() { return upnpManager.getExternalIp(); }
    public boolean isHost() { return isHost; }

    public void onPeerAccepted(PeerConnection peer) {
        assignUdpId(peer);

        // Send info about host player
        PlayerInfoPayload clientInfo = ClientCore.getClientInfo();
        if (clientInfo != null) { peer.sendMessage(clientInfo.setNewConnectionFlag(false).message()); }

        // Send info about all connected players
        for (PlayerInfoPayload player : connectedPlayers.values()) {
            peer.sendMessage(player.setNewConnectionFlag(false).message());
        }
    }

    public void onConnectedToPeer(PeerConnection peer) {
        assignUdpId(peer);

        // Send info about client player to host
        PlayerInfoPayload clientInfo = ClientCore.getClientInfo();
        if (clientInfo != null) { peer.sendMessage(clientInfo.setNewConnectionFlag(true).message()); }
    }

    public void onPeerDisconnected(PeerConnection peer) {
        removePeerConnection(peer);

        for (UUID playerId : peer.announcedPlayers) {
            PacketByteBuf uuidBuf = new PacketByteBuf(Unpooled.buffer());
            uuidBuf.writeUuid(playerId);

            handleMessage(peer, new P2PMessage(
                P2PMessageType.PLAYER_DISCONNECT, uuidBuf
            ));
        }
        PlayerRelay.LOGGER.info("Peer disconnected. Active connections: {}", connectedPeers.size());
    }
}