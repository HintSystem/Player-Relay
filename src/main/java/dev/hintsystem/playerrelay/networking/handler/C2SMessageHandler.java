package dev.hintsystem.playerrelay.networking.handler;

import dev.hintsystem.playerrelay.PlayerRelay;
import dev.hintsystem.playerrelay.PlayerRelayServer;
import dev.hintsystem.playerrelay.PlayerUpdateTracker;
import dev.hintsystem.playerrelay.ServerCore;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Handles messages received from the client on the server */
public class C2SMessageHandler extends PayloadMessageHandler<ServerPlayerEntity> {
    public final NetworkLogger logger;

    public C2SMessageHandler(NetworkLogger logger) {
        this.logger = logger;

        init();
    }

    @Override
    protected void init() {
        register(RelayVersionPayload.class, this::onPlayerRelayVersion);

        register(PlayerInfoPayload.class, this::onPlayerInfo);
        register(PlayerInventoryPayload.class, this::onPlayerInventory);
    }

    public void onPlayerRelayVersion(RelayVersionPayload version, ServerPlayerEntity player) {
        PlayerRelay.LOGGER.info(player.getStringifiedName() + " SENT RELAY VERSION");
        PlayerRelayServer.sendToClient(player, new RelayVersionPayload().packet());
        if (version.networkVersion != RelayVersionPayload.NETWORK_VERSION) {
            ServerCore.listeningPlayers.remove(player.getUuid());
            return;
        }

        boolean added = ServerCore.listeningPlayers.add(player.getUuid());
        if (added) {
            PlayerRelayServer.sendToClient(player, PlayerInventoryPayload.respond(player, true).packet()); // Gratuitous ender chest inventory packet
            for (PlayerUpdateTracker playerTracker : ServerCore.playerUpdateTrackers.values()) {
                PlayerRelayServer.sendToClient(player, playerTracker.getCurrentState().packet());
            }
        }
    }

    public void onPlayerInfo(PlayerInfoPayload playerInfo, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUpdateTracker updateTracker = ServerCore.playerUpdateTrackers.get(playerId);
        if (updateTracker == null) { return; }

        // Build delta with client-only data that's unavailable on the server
        PlayerUpdateTracker.DeltaBuilder deltaBuilder = updateTracker.beginDelta();

        deltaBuilder.withFlag(PlayerInfoPayload.FLAGS.AFK, playerInfo.isAfk());

        // Update name color if provided
        PlayerBasicData basicData = playerInfo.getComponent(PlayerBasicData.class);
        if (basicData != null) {
            deltaBuilder.with(new PlayerBasicData(player.getStringifiedName(), basicData.nameColor));
        }

        PlayerInfoPayload infoDelta = deltaBuilder.build();
        if (infoDelta != null) {
            updateTracker.commitDelta(infoDelta);

            ServerCore.broadcastPayload(
                player.getEntityWorld().getServer(),
                infoDelta.packet(),
                player.getUuid()
            );
        }
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

        Payload payload = message.getPayload();
        if (payload instanceof WaypointPayload || payload instanceof GenericPacketPayload) {
            ServerCore.broadcastPayload(
                player.getEntityWorld().getServer(),
                payload.packet(),
                player.getUuid()
            );
        }
    }
}
