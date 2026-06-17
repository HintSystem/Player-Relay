package dev.hintsystem.playerrelay.network.connection;

import dev.hintsystem.playerrelay.logging.LogEvent;
import dev.hintsystem.playerrelay.logging.LogEventTypes;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.logging.LogLocation;
import dev.hintsystem.playerrelay.network.NetworkProtocol;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;
import dev.hintsystem.playerrelay.payload.UdpHandshakePayload;
import dev.hintsystem.playerrelay.payload.UdpPingPayload;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class PeerConnection extends Connection implements Runnable {
    private final NetworkLogger logger;

    private final Socket tcpSocket;
    private final DataInputStream tcpInput;
    private final DataOutputStream tcpOutput;
    private final P2PNetworkManager manager;

    private final CompletableFuture<RelayVersionPayload> versionHandshake = new CompletableFuture<>();
    private ScheduledFuture<?> versionHandshakeTimeout;
    private volatile boolean versionHandshakeRequired = false;
    private final Queue<PayloadMessage> pendingIncomingMessages = new ConcurrentLinkedQueue<>();

    public Short assignedUdpId;
    private Short peerUdpId;
    private int peerUdpPort;

    private volatile boolean udpHealthy = false;
    private final Map<Integer, Long> pendingPings = new ConcurrentHashMap<>();
    private int pingSequence = 0;
    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private int consecutiveFailedUdpPings = 0;

    public PeerConnection(Socket socket, P2PNetworkManager manager) throws IOException {
        this.logger = manager.logger.withLocation(LogLocation.PEER_CONNECTION);

        this.tcpSocket = socket;
        this.manager = manager;
        this.tcpOutput = new DataOutputStream(socket.getOutputStream());
        this.tcpOutput.flush();
        this.tcpInput = new DataInputStream(socket.getInputStream());

        healthCheckExecutor.scheduleAtFixedRate(this::performUdpHealthCheck,
            manager.config.udpPingTimeoutMs, manager.config.udpPingIntervalMs, TimeUnit.MILLISECONDS);
    }

    public SocketAddress getRemoteAddress() { return tcpSocket.getRemoteSocketAddress(); }
    public P2PNetworkManager getP2PManager() { return manager; }

    public boolean isUdpHealthy() { return udpHealthy && peerUdpId != null; }
    private boolean hasUdpPort() { return peerUdpPort > 0; }

    public CompletableFuture<RelayVersionPayload> requireVersionHandshake() {
        if (versionHandshakeRequired) return versionHandshake;
        versionHandshakeRequired = true;

        this.versionHandshakeTimeout = healthCheckExecutor.schedule(this::onVersionHandshakeTimeout,
            manager.config.peerConnectionTimeout, TimeUnit.MILLISECONDS);

        versionHandshake.whenComplete((result, throwable) -> {
            if (versionHandshakeTimeout != null) versionHandshakeTimeout.cancel(false);

            if (throwable != null) {
                disconnect();
            } else { processPendingMessages(); }
        });

        return versionHandshake;
    }

    @Override
    public void onVersionHandshake(RelayVersionPayload versionPayload) {
        if (versionHandshake.isDone()) return;

        if (isVersionValid(versionPayload)) {
            this.versionPayload = versionPayload;
            versionHandshake.complete(versionPayload);
        } else {
            LogEvent logMessage = logger.versionMismatch(versionPayload).build();

            versionHandshake.completeExceptionally(new IllegalStateException(logMessage.getTitle()));
        }
    }

    private void onVersionHandshakeTimeout() {
        if (versionHandshake.isDone()) return;

        String errTitle = "Version handshake timeout";
        logger.error()
            .type(LogEventTypes.VERSION_FAIL)
            .title(errTitle)
            .message("No version reply received for {} ms", manager.config.peerConnectionTimeout).build();

        versionHandshake.completeExceptionally(new TimeoutException(errTitle));
    }

    private void processPendingMessages() {
        synchronized (pendingIncomingMessages) {
            while (!pendingIncomingMessages.isEmpty()) {
                PayloadMessage message = pendingIncomingMessages.poll();
                try {
                    manager.handleMessage(this, message);
                } catch (Exception e) {
                    logger.error().message("Error processing pending message: {}", e.getMessage(), e).build();
                }
            }
        }
    }

    private boolean shouldProcessMessage(PayloadMessage message) {
        if (isVersionHandshake(message)) return true;

        // If version handshake is required but not complete, queue other messages
        if (versionHandshakeRequired && !versionHandshake.isDone()) {
            synchronized (pendingIncomingMessages) {
                pendingIncomingMessages.offer(message);
                logger.debug().message("Queued message type {} until version handshake completes", message.getPayload().getClass()).build();
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
                manager.config.udpPingTimeoutMs, TimeUnit.MILLISECONDS);

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
        if (consecutiveFailedUdpPings >= manager.config.maxFailedUdpPings) {
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
                PayloadMessage message = PayloadMessage.readFrom(tcpInput, NetworkProtocol.TCP);

                if (shouldProcessMessage(message)) manager.handleMessage(this, message);
            }
        } catch (Exception e) {
            if (connected) logger.error().message("Error in peer connection: {}", e.getMessage()).build();
        } finally {
            if (connected) disconnect();
        }
    }

    @Override
    public void sendMessage(PayloadMessage message) {
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

    private void sendTcpMessage(PayloadMessage message) throws IOException {
        synchronized (tcpOutput) {
            message.writeTo(tcpOutput);
            tcpOutput.flush();
        }
    }

    private void sendUdpMessage(PayloadMessage message) throws IOException {
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

    @Override
    public void disconnect() {
        connected = false;

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
    }
}
