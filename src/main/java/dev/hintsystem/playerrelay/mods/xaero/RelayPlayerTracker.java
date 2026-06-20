package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.CommonCore;
import dev.hintsystem.playerrelay.PlayerRelayClient;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;
import dev.hintsystem.playerrelay.payload.player.PlayerWorldData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import com.google.common.collect.Iterators;

import java.util.*;

public class RelayPlayerTracker {
    public Iterator<PlayerInfoPayload> getTrackedPlayerIterator() {
        if (!PlayerRelayClient.config.showTrackedPlayers) return Collections.emptyIterator();

        final Collection<UUID> serverPlayers = PlayerRelayClient.config.showTrackedPlayersFromOtherServers
            ? null : getServerPlayerUuids();

        return Iterators.filter(
            CommonCore.connections.getTrackedPlayers().values().iterator(),
            player -> {
                if (player == null) return false;

                // Required for RelayTrackedPlayerReader
                if (!player.hasComponent(PlayerPositionData.class)
                    || !player.hasComponent(PlayerWorldData.class)) {
                    return false;
                }

                if (serverPlayers != null && !serverPlayers.contains(player.playerId)) {
                    return false;
                }

                return true;
            }
        );
    }

    public Collection<UUID> getServerPlayerUuids() {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        return (networkHandler != null) ? networkHandler.getOnlinePlayerIds() : Collections.emptySet();
    }
}
