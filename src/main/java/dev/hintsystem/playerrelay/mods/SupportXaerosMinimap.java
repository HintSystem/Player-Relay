package dev.hintsystem.playerrelay.mods;

import dev.hintsystem.playerrelay.networking.PeerConnection;
import dev.hintsystem.playerrelay.networking.message.PlayerInfoHandler;
import dev.hintsystem.playerrelay.payload.PlayerInfoPayload;
import dev.hintsystem.playerrelay.payload.player.PlayerPositionData;
import dev.hintsystem.playerrelay.payload.player.PlayerWorldData;

import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;

import net.minecraft.world.World;

import java.util.UUID;

public class SupportXaerosMinimap implements PlayerInfoHandler {
    public static ClientSyncedTrackedPlayerManager getTrackedPlayerManager() {
        return BuiltInHudModules.MINIMAP.getCurrentSession().getProcessor().getSyncedTrackedPlayerManager();
    }

    @Override
    public void onPlayerInfo(PlayerInfoPayload payload, PeerConnection sender) {
        PlayerPositionData pos = payload.getComponent(PlayerPositionData.class);
        PlayerWorldData world = payload.getComponent(PlayerWorldData.class);

        if (pos != null) {
            getTrackedPlayerManager().update(
                payload.playerId,
                pos.coords.x,
                pos.coords.y,
                pos.coords.z,
                (world != null) ? world.dimension : World.OVERWORLD
            );
        }
    }

    @Override
    public void onPlayerDisconnect(UUID playerId, PlayerInfoPayload lastInfo) {
        getTrackedPlayerManager().remove(playerId);
    }
}
