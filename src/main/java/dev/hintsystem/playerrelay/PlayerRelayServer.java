package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.config.ServerConfig;
import dev.hintsystem.playerrelay.network.P2PNetworkManager;
import dev.hintsystem.playerrelay.network.PayloadMessage;
import dev.hintsystem.playerrelay.network.handler.P2PMessageHandler;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerRelayServer implements DedicatedServerModInitializer {
    public static ServerConfig config = new ServerConfig();

    @Override
    public void onInitializeServer() {
        CommonCore.initConfig(config);
        CommonCore.initP2PNetwork(
            new P2PNetworkManager(
                CommonCore.peerConnections,
                new P2PMessageHandler(
                    CommonCore.peerConnections,
                    null,
                    null,
                    CommonCore.networkLogger
                ),
                config,
                CommonCore.networkLogger
            )
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CommonCore.onStopping());

        PlayerRelay.LOGGER.info("Player Relay server initialized");
    }

    public static void sendToClient(ServerPlayerEntity player, PayloadMessage.Packet payloadMessage) {
        if (ServerPlayNetworking.canSend(player, PayloadMessage.Packet.PACKET_ID)) {
            ServerPlayNetworking.send(player, payloadMessage);
        }
    }
}
