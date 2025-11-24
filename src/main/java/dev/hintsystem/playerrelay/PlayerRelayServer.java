package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.config.ServerConfig;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.handler.P2PMessageHandler;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerRelayServer implements DedicatedServerModInitializer {
    public static ServerConfig config = new ServerConfig();

    @Override
    public void onInitializeServer() {
        CommonCore.initConfig(config);

        PlayerRelay.initializeP2PNetwork(
            new P2PNetworkManager(
                config,
                new P2PMessageHandler(CommonCore.networkLogger, CommonCore.p2pPlayers, null),
                CommonCore.networkLogger
            )
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerCore.onStopping());

        PlayerRelay.LOGGER.info("Player Relay server initialized");
    }

    public static void sendToClient(ServerPlayerEntity player, PayloadMessage.Packet payloadMessage) {
        if (ServerPlayNetworking.canSend(player, PayloadMessage.Packet.PACKET_ID)) {
            ServerPlayNetworking.send(player, payloadMessage);
        }
    }
}
