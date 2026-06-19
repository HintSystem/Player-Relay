package dev.hintsystem.playerrelay.network;

public class NetworkConfig {
    public boolean autoHost = false;
    public boolean UPnPEnabled = true;
    public int defaultHostingPort = P2PNetworkManager.DEFAULT_PORT;

    public int peerConnectionTimeout = 6000;
    public int tcpSendIntervalMs = 500;
    public int udpSendIntervalMs = 100;
    public int udpPingIntervalMs = 5000;
    public int udpPingTimeoutMs = 2000;
    public int maxFailedUdpPings = 3;
}
