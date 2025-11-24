package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.RelayVersionPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.jetbrains.annotations.Nullable;
import java.util.*;

public class ServerCore extends CommonCore {
    private static final Set<UUID> listeningPlayers = new HashSet<>();
    private static final Map<UUID, PlayerUpdateTracker> playerUpdateTrackers = new HashMap<>();

    public static void onTickEnd(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID playerId = player.getUuid();

            PlayerUpdateTracker playerUpdate = playerUpdateTrackers.getOrDefault(playerId, new PlayerUpdateTracker(playerId));
            playerUpdateTrackers.putIfAbsent(playerId, playerUpdate);

            if (playerUpdate.timeSinceLastCommit() < getConfig().udpSendIntervalMs) continue;

            PlayerInfoPayload infoDelta = playerId.equals(ClientCore.getClientUuid()) ?
                updateHostPlayerDelta(playerUpdate, player) : updateServerPlayerDelta(playerUpdate, player);

            if (infoDelta != null) {
                broadcastPayload(world.getServer(), infoDelta.packet(), playerId);
            }
        }
    }

    public static void onPlayerRelayVersion(RelayVersionPayload version, ServerPlayerEntity player) {
        PlayerRelayServer.sendToClient(player, new RelayVersionPayload().packet());
        if (version.networkVersion != RelayVersionPayload.NETWORK_VERSION) {
            listeningPlayers.remove(player.getUuid());
            return;
        }

        boolean added = listeningPlayers.add(player.getUuid());
        if (added) {
            for (PlayerUpdateTracker playerTracker : playerUpdateTrackers.values()) {
                PlayerRelayServer.sendToClient(player, playerTracker.getCurrentState().packet());
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
        PlayerInfoPayload lastInfo = CommonCore.serverPlayers.getOrDefault(updateTracker.playerId, updateTracker.getCurrentState());
        PlayerBasicData lastBasicData = lastInfo.getComponentOrEmpty(PlayerBasicData.class);

        PlayerInfoPayload infoDelta = updateTracker.beginDelta()
            .with(new PlayerBasicData(player.getName().getString(), lastBasicData.nameColor))
            .withFlag(PlayerInfoPayload.FLAGS.AFK, lastInfo.isAfk())
            .with(new PlayerPositionData(player))
            .withCommon(player)
            .build();

        if (infoDelta != null) updateTracker.commitDelta(infoDelta);

        lastInfo.merge(infoDelta);
        CommonCore.serverPlayers.putIfAbsent(updateTracker.playerId, lastInfo);

        return infoDelta;
    }

    @Nullable
    private static PlayerInfoPayload updateHostPlayerDelta(PlayerUpdateTracker updateTracker, ServerPlayerEntity player) {
        PlayerInfoPayload infoDelta = ClientCore.deltaWithClientInfo(updateTracker.beginDelta())
            .with(new PlayerPositionData(player))
            .withCommon(player)
            .build();

        if (infoDelta != null) updateTracker.commitDelta(infoDelta);

        return infoDelta;
    }

    public static void onStopping() {
        listeningPlayers.clear();
        playerUpdateTrackers.clear();
        CommonCore.onStopping();
    }

    public static void broadcastPayload(MinecraftServer server, PayloadMessage.Packet payload, @Nullable UUID excludedUuid) {
        for (UUID playerId : listeningPlayers) {
            if (playerId.equals(excludedUuid) || playerId.equals(ClientCore.getClientUuid())) continue;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) PlayerRelayServer.sendToClient(player, payload);
        }
    }
}
