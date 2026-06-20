package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.mods.SupportXaerosMapMods;
import dev.hintsystem.playerrelay.network.NetworkProtocol;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.network.connection.Connection;
import dev.hintsystem.playerrelay.network.connection.PeerConnection;
import dev.hintsystem.playerrelay.network.handler.S2CMessageHandler;
import dev.hintsystem.playerrelay.party.ClientPartyService;
import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientCore {
    public static final S2CMessageHandler messageHandler = new S2CMessageHandler(CommonCore.networkLogger, CommonCore.serverConnection);
    public static final ClientPartyService partyService = new ClientPartyService(CommonCore.partyManager);

    private static final PlayerUpdateTracker c2sTracker = new PlayerUpdateTracker(getClientUuid());
    private static final PlayerUpdateTracker p2pTracker = new PlayerUpdateTracker(getClientUuid());

    private static long lastSentUdpTime = 0;
    private static long lastSentTcpTime = 0;

    public static final List<WaypointPayload> pendingWaypoints = new ArrayList<>();

    public static final ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingInventoryRequests = new ConcurrentHashMap<>();
    public static final ConcurrentMap<UUID, CompletableFuture<PlayerInventoryPayload>> pendingEnderChestRequests = new ConcurrentHashMap<>();

    public static long lastInputTime = Util.getMillis();

    public static void onServerJoin(Minecraft client) {
        CommonCore.partyManager.reset();
        c2sTracker.reset();

        EnderChestTracker.updateCurrentWorldId(client); // Update world id to accept gratuitous ender chest inventory packet from server
        CommonCore.serverConnection.get().onConnect();
    }

    public static void onServerLeave() {
        CommonCore.serverConnection.close();
    }

    public static void onTickEnd(Minecraft client) {
        EnderChestTracker.tick();

        if (isNetworkActive()) sendC2SUpdate();
        if (isP2PNetworkActive()) sendP2PUpdate(client);
    }

    private static void sendC2SUpdate() {
        if (c2sTracker.timeSinceLastCommit() < PlayerRelayClient.config.tcpSendIntervalMs) return;

        PlayerInfoPayload delta = deltaWithClientInfo(c2sTracker.beginDelta()).build();

        if (delta != null) {
            c2sTracker.commitDelta(delta);
            PlayerRelayClient.sendToServer(delta.packet());
        }
    }

    private static void sendP2PUpdate(Minecraft client) {
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

    public static boolean isNetworkActive() {
        return isServerNetworkActive() || isP2PNetworkActive();
    }

    public static boolean isP2PNetworkActive() {
        return CommonCore.getP2PNetworkManager() != null && CommonCore.getP2PNetworkManager().getPeerCount() != 0;
    }

    public static boolean isServerNetworkActive() {
        return CommonCore.serverConnection.get().isVersionValid();
    }

    public static void updateInputActivity() {
        lastInputTime = Util.getMillis();
    }

    public static boolean isClientAfk() {
        return Util.getMillis() - lastInputTime > PlayerRelayClient.config.afkTimeout;
    }

    public static String getClientPlayerName() {
        Minecraft client = Minecraft.getInstance();
        return (client.player != null) ? client.player.getGameProfile().name() : client.getUser().getName();
    }

    public static UUID getClientUuid() {
        Minecraft client = Minecraft.getInstance();
        return client.getUser().getProfileId();
    }

    public static PlayerUpdateTracker.DeltaBuilder deltaWithClientInfo(PlayerUpdateTracker.DeltaBuilder deltaBuilder) {
        return deltaBuilder
            .with(new PlayerBasicData(getClientPlayerName(), PlayerRelayClient.config.displayNameColor))
            .withFlag(PlayerInfoPayload.FLAGS.AFK, isClientAfk());
    }

    public static PlayerInfoPayload getUpdatedClientInfo() {
        Minecraft client = Minecraft.getInstance();

        PlayerUpdateTracker.DeltaBuilder builder = new PlayerUpdateTracker(getClientUuid())
            .beginSnapshot();

        deltaWithClientInfo(builder)
            .withCommon(client.player);

        if (client.player != null) {
            builder.with(new PlayerPositionData(client.player));
        }

        return builder.build();
    }

    /** Returns a map of tracked players, which should be displayed, based on the current context */
    public static Map<UUID, PlayerInfoPayload> getListedPlayers() {
        Party clientParty = CommonCore.partyManager.getPlayerParty(getClientUuid());
        if (clientParty == null) return CommonCore.connections.getTrackedPlayers();

        Map<UUID, PlayerInfoPayload> listed = new HashMap<>(CommonCore.peerConnections.getTrackedPlayers());
        for (Map.Entry<UUID, PlayerInfoPayload> entry : CommonCore.serverConnection.getTrackedPlayers().entrySet()) {
            if (clientParty.isMember(entry.getKey())) listed.put(entry.getKey(), entry.getValue());
        }

        return listed;
    }

    public record PlayerLookup(String name, @Nullable Component displayName) {}

    private static PlayerLookup resolvePlayer(UUID playerId) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();

        if (networkHandler != null) {
            // This also falls back to a peer connection because of ClientPlayNetworkHandlerMixin#playerListEntryFallback
            PlayerInfo entry = networkHandler.getPlayerInfo(playerId);
            if (entry != null) {
                return new PlayerLookup(entry.getProfile().name(), entry.getTabListDisplayName());
            }
        } else {
            PlayerInfoPayload payload = getTrackedPlayer(playerId);
            if (payload != null) {
                return new PlayerLookup(payload.getName(), null);
            }
        }

        return null;
    }

    public static MutableComponent getPlayerDisplayName(UUID playerId) {
        PlayerLookup player = resolvePlayer(playerId);
        if (player == null) return Component.literal(playerId.toString());
        return player.displayName() != null ? player.displayName().copy() : Component.literal(player.name());
    }

    public static String getPlayerName(UUID playerId) {
        PlayerLookup player = resolvePlayer(playerId);
        return player != null ? player.name() : playerId.toString();
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

        return CommonCore.connections.getPlayer(playerId);
    }

    public static void addHudMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { return; }

        client.execute(() -> client.gui.getChat().addMessage(message));
    }

    /** Broadcast a payload on the P2P network and the server */
    public static void broadcastPayload(Payload payload) {
        PayloadMessage message = payload.message();
        for (Connection connection : CommonCore.connections.getAll()) {
            connection.sendMessage(message);
        }
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
        for (PeerConnection peer : CommonCore.peerConnections.getAll()) {
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
        addHudMessage(Component.literal("✔ ")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
            .append(playerInfo.getName())
            .append(Component.literal(" connected to Player Relay")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false))));
    }

    public static void onPlayerDisconnected(PlayerInfoPayload playerInfo) {
        addHudMessage(Component.literal("❌ ")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true))
            .append(playerInfo.getName())
            .append(Component.literal(" disconnected from Player Relay")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withBold(false))));
    }

    public static void onConnect(String address) {
        addHudMessage(Component.literal("✔ Connected to peer: ")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true))
                .append(Component.literal(address)
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(false))));
    }

    public static class ClientInfoProvider implements CommonCore.LocalInfoProvider {
        @Override
        @Nullable
        public PlayerInfoPayload getClientInfo() { return ClientCore.getUpdatedClientInfo(); }

        @Override
        @Nullable
        public Player getLocalPlayer() { return Minecraft.getInstance().player; }

        @Override
        @Nullable
        public UUID getLocalPlayerId() { return ClientCore.getClientUuid(); }
    }
}
