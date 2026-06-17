package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.network.handler.C2SMessageHandler;
import dev.hintsystem.playerrelay.party.Party;
import dev.hintsystem.playerrelay.party.ServerPartyService;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.PlayerInventoryPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public class ServerCore {
    public static final Map<MinecraftServer, ServerCore> instances = new HashMap<>();

    private final MinecraftServer minecraftServer;

    public final ServerPartyService partyService = new ServerPartyService(this, CommonCore.partyManager);
    public final C2SMessageHandler messageHandler = new C2SMessageHandler(CommonCore.networkLogger, this, partyService);

    public final Set<UUID> listeningPlayers = new HashSet<>();
    public final Map<UUID, PlayerUpdateTracker> playerUpdateTrackers = new HashMap<>();

    @Nullable
    private static UUID localPlayerId = null; // Track the local player if running an integrated server

    public ServerCore(MinecraftServer minecraftServer) {
        this.minecraftServer = minecraftServer;
    }

    public static void setLocalPlayerId(@Nullable UUID playerId) { localPlayerId = playerId; }

    @Nullable
    public static ServerCore getInstance(MinecraftServer minecraftServer) {
        return instances.get(minecraftServer);
    }

    public void onStarting() {
        instances.put(minecraftServer, this);
    }

    public void onStopped() {
        listeningPlayers.clear();
        playerUpdateTrackers.clear();
        instances.remove(minecraftServer);
    }

    public void onTickEnd(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID playerId = player.getUuid();

            PlayerUpdateTracker playerUpdate = playerUpdateTrackers.getOrDefault(playerId, new PlayerUpdateTracker(playerId));
            playerUpdateTrackers.putIfAbsent(playerId, playerUpdate);

            if (playerUpdate.timeSinceLastCommit() < CommonCore.getConfig().udpSendIntervalMs) continue;

            PlayerInfoPayload infoDelta = updateServerPlayerDelta(playerUpdate, player);
            if (infoDelta != null) broadcastPlayerSync(infoDelta);
        }
    }

    public void onPlayerSync(ServerPlayerEntity player) {
        PlayerRelayServer.sendToClient(player, PlayerInventoryPayload.respond(player, true).packet()); // Gratuitous ender chest inventory packet
        for (PlayerUpdateTracker playerTracker : playerUpdateTrackers.values()) {
            PlayerRelayServer.sendToClient(player, playerTracker.getCurrentState().packet());
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        listeningPlayers.remove(player.getUuid());
        playerUpdateTrackers.remove(player.getUuid());
        CommonCore.serverConnection.removeAnnouncedPlayer(player.getUuid());
    }

    @Nullable
    private PlayerInfoPayload updateServerPlayerDelta(PlayerUpdateTracker updateTracker, ServerPlayerEntity player) {
        PlayerInfoPayload infoDelta = updateTracker.beginDelta()
            .with(new PlayerPositionData(player))
            .withCommon(player)
            .build();

        if (infoDelta != null) updateTracker.commitDelta(infoDelta);

        return infoDelta;
    }

    public void broadcastPlayerSync(PlayerInfoPayload infoDelta) {
        Party playerParty = CommonCore.partyManager.getPlayerParty(infoDelta.playerId);

        if (playerParty == null) {
            broadcastPayload(infoDelta.packet(), infoDelta.playerId);
            return;
        }

        Set<UUID> partyMembers =  new HashSet<>(playerParty.members);
        partyMembers.remove(infoDelta.playerId);

        broadcastPayload(infoDelta.packet(), partyMembers);
    }

    public void sendToClient(PayloadMessage.Packet payloadMessage, UUID playerId) {
        ServerPlayerEntity player = minecraftServer.getPlayerManager().getPlayer(playerId);
        if (player != null) PlayerRelayServer.sendToClient(player, payloadMessage);
    }

    public static void sendToClientGlobal(PayloadMessage.Packet payloadMessage, UUID playerId) {
        for (ServerCore server : instances.values()) {
            server.sendToClient(payloadMessage, playerId);
        }
    }

    public void broadcastPayload(PayloadMessage.Packet payloadMessage, @Nullable UUID excludedUuid) {
        broadcastPayload(payloadMessage, excludedUuid, false);
    }

    public void broadcastPayload(PayloadMessage.Packet payloadMessage, @Nullable UUID excludedUuid, boolean excludeClient) {
        for (UUID playerId : listeningPlayers) {
            if (playerId.equals(excludedUuid) || (excludeClient && playerId.equals(localPlayerId))) continue;

            ServerPlayerEntity player = minecraftServer.getPlayerManager().getPlayer(playerId);
            if (player != null) PlayerRelayServer.sendToClient(player, payloadMessage);
        }
    }

    public void broadcastPayload(PayloadMessage.Packet payloadMessage, Set<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            if (!listeningPlayers.contains(playerId)) continue;

            ServerPlayerEntity player = minecraftServer.getPlayerManager().getPlayer(playerId);
            if (player != null) PlayerRelayServer.sendToClient(player, payloadMessage);
        }
    }

    public static void broadcastGlobalPayload(PayloadMessage.Packet payloadMessage, Set<UUID> playerIds) {
        for (ServerCore server : instances.values()) {
            server.broadcastPayload(payloadMessage, playerIds);
        }
    }
}
