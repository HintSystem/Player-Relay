package dev.hintsystem.playerrelay.mods.xaero;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;
import dev.hintsystem.playerrelay.payload.player.PlayerWorldData;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import com.google.common.collect.Iterators;

import java.util.*;

public class RelayPlayerTracker {
    public Iterator<PlayerInfoPayload> getTrackedPlayerIterator() {
        if (!PlayerRelay.config.showTrackedPlayers) return Collections.emptyIterator();

        final Collection<UUID> serverPlayers = PlayerRelay.config.showTrackedPlayersFromOtherServers
            ? null : getServerPlayerUuids();

        return Iterators.filter(
            PlayerRelay.getNetworkManager().connectedPlayers.values().iterator(),
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
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        return (networkHandler != null) ? networkHandler.getPlayerUuids() : Collections.emptySet();
    }
}
