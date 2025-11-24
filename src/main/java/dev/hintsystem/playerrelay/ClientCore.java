package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.mods.SupportXaerosMapMods;
import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.payload.*;

import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientCore extends CommonCore {
    public static final float tickRate = 20;
    public static final int msPerTick = Math.round(1000 / tickRate);

    private static final PlayerUpdateTracker c2sTracker = new PlayerUpdateTracker(getClientUuid());
    private static final PlayerUpdateTracker p2pTracker = new PlayerUpdateTracker(getClientUuid());

    private static long lastSentUdpTime = 0;
    private static long lastSentTcpTime = 0;

    public static final List<WaypointPayload> pendingWaypoints = new ArrayList<>();

    public static final ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingInventoryRequests = new ConcurrentHashMap<>();
    public static final ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingEnderChestRequests = new ConcurrentHashMap<>();

    public static long lastInputTime = Util.getMeasuringTimeMs();

    @Nullable
    public static RelayVersionPayload serverRelayVersion;

    public static void onTickEnd(MinecraftClient client) {
        EnderChestTracker.tick();

        if (isNetworkActive()) sendC2SUpdate();
        if (isP2PNetworkActive()) sendP2PUpdate(client);
    }

    public static void onServerJoin() {
        serverRelayVersion = null;
        c2sTracker.reset();
        PlayerRelayClient.sendToServer(new RelayVersionPayload().packet());
    }

    public static void onServerLeave() {
        serverRelayVersion = null;
        CommonCore.serverPlayers.clear();
    }

    private static void sendC2SUpdate() {
        if (c2sTracker.timeSinceLastCommit() < PlayerRelayClient.config.tcpSendIntervalMs) return;

        PlayerInfoPayload delta = deltaWithClientInfo(c2sTracker.beginDelta()).build();

        if (delta != null) {
            c2sTracker.commitDelta(delta);
            PlayerRelayClient.sendToServer(delta.packet());
        }
    }

    private static void sendP2PUpdate(MinecraftClient client) {
        long now = System.currentTimeMillis();

        boolean udpReady = now - lastSentUdpTime > PlayerRelayClient.config.udpSendIntervalMs;
        boolean tcpReady = now - lastSentTcpTime > PlayerRelayClient.config.tcpSendIntervalMs;

        if (udpReady && client.player != null) {
            PlayerInfoPayload udpDelta = p2pTracker.beginDelta()
                .with(new PlayerPositionData(client.player))
                .build();

            if (udpDelta != null) {
                lastSentUdpTime = now;
                p2pTracker.commitDelta(udpDelta);
                PlayerRelay.getP2PNetworkManager()
                    .broadcastMessage(udpDelta.message(NetworkProtocol.UDP));
            }
        }

        if (tcpReady) {
            PlayerInfoPayload tcpDelta = deltaWithClientInfo(p2pTracker.beginDelta())
                .withCommon(client.player)
                .build();

            if (tcpDelta != null) {
                lastSentTcpTime = now;
                p2pTracker.commitDelta(tcpDelta);
                PlayerRelay.getP2PNetworkManager()
                    .broadcastMessage(tcpDelta.message(NetworkProtocol.TCP));
            }
        }
    }

    public static PlayerUpdateTracker.DeltaBuilder deltaWithClientInfo(PlayerUpdateTracker.DeltaBuilder deltaBuilder) {
        return deltaBuilder
            .with(new PlayerBasicData(getClientPlayerName(), PlayerRelayClient.config.displayNameColor))
            .withFlag(PlayerInfoPayload.FLAGS.AFK, isClientAfk());
    }

    public static boolean isNetworkActive() {
        return isServerNetworkActive() || isP2PNetworkActive();
    }

    public static boolean isP2PNetworkActive() {
        return PlayerRelay.getP2PNetworkManager() != null && PlayerRelay.getP2PNetworkManager().getPeerCount() != 0;
    }

    public static boolean isServerNetworkActive() {
        return serverRelayVersion != null && serverRelayVersion.networkVersion == RelayVersionPayload.NETWORK_VERSION;
    }

    public static boolean serverHasPlayerRelay() { return serverRelayVersion != null; }

    public static void updateInputActivity() { lastInputTime = Util.getMeasuringTimeMs(); }

    public static boolean isClientAfk() { return Util.getMeasuringTimeMs() - lastInputTime > PlayerRelayClient.config.afkTimeout; }

    public static String getClientPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        return (client.player != null) ? client.player.getName().getString() : client.getSession().getUsername();
    }

    public static UUID getClientUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getSession().getUuidOrNull();
    }

    public static int ticksToMs(int ticks) { return Math.round((ticks / tickRate) * 1000); }

    public static PlayerInfoPayload getUpdatedClientInfo() {
        MinecraftClient client = MinecraftClient.getInstance();

        PlayerUpdateTracker.DeltaBuilder builder = new PlayerUpdateTracker(getClientUuid()).beginSnapshot()
            .with(new PlayerBasicData(getClientPlayerName(), PlayerRelayClient.config.displayNameColor))
            .withFlag(PlayerInfoPayload.FLAGS.AFK, isClientAfk())
            .withCommon(client.player);

        if (client.player != null) {
            builder.with(new PlayerPositionData(client.player));
        }

        return builder.build();
    }

    /**
     * Returns a tracked player's {@link PlayerInfoPayload},
     * or the client's PlayerInfoPayload if UUID matches
     */
    public static PlayerInfoPayload getTrackedPlayer(UUID playerId) {
        UUID clientPlayerId = ClientCore.getClientUuid();
        if (clientPlayerId.equals(playerId)) {
            return ClientCore.getUpdatedClientInfo();
        }

        return CommonCore.playerInfoTracker.getTrackedPlayer(playerId);
    }

    public static void sendClientChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) { return; }

        client.execute(() -> client.player.sendMessage(message, false));
    }

    /** Broadcast a payload on the P2P network and the server */
    public static void broadcastPayload(IPayload payload) {
        PlayerRelay.getP2PNetworkManager().broadcastMessage(payload.message());
        PlayerRelayClient.sendToServer(payload.packet());
    }

    @Nullable
    public static WaypointPayload acceptWaypoint(int waypointIndex) {
        WaypointPayload waypoint = pendingWaypoints.get(waypointIndex);

        if (waypoint != null) SupportXaerosMapMods.addWaypoint(waypoint);
        return waypoint;
    }

    public static CompletableFuture<PlayerInventoryPayload> requestInventory(UUID playerId, boolean isEnderChest) {
        ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingRequests = isEnderChest
            ? pendingEnderChestRequests : pendingInventoryRequests;

        CompletableFuture<PlayerInventoryPayload> future = new CompletableFuture<>();
        future.whenComplete((r, e) -> pendingRequests.remove(playerId));

        pendingRequests.put(playerId, future);

        PlayerInventoryPayload inventoryRequest = PlayerInventoryPayload.request(playerId, isEnderChest);

        PlayerRelayClient.sendToServer(inventoryRequest.packet());
        for (PeerConnection peer : PlayerRelay.getP2PNetworkManager().getConnectedPeers()) {
            if (peer.announcedPlayers.contains(playerId)) {
                peer.sendMessage(inventoryRequest.message());
            }
        }

        return future;
    }

    public static void onPlayerConnected(PlayerInfoPayload playerInfo) {
        sendClientChatMessage(Text.literal("✔ ")
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
            .append(playerInfo.getName())
            .append(Text.literal(" connected to Player Relay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onPlayerDisconnected(PlayerInfoPayload playerInfo) {
        sendClientChatMessage(Text.literal("❌ ")
            .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true))
            .append(playerInfo.getName())
            .append(Text.literal(" disconnected from Player Relay")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY).withBold(false))));
    }

    public static void onConnect(String address) {
        sendClientChatMessage(Text.literal("✔ Connected to peer: ")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true))
                .append(Text.literal(address)
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(false))));
    }
}
