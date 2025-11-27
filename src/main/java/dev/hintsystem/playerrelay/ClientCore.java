package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.mods.SupportXaerosMapMods;
import dev.hintsystem.playerrelay.networking.NetworkProtocol;
import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.payload.*;

import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
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

public class ClientCore {
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

    public static void onServerJoin(MinecraftClient client) {
        serverRelayVersion = null;
        c2sTracker.reset();

        EnderChestTracker.updateCurrentWorldId(client); // Update world id to accept gratuitous ender chest inventory packet
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
                CommonCore.getP2PNetworkManager()
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
                CommonCore.getP2PNetworkManager()
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
        return CommonCore.getP2PNetworkManager() != null && CommonCore.getP2PNetworkManager().getPeerCount() != 0;
    }

    public static boolean isServerNetworkActive() {
        return serverRelayVersion != null && serverRelayVersion.networkVersion == RelayVersionPayload.NETWORK_VERSION;
    }

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
    public static void broadcastPayload(Payload payload) {
        CommonCore.getP2PNetworkManager().broadcastMessage(payload.message());
        PlayerRelayClient.sendToServer(payload.packet());
    }

    @Nullable
    public static WaypointPayload acceptWaypoint(int waypointIndex) {
        WaypointPayload waypoint = pendingWaypoints.get(waypointIndex);

        if (waypoint != null) SupportXaerosMapMods.addWaypoint(waypoint);
        return waypoint;
    }

    public static CompletableFuture<PlayerInventoryPayload> requestInventory(UUID playerId, boolean isEnderChest) {
        ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingRequests = isEnderChest ?
            pendingEnderChestRequests : pendingInventoryRequests;

        CompletableFuture<PlayerInventoryPayload> future = new CompletableFuture<>();
        pendingRequests.put(playerId, future);
        future.whenComplete((r, e) -> pendingRequests.remove(playerId));

        PlayerInventoryPayload inventoryRequest = PlayerInventoryPayload.request(playerId, isEnderChest);

        // Requesting client's inventory
        if (playerId.equals(getClientUuid())) {
            if (!isServerNetworkActive()) {
                future.completeExceptionally(new IllegalStateException(
                    "Cannot request local player inventory: no compatible version of the mod on the server to fulfill the request"
                ));
            } else {
                PlayerRelayClient.sendToServer(inventoryRequest.packet());
            }
            return future;
        }

        // Requesting a remote player's inventory
        boolean routed = false;
        if (isServerNetworkActive()) {
            PlayerRelayClient.sendToServer(inventoryRequest.packet());
            routed = true;
        }

        // P2P Routing
        for (PeerConnection peer : CommonCore.getP2PNetworkManager().getConnectedPeers()) {
            if (peer.announcedPlayers.contains(playerId)) {
                peer.sendMessage(inventoryRequest.message());
                routed = true;
            }
        }

        if (!routed) {
            future.completeExceptionally(new IllegalStateException(
                "No route available to request inventory for player " + playerId
            ));
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

    public static class ClientInfoProvider implements CommonCore.LocalInfoProvider {
        @Override
        @Nullable
        public PlayerInfoPayload getClientInfo() { return ClientCore.getUpdatedClientInfo(); }

        @Override
        @Nullable
        public PlayerEntity getLocalPlayer() { return MinecraftClient.getInstance().player; }

        @Override
        @Nullable
        public UUID getLocalPlayerId() {return ClientCore.getClientUuid(); }
    }
}
