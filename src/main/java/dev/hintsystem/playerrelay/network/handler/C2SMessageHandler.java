package dev.hintsystem.playerrelay.network.handler;

import dev.hintsystem.playerrelay.PlayerRelayServer;
import dev.hintsystem.playerrelay.PlayerUpdateTracker;
import dev.hintsystem.playerrelay.ServerCore;
import dev.hintsystem.playerrelay.logging.NetworkLogger;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.party.PartyMethods;
import dev.hintsystem.playerrelay.party.PartyPayloadHandler;
import dev.hintsystem.playerrelay.payload.*;
import dev.hintsystem.playerrelay.payload.player.PlayerBasicData;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/** Handles messages received from the client on the server */
public class C2SMessageHandler extends PayloadMessageHandler<ServerPlayerEntity> {
    public final NetworkLogger logger;
    private final ServerCore server;

    private final PartyPayloadHandler partyPayloadHandler;

    public C2SMessageHandler(NetworkLogger logger, ServerCore server, PartyMethods partyService) {
        this.logger = logger;
        this.server = server;
        this.partyPayloadHandler = new PartyPayloadHandler(partyService);
        init();
    }

    @Override
    protected void init() {
        register(PayloadRegistry.RELAY_VERSION, this::onPlayerRelayVersion);

        register(PayloadRegistry.PARTY, this::onPartyPayload);
        register(PayloadRegistry.PLAYER_INFO, this::onPlayerInfo);
        register(PayloadRegistry.PLAYER_INVENTORY, this::onPlayerInventory);
    }

    public void onPlayerRelayVersion(RelayVersionPayload version, ServerPlayerEntity player) {
        PlayerRelayServer.sendToClient(player, new RelayVersionPayload().packet());
        if (version.networkVersion != RelayVersionPayload.NETWORK_VERSION) {
            server.listeningPlayers.remove(player.getUuid());
            return;
        }

        boolean added = server.listeningPlayers.add(player.getUuid());
        if (added) server.onPlayerSync(player);
    }

    public void onPartyPayload(PartyPayload party, ServerPlayerEntity player) {
        try {
            PartyPayload authoritativeParty = party.withActorId(player.getUuid());
            authoritativeParty.handleAction(partyPayloadHandler);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isEmpty()) message = e.toString();
            PlayerRelayServer.sendToClient(player, party.fail(message).packet());
        }
    }

    public void onPlayerInfo(PlayerInfoPayload playerInfo, ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUpdateTracker updateTracker = server.playerUpdateTrackers.get(playerId);
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

            server.broadcastPayload(
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
            server.broadcastPayload(
                payload.packet(),
                player.getUuid()
            );
        }
    }
}
