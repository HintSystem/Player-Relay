package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.PlayerRelayServer;
import dev.hintsystem.playerrelay.ServerCore;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.TrackedPlayerList;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

/** Handles messages received from the client on the server */
public class C2SMessageHandler extends PayloadMessageHandler<ServerPlayerEntity> {
    public final NetworkLogger logger;
    public final TrackedPlayerList.Sublist playerList;

    public C2SMessageHandler(NetworkLogger logger, TrackedPlayerList.Sublist playerList) {
        this.logger = logger;
        this.playerList = playerList;

        init();
    }

    @Override
    protected void init() {
        register(RelayVersionPayload.class, ServerCore::onPlayerRelayVersion);

        register(PlayerInfoPayload.class, this::onPlayerInfo);
        register(PlayerInventoryPayload.class, this::onPlayerInventory);
    }

    public void onPlayerInfo(PlayerInfoPayload playerInfo, ServerPlayerEntity player) {
        PlayerInfoPayload lastInfo = playerList.get(player.getUuid());
        if (lastInfo == null) return;

        // Only update data that is unavailable on the server
        PlayerBasicData basicData = playerInfo.getComponent(PlayerBasicData.class);
        if (basicData != null) lastInfo.setComponent(basicData);

        lastInfo.setFlag(PlayerInfoPayload.FLAGS.AFK, playerInfo.isAfk());
    }

    public void onPlayerInventory(PlayerInventoryPayload inventory, ServerPlayerEntity player) {
        if (!inventory.isRequest()) return;

        PlayerManager playerManager = player.getEntityWorld().getServer().getPlayerManager();
        ServerPlayerEntity requestedPlayer = playerManager.getPlayer(inventory.playerId);
        if (requestedPlayer == null) return;

        PlayerRelayServer.sendToClient(player,
            PlayerInventoryPayload.respond(requestedPlayer, inventory.isEnderChest()).packet()
        );
    }

    @Override
    protected void onMessagePass(PayloadMessage message, ServerPlayerEntity player) {
        if (!message.getPayloadType().shouldForward()) return;

        IPayload payload = message.getPayload();
        if (payload instanceof WaypointPayload) {
            ServerCore.broadcastPayload(
                player.getEntityWorld().getServer(),
                payload.packet(),
                player.getUuid()
            );
        }
    }
}
