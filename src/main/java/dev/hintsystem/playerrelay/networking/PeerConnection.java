package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.logging.LogEventTypes;
import dev.hintsystem.playerrelay.logging.PlayerRelayLogger;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;
import dev.hintsystem.playerrelay.payload.UdpHandshakePayload;
import dev.hintsystem.playerrelay.payload.UdpPingPayload;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PeerConnection implements Runnable {
    private final PlayerRelayLogger logger;

    private final Socket tcpSocket;
    private final DataInputStream tcpInput;
    private final DataOutputStream tcpOutput;
    private final P2PNetworkManager manager;
    private volatile boolean connected = true;

    private final CompletableFuture<RelayVersionPayload> versionHandshake = new CompletableFuture<>();
    private ScheduledFuture<?> versionHandshakeTimeout;
    private volatile boolean versionHandshakeRequired = false;
    private final Queue<P2PMessage> pendingIncomingMessages = new ConcurrentLinkedQueue<>();

    public Short assignedUdpId;
    private Short peerUdpId;
    private int peerUdpPort;

    private volatile boolean udpHealthy = false;
    private final Map<Integer, Long> pendingPings = new ConcurrentHashMap<>();
    private int pingSequence = 0;
    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private int consecutiveFailedUdpPings = 0;

    public Set<UUID> announcedPlayers = ConcurrentHashMap.newKeySet();

    public PeerConnection(Socket socket, P2PNetworkManager manager) throws IOException {
        this.logger = manager.logger.withLocation(LogLocation.PEER_CONNECTION);

        this.tcpSocket = socket;
        this.manager = manager;
        this.tcpOutput = new DataOutputStream(socket.getOutputStream());
        this.tcpOutput.flush();
        this.tcpInput = new DataInputStream(socket.getInputStream());

        healthCheckExecutor.scheduleAtFixedRate(this::performUdpHealthCheck,
            PlayerRelay.config.udpPingTimeoutMs, PlayerRelay.config.udpPingIntervalMs, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<RelayVersionPayload> requireVersionHandshake() {
        if (versionHandshakeRequired) return versionHandshake;
        versionHandshakeRequired = true;

        this.versionHandshakeTimeout = healthCheckExecutor.schedule(this::onVersionHandshakeTimeout,
            PlayerRelay.config.peerConnectionTimeout, TimeUnit.MILLISECONDS);

        versionHandshake.whenComplete((result, throwable) -> {
            if (versionHandshakeTimeout != null) versionHandshakeTimeout.cancel(false);

            if (throwable != null) {
                disconnect();
            } else { processPendingMessages(); }
        });

        return versionHandshake;
    }

    public void onVersionHandshake(RelayVersionPayload versionPayload) {
        if (versionHandshake.isDone()) return;

        if (versionPayload.networkVersion != PlayerRelay.NETWORK_VERSION) {
            String errTitle = "Network version mismatch";
            logger.error()
                .type(LogEventTypes.VERSION_FAIL)
                .title(errTitle)
                .message("relay={}, client={}", versionPayload.networkVersion, PlayerRelay.NETWORK_VERSION)
                .context("version", versionPayload).build();

            versionHandshake.completeExceptionally(new IllegalStateException(errTitle));
        } else { versionHandshake.complete(versionPayload); }
    }

    private void onVersionHandshakeTimeout() {
        if (versionHandshake.isDone()) return;

        String errTitle = "Version handshake timeout";
        logger.error()
            .type(LogEventTypes.VERSION_FAIL)
            .title(errTitle)
            .message("No version reply received for {} ms", PlayerRelay.config.peerConnectionTimeout).build();

        versionHandshake.completeExceptionally(new TimeoutException(errTitle));
    }

    private void processPendingMessages() {
        synchronized (pendingIncomingMessages) {
            while (!pendingIncomingMessages.isEmpty()) {
                P2PMessage message = pendingIncomingMessages.poll();
                try {
                    manager.handleMessage(this, message);
                } catch (Exception e) {
                    logger.error().message("Error processing pending message: {}", e.getMessage(), e).build();
                }
            }
        }
    }

    private boolean isVersionHandshake(P2PMessage message) { return message.getType() == P2PMessageType.RELAY_VERSION; }

    private boolean shouldProcessMessage(P2PMessage message) {
        if (isVersionHandshake(message)) return true;

        // If version handshake is required but not complete, queue other messages
        if (versionHandshakeRequired && !versionHandshake.isDone()) {
            synchronized (pendingIncomingMessages) {
                pendingIncomingMessages.offer(message);
                logger.debug().message("Queued message type {} until version handshake completes", message.getType()).build();
            }
            return false;
        }

        return true;
    }

    private void performUdpHealthCheck() {
        if (!connected || peerUdpId == null) return;

        try {
            int sequence = ++pingSequence;
            long timestamp = System.currentTimeMillis();
            pendingPings.put(sequence, timestamp);

            UdpPingPayload ping = new UdpPingPayload(timestamp, sequence, false);
            sendUdpMessage(ping.message());

            healthCheckExecutor.schedule(() -> checkPingTimeout(sequence),
                PlayerRelay.config.udpPingTimeoutMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            logger.warn().message("Failed to send UDP ping: {}", e.getMessage()).build();
            onUdpPingFailed();
        }
    }

    private void checkPingTimeout(int sequence) {
        if (pendingPings.containsKey(sequence)) {
            pendingPings.remove(sequence);
            onUdpPingFailed();
        }
    }

    private void onUdpPingFailed() {
        consecutiveFailedUdpPings++;
        if (consecutiveFailedUdpPings >= PlayerRelay.config.maxFailedUdpPings) {
            if (udpHealthy) {
                udpHealthy = false;
                logger.warn().message("UDP connection to {} marked as unhealthy after {} failed pings",
                    getRemoteAddress(), consecutiveFailedUdpPings).build();
            }
        }
    }

    public void onUdpPingReceived(UdpPingPayload ping) {
        if (!ping.isResponse()) {
            UdpPingPayload pong = new UdpPingPayload(ping.getTimestamp(), ping.getSequenceNumber(), true);
            try { sendTcpMessage(pong.message()); } catch (Exception ignored) {}
        } else {

            Long sentTime = pendingPings.remove(ping.getSequenceNumber());
            if (sentTime != null) {
                long roundTripTime = System.currentTimeMillis() - sentTime;
                consecutiveFailedUdpPings = 0;

                if (!udpHealthy) {
                    udpHealthy = true;
                    logger.info().message("UDP connection to {} restored (RTT: {}ms)",
                        getRemoteAddress(), roundTripTime).build();
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            while (connected && !tcpSocket.isClosed()) {
                P2PMessage message = P2PMessage.readFrom(tcpInput, NetworkProtocol.TCP);

                if (shouldProcessMessage(message)) manager.handleMessage(this, message);
            }
        } catch (Exception e) {
            if (connected) logger.error().message("Error in peer connection: {}", e.getMessage()).build();
        } finally {
            if (connected) disconnect();
        }
    }

    public void sendMessage(P2PMessage message) {
        if (!connected || tcpSocket.isClosed()) return;
        if ((versionHandshakeRequired && !versionHandshake.isDone()) || versionHandshake.isCompletedExceptionally()) return;

        try {
            if (message.getPreferredProtocol() == NetworkProtocol.UDP && isUdpHealthy()) {
                sendUdpMessage(message);
            } else {
                sendTcpMessage(message);
            }
        } catch (IOException e) {
            logger.error().message("Failed to send message via {}: {}",
                message.getPreferredProtocol(), e.getMessage()).build();

            if (message.getPreferredProtocol() == NetworkProtocol.UDP) {
                try {
                    sendTcpMessage(message);
                } catch (IOException tcpE) {
                    logger.error().message("TCP fallback also failed: {}", tcpE.getMessage()).build();
                }
            }
        }
    }

    private void sendTcpMessage(P2PMessage message) throws IOException {
        synchronized (tcpOutput) {
            message.writeTo(tcpOutput);
            tcpOutput.flush();
        }
    }

    private void sendUdpMessage(P2PMessage message) throws IOException {
        if (peerUdpId == null) {
            throw new IOException("UDP handshake not complete");
        }

        byte[] messageData = message.toBytes();

        // Check if message is too large for UDP (typical MTU is 1500 bytes)
        if (messageData.length > 1450) {
            throw new IOException(String.format("UDP message too large (%dB), exceeds MTU limit", messageData.length));
        }

        byte[] udpData = new byte[messageData.length + 2];
        udpData[0] = (byte) ((peerUdpId >> 8) & 0xFF); // High byte
        udpData[1] = (byte) (peerUdpId & 0xFF);        // Low byte
        System.arraycopy(messageData, 0, udpData, 2, messageData.length);

        DatagramPacket packet = new DatagramPacket(
            udpData, udpData.length, tcpSocket.getInetAddress(), hasUdpPort() ? peerUdpPort : tcpSocket.getPort()
        );

        manager.getUdpSocket().send(packet);
    }

    public void assignUdpId(short id) {
        this.assignedUdpId = id;
        if (!manager.isHost() || manager.getPort() != manager.getUdpSocket().getLocalPort()) {
            sendMessage(new UdpHandshakePayload(id, manager.getUdpSocket().getLocalPort()).message());
        } else {
            sendMessage(new UdpHandshakePayload(id).message());
        }
    }

    public void setPeerUdpId(short id, int udpPort) {
        this.peerUdpId = id;
        this.peerUdpPort = udpPort;
    }

    public void disconnect() {
        if (versionHandshakeTimeout != null && !versionHandshakeTimeout.isDone()) {
            versionHandshakeTimeout.cancel(false);
        }

        if (!healthCheckExecutor.isShutdown()) {
            healthCheckExecutor.shutdown();
            try {
                if (!healthCheckExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    healthCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthCheckExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (tcpInput != null) tcpInput.close();
            if (tcpOutput != null) tcpOutput.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (IOException e) {
            logger.error().message("Error closing connection: {}", e.getMessage()).build();
        }

        manager.onPeerDisconnected(this);
        connected = false;
    }

    public boolean isUdpHealthy() { return udpHealthy && peerUdpId != null; }
    public String getRemoteAddress() { return tcpSocket.getRemoteSocketAddress().toString(); }
    private boolean hasUdpPort() { return peerUdpPort > 0; }
}
