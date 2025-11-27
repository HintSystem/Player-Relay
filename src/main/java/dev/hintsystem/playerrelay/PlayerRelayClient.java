package dev.hintsystem.playerrelay;

import dev.hintsystem.playerrelay.command.PlayerRelayCommands;
import dev.hintsystem.playerrelay.config.ClientConfig;
import dev.hintsystem.playerrelay.gui.PlayerList;
import dev.hintsystem.playerrelay.logging.ClientLogHandler;
import dev.hintsystem.playerrelay.mods.SupportXaerosMapMods;
import dev.hintsystem.playerrelay.networking.P2PNetworkManager;
import dev.hintsystem.playerrelay.networking.PayloadMessage;
import dev.hintsystem.playerrelay.networking.handler.ClientMessageHandler;
import dev.hintsystem.playerrelay.networking.handler.DefaultP2PMessageHandler;
import dev.hintsystem.playerrelay.networking.handler.S2CMessageHandler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.util.Identifier;

public class PlayerRelayClient implements ClientModInitializer {
    public static ClientConfig config = new ClientConfig();

    private void initModSupport() {
        if (FabricLoader.getInstance().isModLoaded("xaerominimap")
            || FabricLoader.getInstance().isModLoaded("xaeroworldmap")) { SupportXaerosMapMods.init(); }
    }

    @Override
    public void onInitializeClient() {
        CommonCore.networkLogger
            .addLogHandler(new ClientLogHandler());

        CommonCore.initConfig(config);
        CommonCore.initP2PNetwork(
            new P2PNetworkManager(
                config,
                new DefaultP2PMessageHandler(CommonCore.networkLogger, CommonCore.p2pPlayers, new ClientCore.ClientInfoProvider(), new ClientMessageHandler<>(CommonCore.networkLogger)),
                CommonCore.networkLogger
            )
        );

        ServerCore.setLocalPlayerId(ClientCore.getClientUuid());

        S2CMessageHandler clientHandler = new S2CMessageHandler(CommonCore.networkLogger, CommonCore.serverPlayers);
        ClientPlayNetworking.registerGlobalReceiver(PayloadMessage.Packet.PACKET_TYPE, (payloadMessage, context) -> {
            clientHandler.handleMessage(payloadMessage, null);
        });

        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> ClientCore.onServerJoin(c));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> ClientCore.onServerLeave());

        ClientLifecycleEvents.CLIENT_STARTED.register(c -> initModSupport());
        ClientTickEvents.END_CLIENT_TICK.register(ClientCore::onTickEnd);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> CommonCore.onStopping());

        PlayerList playerList = new PlayerList();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(PlayerRelay.MOD_ID, "before_chat"), playerList);
        ClientTickEvents.END_CLIENT_TICK.register(playerList::onClientTickEnd);

        new PlayerRelayCommands(CommonCore.getP2PNetworkManager()).register();
    }

    public static void sendToServer(PayloadMessage.Packet payload) {
        if (ClientPlayNetworking.canSend(PayloadMessage.Packet.PACKET_ID)) {
            ClientPlayNetworking.send(payload);
        }
    }
}