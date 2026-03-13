package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ServerCore {
    public static final Set<UUID> listeningPlayers = new HashSet<>();
    public static final Map<UUID, PlayerUpdateTracker> playerUpdateTrackers = new HashMap<>();

    @Nullable
    private static UUID localPlayerId = null; // Track the local player if running an integrated server

    public static void setLocalPlayerId(@Nullable UUID playerId) { localPlayerId = playerId; }

    public static void onTickEnd(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID playerId = player.getUuid();

            PlayerUpdateTracker playerUpdate = playerUpdateTrackers.getOrDefault(playerId, new PlayerUpdateTracker(playerId));
            playerUpdateTrackers.putIfAbsent(playerId, playerUpdate);

            if (playerUpdate.timeSinceLastCommit() < CommonCore.getConfig().udpSendIntervalMs) continue;

            PlayerInfoPayload infoDelta = updateServerPlayerDelta(playerUpdate, player);
            if (infoDelta != null) {
                broadcastPayload(world.getServer(), infoDelta.packet(), playerId);
            }
        }
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        listeningPlayers.remove(player.getUuid());
        playerUpdateTrackers.remove(player.getUuid());
        CommonCore.serverPlayers.remove(player.getUuid());
    }

    @Nullable
    private static PlayerInfoPayload updateServerPlayerDelta(PlayerUpdateTracker updateTracker, ServerPlayerEntity player) {
        PlayerInfoPayload infoDelta = updateTracker.beginDelta()
            .with(new PlayerPositionData(player))
            .withCommon(player)
            .build();

        if (infoDelta != null) updateTracker.commitDelta(infoDelta);

        return infoDelta;
    }

    public static void broadcastPayload(MinecraftServer server, PayloadMessage.Packet payloadMessage, @Nullable UUID excludedUuid) {
        broadcastPayload(server, payloadMessage, excludedUuid, false);
    }

    public static void broadcastPayload(MinecraftServer server, PayloadMessage.Packet payloadMessage, @Nullable UUID excludedUuid, boolean excludeClient) {
        for (UUID playerId : listeningPlayers) {
            if (playerId.equals(excludedUuid) || (excludeClient && playerId.equals(localPlayerId))) continue;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) PlayerRelayServer.sendToClient(player, payloadMessage);
        }
    }
}
