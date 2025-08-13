package dev.hintsystem.playerrelay.networking;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.UdpHandshakePayload;
import dev.hintsystem.playerrelay.payload.UdpPingPayload;

import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class PeerConnection implements Runnable {
    private final Socket tcpSocket;
    private final DataInputStream tcpInput;
    private final DataOutputStream tcpOutput;
    private final P2PNetworkManager manager;
    private volatile boolean connected = true;

    public Short assignedUdpId;
    private Short peerUdpId;
    private int peerUdpPort;

    private volatile boolean udpHealthy = false;
    private final Map<Integer, Long> pendingPings = new ConcurrentHashMap<>();
    private int pingSequence = 0;
    private final ScheduledExecutorService udpHealthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private int consecutiveFailedUdpPings = 0;

    public Set<UUID> announcedPlayers = new HashSet<>();

    public PeerConnection(Socket socket, P2PNetworkManager manager) throws IOException {
        this.tcpSocket = socket;
        this.manager = manager;
        this.tcpOutput = new DataOutputStream(socket.getOutputStream());
        this.tcpOutput.flush();
        this.tcpInput = new DataInputStream(socket.getInputStream());

        udpHealthCheckExecutor.scheduleAtFixedRate(this::performUdpHealthCheck,
            PlayerRelay.config.udpPingTimeoutMs, PlayerRelay.config.udpPingIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void performUdpHealthCheck() {
        if (!connected || peerUdpId == null) return;

        try {
            int sequence = ++pingSequence;
            long timestamp = System.currentTimeMillis();
            pendingPings.put(sequence, timestamp);

            UdpPingPayload ping = new UdpPingPayload(timestamp, sequence, false);
            sendUdpMessage(ping.message());

            // Schedule timeout check
            udpHealthCheckExecutor.schedule(() -> checkPingTimeout(sequence),
                PlayerRelay.config.udpPingTimeoutMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            PlayerRelay.LOGGER.warn("Failed to send UDP ping: {}", e.getMessage());
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
                PlayerRelay.LOGGER.warn("UDP connection to {} marked as unhealthy after {} failed pings",
                    getRemoteAddress(), consecutiveFailedUdpPings);
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
                    PlayerRelay.LOGGER.info("UDP connection to {} restored (RTT: {}ms)",
                        getRemoteAddress(), roundTripTime);
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            while (connected && !tcpSocket.isClosed()) {
                P2PMessage message = P2PMessage.readFrom(tcpInput, NetworkProtocol.TCP);
                manager.handleMessage(this, message);
            }
        } catch (Exception e) {
            if (connected) {
                PlayerRelay.LOGGER.error("Error in peer connection: {}", e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    public void sendMessage(P2PMessage message) {
        if (!connected || tcpSocket.isClosed()) return;

        try {
            if (message.getPreferredProtocol() == NetworkProtocol.UDP && isUdpHealthy()) {
                sendUdpMessage(message);
            } else {
                sendTcpMessage(message);
            }
        } catch (IOException e) {
            PlayerRelay.LOGGER.error("Failed to send message via {}: {}",
                message.getPreferredProtocol(), e.getMessage());

            if (message.getPreferredProtocol() == NetworkProtocol.UDP) {
                try {
                    sendTcpMessage(message);
                } catch (IOException tcpE) {
                    PlayerRelay.LOGGER.error("TCP fallback also failed: {}", tcpE.getMessage());
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
            String error = "UDP handshake not complete";
            PlayerRelay.LOGGER.warn(error);
            throw new IOException(error);
        }

        byte[] messageData = message.toBytes();

        // Check if message is too large for UDP (typical MTU is 1500 bytes)
        if (messageData.length > 1450) {
            String error = String.format("UDP message too large (%dB), exceeds MTU limit", messageData.length);
            PlayerRelay.LOGGER.warn(error);
            throw new IOException(error);
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
        connected = false;
        try {
            if (tcpInput != null) tcpInput.close();
            if (tcpOutput != null) tcpOutput.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (IOException e) {
            PlayerRelay.LOGGER.error("Error closing connection: {}", e.getMessage());
        }
        manager.onPeerDisconnected(this);
    }

    public boolean isUdpHealthy() { return udpHealthy && peerUdpId != null; }
    public String getRemoteAddress() { return tcpSocket.getRemoteSocketAddress().toString(); }
    private boolean hasUdpPort() { return peerUdpPort > 0; }
}
